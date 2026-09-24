package com.coparently.app.wire

import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.ContactWindowCodec
import com.coparently.app.domain.custody.CustodyDecision
import com.coparently.app.domain.custody.CustodyDecisionOutcome
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.CustodyTimestamp
import com.coparently.app.domain.custody.CustodyWriteKind
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.parentingplan.PlanCitationCodec
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime

/**
 * `custody_models`: read and written by `FirestoreCustodyDataSource` itself — `getCustody` and
 * `setCustody` over a mocked Firestore, so the private mapping inside it is what runs.
 *
 * The round trip is the swap or proposal write (`CustodyModelRepository.writeSwap`,
 * `decideProposal`): the stored `SharedCustody` sent back through `setCustody`, a whole-document
 * `set()`. CLAUDE.md items 24, 30, 32 and 33 are the properties: `contactWindows`,
 * `seasonalLayers`, `childOverrides` and `proposalPlanCitation` come back **verbatim** — a missing
 * key stays missing, unreadable entries stay — and [invariants] adds the pattern write's half:
 * `CustodyModel.seasonalLayersWire()`/`childOverridesWire()`, which a pattern save writes, keep
 * every stored entry, unreadable ones included.
 */
internal object CustodyWireContract : WireContract {

    override val collection = "custody_models"

    override val alwaysWrites = setOf(
        "id", "participants", "lastModifiedBy", "modelType", "patternDays", "momDayIndices", "startDate",
        "repeatYearly", "createdAt", "lastModifiedAt", "lastModifiedKind"
    )

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        val custody = get(document) ?: return null
        val model = custody.model
        return mapOf(
            "id" to model.id,
            "modelType" to CustodyModelType.toString(model.modelType),
            "patternDays" to model.patternDays,
            "momDayIndices" to model.momDayIndices.sorted(),
            "startDate" to model.startDate.toString(),
            "lastModifiedBy" to custody.lastModifiedBy,
            "lastModifiedAtMillis" to custody.lastModifiedAtMillis,
            "lastModifiedKind" to custody.lastModifiedKind.name,
            "repeatYearly" to custody.repeatYearly,
            "contactWindows" to ContactWindowCodec.encodeAll(model.contactWindows),
            "contactWindowsWire" to custody.contactWindowsWire,
            "seasonalLayers" to model.seasonalLayers.map { it.id },
            "unreadableLayers" to model.unreadableLayers,
            "seasonalLayersWire" to custody.seasonalLayersWire,
            "childOverrides" to model.childOverrides.map { it.childId },
            "unreadableChildOverrides" to model.unreadableChildOverrides,
            "childOverridesWire" to custody.childOverridesWire,
            "proposal" to custody.proposal?.let(::proposalView),
            "lastDecision" to custody.lastDecision?.outcome?.name,
            "dayOverrides" to custody.dayOverrides.mapValues { (_, swap) -> swap.status.name },
            "lastSwapDate" to custody.lastSwapDate
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? {
        val custody = get(document) ?: return null
        val participants = (document["participants"] as? List<*>).orEmpty().filterIsInstance<String>()
        return set(participants, custody)
    }

    override fun invariants(document: Map<String, Any?>): List<String> {
        val custody = get(document) ?: return emptyList()
        return buildList {
            patternWriteKeeps("seasonalLayers", document["seasonalLayers"], custody.model.seasonalLayersWire())
                ?.let(::add)
            patternWriteKeeps("childOverrides", document["childOverrides"], custody.model.childOverridesWire())
                ?.let(::add)
            val stored = document["proposal"] as? Map<*, *>
            custody.proposal?.let { pending ->
                val layers = pending.model.seasonalLayersWire()
                patternWriteKeeps("proposal.seasonalLayers", stored?.get("seasonalLayers"), layers)?.let(::add)
                val children = pending.model.childOverridesWire()
                patternWriteKeeps("proposal.childOverrides", stored?.get("childOverrides"), children)?.let(::add)
            }
        }
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "pattern-write",
            about = "A pattern write, as CustodyModelRepository.pushToFirestore builds it: every list key.",
            document = set(PARTICIPANTS, patternWrite())
        ),
        CurrentWrite(
            case = "swap-beside-proposal",
            about = "A swap write re-sending a pending proposal that cites the parenting plan, and the last decision.",
            document = set(PARTICIPANTS, proposalWrite())
        )
    )

    private fun proposalView(proposal: CustodyProposal): Map<String, Any?> = mapOf(
        "proposedBy" to proposal.proposedBy,
        "patternDays" to proposal.model.patternDays,
        "momDayIndices" to proposal.model.momDayIndices.sorted(),
        "contactWindows" to ContactWindowCodec.encodeAll(proposal.model.contactWindows),
        "contactWindowsWire" to proposal.contactWindowsWire,
        "seasonalLayers" to proposal.model.seasonalLayers.map { it.id },
        "seasonalLayersWire" to proposal.seasonalLayersWire,
        "childOverrides" to proposal.model.childOverrides.map { it.childId },
        "childOverridesWire" to proposal.childOverridesWire,
        "planCitation" to proposal.planCitationWire,
        "planCitationQuestion" to PlanCitationCodec.questionIdOf(proposal.planCitationWire)
    )

