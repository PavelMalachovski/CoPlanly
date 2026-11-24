package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_dismiss
import org.jetbrains.compose.resources.stringResource

/**
 * The app's snackbar host: every screen's `Scaffold` takes it as its `snackbarHost` (owner, 24
 * November 2026). A message is drawn in CoPlanly's colours — `primaryContainer` with its
 * `onPrimaryContainer` text and the action in `primary`, on the cards' `large` corner — where
 * Material's default was a dark grey band, and it can be **swiped away** to either side; TalkBack
 * reads the same as a "Dismiss" action. Material 3's `SnackbarHost` still decides how long a message
 * stays and which comes next.
 *
 * @param hostState The screen's snackbar state
 * @param modifier Modifier for the host
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        // One swipe state per message, so a message that follows a swiped one starts in place.
        key(data) { SwipeableSnackbar(data) }
    }
}

@Composable
private fun SwipeableSnackbar(data: SnackbarData) {
    val dismissLabel = stringResource(Res.string.common_dismiss)
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(dismissLabel) {
                    data.dismiss()
                    true
                }
            )
        },
        onDismiss = { data.dismiss() },
        backgroundContent = { Box(Modifier) }
    ) {
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
