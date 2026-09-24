package com.coparently.app.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.coparently.app.presentation.theme.Motion

/**
 * Whether a floating action button is shown, following the reader's scroll.
 *
 * Hidden while the content moves forward, back while it moves back, and back once the content
 * has reached its end — where the screen's bottom clearance keeps the last row clear of the
 * button. That is the one position in which a floating button can honestly promise to cover
 * nothing; everywhere else on a list whose trailing column is money, something sits under it
 * (release audit R-1: "CZK1,89…" behind the Expenses "+", before any scroll at all).
 *
 * It reads [connection], a pass-through: it consumes nothing, so the list scrolls exactly as it
 * did. A TalkBack scroll action reaches the list by `scrollBy`, not through nested scrolling, so
 * the button never hides from a reader who cannot see it go.
 */
@Stable
class FabScrollVisibility internal constructor() {

    /** Whether the button is on screen. */
    var visible by mutableStateOf(true)
        private set

    /** Brings the button back — when the content under it changes wholesale (a month, a view). */
    fun show() {
        visible = true
    }

    /** Attach with `Modifier.nestedScroll(connection)` on an ancestor of the scrolling content. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Negative y is the finger moving up: the content moving forward.
            if (available.y < 0f) visible = false
            if (available.y > 0f) visible = true
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // Scroll left over in the forward direction means the end has been reached.
            if (available.y < 0f) visible = true
            return Offset.Zero
        }
    }
}

/** A [FabScrollVisibility] that lives as long as the calling composable. */
@Composable
fun rememberFabScrollVisibility(): FabScrollVisibility = remember { FabScrollVisibility() }

/**
 * A floating button that leaves and returns as [visibility] says, scaling and fading in the app's
 * short step — the same movement Material gives a FAB, without the spring.
 */
@Composable
fun ScrollAwareFab(visibility: FabScrollVisibility, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visibility.visible,
        enter = scaleIn(tween(Motion.SHORT_MS)) + fadeIn(tween(Motion.SHORT_MS)),
        exit = scaleOut(tween(Motion.SHORT_MS)) + fadeOut(tween(Motion.SHORT_MS))
    ) {
        content()
    }
}