    /** A pattern write keeps every entry the document stored; returns a failure message when it does not. */
    private fun patternWriteKeeps(key: String, stored: Any?, written: List<String>): String? {
        val entries = (stored as? List<*>).orEmpty().filterIsInstance<String>()
        val dropped = entries - written.toSet()
        return if (dropped.isEmpty()) {
            null
        } else {
            "a pattern write would drop $key entries $dropped — unreadable entries must be kept verbatim"
        }
    }

    /** `FirestoreCustodyDataSource.getCustody` over [document]. */
    private fun get(document: Map<String, Any?>): SharedCustody? {
        val snapshot = mockk<DocumentSnapshot>()
        @Suppress("UNCHECKED_CAST")
        every { snapshot.data } returns document as Map<String, Any>
        val (dataSource, reference) = dataSource()
        every { reference.get() } returns Tasks.forResult(snapshot)
        return runBlocking { dataSource.getCustody(DOCUMENT_ID) }
    }

    /** `FirestoreCustodyDataSource.setCustody`, returning what it would store. */
    private fun set(participants: List<String>, custody: SharedCustody): Map<String, Any?> {
        val (dataSource, reference) = dataSource()
        val payload = slot<Any>()
        every { reference.set(capture(payload)) } returns Tasks.forResult(null)
        runBlocking { dataSource.setCustody(DOCUMENT_ID, participants, custody) }
        @Suppress("UNCHECKED_CAST")
        return payload.captured as Map<String, Any?>
    }

    private fun dataSource(): Pair<FirestoreCustodyDataSource, DocumentReference> {
        val reference = mockk<DocumentReference>()
        val collection = mockk<CollectionReference>()
        every { collection.document(any()) } returns reference
        val firestore = mockk<FirebaseFirestore>()
        every { firestore.collection("custody_models") } returns collection
        return FirestoreCustodyDataSource(firestore) to reference
    }

    private fun baseModel(
        windows: List<ContactWindow>,
        layers: List<SeasonalLayer>,
        children: List<ChildScheduleOverride>
    ) = CustodyModel(
        id = "model-current",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.of(2026, 5, 4),
        contactWindows = windows,
        seasonalLayers = layers,
        childOverrides = children
    )

    private fun summer() = SeasonalLayer(
        id = "summer",
        name = "Summer 2026",
        fromDate = LocalDate.of(2026, 7, 1),
        toDate = LocalDate.of(2026, 8, 31),
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate.of(2026, 7, 1),
        contactWindows = emptyList(),
        priority = 0
    )

    private fun anyaOverride() = ChildScheduleOverride(
        childId = "c1",
        patternDays = 7,
        momDayIndices = setOf(0, 1, 2),
        startDate = LocalDate.of(2026, 5, 4),
        contactWindows = emptyList()
    )

    private fun patternWrite(): SharedCustody {
        val model = baseModel(
            windows = listOf(ContactWindow(9, LocalTime.of(15, 0), LocalTime.of(19, 0), parent = "dad")),
            layers = listOf(summer()),
            children = listOf(anyaOverride())
        )
        return SharedCustody(
            model = model,
            lastModifiedBy = "uidA",
            lastModifiedAtMillis = CustodyTimestamp.fromWire("2026-05-04T08:00:00"),
            createdAt = "2026-05-01T09:00:00",
            lastModifiedKind = CustodyWriteKind.PATTERN,
            contactWindowsWire = ContactWindowCodec.encodeAll(model.contactWindows),
            seasonalLayersWire = model.seasonalLayersWire(),
            childOverridesWire = model.childOverridesWire()
        )
    }

    private fun proposalWrite(): SharedCustody {
        val agreed = baseModel(windows = emptyList(), layers = emptyList(), children = emptyList())
        val proposed = agreed.copy(id = DOCUMENT_ID, momDayIndices = (7..13).toSet(), isActive = false)
        return SharedCustody(
            model = agreed,
            lastModifiedBy = "uidB",
            lastModifiedAtMillis = CustodyTimestamp.fromWire("2026-05-04T08:00:00"),
            createdAt = "2026-05-01T09:00:00",
            proposal = CustodyProposal(
                model = proposed,
                repeatYearly = true,
                proposedBy = "uidB",
                proposedAt = "2026-05-06T10:00:00",
                contactWindowsWire = emptyList(),
                seasonalLayersWire = emptyList(),
                childOverridesWire = emptyList(),
                planCitationWire = "p1|care_weekday|0123456789abcdef"
            ),
            lastDecision = CustodyDecision(
                outcome = CustodyDecisionOutcome.DECLINED,
                by = "uidA",
                at = "2026-05-05T09:00:00",
                proposalAt = "2026-05-04T21:00:00",
                note = "Not in June"
            ),
            dayOverrides = mapOf(
                "2026-05-16" to DayOverride(
                    toParent = "dad",
                    requestedBy = "uidA",
                    requestedAt = "2026-05-05T08:00:00",
                    status = DayOverrideStatus.ACCEPTED,
                    decidedBy = "uidB",
                    decidedAt = "2026-05-05T09:30:00",
                    groupId = "run-1"
                )
            ),
            lastSwapDate = "2026-05-16",
            lastModifiedKind = CustodyWriteKind.SWAP,
            contactWindowsWire = emptyList(),
            seasonalLayersWire = emptyList(),
            childOverridesWire = emptyList()
        )
    }

    private const val DOCUMENT_ID = "uidA__uidB"
    private val PARTICIPANTS = listOf("uidA", "uidB")
}
