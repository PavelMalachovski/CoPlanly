package com.coparently.app.data.family

import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.ChildInfoEntity
import com.coparently.app.data.local.entity.CustodyModelEntity
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.local.entity.ExpenseEntity
import com.coparently.app.data.local.entity.PetEntity
import com.coparently.app.data.local.entity.UserEntity
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.data.repository.toDomainModel
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What kind of record is waiting for a family, with the prefix of its [UnassignedRecord.key]. */
enum class UnassignedKind(val key: String) {
    CHILD("child"),
    PET("pet"),
    EVENT("event"),
    EXPENSE("expense"),
    CUSTODY("custody")
}

/**
 * One of the signed-in parent's own records that names no family while they are in one — "Only
 * me" on every screen (`RecordPlace.ONLY_ME`) until they assign it.
 *
 * Only what the list draws: the record itself stays in its table, and the screen formats [date]
 * and [amount] in the reader's language.
 *
 * @property kind Which table the record lives in.
 * @property id The record's id in that table.
 * @property title The event's or expense's title, the child's or pet's name; blank for a custody
 *   schedule, which is named by [custodyType].
 * @property date The event's start day, the expense's day, the child's date of birth or the day a
 *   schedule starts; null when the record has none.
 * @property amount An expense's amount, in [currency].
 * @property currency An expense's ISO 4217 currency.
 * @property custodyType A custody schedule's pattern.
 */
data class UnassignedRecord(
    val kind: UnassignedKind,
    val id: String,
    val title: String,
    val date: LocalDate? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val custodyType: CustodyModelType? = null
) {
    /** `kind:id` — what "Keep it only mine" remembers ([PreferenceKeys.UNASSIGNED_KEPT_PREFIX]). */
    val key: String get() = "${kind.key}:$id"
}

/**
 * The records waiting for a family, and the families they may go to.
 *
 * @property records Every record still to be answered, children and pets first, then events,
 *   expenses and the custody schedule. Empty while the parent is in no family: a record of no
 *   family is then simply theirs, not a question.
 * @property families The parent's live families, oldest relationship first, co-parents unnamed —
 *   naming them costs a read per family, and only the screen needs it.
 * @property onScreenFamilyId The family the switcher shows: the only one a custody schedule can go
 *   to, because the schedule is submitted through the family on screen.
 * @property askOnHome Whether Home asks about [records] now: two families or more, something to
 *   ask about, and not yet asked for this set of co-parents.
 */
data class UnassignedSnapshot(
    val records: List<UnassignedRecord> = emptyList(),
    val families: List<FamilyOption> = emptyList(),
    val onScreenFamilyId: String? = null,
    val askOnHome: Boolean = false
)

/** How an assignment landed. */
enum class AssignOutcome {
    /** The record now names the family; a shared one goes up with the family's audience. */
    ASSIGNED,

    /** A custody schedule went to the family as a proposal its co-parent answers. */
    PROPOSED,

    /** A custody schedule was refused: the co-parent's own proposal waits for an answer. */
    PROPOSAL_WAITING,

    /** Nothing was written: the family is not one of the parent's live ones, or the write failed. */
    NOT_SENT,

    /** The record already names a family, or is gone. */
    GONE
}

/**
 * "Records without a family" (owner decision, October 2026): the signed-in parent's own records
 * that name no family while they co-parent, and the parent's answer for each.
 *
 * A record written before a family could be named — while unpaired, or before the column existed
 * — is stamped by `FamilyIdBackfill` only where its family is not a guess: one co-parent and no
 * earlier family. Everywhere else it stays null, which is "Only me": shared with nobody
 * (`FamilyAudience.recordPartnerFor`), shown in every family with the mark, counted in no family's
 * totals. Only the parent can say where it belongs, so this lists it and lets them answer:
 *
 * - **Assign it to a live family.** An event, a child or a pet is stamped and queued, and the sync
 *   uploads it with that family's audience. An expense, which has no upload outbox, is written
 *   back through [ExpenseRepository.updateExpense]. A custody schedule is submitted to the family
 *   on screen through [CustodyModelRepository.submitPattern] — a family with a schedule gets a
 *   proposal, never an overwrite (CLAUDE.md items 30 and 41).
 * - **Keep it only mine.** Nothing is written to the record; it is remembered per account and not
 *   asked about again, and it keeps its mark.
 *
 * Never a record of an **ended** family (that is history, `RecordPlace.EARLIER_FAMILY`), never a
 * co-parent's record (`FamilyIdBackfill` gives those the family with their author), never a private
 * event (it belongs to nobody but its author anyway) and never a pending deletion.
 */
