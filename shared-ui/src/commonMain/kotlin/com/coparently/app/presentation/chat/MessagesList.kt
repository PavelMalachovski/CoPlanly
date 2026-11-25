package com.coparently.app.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.chat.NewestButton
import com.coparently.app.domain.chat.ReplyQuote
import com.coparently.app.domain.chat.isShownInThread
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.LocalScreenVisible
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.chatBubbleShape
import com.coparently.app.resources.Res
import com.coparently.app.resources.chat_day_today
import com.coparently.app.resources.chat_day_yesterday
import com.coparently.app.resources.chat_failed_to_send
import com.coparently.app.resources.chat_failed_to_send_retry
import com.coparently.app.resources.chat_load_earlier
import com.coparently.app.resources.chat_messages_empty_description
import com.coparently.app.resources.chat_messages_empty_title
import com.coparently.app.resources.chat_new_messages
import com.coparently.app.resources.chat_open_change_request
import com.coparently.app.resources.chat_open_event
import com.coparently.app.resources.chat_scroll_to_newest
import com.coparently.app.resources.chat_status_delivered
import com.coparently.app.resources.chat_status_error
import com.coparently.app.resources.chat_status_read
import com.coparently.app.resources.chat_status_sending
import com.coparently.app.resources.chat_status_sent
import com.coparently.app.resources.chat_unread_count_overflow
import com.coparently.app.resources.parent_label_you
import com.coparently.app.utils.DAY_WITH_WEEKDAY
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.readerMediumDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Consecutive messages from the same sender within this gap render as one group. */
private const val GROUPING_WINDOW_MS = 5 * 60 * 1000L

/**
 * The local day a message was sent on, in the reading device's zone.
 *
 * Day separators have to agree with the times printed under the bubbles, and those are rendered
 * locally by [formatSentAt] — so the day has to be derived the same way. Deriving it from the
 * instant alone would put a message sent at 23:30 in one zone under the wrong heading in another.
 */
private fun Message.localDate(): LocalDate = sentAtLocal(sentAtMillis).date

/**
 * One entry in the rendered thread: either a day separator or a message with the grouping
 * information the renderer needs.
 */
internal sealed interface ThreadEntry {

    /** A "Today" / "12 Aug 2026" pill above the first message of a day. */
    data class DayHeader(val date: LocalDate) : ThreadEntry

    /**
     * A message plus where it sits in its group.
     *
     * @property message The message
     * @property startsGroup First of a run by the same sender — gets the larger top gap
     * @property endsGroup Last of a run — the only one to show a timestamp and receipt
     */
    data class Bubble(
        val message: Message,
        val startsGroup: Boolean,
        val endsGroup: Boolean
    ) : ThreadEntry
}

/**
 * Splits a flat message list into day separators and grouped bubbles.
 *
 * Grouping and day separators were the August 2026 audit's finding on chat: every bubble carried
 * its own 10sp timestamp inside it, three messages sent in the same minute looked like three
 * unrelated events, and nothing marked where yesterday ended.
 *
 * Only what the thread draws goes in ([isShownInThread], November 2026): the parents' messages,
 * their files and stickers. The app's other cards stay in the conversation for the export and are
 * left out here, before the grouping and the separators, so a day that held only cards gets no
 * separator and a card between two messages does not split their group.
 *
 * @param all Messages in ascending time order
 * @return Renderable entries in display order
 */
internal fun buildThread(all: List<Message>): List<ThreadEntry> {
    val messages = all.filter(::isShownInThread)
    val entries = mutableListOf<ThreadEntry>()
    messages.forEachIndexed { index, message ->
        val previous = messages.getOrNull(index - 1)
        val next = messages.getOrNull(index + 1)
        val day = message.localDate()

        if (previous == null || previous.localDate() != day) {
            entries += ThreadEntry.DayHeader(day)
        }

        val continuesFromPrevious = previous != null &&
            previous.senderId == message.senderId &&
            previous.localDate() == day &&
            message.sentAtMillis - previous.sentAtMillis <= GROUPING_WINDOW_MS
        val continuesIntoNext = next != null &&
            next.senderId == message.senderId &&
            next.localDate() == day &&
            next.sentAtMillis - message.sentAtMillis <= GROUPING_WINDOW_MS

        entries += ThreadEntry.Bubble(
            message = message,
            startsGroup = !continuesFromPrevious,
            endsGroup = !continuesIntoNext
        )
    }
    return entries
}

