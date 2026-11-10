package com.coparently.app.domain.usecase

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * The `java.time` recurrence expansion and reminder planning the app ran before MON-24, kept here
 * verbatim as the oracle [EventTimeParityTest] holds the kotlinx-datetime code to. Only the event is
 * reduced to the fields the two read.
 */
// Kept exactly as the old code was written, complexity included: it is the reference, not a design.
@Suppress("CyclomaticComplexMethod", "ReturnCount", "LoopWithTooManyJumpStatements")
internal object EventTimeOracle {

    data class OldEvent(
        val start: LocalDateTime,
        val end: LocalDateTime?,
        val isRecurring: Boolean,
        val pattern: String?,
        val recurrenceEndDate: LocalDate?,
        val reminderMinutes: Int?
    )

    data class OldPlan(val occurrenceStart: LocalDateTime, val remindAt: LocalDateTime)

    private const val MAX_OCCURRENCES = 730
    private const val BIWEEKLY_WEEKS = 2L
    private const val SEARCH_HORIZON_DAYS = 62L

    /** `RecurrenceExpander.expand`, as an occurrence's start and end. */
    fun expand(
        event: OldEvent,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime
    ): List<Pair<LocalDateTime, LocalDateTime?>> {
        if (!event.isRecurring || event.pattern.isNullOrBlank()) {
            return if (event.start <= rangeEnd && (event.end ?: event.start) >= rangeStart) {
                listOf(event.start to event.end)
            } else {
                emptyList()
            }
        }
        val pattern = event.pattern
        val duration = event.end?.let { Duration.between(event.start, it) }
        val recurrenceEnd = event.recurrenceEndDate?.atTime(23, 59, 59)
        val occurrences = mutableListOf<Pair<LocalDateTime, LocalDateTime?>>()
        var index = firstIndexEndingAtOrAfter(event.start, pattern, rangeStart, duration)
        while (occurrences.size < MAX_OCCURRENCES) {
            val start = occurrenceAt(event.start, pattern, index) ?: break
            if (start > rangeEnd) break
            if (recurrenceEnd != null && start > recurrenceEnd) break
            val end = duration?.let { start.plus(it) }
            if ((end ?: start) >= rangeStart) occurrences.add(start to end)
            index++
        }
        return occurrences
    }

    private fun occurrenceAt(start: LocalDateTime, pattern: String, index: Long): LocalDateTime? =
        when (pattern) {
            "daily" -> start.plusDays(index)
            "weekly" -> start.plusWeeks(index)
            "biweekly" -> start.plusWeeks(index * BIWEEKLY_WEEKS)
            "monthly" -> start.plusMonths(index)
            else -> start.takeIf { index == 0L }
        }

    private fun firstIndexEndingAtOrAfter(
        start: LocalDateTime,
        pattern: String,
        rangeStart: LocalDateTime,
        duration: Duration?
    ): Long {
        val target = duration?.takeUnless { it.isNegative }?.let { rangeStart.minus(it) } ?: rangeStart
        if (start >= target) return 0
        val estimate = when (pattern) {
            "daily" -> ChronoUnit.DAYS.between(start, target)
            "weekly" -> ChronoUnit.WEEKS.between(start, target)
            "biweekly" -> ChronoUnit.WEEKS.between(start, target) / BIWEEKLY_WEEKS
            "monthly" -> ChronoUnit.MONTHS.between(start, target)
            else -> return 0
        }.coerceAtLeast(0)
        val candidate = occurrenceAt(start, pattern, estimate) ?: return 0
        return if (candidate < target) estimate + 1 else estimate
    }

    /** `ReminderPlanner.nextReminder`. */
    fun nextReminder(event: OldEvent, now: LocalDateTime): OldPlan? {
        val lead = event.reminderMinutes?.takeIf { it >= 0 }?.toLong() ?: return null
        val earliestStart = now.plusMinutes(lead)
        val start: LocalDateTime? = if (isRecurring(event)) {
            expand(event.copy(end = null), earliestStart, earliestStart.plusDays(SEARCH_HORIZON_DAYS))
                .asSequence()
                .map { it.first }
                .firstOrNull { it > earliestStart }
        } else {
            event.start.takeIf { it > earliestStart }
        }
        return start?.let { OldPlan(occurrenceStart = it, remindAt = it.minusMinutes(lead)) }
    }

    /** `ReminderPlanner.isOccurrence`. */
    fun isOccurrence(event: OldEvent, start: LocalDateTime): Boolean =
        if (isRecurring(event)) {
            expand(event.copy(end = null), start, start).any { it.first == start }
        } else {
            event.start == start
        }

    private fun isRecurring(event: OldEvent): Boolean = event.isRecurring && !event.pattern.isNullOrBlank()

    /** `AllDayEvent.isAllDay`. */
    fun isAllDay(start: LocalDateTime, end: LocalDateTime?): Boolean {
        if (start.toLocalTime() != LocalTime.MIDNIGHT) return false
        val e = end ?: return true
        val endTime = e.toLocalTime()
        return !e.isBefore(start) && (endTime == LocalTime.MIDNIGHT || !endTime.isBefore(LocalTime.of(23, 59)))
    }

    /** `AllDayEvent.lastDay`. */
    fun lastDay(start: LocalDateTime, end: LocalDateTime?): LocalDate {
        val first = start.toLocalDate()
        val e = end ?: return first
        val last = if (e.toLocalTime() == LocalTime.MIDNIGHT) e.toLocalDate().minusDays(1) else e.toLocalDate()
        return if (last.isBefore(first)) first else last
    }
}
