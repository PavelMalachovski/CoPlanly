package com.coparently.app.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coparently.app.R

/**
 * A screen that could not load, said the way an empty one is (docs/AUDIT-2026-10-design.md
 * D-15): the [EmptyState] anatomy — icon on a tonal disc, the sentence, one button — with
 * "Retry" as the action.
 *
 * It replaced four ways of saying the same thing: a paragraph in the error red with a filled
 * button (child information, pets), a grey line with an outlined one (profile), and red text with
 * no way out at all (the events list). Not red: a list that failed to load is usually the network,
 * and a red paragraph reads as the parent's mistake; and red body text is hard to read besides.
 * It inherits what makes [EmptyState] safe to reuse — the caller's modifier, and scrolling when
 * its height is bounded and the text is too tall.
 *
 * @param message What went wrong, in the reader's language (a resolved `UiText`)
 * @param onRetry Tries again, or null where there is nothing to retry
 * @param modifier Applied to the whole state; pass the Scaffold padding and the size here
 */
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Default.ErrorOutline,
        title = message,
        modifier = modifier,
        actionLabel = if (onRetry != null) stringResource(R.string.common_action_retry) else null,
        onAction = onRetry
    )
}