/**
 * [buildThread] in the order the reversed list draws it: the newest message first.
 *
 * The thread is a `LazyColumn` with `reverseLayout = true`, so item 0 is drawn at the bottom, just
 * above the composer, and a day header that follows its day's messages here sits above them on
 * screen, directly over the day's oldest one.
 *
 * @param messages Messages in ascending time order
 * @return Renderable entries, newest first
 */
internal fun threadNewestFirst(messages: List<Message>): List<ThreadEntry> = buildThread(messages).reversed()

/**
 * The message thread, with day separators, grouped bubbles and delivery state.
 *
 * A reversed list whose item 0 is the newest message (chat: newest message always in view,
 * November 2026). Its position is not saved: a thread opened again — the Chat tab entered, a push
 * tapped, a return from another screen, a rotation — starts at item 0, the newest message, just
 * above the composer. Because the list is anchored at its bottom, the keyboard opening, the
 * composer growing over several lines and a reply bar all keep the newest message in view with no
 * scroll code: the insets are the NavHost's alone (design item 14). A message that arrives while
 * the reader is in history leaves the list where it is, and the "newest messages" button counts it
 * ([NewestButton]); one's own message always brings the newest into view.
 *
 * @param messages Messages in ascending time order, with [Message.status] already promoted to
 *   DELIVERED/READ by `ChatViewModel` via `ChatReadState.statusFor`
 * @param currentUserId Firebase uid of the signed-in parent, to side the bubbles
 * @param onRefresh Pull-to-refresh handler, or null to disable the gesture
 * @param onEventLinkClick Handler for tapping a change-request card, given the event id it names,
 *   or null to render such cards inert (e.g. while the destination is not wired up yet). A card
 *   about an event itself opens through [LocalChatEventCard] instead — see [ChatCardTarget]
 * @param modifier Modifier for the list
 * @param revealMessageId A message to scroll to and briefly highlight — a chat search result
 *   (MON-15) — or null. It may not be loaded yet: the caller widens the window, and the scroll
 *   happens once the message arrives.
 * @param onRevealed Called once the list has scrolled to [revealMessageId], so the caller can
 *   drop the request
 * @param quotes The quote each reply's bubble shows, by the reply's message id (`ChatViewModel.quotes`)
 * @param onQuoteClick Called with the quoted original when a reply's quote is tapped
 * @param onReply Called with a message the parent chose to answer, by a swipe to the right or from
 *   its long-press menu; null where the thread takes no reply, which then offers none
 * @param names How a quote names the co-parent (design item 12); null falls back to the name each
 *   original stored
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// One callback per action the thread offers, as on the other thread composables.
@Suppress("LongParameterList")
fun MessagesList(
    messages: List<Message>,
    currentUserId: String,
    canLoadEarlier: Boolean = false,
    onLoadEarlier: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onEventLinkClick: ((String) -> Unit)? = null,
    onOpenInbox: (() -> Unit)? = null,
    onRetryFailed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    revealMessageId: String? = null,
    onRevealed: () -> Unit = {},
    quotes: Map<String, ReplyQuote> = emptyMap(),
    onQuoteClick: (ReplyQuote.Shown) -> Unit = {},
    onReply: ((Message) -> Unit)? = null,
    names: ParentNames? = null
) {
    // Not rememberSaveable, on purpose: a saved position is what brought a returning reader back to
    // the place they left instead of the newest message. A new state starts at item 0, the newest.
    val listState = remember { LazyListState() }
    val pullToRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val entries = remember(messages) { threadNewestFirst(messages) }
    val loadEarlier = onLoadEarlier.takeIf { canLoadEarlier }
    val revealing = revealMessageId != null
    val highlightedId = rememberRevealedMessage(
        listState = listState,
        entries = entries,
        revealMessageId = revealMessageId,
        onRevealed = onRevealed
    )
    FollowNewestMessage(listState, entries, currentUserId, revealing)
    NewestWhenShownAgain(listState, revealing)
    // The message whose long-press menu is open: it is highlighted and the others dimmed.
    val pressed = remember { mutableStateOf<String?>(null) }
    val replies = remember(quotes, currentUserId, onQuoteClick, onReply, names) {
        BubbleReplies(quotes, currentUserId, onQuoteClick, onReply, pressed, names)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (onRefresh != null) {
                isRefreshing = true
                scope.launch {
                    onRefresh()
                    kotlinx.coroutines.delay(REFRESH_SETTLE_MS)
                    isRefreshing = false
                }
            }
        },
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        // The entries, not the messages: a window holding only cards the thread does not draw is
        // empty to the reader, unless there is more to load above it.
        if (entries.isEmpty() && loadEarlier == null) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.Message,
                title = stringResource(Res.string.chat_messages_empty_title),
                description = stringResource(Res.string.chat_messages_empty_description),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CompositionLocalProvider(LocalBubbleReplies provides replies) {
                ThreadColumn(
                    listState = listState,
                    entries = entries,
                    loadEarlier = loadEarlier,
                    highlightedId = highlightedId,
                    currentUserId = currentUserId,
                    onEventLinkClick = onEventLinkClick,
                    onOpenInbox = onOpenInbox,
                    onRetryFailed = onRetryFailed
                )
            }
            NewestMessagesButton(listState, entries, currentUserId)
        }
    }
}

/**
 * The reversed list itself: [entries] newest first from item 0, at the bottom, and the "load
 * earlier" button as the last item, so it is drawn on top, above the oldest loaded message.
 */
