package com.coparently.app.presentation.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.coparently.app.R
import com.coparently.app.domain.chat.ToneCheck
import com.coparently.app.domain.chat.ToneNudge
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.rememberMicrophonePermissionRequester
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.valueOrNull
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import com.coparently.app.utils.dateWithTime
import kotlinx.coroutines.delay

/**
 * A single conversation thread.
 *
 * Reworked by the August 2026 design review. The two unlabelled affordances it found — a
 * `swap_horiz` app-bar icon that meant "request change" here but something else on the
 * calendar, and a `+` in the composer that opened message *templates* rather than attachments —
 * are now labelled chips above the composer, so neither depends on the user guessing. The attach
 * button beside the composer arrived with attachments themselves (MON-23), as design item 8 asked.
 *
 * @param conversationId Thread to show
 * @param onBack Up navigation, or null when the thread is the Chat tab itself and there is
 *   nothing to go back to
 * @param draft Pre-filled composer text (e.g. a settle-up message drafted on Expenses). Never
 *   sent automatically — a message to the co-parent is the user's to send.
 * @param onRequestChangeForEvent Starts a change request for the chosen event
 * @param onOpenSettings Opens settings; shown only when this thread *is* the tab, since the
 *   tab's own gear action would otherwise be lost
 * @param onOpenChangeRequest Opens the change-request inbox with the request for the given
 *   event id highlighted; tapping a change-request card in the thread calls this
 * @param onOpenInbox Opens the change-request inbox with nothing highlighted (a day-swap card)
 * @param onOpenExport Opens the export screen for a thread; offered by the banner over a thread
 *   kept after the co-parent deleted their account ([DepartedThreadBanner]), and by nothing else
 * @param viewModel Chat state
 * @param searchViewModel Search inside this thread (MON-15) — local, this conversation only
 * @param dictationViewModel Voice dictation into the composer — on the phone only, and drawn only
 *   where the phone can recognise speech on the device (see `OnDeviceSpeechDictation`)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// One callback per navigation target this screen offers; the body is the thread scaffold, which
// only reads as one screen when it is written as one — including its kept-thread shape, where the
// banner and the closed composer replace the composer's own branches.
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun ChatScreen(
    conversationId: String,
    onBack: (() -> Unit)? = null,
    draft: String = "",
    onRequestChangeForEvent: (String) -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onOpenChangeRequest: ((String) -> Unit)? = null,
    onOpenInbox: (() -> Unit)? = null,
    onOpenExport: ((conversationId: String) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
    searchViewModel: ChatSearchViewModel = hiltViewModel(),
    dictationViewModel: DictationViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val canLoadEarlier by viewModel.canLoadEarlier.collectAsState()
    val conversations = viewModel.conversations.collectAsState().value.valueOrNull.orEmpty()
    val upcomingEvents by viewModel.upcomingEvents.collectAsState()
    val pendingSend by viewModel.pendingSend.collectAsState()
    val pauseBeforeSending by viewModel.pauseBeforeSending.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    val searchQuery by searchViewModel.query.collectAsState()
    val parentNames = rememberParentNames(searchViewModel.parents.collectAsState().value)
    val searching = searchState != ChatSearchState.Closed

    // A search result sets this; the list scrolls to it once the window has grown to hold it.
    var revealTarget by remember { mutableStateOf<String?>(null) }

    val conversation = conversations.find { it.id == conversationId }
    // A thread kept after the co-parent deleted their account: readable and exportable, closed to
    // new messages. Null for every ordinary thread.
    val departed = viewModel.departedThreads.collectAsState().value.firstOrNull { it.conversationId == conversationId }

    var showTemplates by remember { mutableStateOf(false) }
    var showEventPicker by remember { mutableStateOf(false) }

    // Seeded by the incoming draft (Expenses settle-up), by message templates, and — when
    // neither of those supplied anything — by whatever the user last typed into this thread and
    // did not send. That last source is persisted by the ViewModel rather than held here:
    // switching tabs clears the Chat back-stack entry, so composable state alone did not
    // survive it and a half-written message vanished.
    var composerText by rememberSaveable(conversationId, draft) {
        mutableStateOf(draft.ifEmpty { viewModel.draftFor(conversationId) })
    }
    val composerFocus = remember { FocusRequester() }

    // Voice dictation: the words land in the composer as they are heard, appended to the draft.
    // The draft is persisted when a session ends, not on every partial result.
    val dictation by dictationViewModel.state.collectAsState()
    val dictationLanguage = rememberDictationLanguageTag()
    val microphone = rememberMicrophonePermissionRequester(onDenied = dictationViewModel::onPermissionDenied)
    LaunchedEffect(dictationViewModel, conversationId) {
        dictationViewModel.text.collect { dictated ->
            composerText = dictated.text
            if (dictated.final) viewModel.onDraftChanged(conversationId, dictated.text)
        }
    }
    // The microphone never outlives what the parent sees: leaving the thread, or the app going to
    // the background, ends the session.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, conversationId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) dictationViewModel.cancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            dictationViewModel.cancel()
        }
    }

    // Bumped only when something *seeds* the composer, so the refocus below fires on that and on
    // nothing else — keying the effect on the text itself refocused on every keystroke. Plain
    // `remember`, not `rememberSaveable`: a rotation (or process-death restore) is not a new seed,
    // and saving this counter would replay the last seed's focus request and reopen a keyboard the
    // user had deliberately dismissed. The composer *text* still survives rotation — it is saved
    // separately via `composerText` above.
    var composerSeeds by remember { mutableStateOf(0) }

    // DisposableEffect, not LaunchedEffect: the "thread is open" signal that gates the
    // read/delivered marks must clear when this composable leaves — see
    // ChatViewModel.onThreadClosed. A configuration change disposes and recomposes this
    // screen while the same ChatViewModel instance survives, and onDispose is what closes
    // that window.
    DisposableEffect(conversationId) {
        viewModel.onThreadOpened(conversationId)
        searchViewModel.onThreadShown(conversationId)
        onDispose { viewModel.onThreadClosed() }
    }

    LaunchedEffect(searchViewModel) {
        searchViewModel.jumps.collect { jump ->
            viewModel.showAtLeast(jump.windowNeeded)
            revealTarget = jump.messageId
        }
    }

    // A reveal that has not landed within one highlight hold is given up — the message may have
    // been deleted meanwhile — so a stale target cannot keep the list from following new messages.
    LaunchedEffect(revealTarget) {
        if (revealTarget != null) {
            delay(Motion.HIGHLIGHT_HOLD_MS.toLong())
            revealTarget = null
        }
    }

    // The composer gives way to search, and so does its microphone.
    LaunchedEffect(searching) {
        if (searching) dictationViewModel.cancel()
    }

    // Back closes search first, and only then leaves the thread.
    BackHandler(enabled = searching) { searchViewModel.close() }

    // The lexical second look (MON-19): part of "Pause before sending", so only for a parent who
    // asked for it. Computed while rendering and dropped with the frame — never stored, logged or
    // sent, and never a reason the send button is disabled.
    val nudgeWords = stringArrayResource(R.array.chat_nudge_words).toList()
    val nudge = remember(composerText, pauseBeforeSending, nudgeWords) {
        if (pauseBeforeSending) ToneCheck.check(composerText, nudgeWords) else ToneNudge()
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                searchQuery = searchQuery.takeIf { searching },
                onSearchQueryChange = searchViewModel::onQueryChange,
                onOpenSearch = { searchViewModel.open(conversationId) },
                onCloseSearch = searchViewModel::close,
                onBack = onBack,
                onOpenSettings = onOpenSettings
            ) {
                // A blank (not null) title means this row was mirrored locally before any
                // successful `ensureConversation` set it — `?:` alone never catches that.
                val title = conversation?.title?.takeIf { it.isNotBlank() }
                    ?: departed?.departedName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.chat_title_fallback)
                ChatThreadHeader(title = title, messages = messages, currentUserId = currentUserId)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (departed != null && !searching) {
                DepartedThreadBanner(thread = departed, onOpenExport = onOpenExport)
            }
            if (searching) {
                ChatSearchResults(
                    state = searchState,
                    names = parentNames,
                    onSelect = searchViewModel::select,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // The attachment renderer and the tap-to-open handling (MON-23), provided rather
                // than passed so `MessageItem`'s baselined signature stays as it is.
                ChatAttachmentsHost {
                    MessagesList(
                        messages = messages,
                        currentUserId = currentUserId,
                        canLoadEarlier = canLoadEarlier,
                        onLoadEarlier = viewModel::loadEarlier,
                        onRefresh = {
                            viewModel.refreshThread()
                        },
                        onEventLinkClick = onOpenChangeRequest,
                        onOpenInbox = onOpenInbox,
                        onRetryFailed = { viewModel.resendFailedMessages() },
                        modifier = Modifier.weight(1f),
                        revealMessageId = revealTarget,
                        onRevealed = { revealTarget = null }
                    )
                }
            }

            // Outside the search branch: a held message keeps counting down, and keeps its Undo,
            // while the reader looks something up.
            pendingSend?.takeIf { it.conversationId == conversationId }?.let { held ->
                PendingSendNotice(
                    pending = held,
                    onUndo = {
                        viewModel.undoPendingSend(composerText)?.let { restored ->
                            composerText = restored
                            composerSeeds++
                        }
                    }
                )
            }

            // The composer and its chips give way to the results while search is open, and to a
            // one-line note in a thread the departed co-parent can no longer answer.
            if (departed != null && !searching) {
                DepartedThreadComposerNote()
            } else if (!searching) {
                // Labelled, above the composer — where a compose-time action belongs, and where
                // it can say what it does.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.L, vertical = Spacing.XS),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    PillChip(
                        label = stringResource(R.string.chat_request_change),
                        icon = Icons.Default.SwapHoriz,
                        onClick = { showEventPicker = true }
                    )
                    PillChip(
                        label = stringResource(R.string.chat_templates),
                        icon = Icons.Default.Bolt,
                        onClick = { showTemplates = true }
                    )
                }

                if (!nudge.isEmpty) ToneNudgeHint(nudge)
                dictation.failure?.let { DictationFailureNotice(it) }

                // The attach button beside the field, not inside `MessageInput`: it is its own flow
                // (picker, confirmation, outbox) and the composer stays a text field and a send.
                Row(verticalAlignment = Alignment.Bottom) {
                    ChatAttachButton(
                        conversationId = conversationId,
                        modifier = Modifier.padding(start = Spacing.XS, bottom = Spacing.S)
                    )
                    MessageInput(
                        value = composerText,
                        onValueChange = {
                            composerText = it
                            viewModel.onDraftChanged(conversationId, it)
                            // Typing takes over from the voice; the words heard so far stay.
                            dictationViewModel.cancel()
                            dictationViewModel.dismissFailure()
                        },
                        onSendMessage = { content ->
                            dictationViewModel.cancel()
                            viewModel.sendMessage(content)
                            composerText = ""
                        },
                        modifier = Modifier.weight(1f),
                        focusRequester = composerFocus,
                        placeholder = stringResource(
                            if (dictation.listening) R.string.voice_listening else R.string.chat_type_message
                        ),
                        trailingIcon = if (dictation.available) {
                            {
                                DictationMicButton(
                                    listening = dictation.listening,
                                    onClick = {
                                        if (dictation.listening) {
                                            dictationViewModel.stop()
                                        } else {
                                            microphone.request {
                                                dictationViewModel.start(composerText, dictationLanguage)
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    if (showTemplates) {
        // The template body is a string resource, and the selection callback is not composable,
        // so the context is captured here and the body resolved when a template is tapped.
        val context = LocalContext.current
        MessageTemplatesBottomSheet(
            onTemplateSelected = { template ->
                // A template prepares the message; it does not send it. Sending on tap put three
                // identical placeholders-and-all messages into a real thread during the August
                // 2026 baseline run, because the send was invisible and read as a missed tap.
                composerText = context.getString(template.contentRes)
                viewModel.onDraftChanged(conversationId, composerText)
                composerSeeds++
                showTemplates = false
            },
            onDismiss = { showTemplates = false },
            header = {
                // A draft for the parent to edit, like a template: it goes into the composer and
                // is never sent by itself. Draws nothing while the AI assist is off.
                ReplySuggestionRow(
                    conversationId = conversationId,
                    draftHint = composerText,
                    onSuggested = { draftText ->
                        composerText = draftText
                        viewModel.onDraftChanged(conversationId, draftText)
                        composerSeeds++
                        showTemplates = false
                    }
                )
            }
        )
    }

    if (showEventPicker) {
        ChangeRequestEventPicker(
            events = upcomingEvents,
            onEventSelected = { event ->
                showEventPicker = false
                onRequestChangeForEvent(event.id)
            },
            onDismiss = { showEventPicker = false }
        )
    }

    // Fires only after the composer was seeded: put the cursor in the field and raise the
    // keyboard, so the text does not appear somewhere the user is not looking.
    LaunchedEffect(composerSeeds) {
        if (composerSeeds > 0) {
            composerFocus.requestFocus()
        }
    }
}

/**
 * The thread's top bar: who the thread is with and what can be done from it — or, while search is
 * open (MON-15), the search field with a way back to the thread.
 *
 * Search is an action of the thread header, beside its identity, and it searches what this phone
 * holds of this one conversation.
 *
 * @param searchQuery The query while search is open, or null while it is not
 * @param onSearchQueryChange Called on every edit of the query
 * @param onOpenSearch Opens search over this thread
 * @param onCloseSearch Closes search and returns to the thread
 * @param onBack Up navigation, or null when the thread is the Chat tab itself
 * @param onOpenSettings Opens settings, or null when the tab's own gear is elsewhere
 * @param header The thread's identity line
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// One callback per action the bar offers, plus the identity slot; the same shape as ChatScreen's.
@Suppress("LongParameterList")
private fun ChatTopBar(
    searchQuery: String?,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    header: @Composable () -> Unit
) {
    if (searchQuery != null) {
        TopAppBar(
            title = {
                ChatSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClear = { onSearchQueryChange("") }
                )
            },
            navigationIcon = {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.chat_search_close)
                    )
                }
            }
        )
        return
    }
    TopAppBar(
        title = header,
        navigationIcon = {
            onBack?.let { back ->
                IconButton(onClick = back) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.chat_back)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.chat_search)
                )
            }
            onOpenSettings?.let { openSettings ->
                IconButton(onClick = openSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.nav_settings)
                    )
                }
            }
        }
    )
}

/**
 * Bottom sheet listing upcoming shared events; picking one starts a change request
 * (proposing a new time) for that event from within the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeRequestEventPicker(
    events: List<Event>,
    onEventSelected: (Event) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormatter = dateWithTime("MMMEd")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL)
                .padding(bottom = Spacing.XL),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            Text(
                text = stringResource(R.string.chat_request_change_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_no_upcoming_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.chat_pick_event_to_reschedule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn {
                    items(events, key = { "${it.id}_${it.startDateTime}" }) { event ->
                        ListItem(
                            headlineContent = { Text(event.title) },
                            supportingContent = {
                                Text(event.startDateTime.format(dateFormatter))
                            },
                            modifier = Modifier.clickable { onEventSelected(event) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Diameter of the co-parent avatar in the thread header. */
