package com.coparently.app.domain.usecase.ai

import com.coparently.app.data.remote.ai.AIService
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ai.*
import com.coparently.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for AI-powered smart scheduling
 * Day 1 - Feature 1.1: AI-powered conflict resolution
 */
class SmartSchedulingUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val aiService: AIService,
    private val conflictDetector: ConflictDetector
) {
    suspend fun findOptimalTimeSlot(
        eventRequest: EventRequest,
        parentCalendars: List<CalendarData>
    ): List<TimeSlotSuggestion> {
        // Find all conflicts
        val conflicts = conflictDetector.findConflicts(
            eventRequest.duration,
            eventRequest.preferredTimes,
            parentCalendars
        )

        if (conflicts.isEmpty()) {
            // No conflicts - return preferred slots
            return eventRequest.preferredTimes.map { timeSlot ->
                TimeSlotSuggestion(
                    startTime = timeSlot,
                    endTime = timeSlot.plus(eventRequest.duration),
                    score = 1.0,
                    reasoning = "Perfect time slot - no conflicts"
                )
            }
        }

        // Use AI to find optimal alternatives
        val prompt = buildSchedulingPrompt(eventRequest, conflicts, parentCalendars)
        return aiService.generateTimeSlotSuggestions(prompt)
    }

    private fun buildSchedulingPrompt(
        request: EventRequest,
        conflicts: List<Conflict>,
        calendars: List<CalendarData>
    ): String {
        return """
            Analyze the following scheduling request and find optimal time slots:

            Event: ${request.title}
            Duration: ${request.duration.toMinutes()} minutes
            Preferred times: ${request.preferredTimes.joinToString { it.toString() }}
            Event type: ${request.eventType}

            Existing conflicts:
            ${conflicts.joinToString("\n") { "- ${it.description}" }}

            Parent schedules:
            ${calendars.joinToString("\n") { calendar ->
                "Parent: ${calendar.parentName}\n${calendar.events.joinToString("\n") {
                    "  - ${it.title} at ${it.startDateTime}"
                }}"
            }}

            Suggest 3 optimal time slots that minimize conflicts and consider:
            1. Work schedules and typical availability
            2. Child's routine and energy levels
            3. Travel time between activities
            4. Family preferences and patterns

            Return in JSON array format: [{"startTime": "2024-01-01T10:00:00", "endTime": "2024-01-01T11:00:00", "score": 0.95, "reasoning": "..."}]
        """.trimIndent()
    }
}

/**
 * Conflict detector for finding scheduling conflicts
 */
class ConflictDetector @Inject constructor() {

    fun findConflicts(
        duration: java.time.Duration,
        preferredTimes: List<LocalDateTime>,
        calendars: List<CalendarData>
    ): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()

        preferredTimes.forEach { preferredTime ->
            val endTime = preferredTime.plus(duration)

            calendars.forEach { calendar ->
                val overlappingEvents = calendar.events.filter { event ->
                    val eventEnd = event.endDateTime ?: event.startDateTime.plusHours(1)
                    event.startDateTime.isBefore(endTime) &&
                            eventEnd.isAfter(preferredTime)
                }

                overlappingEvents.forEach { event ->
                    conflicts.add(
                        Conflict(
                            timeSlot = preferredTime to endTime,
                            conflictingEventId = event.id,
                            conflictingEventTitle = event.title,
                            parentName = calendar.parentName,
                            severity = calculateConflictSeverity(event, duration),
                            description = "${calendar.parentName} has ${event.title}"
                        )
                    )
                }
            }
        }

        return conflicts.distinctBy { it.conflictingEventId }
    }

    private fun calculateConflictSeverity(
        event: Event,
        requestedDuration: java.time.Duration
    ): ConflictSeverity {
        return when {
            event.eventType in listOf("work", "meeting") -> ConflictSeverity.HIGH
            event.eventType in listOf("doctor", "school") -> ConflictSeverity.MEDIUM
            else -> ConflictSeverity.LOW
        }
    }
}
