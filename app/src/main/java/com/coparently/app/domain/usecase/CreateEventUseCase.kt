package com.coparently.app.domain.usecase

import android.util.Log
import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ValidationResult
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for creating a new event.
 * Encapsulates validation, creation, and analytics logic — and, for an event that waits for the
 * co-parent's answer, the start of its request clock (phase 3a, spec §5.1).
 */
class CreateEventUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val eventValidator: EventValidator,
    private val analyticsManager: AnalyticsManager,
    private val crashlyticsManager: CrashlyticsManager,
    private val reminderScheduler: com.coparently.app.domain.notification.ReminderScheduler,
    private val requestDeadlines: RequestDeadlineRepository
) {
    /**
     * Creates a new event.
     *
     * @param announce Whether the co-parent's chat is told; see `EventRepository.insertEvent`.
     * @param answerBy The deadline the parent chose for the co-parent's answer (phase 3a), or null
     *   for none. Only an event that waits for an answer has a clock to start, and whether this one
     *   does is the repository's decision when it stores it (`EventAcceptanceTransition.required`),
     *   so the stored event is read back to find out. A clock that cannot be written, or an event
     *   that cannot wait, never fails the save.
     * @return Result with created event or exception
     */
    suspend operator fun invoke(
        event: Event,
        announce: Boolean = true,
        answerBy: AnswerByPick? = null
    ): Result<Event> {
        return try {
            // Validate event
            val validationResult = eventValidator.validate(event)
            if (validationResult is ValidationResult.Error) {
                return Result.failure(ValidationException(validationResult.message, validationResult.field))
            }

            // Ensure event has an ID
            val eventWithId = if (event.id.isEmpty()) {
                event.copy(id = UUID.randomUUID().toString())
            } else {
                event
            }

            // Set timestamps
            val now = LocalDateTime.now()
            val finalEvent = eventWithId.copy(
                createdAt = now,
                updatedAt = now
            )

            // Create event
            eventRepository.insertEvent(finalEvent, announce)

            // Schedule reminder notification if requested
            reminderScheduler.schedule(finalEvent)

            // Log analytics
            analyticsManager.logEventCreated(finalEvent.eventType)

            // Last, because it is the one step that waits on the server: a screen that leaves in the
            // middle of it loses only the clock, never the reminder or the count above.
            startClock(finalEvent.id, answerBy)

            Result.success(finalEvent)
        } catch (e: Exception) {
            crashlyticsManager.recordException(e)
            Result.failure(e)
        }
    }

    /**
     * Starts the clock of the event stored under [eventId] when it waits for the co-parent's answer
     * and [answerBy] was chosen (phase 3a, spec §5.1). Nothing here fails the save: a clock that is
     * not written, or cannot be, leaves the event waiting as it always did, and the failure is logged.
     */
    private suspend fun startClock(eventId: String, answerBy: AnswerByPick?) {
        if (answerBy == null) return
        try {
            val stored = eventRepository.getEventById(eventId) ?: return
            eventClock(stored, answerBy)?.let { requestDeadlines.start(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "The request clock of a new event could not be started", e)
        }
    }

    private companion object {
        const val TAG = "CreateEventUseCase"
    }
}

/** The family an event waits in and the parent whose answer it waits on. */
private data class AwaitedAnswer(val familyId: String, val responderUid: String)

/**
 * Who [this] stored event waits on, or null when it waits on nobody.
 *
 * It waits when the repository made it [com.coparently.app.domain.events.EventAcceptance.PENDING]
 * — created for the other parent's days — and it is not private: a private event never reaches
 * Firestore (CLAUDE.md item 3), so nobody could answer it, and a clock would show the co-parent
 * its start. It also has to name a family — a blank or malformed id is none, [FamilyKey.membersOf]
 * says — and be created by one of that family's two parents; the responder is the other one.
 * Reading the creator and the family off the stored event is what makes the clock agree with the
 * event: the repository stamps both when it stores it.
 */
private fun Event.awaitedAnswer(): AwaitedAnswer? {
    val family = familyId?.takeIf { acceptance.isPending && !isPrivate }
    val members = family?.let(FamilyKey::membersOf)
    return when {
        family == null || members == null -> null
        createdByFirebaseUid == members.first -> AwaitedAnswer(family, members.second)
        createdByFirebaseUid == members.second -> AwaitedAnswer(family, members.first)
        else -> null
    }
}

/**
 * The clock an event waiting for an answer starts (spec §4.2, §5.1), or null when it starts none:
 * [stored] waits on nobody ([awaitedAnswer]), or it begins too soon for a deadline.
 *
 * The cap is the event's start ([RequestCap.forEvent]): the moment the request would change the
 * calendar. A start already behind caps nothing, and the deadline is then the pick alone. The
 * deadline and the cap are worked out here, when the event is created, in the zone of the phone that
 * creates it, which is the zone its form showed them in.
 */
private fun eventClock(stored: Event, answerBy: AnswerByPick): RequestStart? {
    val awaited = stored.awaitedAnswer() ?: return null
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val cap = RequestCap.forEvent(stored.startDateTime, now, zone)
    val deadline = AnswerBy.resolve(answerBy, now, cap, zone) ?: return null
    return RequestStart(
        kind = RequestKind.EVENT,
        ref = stored.id,
        familyId = awaited.familyId,
        responderUid = awaited.responderUid,
        answerByMillis = deadline,
        capMillis = cap
    )
}

/**
 * Exception thrown when validation fails.
 */
class ValidationException(
    message: String,
    val field: String? = null
) : Exception(message)
