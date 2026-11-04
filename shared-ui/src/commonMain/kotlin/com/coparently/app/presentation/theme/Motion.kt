package com.coparently.app.presentation.theme

import androidx.compose.runtime.Composable

/**
 * The app's motion durations — the one place a timing is chosen.
 *
 * Three steps and no others, so that two things of the same kind cannot move at different
 * speeds on two screens: [SHORT_MS] for fades and micro-feedback, [MEDIUM_MS] for navigation and
 * content swaps, [LONG_MS] for a page (month) changing. In-screen expand and collapse is
 * `sectionEnter`/`sectionExit` (`common/animations`), built from the same steps, and nothing in
 * the app springs — the one bouncy spring, on the event form's parent cards, went in the October
 * 2026 audit (D-25).
 *
 * Material's `MotionScheme` is not set, and cannot be yet: material3 1.4, the stable line this
 * app builds on, keeps it out of its public API with the rest of Material 3 Expressive, which is
 * still alpha (1.5). This line used to say springs came from `MaterialTheme.motionScheme`; no
 * code ever read it.
 */
object Motion {
    /** Fades, chevrons, view-mode crossfades. */
    const val SHORT_MS = 150

    /** Navigation push/pop and form screens. */
    const val MEDIUM_MS = 300

    /** A month or page changing. */
    const val LONG_MS = 500

    /**
     * How long something the app pointed the reader at stays marked before the mark fades — a
     * chat message a search result jumped to (MON-15). Not a movement, a hold: long enough to find
     * the bubble after the list lands, short enough that it is gone by the time they read on. The
     * fade itself is [MEDIUM_MS].
     */
    const val HIGHLIGHT_HOLD_MS = 1500
}

/**
 * True when the person has switched animations off (Developer options or Accessibility →
 * Remove animations, both of which set the animator duration scale to 0).
 *
 * Compose tweens and springs already honour that scale; what does not is anything the app
 * *times itself* — a `delay` — and decoration that loops forever. Those read this: the sign-in
 * logo's pulse stands still and the loading skeletons stop shimmering.
 *
 * On the iPhone it is Settings → Accessibility → Motion → Reduce Motion (`Motion.ios.kt`).
 *
 * @return Whether looping decoration and self-timed delays should stand still
 */
@Composable
expect fun rememberReducedMotion(): Boolean
