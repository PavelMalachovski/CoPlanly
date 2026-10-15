package com.coparently.app.domain.usecase

import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Creating an event, and — since phase 3a — starting the clock of one that waits for the
 * co-parent's answer (spec §5.1).
 *
 * The use case cannot know from the event it was handed whether the event will wait: the
 * repository decides that when it stores it (`EventAcceptanceTransition.required`) and stamps the
 * creator and the family at the same time. So it reads the stored event back, and a clock starts
 * only for one the repository itself made [EventAcceptance.PENDING].
 */
class CreateEventUseCaseTest {

    /** Strict: a read or a write the test did not expect fails it. */
    private val eventRepository = mockk<EventRepository>()
    private val requestDeadlines = mockk<RequestDeadlineRepository>()
    private val analytics = mockk<AnalyticsManager>(relaxed = true)
    private val crashlytics = mockk<CrashlyticsManager>(relaxed = true)
    private val reminders = mockk<ReminderScheduler>(relaxed = true)
    private val useCase = CreateEventUseCase(
        eventRepository,
        EventValidator(),
        analytics,
        crashlytics,
        reminders,
        requestDeadlines
    )

    /** Ten days off: far enough for every deadline to be offered. */
    private val start: LocalDateTime = LocalDateTime.now().plusDays(10)

    /** What the form hands over: no creator, no family, nobody's answer awaited yet. */
    private val newEvent = Event(
        id = "e-new",
        title = "Dentist",
        startDateTime = start,
        endDateTime = start.plusHours(1),
        eventType = "medical",
        parentOwner = "dad",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    /** What the repository stored: created by "a" in family "a__b", for the other parent, so waiting. */
    private val stored = newEvent.copy(
        acceptance = EventAcceptance.PENDING,
        familyId = "a__b",
        createdByFirebaseUid = "a"
    )

    private fun storing(event: Event?) {
        coEvery { eventRepository.insertEvent(any(), any()) } returns Unit
        coEvery { eventRepository.getEventById("e-new") } returns event
    }

    @Test
    fun `an event that waits for the co-parent gets a clock in its own family`() = runTest {
        storing(stored)
        val started = slot<RequestStart>()
        coEvery { requestDeadlines.start(capture(started)) } answers { clockFor(firstArg()) }

        val result = useCase(newEvent, answerBy = AnswerByPick.DEFAULT)

        assertTrue(result.isSuccess)
        coVerifyOrder {
            eventRepository.insertEvent(match { it.id == "e-new" }, true)
            eventRepository.getEventById("e-new")
            requestDeadlines.start(
                match {
                    it.kind == RequestKind.EVENT && it.ref == "e-new" && it.responderUid == "b" &&
                        it.familyId == "a__b"
                }
            )
        }
        // The cap is the event's start; the deadline leaves a day for a last chance, and is ahead.
        val cap = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(cap, started.captured.capMillis)
        assertTrue(started.captured.answerByMillis <= cap - AnswerBy.LAST_CHANCE_RESERVE_MILLIS)
        assertTrue(started.captured.answerByMillis > System.currentTimeMillis())
        assertNull(started.captured.firstDate)
        assertNull(started.captured.dayCount)
    }

    @Test
    fun `the responder is whichever of the family's two parents did not create the event`() = runTest {
        // The family id sorts the two uids, so the creator is not always the first of them.
        storing(stored.copy(createdByFirebaseUid = "b"))
        coEvery { requestDeadlines.start(any()) } answers { clockFor(firstArg()) }

        useCase(newEvent, answerBy = AnswerByPick.DEFAULT)

        coVerify(exactly = 1) { requestDeadlines.start(match { it.responderUid == "a" && it.familyId == "a__b" }) }
    }

    @Test
    fun `an event that does not wait, or no deadline chosen, gets no clock`() = runTest {
        // Made for the parent's own day, or answered already: nobody's answer is awaited.
        listOf(EventAcceptance.NOT_REQUIRED, EventAcceptance.ACCEPTED, EventAcceptance.DECLINED).forEach { acceptance ->
            storing(stored.copy(acceptance = acceptance))
            useCase(newEvent, answerBy = AnswerByPick.DEFAULT)
            coVerify(exactly = 0) { requestDeadlines.start(any()) }
        }

        // Waiting, but the parent chose no deadline: the event goes out as it always did, and the
        // stored event is not even read back.
        coEvery { eventRepository.insertEvent(any(), any()) } returns Unit
        val result = useCase(newEvent)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
        coVerify(exactly = 3) { eventRepository.getEventById(any()) }
    }

    @Test
    fun `an event with no family, a creator outside it, or a private one gets no clock`() = runTest {
        listOf(
            stored.copy(familyId = null),
            stored.copy(familyId = "  "),
            // A family id that is not two uids joined, and a creator who is not one of them.
            stored.copy(familyId = "a"),
            stored.copy(createdByFirebaseUid = null),
            stored.copy(createdByFirebaseUid = "c"),
            // A private event never reaches Firestore, so no clock may name it or its start.
            stored.copy(isPrivate = true)
        ).forEach { unfit ->
            storing(unfit)
            val result = useCase(newEvent, answerBy = AnswerByPick.DEFAULT)
            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { requestDeadlines.start(any()) }
        }
    }

    @Test
    fun `an event that starts within two hours gets no clock`() = runTest {
        // Too soon for a deadline: it is created as a plain event waiting for an answer.
        storing(stored.copy(startDateTime = LocalDateTime.now().plusMinutes(90)))

        val result = useCase(newEvent, answerBy = AnswerByPick.DEFAULT)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
    }

    @Test
    fun `an event that cannot be read back gets no clock`() = runTest {
        storing(null)

        val result = useCase(newEvent, answerBy = AnswerByPick.DEFAULT)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { requestDeadlines.start(any()) }
    }

    @Test
    fun `a clock that cannot be written does not fail the save`() = runTest {
        storing(stored)
        coEvery { requestDeadlines.start(any()) } returns null

        val result = useCase(newEvent, answerBy = AnswerByPick.DEFAULT)

        // The rules refused it, or nobody is signed in: the event stands, as one without a deadline.
        assertTrue(result.isSuccess)
        assertEquals("e-new", result.getOrThrow().id)
        coVerify(exactly = 1) { requestDeadlines.start(any()) }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `a clock that fails does not fail the save either`() = runTest {
        storing(stored)
        coEvery { requestDeadlines.start(any()) } throws IllegalStateException("UNAVAILABLE")

        val result = useCase(newEvent, answerBy = AnswerByPick.DEFAULT)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `the clock starts last, so a start that never finishes costs the save nothing`() = runTest {
        storing(stored)
        coEvery { requestDeadlines.start(any()) } answers { clockFor(firstArg()) }

        useCase(newEvent, answerBy = AnswerByPick.DEFAULT)

        // The event is stored, its reminder is set and the creation is counted before the one step
        // that waits on the server: a screen that leaves in the middle of it loses only the clock.
        coVerifyOrder {
            eventRepository.insertEvent(any(), true)
            reminders.schedule(match { it.id == "e-new" })
            analytics.logEventCreated("medical")
            requestDeadlines.start(any())
        }
    }

    /** The clock [start] would write, as the repository returns it. */
    private fun clockFor(start: RequestStart) = RequestDeadline(
        id = "event_a_${start.ref}",
        kind = start.kind,
        ref = start.ref,
        familyId = start.familyId,
        proposerUid = "a",
        responderUid = start.responderUid,
        answerByMillis = start.answerByMillis,
        capMillis = start.capMillis
    )
}
