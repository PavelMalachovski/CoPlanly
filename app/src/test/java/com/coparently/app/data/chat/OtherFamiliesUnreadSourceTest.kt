package com.coparently.app.data.chat

import com.coparently.app.data.family.FamilyOption
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cross-family chat signal behind the switcher's dot (M-8).
 *
 * What is pinned: the rule that reads one conversation document, that a one-family account
 * attaches no listener at all, that the family on screen is never watched, that a switch or a
 * sign-out releases the listeners it held, and that a listener which keeps failing gives up
 * rather than retrying for the life of the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtherFamiliesUnreadSourceTest {

    private val userRepository = mockk<UserRepository>()
    private val selectedFamilySource = mockk<SelectedFamilySource>()
    private val dataSource = mockk<FirestoreMessageDataSource>()

    private fun source() = OtherFamiliesUnreadSource(userRepository, selectedFamilySource, dataSource)

    private fun given(
        uid: Flow<String?> = flowOf(ALICE),
        families: Flow<List<FamilyOption>> = flowOf(listOf(BOB_FAMILY, CAROL_FAMILY)),
        selected: Flow<FamilyOption?> = flowOf(BOB_FAMILY)
    ) {
        every { userRepository.observeCurrentUserId() } returns uid
        every { selectedFamilySource.observeFamilies(ALICE) } returns families
        every { selectedFamilySource.observe(ALICE) } returns selected
    }

    // ---- the pure rules ---------------------------------------------------------------

    @Test
    fun `a document newer than my mark is unread, whatever number type Firestore hands back`() {
        assertTrue(OtherFamiliesUnreadSource.hasUnread(document(lastMessageAt = 300L, myMark = 200L), ALICE))
        assertTrue(OtherFamiliesUnreadSource.hasUnread(document(lastMessageAt = 300.0, myMark = 200L), ALICE))
    }

    @Test
    fun `only my own mark counts, not the co-parent's`() {
        val doc = mapOf<String, Any>(
            "lastMessageAt" to 300L,
            "lastReadAt" to mapOf(BOB to 300L)
        )

        assertTrue(OtherFamiliesUnreadSource.hasUnread(doc, ALICE))
    }

    @Test
    fun `a read thread, an empty thread and a missing document raise nothing`() {
        assertFalse(OtherFamiliesUnreadSource.hasUnread(document(lastMessageAt = 300L, myMark = 300L), ALICE))
        assertFalse(OtherFamiliesUnreadSource.hasUnread(mapOf("lastReadAt" to mapOf(ALICE to 1L)), ALICE))
        assertFalse(OtherFamiliesUnreadSource.hasUnread(null, ALICE))
    }

    @Test
    fun `one family is watched by nothing, two by the one not on screen`() {
        assertEquals(
            emptyList(),
            OtherFamiliesUnreadSource.familiesToWatch(listOf(BOB_FAMILY), BOB_FAMILY.familyId)
        )
        assertEquals(
            listOf(CAROL_FAMILY.familyId),
            OtherFamiliesUnreadSource.familiesToWatch(listOf(BOB_FAMILY, CAROL_FAMILY), BOB_FAMILY.familyId)
        )
    }

    // ---- the stream -------------------------------------------------------------------

    @Test
    fun `a one-family account attaches no listener`() = runTest {
        given(families = flowOf(listOf(BOB_FAMILY)))

        val emissions = source().observeUnread().toList()

        assertEquals(listOf(emptySet<String>()), emissions)
        verify(exactly = 0) { dataSource.observeConversation(any()) }
    }

    @Test
    fun `the family not on screen is watched, and its news is reported`() = runTest {
        given()
        every { dataSource.observeConversation(CAROL_FAMILY.familyId) } returns
            flowOf(document(lastMessageAt = 300L, myMark = 200L))

        val emissions = source().observeUnread().toList()

        assertEquals(setOf(CAROL_FAMILY.familyId), emissions.last())
        verify(exactly = 0) { dataSource.observeConversation(BOB_FAMILY.familyId) }
    }

    @Test
    fun `a switch releases the old listener and watches the family just left`() = runTest {
        val selected = MutableStateFlow<FamilyOption?>(BOB_FAMILY)
        given(families = MutableStateFlow(listOf(BOB_FAMILY, CAROL_FAMILY)), selected = selected)
        val released = mutableListOf<String>()
        every { dataSource.observeConversation(any()) } answers { held(firstArg(), released) }
        val emissions = mutableListOf<Set<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source().observeUnread().collect { emissions += it }
        }
        runCurrent()

        assertEquals(setOf(CAROL_FAMILY.familyId), emissions.last())

        selected.value = CAROL_FAMILY
        runCurrent()

        assertEquals(listOf(CAROL_FAMILY.familyId), released)
        assertEquals(setOf(BOB_FAMILY.familyId), emissions.last())
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
        every { dataSource.observeConversation(any()) } answers { held(firstArg(), released) }
        val emissions = mutableListOf<Set<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source().observeUnread().collect { emissions += it }
        }
        runCurrent()

        uid.value = null
        runCurrent()

        assertEquals(listOf(CAROL_FAMILY.familyId), released)
        assertEquals(emptySet(), emissions.last())
    }

    @Test
    fun `a listener that keeps failing gives up after a bounded number of attempts`() = runTest {
        given()
        var attempts = 0
        every { dataSource.observeConversation(CAROL_FAMILY.familyId) } returns flow<Map<String, Any>?> {
            attempts++
            throw IllegalStateException("PERMISSION_DENIED")
        }

        // Completes — which is the point: a retry without a bound would never let it.
        val emissions = source().observeUnread().toList()

        assertEquals(MAX_ATTEMPTS, attempts)
        assertEquals(emptySet(), emissions.last())
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

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"

        /** The first attempt plus the eight retries `reconnecting()` also allows. */
        const val MAX_ATTEMPTS = 9

        val BOB_FAMILY = FamilyOption("alice-uid__bob-uid", BOB)
        val CAROL_FAMILY = FamilyOption("alice-uid__carol-uid", "carol-uid")
    }
}
