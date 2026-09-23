package com.coparently.app.presentation.custody

import com.coparently.app.domain.custody.ContactWindow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * What the contact-window editor asks for, turned into the cycle-day windows the pattern stores
 * (MON-6b).
 *
 * A parent thinks "every Wednesday" or "Wednesday of the second week"; the pattern stores cycle
 * positions, because that is how `momDayIndices` already works and how a window repeats with the
 * cycle. This is the one translation between the two, kept pure so it can be tested without a
 * ViewModel.
 *
 * @property weekday The weekday the window falls on.
 * @property week Zero-based week of the cycle, or null for every week the cycle has.
 * @property start When the window opens.
 * @property end When it closes; after [start], same day.
 * @property parent The slot the child is with during the window.
 */
data class ContactWindowDraft(
    val weekday: DayOfWeek,
    val week: Int?,
    val start: LocalTime,
    val end: LocalTime,
    val parent: String
) {
    /** Whether the draft can become windows at all: a window ends after it starts. */
    val isValid: Boolean get() = start < end

    /**
     * The windows this draft describes in a cycle of [cycleDays] days anchored at [startDate],
     * one per matching cycle day. Empty when the draft is invalid or no day of the cycle matches
     * (a week past the cycle's end).
     */
    fun toWindows(startDate: LocalDate, cycleDays: Int): List<ContactWindow> {
        if (!isValid) return emptyList()
        return (0 until cycleDays)
            .filter { index ->
                startDate.plusDays(index.toLong()).dayOfWeek == weekday &&
                    (week == null || index / DAYS_PER_WEEK == week)
            }
            .map { index -> ContactWindow(index, start, end, parent) }
    }

    companion object {
        /** Days in a week, for turning a cycle day into the week of the cycle it falls in. */
        const val DAYS_PER_WEEK = 7

        /** How many weeks a cycle of [cycleDays] days touches — the week chips the editor offers. */
        fun weeksIn(cycleDays: Int): Int = (cycleDays + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK
    }
}
