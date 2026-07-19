package com.coparently.app.presentation.calendar

/**
 * Calendar view modes, ordered by roadmap priority: month first, week next, day third.
 */
enum class CalendarViewMode {
    MONTH,
    WEEK,
    DAY
}

/**
 * Which parent's events are visible in the calendar.
 * BOTH shows the mutual view with both parents' events at the same time.
 */
enum class ParentFilter {
    BOTH,
    MOM,
    DAD
}