@Composable
// LongParameterList: the thread's callbacks, passed through to each bubble.
@Suppress("LongParameterList")
private fun ThreadColumn(
    listState: LazyListState,
    entries: List<ThreadEntry>,
    loadEarlier: (() -> Unit)?,
    highlightedId: String?,
    currentUserId: String,
    onEventLinkClick: ((String) -> Unit)?,
    onOpenInbox: (() -> Unit)?,
    onRetryFailed: (() -> Unit)?
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(Spacing.L),
        // Bottom: a thread shorter than the screen sits on the composer, not under the header.
        verticalArrangement = Arrangement.spacedBy(Spacing.XXS, Alignment.Bottom)
    ) {
        items(
            count = entries.size,
            key = { index ->
                when (val entry = entries[index]) {
                    is ThreadEntry.DayHeader -> "day_${entry.date}"
                    is ThreadEntry.Bubble -> entry.message.id
                }
            }
        ) { index ->
            when (val entry = entries[index]) {
                is ThreadEntry.DayHeader -> DaySeparator(entry.date)
                is ThreadEntry.Bubble -> RevealHighlight(entry.message.id == highlightedId) {
                    PressFocus(entry.message.id) {
                        MessageItem(
                            message = entry.message,
                            isCurrentUser = entry.message.senderId == currentUserId,
                            startsGroup = entry.startsGroup,
                            endsGroup = entry.endsGroup,
                            onEventLinkClick = onEventLinkClick,
                            onOpenInbox = onOpenInbox,
                            onRetryFailed = onRetryFailed
                        )
                    }
                }
            }
        }
        // The last item, so drawn on top, above the oldest loaded message, and deliberately inside
        // the list rather than pinned above it (CQ-6). The reader has to be at the top to see it,
        // and the older messages it loads are appended after the ones on screen, so nothing moves.
        if (loadEarlier != null) {
            item(key = "load_earlier") {
                TextButton(
                    onClick = loadEarlier,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.chat_load_earlier))
                }
            }
        }
    }
}

/**
 * Brings a new newest message into view when it should come into view.
 *
 * A message inserted at item 0 is placed below a reader who was looking at the previous newest:
 * the list keeps its first visible item by key. So when the newest message changes this brings
 * item 0 into view if the reader was at the newest (the previous newest is on screen), if the
 * message is the reader's own (a send always shows what was sent), or if the previous newest is
 * gone (another conversation is on screen). A reader in history otherwise stays where
 * they are, and nothing moves while a search result is being revealed.
 */
