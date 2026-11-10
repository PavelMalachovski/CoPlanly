package com.coparently.app.presentation.custody

/**
 * The parts of the custody screen, each opened from [CustodyOverview] (UX audit H3).
 *
 * The screen used to be one form of six tasks — the pattern, contact windows, the deadline for the
 * answer, seasonal schedules, holidays and a child's own schedule — with one "Send for approval" at
 * the bottom that sent only the first two, while the seasonal ones went on their own. Each part now
 * has a screen of its own, so a parent can tell what a button sends.
 */
enum class CustodySection {
    /** The whole-day pattern: its type, start and who starts. Sent with the pinned button. */
    PATTERN,

    /** Contact windows on top of the whole days (MON-6b). Sent with the pinned button. */
    WINDOWS,

    /** Seasonal schedules (MON-14) and the year's holidays (MON-20), each change sent on its own. */
    SEASONAL,

    /** A child's own schedule (FAM-4), at two children or more. */
    CHILDREN
}
