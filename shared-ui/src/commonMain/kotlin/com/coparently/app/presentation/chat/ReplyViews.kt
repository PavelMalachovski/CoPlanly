package com.coparently.app.presentation.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.chat.ReplyExcerpt
import com.coparently.app.domain.chat.ReplyQuote
import com.coparently.app.domain.model.Message
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.CoPlanlyCorners
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.labelMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.chat_cancel_reply
import com.coparently.app.resources.chat_quote_file
import com.coparently.app.resources.chat_quote_sticker
import com.coparently.app.resources.chat_quote_unavailable
import com.coparently.app.resources.chat_reply_to
import com.coparently.app.resources.chat_reply_to_own
import com.coparently.app.resources.parent_label_you
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What the thread's bubbles offer for replies (chat: replies, November 2026), provided once by
 * `MessagesList` through [LocalBubbleReplies]: a CompositionLocal rather than parameters of
 * `MessageItem`, whose signature the detekt baseline keys its entries on (as [LocalChatAttachments]).
 *
 * @property quotes The quote each reply's bubble shows, by the reply's message id
 * @property readerId The signed-in parent's uid: a quote of their own message is headed "You"
 * @property onQuoteClick Called with the quoted original when a quote is tapped
 * @property onReply Starts a reply to a message, or null where the thread takes no reply (a thread
 *   the departed co-parent can no longer answer)
 * @property pressed The message whose long-press menu is open, or null: the thread highlights it and
 *   dims the others while the menu is open (see `PressFocus`)
 * @property names How a quote names the co-parent ([chatAuthor]), or null where none are known
 */
internal class BubbleReplies(
    val quotes: Map<String, ReplyQuote>,
    val readerId: String,
    val onQuoteClick: (ReplyQuote.Shown) -> Unit,
    val onReply: ((Message) -> Unit)?,
    val pressed: MutableState<String?>,
    val names: ParentNames? = null
)

/**
 * Where a bubble stands while some bubble's long-press menu is open: the one pressed, or one of the
 * others, dimmed behind it. It is the value [PressFocus] draws from, set as semantics so a test can
 * read what is drawn without a pixel comparison; TalkBack does not announce it.
 */
enum class BubbleEmphasis {
    /** The bubble whose menu is open: highlighted. */
    PRESSED,

    /** Every other bubble while a menu is open: dimmed. */
    DIMMED
}

/** The semantics key of [BubbleEmphasis]; absent while no menu is open. */
val BubbleEmphasisKey = SemanticsPropertyKey<BubbleEmphasis>("BubbleEmphasis")

internal var SemanticsPropertyReceiver.bubbleEmphasis by BubbleEmphasisKey

/**
 * How the thread names the author of a message: through the family's [ParentNames] by uid, so a
 * co-parent who renamed is named as they are now (design item 12), and by the name the message
 * stored only when [authorId] is not one of the family's two parents (a message from before a
 * re-pairing) or no names are known yet.
 */
internal fun ParentNames?.chatAuthor(authorId: String, storedName: String): String {
    if (this == null) return storedName
    val label = labelForUid(authorId)
    return if (label == unknownFallback) storedName.ifBlank { label } else label
}

/** The thread's [BubbleReplies], or null outside a thread (a bubble then draws no quote or reply). */
internal val LocalBubbleReplies = compositionLocalOf<BubbleReplies?> { null }

/**
 * What a reply to [message] would quote, or null when there is nothing to quote: the same decision
 * `ReplyQuote.of` makes, so the thread never offers a reply `ChatViewModel.startReply` refuses
 * (design item 8).
 */
internal fun excerptOf(message: Message): ReplyExcerpt? = ReplyExcerpt.of(
    content = message.content,
    files = ChatAttachmentCodec.attachmentsOf(message.attachments),
    isSticker = message.activity?.kind == ActivityKind.STICKER_SENT
)

/** The excerpt in the reader's words: the line itself, "File: name", or "Sticker". */
@Composable
private fun excerptText(excerpt: ReplyExcerpt): String = when (excerpt) {
    is ReplyExcerpt.Text -> excerpt.line
    is ReplyExcerpt.File -> stringResource(Res.string.chat_quote_file, excerpt.name)
    ReplyExcerpt.Sticker -> stringResource(Res.string.chat_quote_sticker)
}