@Singleton
// The five tables this answers about, the two repositories whose write paths an expense and a
// schedule must take, and the sync and the store: the list is the scope of the class.
@Suppress("LongParameterList")
class UnassignedRecords @Inject constructor(
    private val userDao: UserDao,
    private val eventDao: EventDao,
    private val expenseDao: ExpenseDao,
    private val childInfoDao: ChildInfoDao,
    private val petDao: PetDao,
    private val custodyModelDao: CustodyModelDao,
    private val expenseRepository: ExpenseRepository,
    private val custodyModelRepository: CustodyModelRepository,
    private val syncRequester: SyncRequester,
    private val encryptedPreferences: EncryptedPreferences
) {

    /**
     * Bumped by every answer stored in [encryptedPreferences], which cannot be observed, so the
     * snapshots re-read it.
     */
    private val answers = MutableStateFlow(0)

    /**
     * [uid]'s records waiting for a family, following Room, the co-parent set and every answer.
     *
     * The DAO queries narrow; the same conditions are applied again here, so the rule is stated in
     * one place a JVM test can reach.
     */
    fun observe(uid: String): Flow<UnassignedSnapshot> {
        val families = userDao.observeUserById(uid)
            .map { row -> familiesOf(uid, row) }
            .distinctUntilChanged()
        val rows = combine(
            eventDao.observeOwnWithoutFamily(uid),
            expenseDao.observeOwnWithoutFamily(uid),
            childInfoDao.observeOwnWithoutFamily(uid),
            petDao.observeOwnWithoutFamily(uid),
            custodyModelDao.observeActiveModels()
        ) { events, expenses, children, pets, schedules ->
            OwnRows(
                records = children.filter { it.isOwnWithoutFamily(uid) }.map { it.asRecord() } +
                    pets.filter { it.isOwnWithoutFamily(uid) }.map { it.asRecord() } +
                    events.filter { it.isOwnWithoutFamily(uid) }.map { it.asRecord() } +
                    expenses.filter { it.isOwnWithoutFamily(uid) }.map { it.asRecord() },
                schedules = schedules.filter { it.isActive && it.familyId == null }.map { it.asRecord() }
            )
        }
        return combine(families, rows, answers) { live, own, _ -> snapshotOf(uid, live, own) }
    }

    /**
     * Gives [record] to [familyId], one of [uid]'s live families.
     *
     * A custody schedule goes only to the family on screen, and is remembered as answered once it
     * is activated or proposed: a proposal leaves the row as it was until the co-parent agrees.
     */
    suspend fun assign(uid: String, record: UnassignedRecord, familyId: String): AssignOutcome {
        val live = familiesOf(uid, userDao.getUserById(uid))
        if (live.options.none { it.familyId == familyId }) return AssignOutcome.NOT_SENT
        return when (record.kind) {
            UnassignedKind.EVENT -> stamped(eventDao.assignFamily(record.id, familyId, uid))
            UnassignedKind.CHILD -> stamped(childInfoDao.assignFamily(record.id, familyId, uid))
            UnassignedKind.PET -> stamped(petDao.assignFamily(record.id, familyId, uid))
            UnassignedKind.EXPENSE -> assignExpense(uid, record.id, familyId)
            UnassignedKind.CUSTODY -> assignSchedule(uid, record, familyId, live.onScreen)
        }
    }

    /** "Keep it only mine": [record] is not asked about again; it stays only [uid]'s. */
    fun keep(uid: String, record: UnassignedRecord) {
        val kept = keptKeys(uid) + record.key
        encryptedPreferences.putString(keptKey(uid), kept.sorted().joinToString(PreferenceKeys.LIST_SEPARATOR))
        answers.update { it + 1 }
    }

    /** Home asked, and was answered or put off: not again until [uid]'s co-parent set changes. */
    suspend fun markAsked(uid: String) {
        val families = familiesOf(uid, userDao.getUserById(uid)).options
        encryptedPreferences.putString(askedKey(uid), coParentSet(families))
        answers.update { it + 1 }
    }

    private fun snapshotOf(uid: String, live: LiveFamilies, own: OwnRows): UnassignedSnapshot {
        if (live.options.isEmpty()) return UnassignedSnapshot()
        val kept = keptKeys(uid)
        // A schedule of no family is the family's own while there is exactly one
        // (`ActiveCustodyRow.belongsTo`); it is a question only from two.
        val schedules = if (live.options.size > 1) own.schedules else emptyList()
        val records = (own.records + schedules).filterNot { it.key in kept }
        val ask = live.options.size > 1 && records.isNotEmpty() &&
            encryptedPreferences.getString(askedKey(uid)) != coParentSet(live.options)
        return UnassignedSnapshot(records, live.options, live.onScreen, ask)
    }

    /** An event, a child or a pet stamped (one row) or not (none): a sync carries it to the family. */
    private fun stamped(rows: Int): AssignOutcome {
        if (rows == 0) return AssignOutcome.GONE
        syncRequester.requestSyncNow()
        return AssignOutcome.ASSIGNED
    }

    /**
     * An expense has no upload outbox — an add or an edit pushes inline — so it goes back through
     * the repository's own update, which writes Room, tells the family's co-parent and pushes it.
     * Every other field is kept: the split it was recorded under is a snapshot, never re-priced.
     */
    private suspend fun assignExpense(uid: String, id: String, familyId: String): AssignOutcome {
        val expense = expenseRepository.getExpenseById(id)
            ?.takeIf { it.familyId.isNullOrBlank() && it.createdByFirebaseUid == uid }
            ?: return AssignOutcome.GONE
        expenseRepository.updateExpense(expense.copy(familyId = familyId, syncedToFirestore = false))
        return AssignOutcome.ASSIGNED
    }

    /**
     * A schedule of no family, submitted to the family on screen: activated where the family has no
     * schedule yet, proposed where it has one. Never to another family — the submission acts on the
     * family on screen, so that is the only one offered.
     */
    private suspend fun assignSchedule(
        uid: String,
        record: UnassignedRecord,
        familyId: String,
        onScreen: String?
    ): AssignOutcome {
        val model = custodyModelDao.getModelById(record.id)
            ?.takeIf { it.isActive && it.familyId == null }
            ?.let { runCatching { it.toDomainModel() }.getOrNull() }
        val outcome = when {
            familyId != onScreen -> AssignOutcome.NOT_SENT
            model == null -> AssignOutcome.GONE
            else -> when (custodyModelRepository.submitPattern(model)) {
                PatternSubmission.ACTIVATED -> AssignOutcome.ASSIGNED
                PatternSubmission.PROPOSED -> AssignOutcome.PROPOSED
                PatternSubmission.COPARENT_PROPOSAL_WAITING -> AssignOutcome.PROPOSAL_WAITING
                PatternSubmission.NOT_SENT -> AssignOutcome.NOT_SENT
            }
        }
        // Answered: an activated row now names the family, and a proposed one stays as it was
        // until the co-parent agrees — it must not be asked about again meanwhile.
        if (outcome == AssignOutcome.ASSIGNED || outcome == AssignOutcome.PROPOSED) keep(uid, record)
        return outcome
    }

    private fun keptKeys(uid: String): Set<String> =
        encryptedPreferences.getString(keptKey(uid)).orEmpty()
            .split(PreferenceKeys.LIST_SEPARATOR)
            .filter { it.isNotBlank() }
            .toSet()

    private fun keptKey(uid: String) = "${PreferenceKeys.UNASSIGNED_KEPT_PREFIX}$uid"

    private fun askedKey(uid: String) = "${PreferenceKeys.UNASSIGNED_ASKED_PREFIX}$uid"
}

