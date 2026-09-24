package com.coparently.app.presentation.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Messages that must outlive the screen that raised them, shown as snackbars in the root
 * Scaffold (docs/AUDIT-2026-10-design.md D-25).
 *
 * These were Toasts: a save that navigates away and still owes the parent a warning (a photo
 * or a receipt that did not upload), or an error raised where there is no Scaffold to hold a
 * snackbar. A screen's own snackbar dies with the screen, and the scope that shows it too,
 * which is why the Toasts were there. This host lives with the navigation graph, so the
 * message is a snackbar like every other one in the app and still survives the navigation.
 *
 * @property hostState What the root Scaffold's snackbar host shows.
 */
class AppMessages internal constructor(
    private val scope: CoroutineScope,
    val hostState: SnackbarHostState
) {
    /** Shows [message] and returns at once: the snackbar outlives whoever asked for it. */
    fun show(message: String) {
        scope.launch { hostState.showSnackbar(message, duration = SnackbarDuration.Long) }
    }
}

/** The app's [AppMessages]; null outside the navigation graph (previews, component tests). */
val LocalAppMessages = staticCompositionLocalOf<AppMessages?> { null }

/** An [AppMessages] that lives as long as the composition that remembers it — the graph's. */
@Composable
fun rememberAppMessages(): AppMessages {
    val scope = rememberCoroutineScope()
    return remember(scope) { AppMessages(scope, SnackbarHostState()) }
}
