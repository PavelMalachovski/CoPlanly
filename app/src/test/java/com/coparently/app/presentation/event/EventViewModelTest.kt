package com.coparently.app.presentation.event

import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.AnswerByPreset
import com.coparently.app.domain.error.AppError
import com.coparently.app.domain.error.ErrorHandler
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.domain.model.User
import com.coparently.app.domain.usecase.CreateEventUseCase
import com.coparently.app.domain.usecase.DeleteEventUseCase
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.domain.usecase.GetEventsUseCase
import com.coparently.app.domain.usecase.UpdateEventUseCase
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.testFamilyMembersSource
import com.coparently.app.presentation.common.testParentsSource
import com.google.gson.Gson
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for EventViewModel.
 * Covers loading, deletion and pickup confirmation flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var createEvent: CreateEventUseCase
    private lateinit var updateEvent: UpdateEventUseCase
    private lateinit var deleteEvent: DeleteEventUseCase
    private lateinit var getEvents: GetEventsUseCase
    private lateinit var errorHandler: ErrorHandler
    private lateinit var encryptedPreferences: EncryptedPreferences
    private lateinit var viewModel: EventViewModel

    /** The signed-in parent, in slot 2. `confirmPickup` stamps their slot on the event. */
    private val signedInParent = User(
        id = "u1",
        email = "pavel@example.com",
        name = "Pavel",
        role = "dad",
        colorCode = "#2196F3"
    )

    private val sampleEvent = Event(
        id = "e1",
        title = "Soccer",
        startDateTime = LocalDateTime.of(2026, 7, 20, 16, 0),
        endDateTime = LocalDateTime.of(2026, 7, 20, 17, 0),
        eventType = "sports",
        parentOwner = "mom",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        createEvent = mockk(relaxed = true)
        updateEvent = mockk(relaxed = true)
        deleteEvent = mockk(relaxed = true)
        getEvents = mockk {
            every { this@mockk.invoke() } returns flowOf(listOf(sampleEvent))
        }
        errorHandler = mockk(relaxed = true)
        encryptedPreferences = mockk(relaxed = true)
        viewModel = EventViewModel(
            EventUseCases(createEvent, updateEvent, deleteEvent, getEvents),
            errorHandler,
            encryptedPreferences,
            Gson(),
            photoStorage = mockk(relaxed = true),
            parentsSource = testParentsSource(me = signedInParent),
            familyMembersSource = testFamilyMembersSource()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun `loadEvents populates events state`() = runTest {
        advanceUntilIdle()
        assertEquals(listOf(sampleEvent), viewModel.events.value)
    }

    @Test
    fun `createEvent passes the deadline to the use case`() = runTest {
        coEvery { createEvent.invoke(any(), any(), any()) } returns Result.success(sampleEvent)
        val aWeek = AnswerByPick.Preset(AnswerByPreset.WEEK)

        viewModel.createEvent(sampleEvent, aWeek)
        advanceUntilIdle()

        // The pick travels as chosen: the use case resolves it once it knows whether the event
        // waits for the co-parent, and what its cap is.
        coVerify(exactly = 1) { createEvent.invoke(sampleEvent, true, aWeek) }
    }

    @Test
    fun `createEvent without a deadline asks for none`() = runTest {
        coEvery { createEvent.invoke(any(), any(), any()) } returns Result.success(sampleEvent)

        viewModel.createEvent(sampleEvent)
        advanceUntilIdle()

        coVerify(exactly = 1) { createEvent.invoke(sampleEvent, true, null) }
    }

    @Test
    fun `a create with a deadline finishes after the screen is gone, one without does not`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var finished = 0
        coEvery { createEvent.invoke(any(), any(), any()) } coAnswers {
            gate.await()
            finished++
            Result.success(sampleEvent)
        }

        // The form pops at once and the ViewModel's scope is cancelled with it, while the event still
        // waits for the server: the clock is the use case's last step and must not be lost.
        viewModel.createEvent(sampleEvent, AnswerByPick.Preset(AnswerByPreset.WEEK))
        runCurrent()
        viewModel.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, finished)
    }

    @Test
    fun `a create without a deadline is cancelled with the screen as before`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var finished = 0
        coEvery { createEvent.invoke(any(), any(), any()) } coAnswers {
            gate.await()
            finished++
            Result.success(sampleEvent)
        }

        viewModel.createEvent(sampleEvent)
        runCurrent()
        viewModel.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, finished)
    }

    @Test
    fun `deleteEventById delegates to use case`() = runTest {
        coEvery { deleteEvent.deleteById("e1") } returns Result.success(Unit)

        viewModel.deleteEventById("e1")
        advanceUntilIdle()

        coVerify { deleteEvent.deleteById("e1") }
    }

    @Test
    fun `confirmPickup stamps the signed-in parent's slot`() = runTest {
        coEvery { getEvents.getById("e1") } returns sampleEvent
        val saved = slot<Event>()
        coEvery { updateEvent.invoke(capture(saved)) } answers { Result.success(saved.captured) }

        viewModel.confirmPickup("e1")
        advanceUntilIdle()

        assertEquals("dad", saved.captured.pickupConfirmedBy)
        assertNotNull(saved.captured.pickupConfirmedAt)
    }

    @Test
    fun `undoPickupConfirmation clears confirmation`() = runTest {
        coEvery { getEvents.getById("e1") } returns sampleEvent.copy(
            pickupConfirmedBy = "dad",
            pickupConfirmedAt = LocalDateTime.now()
        )
        val saved = slot<Event>()
        coEvery { updateEvent.invoke(capture(saved)) } answers { Result.success(saved.captured) }

        viewModel.undoPickupConfirmation("e1")
        advanceUntilIdle()

        assertNull(saved.captured.pickupConfirmedBy)
        assertNull(saved.captured.pickupConfirmedAt)
    }

    @Test
    fun `setting a new range cancels the previous collection`() = runTest {
        val firstStart = LocalDateTime.of(2026, 8, 1, 0, 0)
        val firstEnd = LocalDateTime.of(2026, 8, 31, 23, 59, 59)
        val secondStart = LocalDateTime.of(2026, 11, 1, 0, 0)
        val secondEnd = LocalDateTime.of(2026, 11, 30, 23, 59, 59)

        val firstRange = MutableSharedFlow<List<Event>>()
        val laterEvent = sampleEvent.copy(id = "e2", title = "Dentist")
        every { getEvents.getByDateRange(firstStart, firstEnd) } returns firstRange
        every { getEvents.getByDateRange(secondStart, secondEnd) } returns flowOf(listOf(laterEvent))

        viewModel.loadEventsForDateRange(firstStart, firstEnd)
        advanceUntilIdle()
        firstRange.emit(listOf(sampleEvent))
        advanceUntilIdle()
        assertEquals(listOf(sampleEvent), viewModel.events.value)

        viewModel.loadEventsForDateRange(secondStart, secondEnd)
        advanceUntilIdle()

        assertEquals(0, firstRange.subscriptionCount.value)
        assertEquals(listOf(laterEvent), viewModel.events.value)

        // A late emission from the abandoned range must not reach the UI.
        firstRange.emit(listOf(sampleEvent))
        advanceUntilIdle()
        assertEquals(listOf(laterEvent), viewModel.events.value)
    }

    @Test
    fun `re-requesting the same range does not restart the collection`() = runTest {
        val start = LocalDateTime.of(2026, 8, 1, 0, 0)
        val end = LocalDateTime.of(2026, 8, 31, 23, 59, 59)
        val range = MutableSharedFlow<List<Event>>()
        every { getEvents.getByDateRange(start, end) } returns range

        viewModel.loadEventsForDateRange(start, end)
        advanceUntilIdle()
        viewModel.loadEventsForDateRange(start, end)
        advanceUntilIdle()

        assertEquals(1, range.subscriptionCount.value)
    }

    @Test
    fun `a failed range does not block a later different range`() = runTest {
        val start = LocalDateTime.of(2026, 8, 1, 0, 0)
        val end = LocalDateTime.of(2026, 8, 31, 23, 59, 59)
        val secondStart = LocalDateTime.of(2026, 11, 1, 0, 0)
        val secondEnd = LocalDateTime.of(2026, 11, 30, 23, 59, 59)
        val laterEvent = sampleEvent.copy(id = "e2", title = "Dentist")
        val failure = RuntimeException("boom")

        every { getEvents.getByDateRange(start, end) } returns flow { throw failure }
        every { errorHandler.handleError(failure) } returns AppError.UnknownError(originalException = failure)
        every { getEvents.getByDateRange(secondStart, secondEnd) } returns flowOf(listOf(laterEvent))

        viewModel.loadEventsForDateRange(start, end)
        advanceUntilIdle()

        assertEquals(
            EventUiState.Error(UiText.Res(R.string.common_error_generic)),
            viewModel.uiState.value
        )

        // The failed range's .catch completed only the inner flow; a different query must still
        // be servable through the same flatMapLatest chain.
        viewModel.loadEventsForDateRange(secondStart, secondEnd)
        advanceUntilIdle()

        assertEquals(listOf(laterEvent), viewModel.events.value)
    }

    @Test
    fun `refresh restarts a failed range once it stops failing`() = runTest {
        val start = LocalDateTime.of(2026, 8, 1, 0, 0)
        val end = LocalDateTime.of(2026, 8, 31, 23, 59, 59)
        val failure = RuntimeException("boom")
        var shouldFail = true
        val recovered = MutableSharedFlow<List<Event>>()
        every { getEvents.getByDateRange(start, end) } answers {
            if (shouldFail) flow { throw failure } else recovered
        }
        every { errorHandler.handleError(failure) } returns AppError.UnknownError(originalException = failure)

        viewModel.loadEventsForDateRange(start, end)
        advanceUntilIdle()
        assertEquals(
            EventUiState.Error(UiText.Res(R.string.common_error_generic)),
            viewModel.uiState.value
        )

        // Re-requesting the identical range is conflated away and could never restart it -
        // refresh() is the only lever, and it works because the underlying source now succeeds.
        shouldFail = false
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, recovered.subscriptionCount.value)
        recovered.emit(listOf(sampleEvent))
        advanceUntilIdle()
        assertEquals(listOf(sampleEvent), viewModel.events.value)
    }

    @Test
    fun `a move reports RESCHEDULED as a type, which is what offers Undo`() = runTest {
        // UX-12: CalendarScreen used to offer Undo only when the message equalled the English
        // literal "Event rescheduled", so localising that string would have removed the Undo.
        advanceUntilIdle()
        coEvery { getEvents.getById("e1") } returns sampleEvent
        val saved = slot<Event>()
        coEvery { updateEvent.invoke(capture(saved)) } answers { Result.success(saved.captured) }

        viewModel.moveEvent("e1", LocalDate.of(2026, 7, 22))
        runCurrent()

        assertEquals(EventUiState.OperationSuccess(EventOperation.RESCHEDULED), viewModel.uiState.value)
        assertTrue(viewModel.hasUndoAction())
    }

    @Test
    fun `undo of a move restores the exact start and end, minutes included`() = runTest {
        advanceUntilIdle()
        val halfPast = sampleEvent.copy(
            startDateTime = LocalDateTime.of(2026, 7, 20, 9, 30),
            endDateTime = LocalDateTime.of(2026, 7, 20, 10, 45)
        )
        coEvery { getEvents.getById("e1") } returns halfPast
        val saved = mutableListOf<Event>()
        coEvery { updateEvent.invoke(capture(saved)) } answers { Result.success(saved.last()) }

        viewModel.moveEvent("e1", LocalDate.of(2026, 7, 22), 14 * 60 + 15)
        advanceUntilIdle()
        viewModel.undoLastMove()
        advanceUntilIdle()

        assertEquals(LocalDateTime.of(2026, 7, 22, 14, 15), saved[0].startDateTime)
        assertEquals(halfPast.startDateTime, saved[1].startDateTime)
        assertEquals(halfPast.endDateTime, saved[1].endDateTime)
    }

    @Test
    fun `a recurring series is neither moved nor resized from one of its occurrences`() = runTest {
        advanceUntilIdle()
        coEvery { getEvents.getById("e1") } returns sampleEvent.copy(isRecurring = true)

        viewModel.moveEvent("e1", LocalDate.of(2026, 7, 22), 10 * 60)
        viewModel.resizeEvent("e1", newEndTime = LocalDateTime.of(2026, 7, 20, 18, 0))
        advanceUntilIdle()

        coVerify(exactly = 0) { updateEvent.invoke(any()) }
        assertTrue(!viewModel.hasUndoAction())
    }

    @Test
    fun `a validation failure names the field in the reader's language, not the validator's`() = runTest {
        advanceUntilIdle()
        val failure = RuntimeException("Event title cannot be empty")
        coEvery { updateEvent.invoke(any()) } returns Result.failure(failure)
        val refusal = AppError.ValidationError(field = "title", validationMessage = "Event title cannot be empty")
        every { errorHandler.handleError(failure) } returns refusal

        viewModel.updateEvent(sampleEvent)
        runCurrent()

        assertEquals(
            EventUiState.Error(UiText.Res(R.string.event_error_title_invalid)),
            viewModel.uiState.value
        )
    }

    // ---- the new-event draft, one per family (isolation audit, October 2026) ----------------

    /** An event form opened while [coParent]'s family is on screen. */
    private fun formInFamilyWith(coParent: String) = EventViewModel(
        EventUseCases(createEvent, updateEvent, deleteEvent, getEvents),
        errorHandler,
        encryptedPreferences,
        Gson(),
        photoStorage = mockk(relaxed = true),
        parentsSource = testParentsSource(
            me = signedInParent,
            partner = PartnerSummary(id = coParent, name = coParent, email = "", pairedSinceMillis = null)
        ),
        familyMembersSource = testFamilyMembersSource()
    )

    @Test
    fun `a draft typed for one family never comes back on the other family's form`() = runTest {
        val carolsDraft = EventDraft("Pick up at Carol's", "", "dad", "general", "2026-07-20", "16:00", "17:00")
        every { encryptedPreferences.getEventDraft(any()) } returns null
        every { encryptedPreferences.getEventDraft("carol__u1") } returns Gson().toJson(carolsDraft)

        assertEquals("Pick up at Carol's", formInFamilyWith("carol").loadEventDraft()?.title)
        assertNull(formInFamilyWith("igor").loadEventDraft())
    }

    @Test
    fun `a draft is saved and cleared under the family on screen`() = runTest {
        val form = formInFamilyWith("carol")
        advanceUntilIdle()

        form.saveEventDraft(EventDraft("Dentist", "", "dad", "general", "2026-07-20", "16:00", "17:00"))
        // The save writes from the IO dispatcher, outside the test's clock.
        verify(timeout = IO_WRITE_TIMEOUT_MS) { encryptedPreferences.putEventDraft("carol__u1", any()) }

        form.clearEventDraft()
        advanceUntilIdle()
        verify { encryptedPreferences.clearEventDraft("carol__u1") }
    }

    @Test
    fun `a draft keeps its end date and all-day switch`() = runTest {
        val form = formInFamilyWith("carol")
        advanceUntilIdle()
        val stored = slot<String>()
        every { encryptedPreferences.putEventDraft("carol__u1", capture(stored)) } just Runs

        val weekend = EventDraft(
            title = "Weekend at grandma's",
            description = "",
            parentOwner = "dad",
            eventType = "general",
            startDate = "2026-10-23",
            startTime = "00:00",
            endTime = "23:59",
            endDaySpan = 2,
            allDay = true
        )
        form.saveEventDraft(weekend)
        verify(timeout = IO_WRITE_TIMEOUT_MS) { encryptedPreferences.putEventDraft("carol__u1", any()) }

        every { encryptedPreferences.getEventDraft("carol__u1") } returns stored.captured
        assertEquals(weekend, form.loadEventDraft())
    }

    @Test
    fun `a draft saved before the end date existed opens as a one-day timed event`() = runTest {
        every { encryptedPreferences.getEventDraft("carol__u1") } returns
            """{"title":"Dentist","description":"","parentOwner":"dad","eventType":"general",""" +
            """"startDate":"2026-07-20","startTime":"16:00","endTime":"17:00"}"""

        val draft = formInFamilyWith("carol").loadEventDraft()

        assertEquals(0, draft?.endDaySpan)
        assertEquals(false, draft?.allDay)
    }

    private companion object {
        /** How long a test waits for the draft's write on the IO dispatcher. */
        const val IO_WRITE_TIMEOUT_MS = 2_000L
    }
}
