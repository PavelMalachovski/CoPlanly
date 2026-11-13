package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Whether the hosted screen is what the parent sees now (MON-24 phase 3, part B): provided by the
 * iPhone's shell alone, where a hidden tab's screens stay composed in their view controllers. There
 * it is true only while the screen's tab is selected, the screen is on top of its stack (or under a
 * sheet presented over it) and the app is in the foreground.
 *
 * Null on Android, which means always visible: there a screen that is not shown has left
 * composition, and its effects ended with it.
 */
val LocalScreenVisible: ProvidableCompositionLocal<State<Boolean>?> = staticCompositionLocalOf { null }

/**
 * Runs [onVisible] while the screen is visible ([LocalScreenVisible]) and [onHidden] the moment it
 * stops being so or leaves composition, again for each new [key]; it runs [onVisible] once more
 * when the screen is shown again. With no [LocalScreenVisible] it is `DisposableEffect(key)` with
 * [onVisible] in its body and [onHidden] in its `onDispose`, so Android behaves as it did.
 *
 * The visibility is followed by a coroutine ([snapshotFlow]), not by recomposition: a hidden view
 * controller draws no frames, so a change that waited for the next recomposition might wait until
 * the screen is shown again. The first answer is read at once, so a visible screen runs
 * [onVisible] within the effect, in composition order, as the `DisposableEffect` did.
 *
 * @param key What restarts the effect, as a `DisposableEffect`'s key.
 * @param onVisible What the screen does on becoming visible.
 * @param onHidden What it does on stopping being visible, the undoing of [onVisible].
 */
@Composable
fun WhileScreenVisible(key: Any?, onVisible: () -> Unit, onHidden: () -> Unit) {
    val visibility = LocalScreenVisible.current
    val shown by rememberUpdatedState(onVisible)
    val hidden by rememberUpdatedState(onHidden)
    if (visibility == null) {
        DisposableEffect(key) {
            shown()
            onDispose { hidden() }
        }
        return
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(key, visibility) {
        var open = false
        // Undispatched: a screen visible now opens before the effects after this one run.
        val following = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            snapshotFlow { visibility.value }.collect { visible ->
                if (visible != open) {
                    open = visible
                    if (visible) shown() else hidden()
                }
            }
        }
        onDispose {
            following.cancel()
            if (open) hidden()
        }
    }
}