/** The parent's live families and the one on screen. */
private data class LiveFamilies(val options: List<FamilyOption>, val onScreen: String?)

/** This parent's rows of no family, before the family count and the answers are applied. */
private data class OwnRows(val records: List<UnassignedRecord>, val schedules: List<UnassignedRecord>)

/**
 * Every co-parent the row names — the list and the family on screen, as `FamilyAudience.coParents`
 * reads them — each as a family.
 */
private fun familiesOf(uid: String, row: UserEntity?): LiveFamilies {
    if (row == null) return LiveFamilies(emptyList(), null)
    val partners = FamilyAudience.coParents(
        myUid = uid,
        livePartners = EventVersionDocument.decodeAudience(row.partnerIdsJson),
        selectedPartner = row.partnerId
    )
    val options = partners.mapNotNull { partner ->
        FamilyKey.orNull(uid, partner)?.let { FamilyOption(it, partner) }
    }
    return LiveFamilies(options, FamilyKey.orNull(uid, row.partnerId))
}

/** The co-parents, sorted and comma-joined: what Home was last asked for. */
private fun coParentSet(families: List<FamilyOption>): String =
    families.map { it.partnerUid }.sorted().joinToString(",")

/** [uid]'s own row, naming no family, and not a pending deletion. */
private fun ownWithoutFamily(createdBy: String?, familyId: String?, deletedAtMillis: Long?, uid: String) =
    createdBy == uid && familyId.isNullOrBlank() && deletedAtMillis == null

private fun EventEntity.isOwnWithoutFamily(uid: String) =
    !isPrivate && ownWithoutFamily(createdByFirebaseUid, familyId, deletedAtMillis, uid)

private fun ExpenseEntity.isOwnWithoutFamily(uid: String) =
    ownWithoutFamily(createdByFirebaseUid, familyId, deletedAtMillis, uid)

private fun ChildInfoEntity.isOwnWithoutFamily(uid: String) =
    ownWithoutFamily(createdByFirebaseUid, familyId, deletedAtMillis, uid)

private fun PetEntity.isOwnWithoutFamily(uid: String) =
    ownWithoutFamily(createdByFirebaseUid, familyId, deletedAtMillis, uid)

private fun EventEntity.asRecord() =
    UnassignedRecord(UnassignedKind.EVENT, id, title, date = startDateTime.toLocalDate())

private fun ExpenseEntity.asRecord() = UnassignedRecord(
    kind = UnassignedKind.EXPENSE,
    id = id,
    title = title,
    date = date,
    amount = amount,
    currency = currency
)

private fun ChildInfoEntity.asRecord() =
    UnassignedRecord(UnassignedKind.CHILD, id, childName, date = dateOfBirth?.toLocalDate())

private fun PetEntity.asRecord() = UnassignedRecord(UnassignedKind.PET, id, name)

private fun CustodyModelEntity.asRecord() = UnassignedRecord(
    kind = UnassignedKind.CUSTODY,
    id = id,
    title = "",
    date = runCatching { LocalDate.parse(startDate) }.getOrNull(),
    custodyType = CustodyModelType.fromString(modelType)
)
