package com.coparently.app.data.family

import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreFamilySettingsDataSource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.data.remote.firebase.FirestoreParentingPlanDataSource
import com.coparently.app.data.requests.RequestClocks
import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.data.support.SupportRead
import com.coparently.app.domain.changerequests.ClocksAt
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.support.SupportAgreement
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cross-family signals behind the switcher's dot (M-8): chat, change requests and schedule.
 *
 * What is pinned: the rules that read one conversation document and one custody document, that
 * a one-family account attaches no listener at all, that the family on screen is never watched,
 * that a switch or a sign-out releases the listeners it held, that each kind is reported as
 * itself, and that a listener which keeps failing gives up — for its own kind only — rather than
 * retrying for the life of the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtherFamiliesSignalsTest {

    private val userRepository = mockk<UserRepository>()
    private val selectedFamilySource = mockk<SelectedFamilySource>()
    private val messages = mockk<FirestoreMessageDataSource>()
    private val requests = mockk<FirestoreChangeRequestDataSource>()
    private val custodySource = mockk<FirestoreCustodyDataSource>()
    private val clocks = mockk<RequestClocks>()
    private val settingsSource = mockk<FirestoreFamilySettingsDataSource>()
    private val supportStore = mockk<SupportAgreementStore>()
    private val professionals = mockk<ProfessionalRepository>()
    private val plans = mockk<FirestoreParentingPlanDataSource>()

    private fun source() = OtherFamiliesSignals(
        userRepository,
        selectedFamilySource,
        messages,
        requests,
        custodySource,
        clocks,
        settingsSource,
        AgreementSignalSources(supportStore, professionals, plans)
    )

    private fun given(
        uid: Flow<String?> = flowOf(ALICE),
        families: Flow<List<FamilyOption>> = flowOf(listOf(BOB_FAMILY, CAROL_FAMILY)),
        selected: Flow<FamilyOption?> = flowOf(BOB_FAMILY)
    ) {
        every { userRepository.observeCurrentUserId() } returns uid
        every { selectedFamilySource.observeFamilies(ALICE) } returns families
        every { selectedFamilySource.observe(ALICE) } returns selected
        // Quiet by default; a test that is about one kind overrides that kind alone.
        every { messages.observeConversation(any()) } returns flowOf(null)
        every { requests.observePendingIdsFrom(any(), any()) } returns flowOf(emptySet())
        every { clocks.observeFamily(any(), any()) } returns flowOf(ClocksAt())
        every { custodySource.observeCustody(any()) } returns flowOf(null)
        every { settingsSource.observeSettings(any()) } returns flowOf(null)
        every { supportStore.observe(any()) } returns flowOf(SupportRead.None)
        every { professionals.observeFamilyGrants(any()) } returns flowOf(emptyList())
        every { plans.observePlan(any()) } returns flowOf(emptyMap())
    }

    // ---- the pure rules ---------------------------------------------------------------

    @Test
    fun `a document newer than my mark is unread, whatever number type Firestore hands back`() {
        assertTrue(OtherFamiliesSignals.hasUnread(document(lastMessageAt = 300L, myMark = 200L), ALICE))
        assertTrue(OtherFamiliesSignals.hasUnread(document(lastMessageAt = 300.0, myMark = 200L), ALICE))
    }

    @Test
    fun `only my own mark counts, not the co-parent's`() {
        val doc = mapOf<String, Any>(
            "lastMessageAt" to 300L,
            "lastReadAt" to mapOf(BOB to 300L)
        )

        assertTrue(OtherFamiliesSignals.hasUnread(doc, ALICE))
    }

    @Test
    fun `a read thread, an empty thread and a missing document raise nothing`() {
        assertFalse(OtherFamiliesSignals.hasUnread(document(lastMessageAt = 300L, myMark = 300L), ALICE))
        assertFalse(OtherFamiliesSignals.hasUnread(mapOf("lastReadAt" to mapOf(ALICE to 1L)), ALICE))
        assertFalse(OtherFamiliesSignals.hasUnread(null, ALICE))
    }

    @Test
    fun `one family is watched by nothing, two by the one not on screen`() {
        assertEquals(
            emptyList(),
            OtherFamiliesSignals.familiesToWatch(listOf(BOB_FAMILY), BOB_FAMILY.familyId)
        )
        assertEquals(
            listOf(CAROL_FAMILY),
            OtherFamiliesSignals.familiesToWatch(listOf(BOB_FAMILY, CAROL_FAMILY), BOB_FAMILY.familyId)
        )
    }

    @Test
    fun `a proposal from the co-parent waits on me, my own proposal waits on nobody here`() {
        assertTrue(OtherFamiliesSignals.hasPendingSchedule(custody(proposedBy = CAROL), ALICE, TODAY))
        assertFalse(OtherFamiliesSignals.hasPendingSchedule(custody(proposedBy = ALICE), ALICE, TODAY))
        assertFalse(OtherFamiliesSignals.hasPendingSchedule(custody(), ALICE, TODAY))
        assertFalse(OtherFamiliesSignals.hasPendingSchedule(null, ALICE, TODAY))
    }

    @Test
    fun `a swap counts only while it is pending, offered by the co-parent, and not yet lived`() {
        val offered = swap(requestedBy = CAROL)
        assertTrue(hasSwap(TODAY to offered))
        assertFalse(hasSwap(TODAY to swap(requestedBy = ALICE)))
        assertFalse(hasSwap(TODAY to offered.copy(status = DayOverrideStatus.ACCEPTED)))
        assertFalse(hasSwap(TODAY.minusDays(1) to offered))
    }

    // ---- the stream -------------------------------------------------------------------

    @Test
    fun `a one-family account attaches no listener`() = runTest {
        given(families = flowOf(listOf(BOB_FAMILY)))

        val emissions = source().observeSignals().toList()

        assertEquals(listOf(emptyMap<String, Set<FamilySignal>>()), emissions)
        verify(exactly = 0) { messages.observeConversation(any()) }
        verify(exactly = 0) { requests.observePendingIdsFrom(any(), any()) }
        verify(exactly = 0) { custodySource.observeCustody(any()) }
        verify(exactly = 0) { settingsSource.observeSettings(any()) }
        verify(exactly = 0) { supportStore.observe(any()) }
        verify(exactly = 0) { professionals.observeFamilyGrants(any()) }
        verify(exactly = 0) { plans.observePlan(any()) }
    }

    @Test
    fun `the family not on screen is watched, and its news is reported`() = runTest {
        given()
        every { messages.observeConversation(CAROL_FAMILY.familyId) } returns
            flowOf(document(lastMessageAt = 300L, myMark = 200L))

        val emissions = source().observeSignals().toList()

        assertEquals(mapOf(CAROL_FAMILY.familyId to setOf(FamilySignal.CHAT)), emissions.last())
        verify(exactly = 0) { messages.observeConversation(BOB_FAMILY.familyId) }
        verify(exactly = 0) { requests.observePendingIdsFrom(ALICE, BOB) }
        verify(exactly = 0) { custodySource.observeCustody(BOB_FAMILY.familyId) }
        verify(exactly = 0) { settingsSource.observeSettings(BOB_FAMILY.familyId) }
    }

    @Test
    fun `a pending split proposal is reported as itself, by the family's settings read by id`() = runTest {
        given()
        every { settingsSource.observeSettings(CAROL_FAMILY.familyId) } returns flowOf(settings(proposedBy = CAROL))

        val emissions = source().observeSignals().toList()

        assertEquals(mapOf(CAROL_FAMILY.familyId to setOf(FamilySignal.SPLIT)), emissions.last())
    }

    @Test
    fun `a split from the co-parent waits on me, my own on nobody, and none asks nothing`() {
        assertTrue(OtherFamiliesSignals.hasPendingSplit(settings(proposedBy = CAROL), ALICE))
        assertFalse(OtherFamiliesSignals.hasPendingSplit(settings(proposedBy = ALICE), ALICE))
        assertFalse(OtherFamiliesSignals.hasPendingSplit(settings(proposedBy = null), ALICE))
        assertFalse(OtherFamiliesSignals.hasPendingSplit(null, ALICE))
    }

    @Test
    fun `a split past its deadline does not light the dot, and under a last chance it does again`() {
        val split = settings(proposedBy = CAROL)
        val clock = splitClock(checkNotNull(split.proposal), answerBy = NOW)

        assertTrue(OtherFamiliesSignals.hasPendingSplit(split, ALICE, clocksAt(NOW - 1, clock)))
        assertFalse(OtherFamiliesSignals.hasPendingSplit(split, ALICE, clocksAt(NOW + DAY, clock)))
        assertTrue(
            OtherFamiliesSignals.hasPendingSplit(
                split,
                ALICE,
                clocksAt(NOW + DAY, clock.copy(lastChanceUntilMillis = NOW + 2 * DAY))
            )
        )
    }

    @Test
    fun `a pending request and a pending proposal are each reported as themselves`() = runTest {
        given()
        every { requests.observePendingIdsFrom(ALICE, CAROL) } returns flowOf(setOf(REQUEST_ID))
        every { custodySource.observeCustody(CAROL_FAMILY.familyId) } returns flowOf(custody(proposedBy = CAROL))

        val emissions = source().observeSignals().toList()

        assertEquals(
            mapOf(CAROL_FAMILY.familyId to setOf(FamilySignal.CHANGE_REQUEST, FamilySignal.SCHEDULE)),
            emissions.last()
        )
    }

    @Test
    fun `a switch releases the old listener and watches the family just left`() = runTest {
        val selected = MutableStateFlow<FamilyOption?>(BOB_FAMILY)
        given(families = MutableStateFlow(listOf(BOB_FAMILY, CAROL_FAMILY)), selected = selected)
        val released = mutableListOf<String>()
        every { messages.observeConversation(any()) } answers { held(firstArg(), released) }
        val emissions = mutableListOf<Map<String, Set<FamilySignal>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source().observeSignals().collect { emissions += it }
        }
        runCurrent()

        assertEquals(setOf(CAROL_FAMILY.familyId), emissions.last().keys)

        selected.value = CAROL_FAMILY
        runCurrent()

        assertEquals(listOf(CAROL_FAMILY.familyId), released)
        assertEquals(setOf(BOB_FAMILY.familyId), emissions.last().keys)
    }

    @Test
    fun `signing out releases every listener and clears the signal`() = runTest {
        val uid = MutableStateFlow<String?>(ALICE)
        given(
            uid = uid,
            families = MutableStateFlow(listOf(BOB_FAMILY, CAROL_FAMILY)),
            selected = MutableStateFlow(BOB_FAMILY)
        )
        val released = mutableListOf<String>()
        every { messages.observeConversation(any()) } answers { held(firstArg(), released) }
        val emissions = mutableListOf<Map<String, Set<FamilySignal>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source().observeSignals().collect { emissions += it }
        }
        runCurrent()

        uid.value = null
        runCurrent()

        assertEquals(listOf(CAROL_FAMILY.familyId), released)
        assertEquals(emptyMap(), emissions.last())
    }

    @Test
    fun `a listener that keeps failing gives up after a bounded number of attempts`() = runTest {
        given()
        var attempts = 0
        every { messages.observeConversation(CAROL_FAMILY.familyId) } returns flow<Map<String, Any>?> {
            attempts++
            throw IllegalStateException("PERMISSION_DENIED")
        }

        // Completes — which is the point: a retry without a bound would never let it.
        val emissions = source().observeSignals().toList()

        assertEquals(MAX_ATTEMPTS, attempts)
        assertEquals(emptyMap(), emissions.last())
    }

    @Test
    fun `a custody listener that gives up does not take the chat dot with it`() = runTest {
        given()
        every { messages.observeConversation(CAROL_FAMILY.familyId) } returns
            flowOf(document(lastMessageAt = 300L, myMark = 200L))
        every { custodySource.observeCustody(CAROL_FAMILY.familyId) } returns flow<SharedCustody?> {
            throw IllegalStateException("PERMISSION_DENIED")
        }

        val emissions = source().observeSignals().toList()

        assertEquals(mapOf(CAROL_FAMILY.familyId to setOf(FamilySignal.CHAT)), emissions.last())
    }

    // ---- the clocks (phase 3a) ----------------------------------------------------------

    @Test
    fun `a request past its deadline does not light the dot`() {
        val past = clocksAt(NOW + DAY, changeClock(answerBy = NOW))

        assertFalse(waiting(past))
    }

    @Test
    fun `a request under a last chance lights it again`() {
        val chance = changeClock(answerBy = NOW, lastChanceUntil = NOW + 2 * DAY)

        assertTrue(waiting(clocksAt(NOW + DAY, chance)))
    }

    @Test
    fun `a request without a clock lights it as before`() {
        assertTrue(waiting(ClocksAt()))
        assertFalse(waiting(ClocksAt(), ids = emptySet()))
    }

    @Test
    fun `a clock that does not name the author as its proposer is ignored`() {
        val forged = changeClock(answerBy = NOW).copy(proposerUid = ALICE, responderUid = CAROL)

        assertTrue(waiting(clocksAt(NOW + DAY, forged)))
    }

    @Test
    fun `a proposal and a swap past their deadline do not light the schedule`() {
        val proposal = custody(proposedBy = CAROL)
        val proposalClock = proposalClock(proposal.proposal!!, answerBy = NOW)
        val swapDoc = custody(swaps = mapOf(TODAY.toString() to swap(CAROL).copy(groupId = "g1")))
        val swapClock = proposalClock.copy(id = "swap_${CAROL}_g1", kind = RequestKind.SWAP, ref = "g1")

        val late = NOW + DAY
        val early = NOW - 1
        assertFalse(OtherFamiliesSignals.hasPendingSchedule(proposal, ALICE, TODAY, clocksAt(late, proposalClock)))
        assertFalse(OtherFamiliesSignals.hasPendingSchedule(swapDoc, ALICE, TODAY, clocksAt(late, swapClock)))
        assertTrue(OtherFamiliesSignals.hasPendingSchedule(proposal, ALICE, TODAY, clocksAt(early, proposalClock)))
        assertTrue(OtherFamiliesSignals.hasPendingSchedule(swapDoc, ALICE, TODAY, clocksAt(early, swapClock)))
    }

    @Test
    fun `the dot goes out at the deadline without any document changing`() = runTest {
        given()
        every { requests.observePendingIdsFrom(ALICE, CAROL) } returns flowOf(setOf(REQUEST_ID))
        val ticking = MutableStateFlow(clocksAt(NOW - 1, changeClock(answerBy = NOW)))
        every { clocks.observeFamily(ALICE, CAROL_FAMILY.familyId) } returns ticking
        val emissions = mutableListOf<Map<String, Set<FamilySignal>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source().observeSignals().collect { emissions += it }
        }
        runCurrent()

        assertEquals(setOf(FamilySignal.CHANGE_REQUEST), emissions.last()[CAROL_FAMILY.familyId])

        ticking.value = clocksAt(NOW + 1, changeClock(answerBy = NOW))
        runCurrent()

        assertEquals(emptyMap(), emissions.last())
    }

    private fun waiting(clocks: ClocksAt, ids: Set<String> = setOf(REQUEST_ID)) =
        OtherFamiliesSignals.waitingRequests(ids, CAROL, CAROL_FAMILY.familyId, clocks)

    private fun clocksAt(now: Long, vararg clocks: RequestDeadline) = ClocksAt.of(clocks.toList(), now)

    private fun changeClock(answerBy: Long, lastChanceUntil: Long? = null) = RequestDeadline(
        id = RequestDeadlineId.of(RequestKind.CHANGE, CAROL, REQUEST_ID, ""),
        kind = RequestKind.CHANGE,
        ref = REQUEST_ID,
        familyId = CAROL_FAMILY.familyId,
        proposerUid = CAROL,
        responderUid = ALICE,
        answerByMillis = answerBy,
        lastChanceUntilMillis = lastChanceUntil
    )

    private fun proposalClock(proposal: CustodyProposal, answerBy: Long) = RequestDeadline(
        id = RequestDeadlineId.forProposal(proposal, CAROL_FAMILY.familyId),
        kind = RequestKind.PROPOSAL,
        ref = proposal.proposedAt,
        familyId = CAROL_FAMILY.familyId,
        proposerUid = CAROL,
        responderUid = ALICE,
        answerByMillis = answerBy
    )

    private fun splitClock(proposal: SplitRatioProposal, answerBy: Long) = RequestDeadline(
        id = RequestDeadlineId.forSplit(proposal, CAROL_FAMILY.familyId),
        kind = RequestKind.SPLIT,
        ref = proposal.proposedAtMillis.toString(),
        familyId = CAROL_FAMILY.familyId,
        proposerUid = CAROL,
        responderUid = ALICE,
        answerByMillis = answerBy
    )

    /** The family with Carol's money agreement: half each, with [proposedBy]'s 70/30 proposal, if any. */
    private fun settings(proposedBy: String?) = FamilySettings(
        ratio = SplitRatio.EVEN,
        participants = listOf(ALICE, CAROL).sorted(),
        lastModifiedBy = CAROL,
        lastModifiedAtMillis = 1L,
        proposal = proposedBy?.let {
            SplitRatioProposal(ratio = SplitRatio(7_000), proposedBy = it, proposedAtMillis = NOW - DAY)
        }
    )

    /** A listener that reports news and stays attached until cancelled, recording the release. */
    private fun held(conversationId: String, released: MutableList<String>): Flow<Map<String, Any>?> = flow {
        emit(document(lastMessageAt = 300L, myMark = 200L))
        try {
            awaitCancellation()
        } finally {
            released += conversationId
        }
    }

    private fun document(lastMessageAt: Number, myMark: Long): Map<String, Any> = mapOf(
        "lastMessageAt" to lastMessageAt,
        "lastReadAt" to mapOf(ALICE to myMark)
    )

    private fun hasSwap(entry: Pair<LocalDate, DayOverride>): Boolean =
        OtherFamiliesSignals.hasPendingSchedule(
            custody(swaps = mapOf(entry.first.toString() to entry.second)),
            ALICE,
            TODAY
        )

    private fun swap(requestedBy: String) =
        DayOverride(toParent = "mom", requestedBy = requestedBy, requestedAt = "2026-09-20T10:00:00Z")

    private fun custody(
        proposedBy: String? = null,
        swaps: Map<String, DayOverride> = emptyMap()
    ): SharedCustody {
        val model = CustodyModel(
            id = CAROL_FAMILY.familyId,
            modelType = CustodyModelType.CUSTOM,
            patternDays = 14,
            momDayIndices = setOf(0, 1),
            startDate = TODAY
        )
        return SharedCustody(
            model = model,
            lastModifiedBy = CAROL,
            lastModifiedAtMillis = 0L,
            createdAt = "",
            proposal = proposedBy?.let {
                CustodyProposal(model.copy(isActive = false), repeatYearly = true, proposedBy = it, proposedAt = "")
            },
            dayOverrides = swaps
        )
    }

    // ---- child support, a professional's access and the plan (board SW-7) ---------------

    private fun supportBy(uid: String) = SupportRead.Known(
        SupportAgreement(
            familyId = CAROL_FAMILY.familyId,
            participants = listOf(ALICE, CAROL),
            proposal = SupportProposal(
                SupportTerms(BigDecimal("3000"), "CZK", 15, CAROL, YearMonth.of(2026, 11)),
                proposedBy = uid,
                proposedAtMillis = 1L
            )
        )
    )

    private fun grant(familyId: String, consents: Map<String, Long>, expiresAt: Long = Long.MAX_VALUE) =
        ProfessionalGrant(
            id = "${familyId}__pro",
            familyId = familyId,
            familyParents = listOf(ALICE, CAROL),
            proUid = "pro",
            role = ProfessionalRole.THERAPIST,
            name = "Dana",
            invitedBy = consents.keys.first(),
            grantedAtMillis = 1L,
            expiresAtMillis = expiresAt,
            consents = consents
        )

    @Test
    fun hasPendingSupport() {
        assertTrue(OtherFamiliesSignals.hasPendingSupport(supportBy(CAROL), ALICE))
        assertFalse(OtherFamiliesSignals.hasPendingSupport(supportBy(ALICE), ALICE))
        assertFalse(OtherFamiliesSignals.hasPendingSupport(SupportRead.None, ALICE))
        assertFalse(OtherFamiliesSignals.hasPendingSupport(SupportRead.Unavailable, ALICE))
    }

    @Test
    fun hasProfessionalWaiting() {
        val family = CAROL_FAMILY.familyId
        fun waits(vararg grants: ProfessionalGrant) =
            OtherFamiliesSignals.hasProfessionalWaiting(grants.toList(), ALICE, family, nowMillis = 1_000L)
        assertTrue(waits(grant(family, mapOf(CAROL to 1L))))
        assertFalse(waits(grant(family, mapOf(CAROL to 1L), expiresAt = 500L)))
        assertFalse(waits(grant(BOB_FAMILY.familyId, mapOf(CAROL to 1L))))
        assertFalse(waits(grant(family, mapOf(ALICE to 1L))))
    }

    @Test
    fun hasPlanWaiting() {
        val question = ParentingPlanCatalogue.questions.first().id
        val halves = mapOf(CAROL to ParentingPlanEntry(answers = mapOf(question to "Alternating weeks")))
        assertTrue(OtherFamiliesSignals.hasPlanWaiting(halves, ALICE, CAROL))
        assertFalse(OtherFamiliesSignals.hasPlanWaiting(emptyMap(), ALICE, CAROL))
        assertFalse(OtherFamiliesSignals.hasPlanWaiting(mapOf(ALICE to ParentingPlanEntry()), ALICE, CAROL))
    }

    @Test
    fun supportProposalLightsTheDot() = runTest {
        given()
        every { supportStore.observe(CAROL_FAMILY.familyId) } returns flowOf(supportBy(CAROL))

        val emissions = source().observeSignals().toList()

        assertEquals(mapOf(CAROL_FAMILY.familyId to setOf(FamilySignal.SUPPORT)), emissions.last())
        verify(exactly = 0) { supportStore.observe(BOB_FAMILY.familyId) }
        verify { professionals.observeFamilyGrants(CAROL_FAMILY.familyId) }
        verify { plans.observePlan(CAROL_FAMILY.familyId) }
    }

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val CAROL = "carol-uid"

        /** The first attempt plus the eight retries `reconnecting()` also allows. */
        const val MAX_ATTEMPTS = 9

        const val REQUEST_ID = "r1"
        const val NOW = 1_800_000_000_000L
        const val DAY = 86_400_000L

        val TODAY: LocalDate = LocalDate.of(2026, 9, 23)
        val BOB_FAMILY = FamilyOption("alice-uid__bob-uid", BOB)
        val CAROL_FAMILY = FamilyOption("alice-uid__carol-uid", CAROL)
    }
}