@Composable
private fun FollowNewestMessage(
    listState: LazyListState,
    entries: List<ThreadEntry>,
    currentUserId: String,
    revealing: Boolean
) {
    val newest = (entries.firstOrNull() as? ThreadEntry.Bubble)?.message
    val seen = remember { NewestSeen() }
    LaunchedEffect(newest?.id) {
        val previous = seen.id
        seen.id = newest?.id
        if (newest == null || previous == null || revealing) return@LaunchedEffect
        // The previous newest on screen, wherever the remeasure that places the new one has got to.
        val wasReadingNewest = listState.firstVisibleItemIndex == 0 ||
            listState.layoutInfo.visibleItemsInfo.any { it.key == previous }
        val replaced = entries.none { it is ThreadEntry.Bubble && it.message.id == previous }
        when {
            // A request, not a scroll: it holds for the remeasure that places the new message,
            // which may not have happened yet, and overrides the list's keeping of its first item.
            // Instant, too: a flight through another conversation is its own defect.
            replaced || wasReadingNewest -> listState.requestScrollToItem(0)
            newest.senderId == currentUserId -> listState.animateScrollToItem(0)
        }
    }
}

/** The newest message's id as [FollowNewestMessage] last saw it; not state, nothing draws it. */
private class NewestSeen {
    var id: String? = null
}

/**
 * Starts the thread at its newest message again each time its screen is shown again — on the
 * iPhone, whose shell keeps a hidden tab's screens composed ([LocalScreenVisible]), so the Chat tab
 * entered again, a return from a screen pushed over the thread, or the app brought back. Android
 * provides no visibility: there a screen that is not shown has left composition, and the list's
 * unsaved position starts at the newest anyway.
 */
@Composable
private fun NewestWhenShownAgain(listState: LazyListState, revealing: Boolean) {
    val visibility = LocalScreenVisible.current ?: return
    val revealingNow by rememberUpdatedState(revealing)
    LaunchedEffect(listState, visibility) {
        snapshotFlow { visibility.value }
            .drop(1)
            .filter { it }
            .collect { if (!revealingNow) listState.scrollToItem(0) }
    }
}

/**
 * How many of the co-parent's messages arrived since the reader left the newest one
 * ([NewestButton.unseen]).
 *
 * The reader leaves the newest when item 0 leaves the screen; the newest message's id at that
 * moment is kept, and forgotten again when item 0 is back on screen.
 */
@Composable
private fun rememberUnseenCount(
    listState: LazyListState,
    entries: List<ThreadEntry>,
    currentUserId: String
): Int {
    val newestFirst = remember(entries) {
        entries.mapNotNull { (it as? ThreadEntry.Bubble)?.message }
    }
    var newestWhenLeft by remember { mutableStateOf<String?>(null) }
    val newest by rememberUpdatedState(newestFirst.firstOrNull()?.id)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }.collect { atNewest ->
            newestWhenLeft = if (atNewest) null else newestWhenLeft ?: newest
        }
    }
    return NewestButton.unseen(newestWhenLeft, newestFirst, currentUserId)
}

/**
 * The "↓" button over the thread's bottom end: it brings the newest message into view, and a
 * badge counts the messages that arrived while the reader was in history. It shows while the
 * reader is far from the newest ([NewestButton.shows]), and whenever something arrived unseen.
 * TalkBack and VoiceOver read the button's name and, as its state, the count ("2 new messages").
 */
