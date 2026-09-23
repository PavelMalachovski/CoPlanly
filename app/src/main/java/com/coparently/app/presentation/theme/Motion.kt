package com.coparently.app.presentation.theme

/**
 * The app's motion durations — the one place a timing is chosen.
 *
 * Three steps and no others, so that two things of the same kind cannot move at different
 * speeds on two screens: [SHORT_MS] for fades and micro-feedback, [MEDIUM_MS] for navigation and
 * content swaps, [LONG_MS] for a page (month) changing. Springs for in-screen expand/collapse
 * come from `MaterialTheme.motionScheme` rather than from here.
 */
object Motion {
    /** Fades, chevrons, view-mode crossfades. */
    const val SHORT_MS = 150

    /** Navigation push/pop and form screens. */
    const val MEDIUM_MS = 300

    /** A month or page changing, and the splash exit. */
    const val LONG_MS = 500
}
