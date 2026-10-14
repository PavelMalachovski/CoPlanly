package com.coparently.app.presentation.friends

import androidx.lifecycle.SavedStateHandle
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.FriendCalendarRead
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.FriendRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * A family friend's read-only calendar (phase 2, spec §4.4): four weeks of the family's agreed
 * events, and an honest word when the read fails or the access has ended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendCalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val far = System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000

    private val grant = CalendarFriendGrant(
        friendUid = "f1",
        name = "Babička",
        familyParents = listOf("u1", "u2"),
        grantedBy = "u1",
        grantedAtMillis = 1L,
        expiresAtMillis = far,
        familyId = FAMILY_ID,
        parentNames = mapOf("u1" to "Olya", "u2" to "Pavel")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(
        grants: Flow<List<CalendarFriendGrant>> = flowOf(listOf(grant)),
        read: () -> Flow<FriendCalendarRead>
    ): FriendRepository = mockk {
        every { observeMyGrants() } returns grants
        every { observeFamilyEvents(FAMILY_ID, any(), any()) } answers { read() }
    }

    private fun vm(repository: FriendRepository) =
        FriendCalendarViewModel(SavedStateHandle(mapOf("familyId" to FAMILY_ID)), repository)

    private fun event(id: String, day: LocalDate) = Event(
        id = id,
        title = "Event $id",
        startDateTime = day.atTime(10, 0),
        eventType = "OTHER",
        parentOwner = "mom",
        createdAt = day.minusDays(7).atStartOfDay(),
        updatedAt = day.minusDays(7).atStartOfDay(),
        createdByFirebaseUid = "u1"
    )

    @Test
    fun `loading until the first read answers`() = runTest(dispatcher) {
        val reads = MutableSharedFlow<FriendCalendarRead>()
        val model = vm(repository { reads })
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(FriendCalendarUiState.Loading, model.uiState.value)

        reads.emit(FriendCalendarRead.Events(emptyList()))
        advanceUntilIdle()

        assertIs<FriendCalendarUiState.Ready>(model.uiState.value)
    }

    @Test
    fun `events are laid out over four weeks`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val dentist = event("dentist", today.plusDays(3))
        val model = vm(repository { flowOf(FriendCalendarRead.Events(listOf(dentist))) })
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        val ready = assertIs<FriendCalendarUiState.Ready>(model.uiState.value)
        assertEquals(grant, ready.grant)
        assertEquals(FriendCalendarViewModel.WINDOW_DAYS, ready.days.size)
        assertEquals(today, ready.days.first().date)
        assertEquals(listOf("dentist"), ready.days[3].events.map { it.id })
        assertEquals(listOf("dentist"), ready.days.flatMap { it.events }.map { it.id })
        // A friend reads no custody document, so no day says whose it is.
        assertNull(ready.days.firstNotNullOfOrNull { it.custodySlot })
    }

    @Test
    fun `a refused read shows that access has ended`() = runTest(dispatcher) {
        val model = vm(repository { flowOf(FriendCalendarRead.AccessEnded) })
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(FriendCalendarUiState.AccessEnded, model.uiState.value)
    }

    @Test
    fun `a grant that leaves the list ends access although the read still answers`() = runTest(dispatcher) {
        // Firestore does not promise to end a live listener when another document revokes it.
        val grants = MutableStateFlow(listOf(grant))
        val model = vm(repository(grants) { flowOf(FriendCalendarRead.Events(emptyList())) })
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertIs<FriendCalendarUiState.Ready>(model.uiState.value)

        grants.value = emptyList()
        advanceUntilIdle()

        assertEquals(FriendCalendarUiState.AccessEnded, model.uiState.value)
    }

    @Test
    fun `a grant that has not arrived yet is not an ended one`() = runTest(dispatcher) {
        // Right after a redeem the read can answer before the grant listener has.
        val grants = MutableStateFlow(emptyList<CalendarFriendGrant>())
        val model = vm(repository(grants) { flowOf(FriendCalendarRead.Events(emptyList())) })
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertNull(assertIs<FriendCalendarUiState.Ready>(model.uiState.value).grant)

        grants.value = listOf(grant)
        advanceUntilIdle()

        assertEquals(grant, assertIs<FriendCalendarUiState.Ready>(model.uiState.value).grant)
    }

    @Test
    fun `a failed read offers retry, and retry reads again`() = runTest(dispatcher) {
        val repository = repository { flowOf(FriendCalendarRead.Failed) }
        val model = vm(repository)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(FriendCalendarUiState.Failed, model.uiState.value)

        model.retry()
        advanceUntilIdle()

        verify(exactly = 2) { repository.observeFamilyEvents(FAMILY_ID, any(), any()) }
    }

    @Test
    fun `paging moves the window by four weeks`() = runTest(dispatcher) {
        val froms = mutableListOf<LocalDate>()
        val tos = mutableListOf<LocalDate>()
        val repository = mockk<FriendRepository> {
            every { observeMyGrants() } returns flowOf(listOf(grant))
            every { observeFamilyEvents(FAMILY_ID, capture(froms), capture(tos)) } returns
                flowOf(FriendCalendarRead.Events(emptyList()))
        }
        val model = vm(repository)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        model.next()
        advanceUntilIdle()
        model.previous()
        advanceUntilIdle()

        val today = froms.first()
        assertEquals(LocalDate.now(), today)
        assertEquals(listOf(today, today.plusDays(28), today), froms)
        assertEquals(today.plusDays(27), tos.first())
        assertEquals(today, assertIs<FriendCalendarUiState.Ready>(model.uiState.value).days.first().date)
    }

    private companion object {
        const val FAMILY_ID = "u1__u2"
    }
}