@Composable
private fun BoxScope.NewestMessagesButton(
    listState: LazyListState,
    entries: List<ThreadEntry>,
    currentUserId: String
) {
    val scope = rememberCoroutineScope()
    val unseen = rememberUnseenCount(listState, entries, currentUserId)
    val far by remember(listState) {
        derivedStateOf { NewestButton.shows(listState.firstVisibleItemIndex) }
    }
    AnimatedVisibility(
        visible = far || unseen > 0,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(Spacing.L),
        enter = fadeIn(tween(Motion.SHORT_MS)),
        exit = fadeOut(tween(Motion.SHORT_MS))
    ) {
        Box {
            val count = if (unseen > 0) {
                pluralStringResource(Res.plurals.chat_new_messages, unseen, unseen)
            } else {
                null
            }
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.semantics { count?.let { stateDescription = it } }
            ) {
                AppIcon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = stringResource(Res.string.chat_scroll_to_newest)
                )
            }
            if (unseen > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = Spacing.XS, y = -Spacing.XS)
                        // The button says the count as its state; the badge only draws it.
                        .clearAndSetSemantics {}
                ) {
                    Text(
                        if (unseen > MAX_BADGE_COUNT) {
                            stringResource(Res.string.chat_unread_count_overflow, MAX_BADGE_COUNT)
                        } else {
                            unseen.toString()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Scrolls to [revealMessageId] once it is among [entries], and returns the id to highlight —
 * cleared again after [Motion.HIGHLIGHT_HOLD_MS].
 *
 * The list draws [entries] as they are, from item 0 (the "load earlier" button is the last item),
 * so an entry's index is the item's. A reversed list's `scrollToItem` puts the item on its bottom
 * edge, under where the newest-messages button floats as soon as the reader is that far up, so the
 * item is lifted to the middle of the screen, and at least clear of the button.
 *
 * Two effects, not one: [onRevealed] clears the request, which would cancel an effect keyed on it
 * halfway through the hold and leave the highlight on for good.
 */
@Composable
private fun rememberRevealedMessage(
    listState: LazyListState,
    entries: List<ThreadEntry>,
    revealMessageId: String?,
    onRevealed: () -> Unit
): String? {
    var highlighted by remember { mutableStateOf<String?>(null) }
    val clearancePx = with(LocalDensity.current) { REVEAL_CLEARANCE.roundToPx() }
    LaunchedEffect(revealMessageId, entries) {
        val target = revealMessageId ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it is ThreadEntry.Bubble && it.message.id == target }
        // Not loaded yet: the window is still growing towards it, and this runs again when it has.
        if (index < 0) return@LaunchedEffect
        listState.scrollToItem(index)
        liftClearOfTheBottom(listState, index, clearancePx)
        highlighted = target
        onRevealed()
    }
    LaunchedEffect(highlighted) {
        if (highlighted != null) {
            delay(Motion.HIGHLIGHT_HOLD_MS.toLong())
            highlighted = null
        }
    }
    return highlighted
}

/**
 * Lifts item [index], just placed on the reversed list's bottom edge by `scrollToItem`, to the
 * middle of the viewport, and at least [minimumPx] above that edge: clear of the newest-messages
 * button. Scrolling backward moves the content up; the list stops it where the newest message
 * reaches the bottom, which leaves the item where it can be.
 */
private suspend fun liftClearOfTheBottom(listState: LazyListState, index: Int, minimumPx: Int) {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val lift = maxOf(minimumPx, (viewport - item.size) / 2)
    listState.scrollBy(-lift.toFloat())
}

/** A bubble's row, tinted while it is the message a search result pointed at. */
@Composable
private fun RevealHighlight(highlighted: Boolean, content: @Composable () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = REVEAL_TINT_ALPHA)
        } else {
            Color.Transparent
        },
        animationSpec = tween(Motion.MEDIUM_MS),
        label = "revealHighlight"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The shape bounds the tint only, never the bubble: a clip would cut one swiped to reply.
            .background(tint, MaterialTheme.shapes.small)
    ) {
        content()
    }
}

/** Small centred pill marking where a new day begins in the thread. */
@Composable
private fun DaySeparator(date: LocalDate) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val label = when (date) {
        today -> stringResource(Res.string.chat_day_today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.chat_day_yesterday)
        else -> readerMediumDate(date)
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(vertical = Spacing.M)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = Spacing.M, vertical = Spacing.XS)
        )
    }
}

/**
 * One message bubble.
 *
 * The timestamp and delivery receipt sit *inside* the bubble, hugging its bottom-end corner —
 * the messenger convention, adopted in the August 2026 second pass. The first pass had moved
 * them below the bubble over a contrast worry, but that worry belonged to the old 10sp tail:
 * this meta renders at labelSmall in `onPrimary` at [META_ALPHA], which stays comfortably
 * readable on the filled primary bubble, and bottom-aligning it beside the last line keeps the
 * bubble no wider than its text plus one small trailer. The one state that stays *outside* is
 * a failed send: "failed to send" must shout in `error` red, and error-on-primary is exactly
 * the pairing that fails, so it renders as its own line under the bubble.
 *
 * @param message The message
 * @param isCurrentUser Whether this parent sent it, which sides and colours the bubble
 * @param startsGroup First of a run by the same sender — takes the larger top gap
 * @param endsGroup Last of a run — carries the tail corner
 * @param onEventLinkClick Handler for tapping a change-request card — an `EVENT_LINK` message or a
 *   change-request announcement — given the event id it names, or null to render such cards inert.
 *   Not for a card about an event itself, which opens the event through [LocalChatEventCard]
 * @param onOpenInbox Opens the change-request inbox with nothing highlighted — the tap target
 *   for a day-swap card, whose entity is a date and not an event id, or null to render it inert
 * @param onRetryFailed Re-sends everything still queued, from a tap on a failed message, or null
 *   to leave the failure notice inert
 */
