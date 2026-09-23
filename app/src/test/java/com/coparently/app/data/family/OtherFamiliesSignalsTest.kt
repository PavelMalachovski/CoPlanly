package com.coparently.app.data.family

import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.repository.UserRepository
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
import java.time.LocalDate
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

    private fun source() = OtherFamiliesSignals(userRepository, selectedFamilySource, messages, requests, custodySource)

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
        every { requests.observeHasPendingFrom(any(), any()) } returns flowOf(false)
        every { custodySource.observeCustody(any()) } returns flowOf(null)
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
        verify(exactly = 0) { requests.observeHasPendingFrom(any(), any()) }
        verify(exactly = 0) { custodySource.observeCustody(any()) }
    }

    @Test
    fun `the family not on screen is watched, and its news is reported`() = runTest {
        given()
        every { messages.observeConversation(CAROL_FAMILY.familyId) } returns
            flowOf(document(lastMessageAt = 300L, myMark = 200L))

        val emissions = source().observeSignals().toList()

        assertEquals(mapOf(CAROL_FAMILY.familyId to setOf(FamilySignal.CHAT)), emissions.last())
        verify(exactly = 0) { messages.observeConversation(BOB_FAMILY.familyId) }
        verify(exactly = 0) { requests.observeHasPendingFrom(ALICE, BOB) }
        verify(exactly = 0) { custodySource.observeCustody(BOB_FAMILY.familyId) }
    }

    @Test
    fun `a pending request and a pending proposal are each reported as themselves`() = runTest {
        given()
        every { requests.observeHasPendingFrom(ALICE, CAROL) } returns flowOf(true)
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

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val CAROL = "carol-uid"

        /** The first attempt plus the eight retries `reconnecting()` also allows. */
        const val MAX_ATTEMPTS = 9

        val TODAY: LocalDate = LocalDate.of(2026, 9, 23)
        val BOB_FAMILY = FamilyOption("alice-uid__bob-uid", BOB)
        val CAROL_FAMILY = FamilyOption("alice-uid__carol-uid", CAROL)
    }
}