/**
 * The quote a reply's bubble shows over its own words, when [message] is a reply: drawn from the
 * thread's [LocalBubbleReplies], nothing otherwise.
 */
@Composable
internal fun BubbleQuote(message: Message, isCurrentUser: Boolean) {
    val replies = LocalBubbleReplies.current ?: return
    val quote = replies.quotes[message.id] ?: return
    ReplyQuoteBlock(
        quote = quote,
        onOwnBubble = isCurrentUser,
        readerId = replies.readerId,
        onClick = replies.onQuoteClick,
        modifier = Modifier.padding(bottom = Spacing.XS),
        names = replies.names
    )
}

/**
 * A quote inside a reply's bubble: a bar in the role colour of the bubble it sits on (`primary` on
 * the co-parent's, `onPrimary` on one's own; never a parent's hue, which is a person), the original
 * author's name ("You" for the reader's own, design item 22) and one line of the original. A tap
 * asks for the original ([onClick]). [ReplyQuote.Unavailable] says so, alone, and takes no tap.
 * Public for the app's `ChatReplyTest`.
 *
 * @param quote What the reply quotes
 * @param onOwnBubble Whether the quote sits on the reader's own (primary) bubble
 * @param readerId The signed-in parent's uid
 * @param onClick Called with the original when the quote is tapped
 * @param modifier Modifier for the quote
 * @param names Names the co-parent as the family does now ([chatAuthor]); null falls back to the
 *   name the original stored
 */
