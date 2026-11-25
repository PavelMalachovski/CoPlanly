package com.coparently.app.presentation.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.coparently.app.domain.chat.MessageReport
import com.coparently.app.domain.chat.ReplyExcerpt
import com.coparently.app.domain.model.Message
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.BarMenuItem
import com.coparently.app.presentation.common.nativeControls
import com.coparently.app.presentation.platform.LocalSensitiveClipboard
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.resources.Res
import com.coparently.app.resources.chat_copy_text
import com.coparently.app.resources.chat_reply
import com.coparently.app.resources.chat_report_message
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Draws a bubble's row as the long-press menu leaves it (the approved board): while a menu is open
 * the pressed bubble is washed in `primaryContainer` and every other bubble is dimmed, both over
 * [Motion.SHORT_MS], and all return when the menu closes. Outside a thread it draws [content] as is.
 *
 * @param messageId The message the row shows
 * @param content The row
 */
@Composable
internal fun PressFocus(messageId: String, content: @Composable () -> Unit) {
    val pressed = LocalBubbleReplies.current?.pressed?.value
    val emphasis = when (pressed) {
        null -> null
        messageId -> BubbleEmphasis.PRESSED
        else -> BubbleEmphasis.DIMMED
    }
    val alpha by animateFloatAsState(
        targetValue = if (emphasis == BubbleEmphasis.DIMMED) DIMMED_ALPHA else 1f,
        animationSpec = tween(Motion.SHORT_MS),
        label = "bubbleDim"
    )
    val wash by animateColorAsState(
        targetValue = if (emphasis == BubbleEmphasis.PRESSED) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = PRESSED_WASH_ALPHA)
        } else {
            Color.Transparent
        },
        animationSpec = tween(Motion.SHORT_MS),
        label = "bubblePressed"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(MaterialTheme.shapes.small)
            .background(wash)
            .semantics { emphasis?.let { bubbleEmphasis = it } }
    ) {
        content()
    }
}

/** One entry of a bubble's long-press menu, in both anatomies. */
private class BubbleAction(val label: String, val sfSymbol: String, val run: () -> Unit)

/**
 * Wraps a message bubble with what can be done to it: a swipe to the right starts a reply
 * ([swipeToReply]), and a long press opens the bubble's one menu — Reply, Copy text (a text message
 * only, where the phone has a clipboard) and Report message (the co-parent's words, inside a
 * [ChatReportHost] with an address). Under the iPhone's native controls the menu is the system's
 * action sheet, and on Android Material's menu anchored on the bubble. While it is open the thread
 * marks the bubble pressed ([PressFocus]). TalkBack and VoiceOver get the same entries as custom
 * actions, Reply on every message a reply can quote. The bubble is always drawn in the same place,
 * so a menu entry appearing or going does not recreate it; only the gestures and actions follow.
 *
 * @param message The message the bubble shows
 * @param isCurrentUser Whether the reader sent it
 * @param content The bubble
 */
@Composable
internal fun MessageActions(message: Message, isCurrentUser: Boolean, content: @Composable () -> Unit) {
    val actions = rememberBubbleActions(message, isCurrentUser)
    val replies = LocalBubbleReplies.current
    val reply = replies?.onReply?.takeIf { excerptOf(message) != null }
    // The thread's, so it can highlight this bubble and dim the others; a bubble outside a thread
    // keeps its own.
    val ownPressed = remember { mutableStateOf<String?>(null) }
    val pressed = replies?.pressed ?: ownPressed
    val menuOpen = pressed.value == message.id
    DisposableEffect(pressed, message.id) {
        // A bubble that leaves the screen with its menu open must not leave the thread dimmed.
        onDispose { if (pressed.value == message.id) pressed.value = null }
    }
    val haptic = LocalHapticFeedback.current
    val swipe = rememberSwipeToReply(onReply = reply?.let { start -> { start(message) } })
    Box(
        modifier = Modifier
            .then(if (swipe != null) Modifier.swipeToReply(swipe) else Modifier)
            .then(
                if (actions.isEmpty()) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(pressed, message.id) {
                            detectTapGestures(
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pressed.value = message.id
                                }
                            )
                        }
                        // One node for the message, so TalkBack reads it whole and offers its actions.
                        .semantics(mergeDescendants = true) {
                            customActions = actions.map { action ->
                                CustomAccessibilityAction(action.label) {
                                    action.run()
                                    true
                                }
                            }
                        }
                }
            )
    ) {
        if (swipe != null) SwipeReplyArrow(swipe, Modifier.align(Alignment.CenterStart))
        Box(modifier = Modifier.offset { IntOffset(swipe?.offset?.value?.roundToInt() ?: 0, 0) }) {
            content()
        }
        if (actions.isNotEmpty()) {
            BubbleMenu(
                actions = actions,
                open = menuOpen,
                onClose = { if (pressed.value == message.id) pressed.value = null }
            )
        }
    }
}

/** The bubble's entries, in their order: Reply, Copy text, Report message. */
@Composable
private fun rememberBubbleActions(message: Message, isCurrentUser: Boolean): List<BubbleAction> {
    val excerpt = excerptOf(message)
    val reply = LocalBubbleReplies.current?.onReply?.takeIf { excerpt != null }
    val clipboard = LocalSensitiveClipboard.current.takeIf { it.available && excerpt is ReplyExcerpt.Text }
    val report = LocalChatMessageReport.current?.takeIf { MessageReport.isReportable(message, isCurrentUser) }
    val replyLabel = stringResource(Res.string.chat_reply)
    val copyLabel = stringResource(Res.string.chat_copy_text)
    val reportLabel = stringResource(Res.string.chat_report_message)
    val currentReply by rememberUpdatedState(reply)
    val currentReport by rememberUpdatedState(report)
    return remember(message, reply != null, clipboard, report != null, replyLabel, copyLabel, reportLabel) {
        listOfNotNull(
            reply?.let { BubbleAction(replyLabel, "arrowshape.turn.up.left") { currentReply?.invoke(message) } },
            clipboard?.let { board ->
                BubbleAction(copyLabel, "doc.on.doc") { board.copySensitive(copyLabel, message.content) }
            },
            report?.let { BubbleAction(reportLabel, "flag") { currentReport?.invoke(message) } }
        )
    }
}

/** The long-press menu: the system's action sheet under native controls, else Material's menu. */
@Composable
private fun BubbleMenu(actions: List<BubbleAction>, open: Boolean, onClose: () -> Unit) {
    val native = nativeControls()
    if (native != null) {
        if (open) {
            native.ActionSheet(
                title = null,
                items = actions.map { action ->
                    BarMenuItem(
                        label = action.label,
                        sfSymbol = action.sfSymbol,
                        onClick = {
                            onClose()
                            action.run()
                        }
                    )
                },
                onDismiss = onClose
            )
        }
    } else {
        DropdownMenu(expanded = open, onDismissRequest = onClose) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { AppIcon(menuIconOf(action.sfSymbol), contentDescription = null) },
                    onClick = {
                        onClose()
                        action.run()
                    }
                )
            }
        }
    }
}

/** Material's icon for a menu entry, by the SF Symbol the iPhone draws for it. */
private fun menuIconOf(sfSymbol: String) = when (sfSymbol) {
    "doc.on.doc" -> Icons.Default.ContentCopy
    "flag" -> Icons.Outlined.Flag
    else -> Icons.AutoMirrored.Filled.Reply
}

/** How far the other bubbles fade while one bubble's menu is open. */
private const val DIMMED_ALPHA = 0.4f

/** The wash over the pressed bubble's row while its menu is open. */
private const val PRESSED_WASH_ALPHA = 0.6f
