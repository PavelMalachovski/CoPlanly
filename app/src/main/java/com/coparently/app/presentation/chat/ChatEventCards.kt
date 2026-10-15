package com.coparently.app.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.event.EventPreviewSheet

/**
 * Opens a chat card about an event, given the event's id ([ChatCardTarget.Event]) — or null where
 * no thread provides it (a preview, a screenshot test), so `MessageItem` draws such a card inert.
 *
 * A CompositionLocal rather than a parameter for the reason [LocalChatAttachments] gives:
 * `MessageItem`'s signature is what the detekt baseline keys two of its entries on.
 */
val LocalChatEventCard = compositionLocalOf<((eventId: String) -> Unit)?> { null }

/**
 * Provides [LocalChatEventCard] for [content], and shows what a tap on an event card finds
 * ([EventCardOutcome]): the event's preview over the thread, whose Edit opens the editor — the
 * preview first and the editor one tap further, as on Home and the calendar (design item 5); the
 * inbox, when the event still waits on this parent's answer; or a sentence saying the event no
 * longer exists.
 *
 * The cards stay inert unless both destinations are wired, because every tap ends at one of them.
 *
 * @param parentNames Names the event's parent in the preview
 * @param onEditEvent Opens the editor for an event id: the preview's Edit
 * @param onOpenInbox Opens the change-request inbox with nothing highlighted
 * @param viewModel The lookup behind the cards
 * @param content The thread whose cards open events
 */
@Composable
fun ChatEventCardHost(
    parentNames: ParentNames,
    onEditEvent: ((eventId: String) -> Unit)?,
    onOpenInbox: (() -> Unit)?,
    viewModel: ChatEventCardViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val enabled = onEditEvent != null && onOpenInbox != null
    val outcome by viewModel.outcome.collectAsState()
    val editEvent by rememberUpdatedState(onEditEvent)
    val openInbox by rememberUpdatedState(onOpenInbox)
    // The thread's Scaffold holds no snackbar, so the sentence goes to the root one, as the
    // thread's other messages do (`ChatAttachmentsHost`). Resolved here: the effect below is not
    // composable and cannot read a resource itself.
    val appMessages = LocalAppMessages.current
    val missingText = stringResource(R.string.chat_event_missing)

    // The inbox and the sentence are acted on once; the preview stays until it is closed.
    val current = outcome
    LaunchedEffect(current) {
        when (current) {
            EventCardOutcome.AwaitingMe -> {
                viewModel.dismiss()
                openInbox?.invoke()
            }
            EventCardOutcome.Missing -> {
                viewModel.dismiss()
                appMessages?.show(missingText)
            }
            is EventCardOutcome.Preview, null -> Unit
        }
    }

    if (current is EventCardOutcome.Preview) {
        val event = current.event
        EventPreviewSheet(
            event = event,
            parentNames = parentNames,
            onEdit = {
                viewModel.dismiss()
                editEvent?.invoke(event.id)
            },
            // No delete here, as on Home: the thread has no delete-with-undo, and the editor one
            // tap away does.
            onDelete = null,
            onDismiss = viewModel::dismiss
        )
    }

    val open: ((String) -> Unit)? = remember(viewModel, enabled) {
        if (enabled) viewModel::open else null
    }
    CompositionLocalProvider(LocalChatEventCard provides open, content = content)
}
