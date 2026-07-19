package com.coparently.app.domain.usecase

import com.coparently.app.domain.model.Event
import java.time.Duration
import java.time.LocalDateTime

/**
 * Expands recurring events into concrete occurrences within a date range.
 *
 * Occurrences keep the id of their master event so tapping one opens the
 * master for editing. Supported patterns: "daily", "weekly", "biweekly", "monthly".
 */
object RecurrenceExpander {

    /** Safety cap so a malformed pattern can never loop forever. */
    private const val MAX_OCCURRENCES = 730

    /**
     * Expands a recurring event into occurrences overlapping [rangeStart, rangeEnd].
     * Returns the event as-is (single occurrence) when it is not recurring.
     */
    fun expand(event: Event, rangeStart: LocalDateTime, rangeEnd: LocalDateTime): List<Event> {
        if (!event.isRecurring || event.recurrencePattern.isNullOrBlank()) {
            return if (event.startDateTime <= rangeEnd &&
                (event.endDateTime ?: event.startDateTime) >= rangeStart
            ) listOf(event) else emptyList()
        }

        val duration = event.endDateTime?.let { Duration.between(event.startDateTime, it) }
        val recurrenceEnd = event.recurrenceEndDate?.atTime(23, 59, 59)

        val occurrences = mutableListOf<Event>()
        var current = event.startDateTime
        var count = 0

        while (current <= rangeEnd && count < MAX_OCCURRENCES) {
            if (recurrenceEnd != null && current > recurrenceEnd) break

            val occurrenceEnd = duration?.let { current.plus(it) } ?: current
            if (occurrenceEnd >= rangeStart) {
                occurrences.add(
                    event.copy(
                        startDateTime = current,
                        endDateTime = duration?.let { current.plus(it) }
                    )
                )
            }

            current = next(current, event.recurrencePattern) ?: break
            count++
        }

        return occurrences
    }

    /**
     * Expands every event in the list against the given range and sorts the result.
     */
    fun expandAll(events: List<Event>, rangeStart: LocalDateTime, rangeEnd: LocalDateTime): List<Event> {
        return events
            .flatMap { expand(it, rangeStart, rangeEnd) }
            .sortedBy { it.startDateTime }
    }

    private fun next(current: LocalDateTime, pattern: String): LocalDateTime? = when (pattern) {
        "daily" -> current.plusDays(1)
        "weekly" -> current.plusWeeks(1)
        "biweekly" -> current.plusWeeks(2)
        "monthly" -> current.plusMonths(1)
        else -> null
    }
}
