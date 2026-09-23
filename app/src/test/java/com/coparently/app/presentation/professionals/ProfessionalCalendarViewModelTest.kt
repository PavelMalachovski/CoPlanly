package com.coparently.app.presentation.professionals

import androidx.lifecycle.SavedStateHandle
import com.coparently.app.domain.custody.SharedCustody
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.repository.ProfessionalRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The professional's read-only calendar (MON-18) — and its agenda builder. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfessionalCalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val far = System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000

    private fun grant(consents: Map<String, Long>) = ProfessionalGrant(
        id = "a__b__pro", familyId = "a__b", familyParents = listOf("a", "b"), proUid = "pro",
        role = ProfessionalRole.MEDIATOR, name = "M", invitedBy = "a", grantedAtMillis = 1L,
        expiresAtMillis = far, consents = consents
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(repository: ProfessionalRepository) =
        ProfessionalCalendarViewModel(SavedStateHandle(mapOf("grantId" to "a__b__pro")), repository)

    @Test
    fun `a grant with one consent shows nothing and reads no events`() = runTest(dispatcher) {
        val repository = mockk<ProfessionalRepository> {
            every { observeMyGrants() } returns flowOf(listOf(grant(mapOf("a" to 1L))))
        }
        val model = vm(repository)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(ProfessionalCalendarUiState.Unavailable, model.uiState.value)
        verify(exactly = 0) { repository.observeFamilyEvents(any(), any(), any()) }
    }

    @Test
    fun `an active grant shows four weeks`() = runTest(dispatcher) {
        val active = grant(mapOf("a" to 1L, "b" to 2L))
        val repository = mockk<ProfessionalRepository> {
            every { observeMyGrants() } returns flowOf(listOf(active))
            every { observeFamilyEvents(active, any(), any()) } returns flowOf(emptyList())
            every { observeCustody(active) } returns flowOf(null)
        }
        val model = vm(repository)
        backgroundScope.launch { model.uiState.collect {} }
        advanceUntilIdle()

        val ready = assertIs<ProfessionalCalendarUiState.Ready>(model.uiState.value)
        assertEquals(ProfessionalCalendarViewModel.WINDOW_DAYS.toInt(), ready.days.size)
    }

    @Test
    fun `the agenda names whose day it is and places multi-day events on each day`() {
        val start = LocalDate.of(2026, 1, 5)
        val custody = SharedCustody(
            model = CustodyModel(
                id = "m",
                modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
                patternDays = 14,
                momDayIndices = (0..6).toSet(),
                startDate = start
            ),
            lastModifiedBy = "a",
            lastModifiedAtMillis = 1L,
            createdAt = "2026-01-05T00:00:00"
        )
        val created = LocalDateTime.of(2026, 1, 1, 0, 0)
        val trip = Event(
            id = "e",
            title = "Trip",
            eventType = "OTHER",
            parentOwner = "dad",
            startDateTime = LocalDateTime.of(2026, 1, 12, 9, 0),
            endDateTime = LocalDateTime.of(2026, 1, 13, 18, 0),
            createdAt = created,
            updatedAt = created
        )

        val days = ProfessionalAgenda.days(start, 14, listOf(trip), custody)

        assertEquals("mom", days[0].custodySlot)
        assertEquals("dad", days[7].custodySlot)
        assertEquals(listOf("e"), days[7].events.map { it.id })
        assertEquals(listOf("e"), days[8].events.map { it.id })
        assertEquals(emptyList(), days[9].events)
    }
}