@Composable
// LongParameterList: the bubble's own facts and its tap, plus the names the quote is worded with.
@Suppress("LongParameterList")
fun ReplyQuoteBlock(
    quote: ReplyQuote,
    onOwnBubble: Boolean,
    readerId: String,
    onClick: (ReplyQuote.Shown) -> Unit,
    modifier: Modifier = Modifier,
    names: ParentNames? = null
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (onOwnBubble) colors.onPrimary else colors.primary
    val secondary = if (onOwnBubble) colors.onPrimary.copy(alpha = QUOTE_TEXT_ALPHA) else colors.onSurfaceVariant
    val shown = quote as? ReplyQuote.Shown
    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = QUOTE_WASH_ALPHA))
            .then(
                if (shown != null) {
                    Modifier.clickable(role = Role.Button) { onClick(shown) }
                } else {
                    Modifier
                }
            )
            .padding(end = Spacing.S)
    ) {
        Box(
            modifier = Modifier
                .width(QUOTE_BAR_WIDTH)
                .fillMaxHeight()
                .clip(CoPlanlyCorners.Mark)
                .background(accent)
        )
        Column(modifier = Modifier.padding(start = Spacing.S, top = Spacing.XS, bottom = Spacing.XS)) {
            if (shown != null) {
                Text(
                    text = if (shown.authorId == readerId) {
                        stringResource(Res.string.parent_label_you)
                    } else {
                        names.chatAuthor(shown.authorId, shown.authorName)
                    },
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = shown?.let { excerptText(it.excerpt) } ?: stringResource(Res.string.chat_quote_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The bar over the composer while a reply is chosen: the reply icon, "Reply to {name}" ("Reply to
 * your message" for the reader's own, design item 22), one line of the message answered, and ✕ to
 * drop the reply. Public for the app's `ChatReplyTest`.
 *
 * @param quote The message being answered
 * @param readerId The signed-in parent's uid
 * @param onCancel Drops the reply
 * @param modifier Modifier for the bar
 * @param names Names the co-parent as the family does now ([chatAuthor]); null falls back to the
 *   name the original stored
 */
@Composable
fun ReplyBar(
    quote: ReplyQuote.Shown,
    readerId: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    names: ParentNames? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = Spacing.L, top = Spacing.XS, bottom = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSizes.Standard)
        )
        Spacer(Modifier.width(Spacing.M))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (quote.authorId == readerId) {
                    stringResource(Res.string.chat_reply_to_own)
                } else {
                    stringResource(Res.string.chat_reply_to, names.chatAuthor(quote.authorId, quote.authorName))
                },
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = excerptText(quote.excerpt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCancel) {
            AppIcon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.chat_cancel_reply)
            )
        }
    }
}

/**
 * A bubble's swipe to reply: how far it is drawn to the right ([offset], in pixels), and what a
 * swipe past [thresholdPx] does.
 */
internal class SwipeToReplyState(
    val offset: Animatable<Float, AnimationVector1D>,
    val thresholdPx: Float,
    val maxPx: Float,
    val haptic: HapticFeedback,
    val scope: CoroutineScope,
    val onReply: () -> Unit
)

/** The swipe's state for a bubble, or null when the bubble takes no reply. */
@Composable
internal fun rememberSwipeToReply(onReply: (() -> Unit)?): SwipeToReplyState? {
    val current by rememberUpdatedState(onReply)
    val offset = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    if (onReply == null) return null
    return remember(haptic, scope, density) {
        SwipeToReplyState(
            offset = offset,
            thresholdPx = with(density) { SWIPE_THRESHOLD.toPx() },
            maxPx = with(density) { SWIPE_MAX.toPx() },
            haptic = haptic,
            scope = scope,
            onReply = { current?.invoke() }
        )
    }
}

/**
 * A horizontal drag to the right moves the bubble up to [SWIPE_MAX]; crossing [SWIPE_THRESHOLD]
 * gives one light haptic, and letting go past it starts the reply. The bubble then returns over
 * [Motion.SHORT_MS] (nothing springs, design item 11). Only a horizontal drag is taken, after the
 * touch slop: a vertical one is left to the list, which scrolls as before. The distance counts from
 * where the finger went down, the slop included.
 */
internal fun Modifier.swipeToReply(state: SwipeToReplyState): Modifier = pointerInput(state) {
    var dragged = 0f
    var crossed = false
    fun settle() {
        dragged = 0f
        crossed = false
        state.scope.launch { state.offset.animateTo(0f, tween(Motion.SHORT_MS)) }
    }
    var leftward = false
    var started = false
    detectHorizontalDragGestures(
        onDragStart = {
            dragged = 0f
            crossed = false
            started = false
            leftward = false
        },
        onDragEnd = {
            val past = !leftward && dragged >= state.thresholdPx
            settle()
            if (past) state.onReply()
        },
        onDragCancel = { settle() },
        onHorizontalDrag = { change, amount ->
            if (!started) {
                // The direction is known only with the first amount: a drag that starts leftward
                // never moves the bubble. A rightward one has already travelled the touch slop,
                // counted, so the threshold is the distance swiped from where the finger went down.
                started = true
                leftward = amount < 0f
                if (!leftward) dragged = viewConfiguration.touchSlop
            }
            if (leftward) return@detectHorizontalDragGestures
            change.consume()
            dragged = (dragged + amount).coerceIn(0f, state.maxPx)
            state.scope.launch { state.offset.snapTo(dragged) }
            if (!crossed && dragged >= state.thresholdPx) {
                crossed = true
                state.haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } else if (crossed && dragged < state.thresholdPx) {
                crossed = false
            }
        }
    )
}

/** The reply arrow the swipe uncovers at the bubble's start, fully drawn at the threshold. */
@Composable
internal fun SwipeReplyArrow(state: SwipeToReplyState, modifier: Modifier) {
    AppIcon(
        imageVector = Icons.AutoMirrored.Filled.Reply,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = Spacing.XS)
            .size(IconSizes.Small)
            .graphicsLayer { alpha = (state.offset.value / state.thresholdPx).coerceIn(0f, 1f) }
    )
}

/** How far a bubble must be drawn for a swipe to start a reply. */
private val SWIPE_THRESHOLD = 56.dp

/** The farthest a bubble is drawn by a swipe. */
private val SWIPE_MAX = 72.dp

/** The width of a quote's bar. */
private val QUOTE_BAR_WIDTH = 3.dp

/** The wash behind a quote, of its accent colour. */
private const val QUOTE_WASH_ALPHA = 0.12f

/** The quoted line on one's own bubble, a step quieter than the author's name (as the bubble's meta). */
private const val QUOTE_TEXT_ALPHA = 0.78f