@Composable
// Already over detekt's LongMethod threshold before this task added the clickable chevron (the
// codebase's own baseline records that); explicit like the other screen composables that carry
// the same suppression rather than relying on a baseline entry keyed to an exact signature.
@Suppress("LongMethod")
fun MessageItem(
    message: Message,
    isCurrentUser: Boolean,
    startsGroup: Boolean = true,
    endsGroup: Boolean = true,
    onEventLinkClick: ((String) -> Unit)? = null,
    onOpenInbox: (() -> Unit)? = null,
    onRetryFailed: (() -> Unit)? = null
) {
    // Where a tap leads is `ChatCardTarget`'s decision: a change-request card opens the inbox on
    // its request, a day-swap card the inbox unhighlighted, and a card about an event the event
    // itself, through the thread's `ChatEventCardHost`. A card whose destination is not wired here
    // stays inert rather than showing a chevron that does nothing.
    val activity = message.activity
    val target = ChatCardTarget.of(message)
    val openEvent = LocalChatEventCard.current
    val openSupport = LocalOpenSupport.current
    val onCardTap: (() -> Unit)? = when (target) {
        is ChatCardTarget.ChangeRequest -> onEventLinkClick?.let { open -> { open(target.eventId) } }
        is ChatCardTarget.Event -> openEvent?.let { open -> { open(target.eventId) } }
        ChatCardTarget.Inbox -> onOpenInbox
        ChatCardTarget.Support -> openSupport
        null -> null
    }

    // The sentence is composed **here**, on the reading device, from the facts the sender put in
    // the payload — see `ActivityCardText`. `message.content` is the fallback a build that knows
    // nothing about `ACTIVITY` shows instead, and the fallback this one shows if the payload
    // could not be parsed.
    val bubbleText = activity
        ?.let { card ->
            // A settle-up card says "you owe" or "you are owed" by who reads it, with the month and
            // the amount in the reader's language.
            if (card.kind.isSettleUp) return@let settleUpCardText(card, isCurrentUser)
            // A payment or an alimony agreement: the month or the day, and the amount.
            if (ActivityCardText.takesMoney(card.kind)) return@let moneyCardText(card)
            // What the sentence is about is `ActivityCardText.subjectOf`'s decision. A day swap's
            // is the day itself, as ISO text on the wire (older builds print it as it is); this
            // build says it the way the reader's language does (D-18). A custody proposal has no
            // title of its own, so it is worded; anything else is the sender's own title.
            val subject = when (val about = ActivityCardText.subjectOf(card)) {
                is CardSubjectText.IsoDate -> ReaderDateText.isoDate(about.iso, DAY_WITH_WEEKDAY)
                is CardSubjectText.Res -> stringResource(about.id)
                is CardSubjectText.Raw -> about.text
            }
            stringResource(ActivityCardText.resourceFor(card.kind), subject)
        }
        ?: message.content

    // A card the app composed is not either parent's words, and in a thread a court may read it
    // must not look like them: it is drawn centred and neutral, with who acted on its first line,
    // instead of in the sender's bubble (new-user review, October 2026 — "Event declined" in the
    // reader's own blue read as something they had written).
    val isCard = activity != null
    val cardActor = when {
        !isCard -> null
        isCurrentUser -> stringResource(Res.string.parent_label_you)
        else -> message.senderName.takeIf { it.isNotBlank() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (startsGroup || isCard) Spacing.S else 0.dp),
        horizontalAlignment = when {
            isCard -> Alignment.CenterHorizontally
            isCurrentUser -> Alignment.End
            else -> Alignment.Start
        }
    ) {
        // Files the message carries (MON-23), above its bubble. The bubble below still carries the
        // text — the file's name for a file sent alone — with the time and the honest tick.
        if (message.attachments.any { it.startsWith(ChatAttachmentCodec.PREFIX) }) {
            LocalChatAttachments.current?.invoke(message, isCurrentUser)
        }
        // What TalkBack says the tap does: an event card opens the event, not a change request.
        val openLabel = stringResource(
            if (target is ChatCardTarget.Event) Res.string.chat_open_event else Res.string.chat_open_change_request
        )
        // A swipe to the right starts a reply, and a long press opens the bubble's one menu: Reply,
        // Copy text, and Report message for a co-parent's own words (play-final audit F-10).
        MessageActions(message = message, isCurrentUser = isCurrentUser) {
            // From the large-font scale the time goes under the text: beside it, the trailer kept
            // its width while the text grew, and a message wrapped two words to a line.
            val metaBelow = stacksAtLargeFont()
            Column(
                modifier = Modifier
                    .widthIn(max = BUBBLE_MAX_WIDTH)
                    .clip(
                        if (isCard) {
                            MaterialTheme.shapes.medium
                        } else {
                            chatBubbleShape(isCurrentUser, startsGroup, endsGroup)
                        }
                    )
                    .background(
                        if (isCard) {
                            // The day pill's surface: a note in the thread, not a message.
                            MaterialTheme.colorScheme.surfaceContainer
                        } else if (isCurrentUser) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            // A step above the day pill's surfaceContainer, so a bubble and a
                            // separator never read as the same kind of surface.
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .then(
                        if (onCardTap != null) {
                            Modifier.clickable(
                                onClickLabel = openLabel,
                                role = Role.Button
                            ) { onCardTap() }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = Spacing.M, vertical = Spacing.S)
            ) {
                // A reply quotes the message it answers over its own words.
                if (!isCard) BubbleQuote(message = message, isCurrentUser = isCurrentUser)
                Row(
                    // Bottom, not centre: the meta trailer sits beside the *last* line of a
                    // multi-line message, hugging the bubble's bottom-end corner.
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        cardActor?.let { actor ->
                            Text(
                                text = actor,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = bubbleText,
                            color = when {
                                isCard -> MaterialTheme.colorScheme.onSurfaceVariant
                                isCurrentUser -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            // Hyphenated, because the bubble is capped in dp while the text scales: at
                            // 1.5x a German compound broke mid-word with no hyphen ("…erklär / ung").
                            style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto)
                        )
                    }
                    if (onCardTap != null) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = Spacing.XS)
                                .size(RECEIPT_ICON_SIZE * CHEVRON_SCALE),
                            tint = if (isCurrentUser && !isCard) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // A card carries its time and no ticks: the neutral surface has no onPrimary to
                    // draw them in, and whether the other phone has the card is the thread's business.
                    if (!metaBelow) {
                        BubbleMeta(
                            message = message,
                            isCurrentUser = isCurrentUser && !isCard,
                            modifier = Modifier.padding(start = Spacing.S)
                        )
                    }
                }
                if (metaBelow) {
                    BubbleMeta(
                        message = message,
                        isCurrentUser = isCurrentUser && !isCard,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        // The one delivery state loud enough to leave the bubble: error red on the filled
        // primary background is the contrast pairing that fails, so a failed send says so
        // on the surface below instead.
        if (isCurrentUser && message.status == MessageSendStatus.ERROR) {
            // Tappable, because until the outbox existed this notice was a dead end: nothing in
            // the app ever retried a message whose write had failed, so it stayed undelivered
            // forever while the thread looked normal on this device.
            Row(
                modifier = Modifier
                    .padding(top = Spacing.XXS, start = Spacing.XS, end = Spacing.XS)
                    .then(
                        if (onRetryFailed == null) {
                            Modifier
                        } else {
                            // The only recovery for an undelivered message: a real button
                            // for TalkBack, and a 48dp target rather than a 16dp caption.
                            Modifier
                                .minimumInteractiveComponentSize()
                                .clickable(role = Role.Button, onClick = onRetryFailed)
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                AppIcon(
                    imageVector = Icons.Default.Error,
                    contentDescription = stringResource(Res.string.chat_status_error),
                    modifier = Modifier.size(RECEIPT_ICON_SIZE),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (onRetryFailed == null) {
                        stringResource(Res.string.chat_failed_to_send)
                    } else {
                        stringResource(Res.string.chat_failed_to_send_retry)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * The in-bubble trailer: sent time, and (on an outgoing message) the delivery receipt.
 *
 * On the primary bubble the trailer renders in `onPrimary` at [META_ALPHA] — present but a
 * step quieter than the message itself — and the READ receipt comes back to full `onPrimary`,
 * so "read" is visibly brighter than "delivered" the way messengers signal it. A failed
 * send shows its time here but keeps its receipt outside the bubble (see [MessageItem]).
 */
@Composable
private fun BubbleMeta(
    message: Message,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val metaColor = if (isCurrentUser) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = META_ALPHA)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        Text(
            text = formatSentAt(message.sentAtMillis),
            style = MaterialTheme.typography.labelSmall,
            color = metaColor
        )
        if (isCurrentUser && message.status != MessageSendStatus.ERROR) {
            DeliveryReceipt(
                message = message,
                baseTint = metaColor,
                readTint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * Sending / sent / delivered / read, as an icon rather than a sentence.
 *
 * The state is real: `ChatViewModel` promotes a SENT message to DELIVERED or READ from the
 * conversation's per-user marks (`ChatReadState.statusFor`), so a delivered-but-unread message is
 * distinguishable from one the co-parent has actually opened.
 *
 * This `when` must handle every [MessageSendStatus] — the compiler rejects it as non-exhaustive
 * otherwise. [BubbleMeta] never calls this for ERROR (a failed send renders its own loud row
 * under the bubble), so that branch is a defensive no-frills glyph rather than a reachable state.
 *
 * Tints are taken as parameters because the receipt now lives *inside* the primary-filled
 * bubble: SENDING/SENT/DELIVERED render in [baseTint] (the quiet meta colour), READ in
 * [readTint] (full brightness) — never Mom-pink/Dad-blue, which are parent identity colours,
 * not status colours.
 */
@Composable
private fun DeliveryReceipt(message: Message, baseTint: Color, readTint: Color) {
    when (message.status) {
        MessageSendStatus.SENDING -> {
            AppIcon(
                imageVector = Icons.Default.Schedule,
                contentDescription = stringResource(Res.string.chat_status_sending),
                modifier = Modifier.size(RECEIPT_ICON_SIZE),
                tint = baseTint
            )
        }
        MessageSendStatus.ERROR -> {
            AppIcon(
                imageVector = Icons.Default.Error,
                contentDescription = stringResource(Res.string.chat_status_error),
                modifier = Modifier.size(RECEIPT_ICON_SIZE),
                tint = baseTint
            )
        }
        MessageSendStatus.SENT -> {
            AppIcon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(Res.string.chat_status_sent),
                modifier = Modifier.size(RECEIPT_ICON_SIZE),
                tint = baseTint
            )
        }
        MessageSendStatus.DELIVERED -> {
            AppIcon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = stringResource(Res.string.chat_status_delivered),
                modifier = Modifier.size(RECEIPT_ICON_SIZE),
                tint = baseTint
            )
        }
        MessageSendStatus.READ -> {
            AppIcon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = stringResource(Res.string.chat_status_read),
                modifier = Modifier.size(RECEIPT_ICON_SIZE),
                tint = readTint
            )
        }
    }
}

/** Widest a bubble may grow before its text wraps. */
private val BUBBLE_MAX_WIDTH = 300.dp

/**
 * Opacity of the in-bubble meta (time and non-READ receipts) on an outgoing bubble.
 *
 * `onPrimary` is white on the indigo primary — a very high-contrast pairing — so even at this
 * alpha the labelSmall trailer clears readable contrast while sitting a step behind the
 * message text; READ receipts come back to full opacity so "read" is visibly brighter.
 */
private const val META_ALPHA = 0.78f

/** Size of the delivery-receipt glyph under an outgoing bubble. */
private val RECEIPT_ICON_SIZE = 13.dp

/** How much larger a tappable card's chevron is than the delivery-receipt glyph. */
private const val CHEVRON_SCALE = 1.4f

/** Opacity of the wash behind a message a search result jumped to. */
private const val REVEAL_TINT_ALPHA = 0.6f

/** Brief pause after a manual refresh so the spinner does not flash out instantly. */
private const val REFRESH_SETTLE_MS = 500L

/**
 * The newest-messages button's drawn size: Material's small floating action button, whose size
 * material3 does not make public. Not set on the button, which keeps Material's own touch target.
 */
private val SMALL_FAB_SIZE = 40.dp

/**
 * How far above the thread's bottom edge a revealed message is kept at the least: the button's
 * own padding, the small button and the same padding again.
 */
private val REVEAL_CLEARANCE = Spacing.L + SMALL_FAB_SIZE + Spacing.L

/** Above this, the newest-messages badge reads "99+". */
private const val MAX_BADGE_COUNT = 99
