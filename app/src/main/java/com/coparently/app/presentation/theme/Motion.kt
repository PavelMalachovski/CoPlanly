package com.coparently.app.presentation.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
 * *times itself* — a `delay` — and decoration that loops forever. Those read this: the splash
 * skips its entrance and its hold, and the decorative pulses stand still.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