private val HEADER_AVATAR_SIZE = 36.dp

/**
 * The thread's identity line: who you are talking to, and whether what you sent actually left
 * the device.
 *
 * The status is derived from **your own** messages only. A co-parent's message that is still
 * SENDING is their problem to see, not yours, and folding it in here would make the header
 * flicker on every incoming message. The wording deliberately says "up to date" rather than the
 * mock's "synced just now": the app tracks no chat sync timestamp, and printing one it does not
 * have is exactly the kind of affordance this refresh removed elsewhere.
 *
 * Internal rather than private so the JVM screenshot tests (`ScreenshotMatrix` and its
 * subclasses under `app/src/test`) can render it on its own.
 *
 * @param title Conversation title — the co-parent's name once `ensureConversation` has run
 * @param messages Thread contents, newest last
 * @param currentUserId Whose messages count towards the status
 */
@Composable
internal fun ChatThreadHeader(title: String, messages: List<Message>, currentUserId: String) {
    val mine = messages.filter { it.senderId == currentUserId }
    val status = when {
        mine.any { it.status == MessageSendStatus.ERROR } -> R.string.chat_failed_to_send
        mine.any { it.status == MessageSendStatus.SENDING } -> R.string.chat_sending_ellipsis
        else -> R.string.chat_header_synced
    }
    // Same three-way colour split as the sync row in Settings: errors shout, in-flight is muted,
    // settled is the tertiary accent.
    val statusColor: Color = when (status) {
        R.string.chat_failed_to_send -> MaterialTheme.colorScheme.error
        R.string.chat_sending_ellipsis -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.tertiary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Box(
            modifier = Modifier
                .size(HEADER_AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(status),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun ChatThreadHeaderPreview() {
    PreviewWrapper {
        ChatThreadHeader(title = "Pavel", messages = emptyList(), currentUserId = "u1")
    }
}
