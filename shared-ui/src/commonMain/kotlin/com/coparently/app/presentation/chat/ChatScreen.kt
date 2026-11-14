package com.coparently.app.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.coparently.app.domain.chat.ToneCheck
import com.coparently.app.domain.chat.ToneNudge
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.BarAction
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.BarMenuItem
import com.coparently.app.presentation.common.ScreenBackStep
import com.coparently.app.presentation.common.ScreenBar
import com.coparently.app.presentation.common.WhileScreenVisible
import com.coparently.app.presentation.common.rememberMicrophonePermissionRequester
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.shellDrawsBars
import com.coparently.app.presentation.common.valueOrNull
import com.coparently.app.presentation.platform.LocalDictation
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.screenTopAppBarColors
import com.coparently.app.resources.Res
import com.coparently.app.resources.chat_back
import com.coparently.app.resources.chat_failed_to_send
import com.coparently.app.resources.chat_header_synced
import com.coparently.app.resources.chat_more_actions
import com.coparently.app.resources.chat_no_upcoming_events
import com.coparently.app.resources.chat_nudge_words
import com.coparently.app.resources.chat_pick_event_to_reschedule
import com.coparently.app.resources.chat_request_change_title
import com.coparently.app.resources.chat_search
import com.coparently.app.resources.chat_search_close
import com.coparently.app.resources.chat_sending_ellipsis
import com.coparently.app.resources.chat_title_fallback
import com.coparently.app.resources.chat_type_message
import com.coparently.app.resources.voice_listening
import com.coparently.app.utils.ReaderDateText
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
 * @param onOpenInbox Opens the change-request inbox with nothing highlighted: a day-swap card, and
 *   a card about an event that still waits on this parent's answer
 * @param onEditEvent Opens the editor for the given event id. A card about an event opens that
 *   event's preview over the thread ([ChatEventCardHost]), and the preview's Edit calls this
 * @param onOpenExport Opens the export screen for a thread; offered by the banner over a thread
 *   kept after the co-parent deleted their account ([DepartedThreadBanner]), and by nothing else
 * @param onBlock Opens the existing unpair confirmation from the header's "Block and stop sharing"
 *   (play-final audit F-10), or null to leave it out; never offered over a kept thread, whose
 *   pairing has already ended
 * @param viewModel Chat state
 * @param onOpenSupport Opens the family's child support screen from one of its cards, or null
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
    onEditEvent: ((eventId: String) -> Unit)? = null,
    onOpenExport: ((conversationId: String) -> Unit)? = null,
    onBlock: (() -> Unit)? = null,
    onOpenSupport: (() -> Unit)? = null,
    viewModel: ChatViewModel = koinViewModel(),
    searchViewModel: ChatSearchViewModel = koinViewModel(),
    dictationViewModel: DictationViewModel = koinViewModel()
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
    // The platform's half (`Dictation`): the language to dictate in, and whether this phone can
    // dictate at all — asked once, as the ViewModel asks its own recogniser once.
    val dictationPlatform = LocalDictation.current
    val dictationLanguage = dictationPlatform.rememberLanguageTag()
    val canDictate = remember(dictationPlatform) { dictationPlatform.available }
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

    ChatThreadOpenEffect(conversationId, viewModel)
    DisposableEffect(conversationId) {
        searchViewModel.onThreadShown(conversationId)
        onDispose { }
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
    ScreenBackStep(enabled = searching) { searchViewModel.close() }

    // The lexical second look (MON-19): part of "Pause before sending", so only for a parent who
    // asked for it. Computed while rendering and dropped with the frame — never stored, logged or
    // sent, and never a reason the send button is disabled.
    val nudgeWords = stringArrayResource(Res.array.chat_nudge_words).toList()
    val nudge = remember(composerText, pauseBeforeSending, nudgeWords) {
        if (pauseBeforeSending) ToneCheck.check(composerText, nudgeWords) else ToneNudge()
    }

    // A blank (not null) title means this row was mirrored locally before any successful
    // `ensureConversation` set it — `?:` alone never catches that.
    val threadTitle = conversation?.title?.takeIf { it.isNotBlank() }
        ?: departed?.departedName?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.chat_title_fallback)

    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = {
            ChatTopBar(
                title = threadTitle,
                searchQuery = searchQuery.takeIf { searching },
                onSearchQueryChange = searchViewModel::onQueryChange,
                onOpenSearch = { searchViewModel.open(conversationId) },
                onCloseSearch = searchViewModel::close,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onBlock = onBlock.takeIf { departed == null }
            ) {
                ChatThreadHeader(title = threadTitle, messages = messages, currentUserId = currentUserId)
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
                    // Long-press "Report message" on the co-parent's bubbles (F-10), and what to pay
                    // under a settle-up card.
                    ChatReportHost {
                        SettleUpPaymentHost(parentNames, onOpenSupport) {
                            // A card about an event opens that event — its preview, or the inbox while
                            // it waits on this parent — never the change-request inbox on a request
                            // that does not exist.
                            ChatEventCardHost(
                                parentNames = parentNames,
                                onEditEvent = onEditEvent,
                                onOpenInbox = onOpenInbox
                            ) {
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
                    }
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
                if (!nudge.isEmpty) ToneNudgeHint(nudge)
                dictation.failure?.let { DictationFailureNotice(it) }

                // The `+` beside the field, not inside `MessageInput`: a file, a change request and
                // a template each have their own flow, and the composer stays a text field and a
                // send. Its entries are labelled, which the chips above the field used to be for.
                Row(verticalAlignment = Alignment.Bottom) {
                    ChatComposerMenu(
                        conversationId = conversationId,
                        onRequestChange = { showEventPicker = true },
                        onTemplates = { showTemplates = true },
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
                            if (dictation.listening) Res.string.voice_listening else Res.string.chat_type_message
                        ),
                        trailingIcon = if (dictation.available && canDictate) {
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
        // The template body is read in the sheet, in composition, and handed over on a tap.
        MessageTemplatesBottomSheet(
            onTemplateSelected = { content ->
                // A template prepares the message; it does not send it. Sending on tap put three
                // identical placeholders-and-all messages into a real thread during the August
                // 2026 baseline run, because the send was invisible and read as a missed tap.
                composerText = content
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
 * Where a shell draws the bars (the iPhone, MON-24 phase 3, part B) the bar is described to it
 * instead ([ChatShellBar]); Android draws the Material bar it always drew, its overflow included.
 * Public for the app's `ChatBarsTest`.
 *
 * @param title The thread's name: the co-parent's, or the fallback; the iPhone's bar names the
 *   screen with it (its back button's label on a screen pushed over it, and VoiceOver)
 * @param searchQuery The query while search is open, or null while it is not
 * @param onSearchQueryChange Called on every edit of the query
 * @param onOpenSearch Opens search over this thread
 * @param onCloseSearch Closes search and returns to the thread
 * @param onBack Up navigation, or null when the thread is the Chat tab itself
 * @param onOpenSettings Opens settings, or null when the tab's own gear is elsewhere
 * @param onBlock Opens the unpair confirmation from the overflow menu, or null for no menu
 * @param header The thread's identity line
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// One callback per action the bar offers, plus the identity slot; the same shape as ChatScreen's.
@Suppress("LongParameterList")
fun ChatTopBar(
    title: String,
    searchQuery: String?,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onBlock: (() -> Unit)?,
    header: @Composable () -> Unit
) {
    if (shellDrawsBars()) {
        ChatShellBar(
            title = title,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onOpenSearch = onOpenSearch,
            onCloseSearch = onCloseSearch,
            onBack = onBack,
            menu = chatOverflowEntries(onOpenSettings = onOpenSettings, onBlock = onBlock),
            header = header
        )
        return
    }
    if (searchQuery != null) {
        TopAppBar(
            colors = screenTopAppBarColors(),
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
                        contentDescription = stringResource(Res.string.chat_search_close)
                    )
                }
            }
        )
        return
    }
    TopAppBar(
        colors = screenTopAppBarColors(),
        title = header,
        navigationIcon = {
            onBack?.let { back ->
                IconButton(onClick = back) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.chat_back)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.chat_search)
                )
            }
            // Settings sits in the overflow menu here (UX audit M12): the thread's bar carries the
            // co-parent's name and search, and a third icon crowded both.
            if (onOpenSettings != null || onBlock != null) {
                ChatOverflowMenu(onOpenSettings = onOpenSettings, onBlock = onBlock)
            }
        }
    )
}

/**
 * The thread's bar as the iPhone's navigation bar draws it: the header ([ChatThreadHeader]: the
 * initial, the name and whether this parent's messages left the phone) as the title view, search
 * (`magnifyingglass`) and the overflow (`ellipsis.circle`) as a menu holding today's entries in
 * today's order; or, while search is open, the search field as the title view with a close button
 * that ends the search (the system back does the same, through the screen's back step).
 *
 * @param title The thread's name
 * @param searchQuery The query while search is open, or null while it is not
 * @param onSearchQueryChange Called on every edit of the query
 * @param onOpenSearch Opens search over this thread
 * @param onCloseSearch Closes search and returns to the thread
 * @param onBack Up navigation, or null when the thread is the Chat tab itself
 * @param menu The overflow's entries; the overflow is left out when there are none, as on Android
 * @param header The thread's identity line
 */
@Composable
// One callback per action the bar offers, as ChatTopBar's.
@Suppress("LongParameterList")
private fun ChatShellBar(
    title: String,
    searchQuery: String?,
    onSearchQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: (() -> Unit)?,
    menu: List<BarMenuItem>,
    header: @Composable () -> Unit
) {
    if (searchQuery != null) {
        AppTopBar(
            ScreenBar(
                title = stringResource(Res.string.chat_search),
                leading = BarLeading.Close(
                    label = stringResource(Res.string.chat_search_close),
                    onClick = onCloseSearch
                ),
                titleContent = {
                    ChatSearchField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClear = { onSearchQueryChange("") }
                    )
                }
            )
        )
        return
    }
    val backLabel = stringResource(Res.string.chat_back)
    val search = BarAction(
        label = stringResource(Res.string.chat_search),
        icon = Icons.Default.Search,
        sfSymbol = "magnifyingglass",
        onClick = onOpenSearch
    )
    val overflow = BarAction(
        label = stringResource(Res.string.chat_more_actions),
        icon = Icons.Default.MoreVert,
        sfSymbol = "ellipsis.circle",
        onClick = {},
        menu = menu
    ).takeIf { menu.isNotEmpty() }
    AppTopBar(
        ScreenBar(
            title = title,
            leading = onBack?.let { BarLeading.Back(label = backLabel, onClick = it) } ?: BarLeading.None,
            actions = listOfNotNull(search, overflow),
            titleContent = header
        )
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
                text = stringResource(Res.string.chat_request_change_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (events.isEmpty()) {
                Text(
                    text = stringResource(Res.string.chat_no_upcoming_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(Res.string.chat_pick_event_to_reschedule),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn {
                    items(events, key = { "${it.id}_${it.startDateTime}" }) { event ->
                        ListItem(
                            headlineContent = { Text(event.title) },
                            supportingContent = {
                                Text(ReaderDateText.dateWithTime(event.startDateTime, "MMMEd"))
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
 * Public rather than private so the JVM screenshot tests (`ScreenshotMatrix` and its subclasses
 * under `app/src/test`) can render it on its own: `internal` stops at `:shared-ui`'s edge.
 *
 * @param title Conversation title — the co-parent's name once `ensureConversation` has run
 * @param messages Thread contents, newest last
 * @param currentUserId Whose messages count towards the status
 */
@Composable
fun ChatThreadHeader(title: String, messages: List<Message>, currentUserId: String) {
    val mine = messages.filter { it.senderId == currentUserId }
    val status = when {
        mine.any { it.status == MessageSendStatus.ERROR } -> Res.string.chat_failed_to_send
        mine.any { it.status == MessageSendStatus.SENDING } -> Res.string.chat_sending_ellipsis
        else -> Res.string.chat_header_synced
    }
    // Same three-way colour split as the sync row in Settings: errors shout, in-flight is muted,
    // settled is the tertiary accent.
    val statusColor: Color = when (status) {
        Res.string.chat_failed_to_send -> MaterialTheme.colorScheme.error
        Res.string.chat_sending_ellipsis -> MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * Keeps [conversationId] open in [viewModel] while the thread is what the parent sees: the "thread
 * is open" signal that gates the read and delivered marks ([ChatViewModel.onThreadOpened]) must
 * clear the moment the thread is not seen ([ChatViewModel.onThreadClosed]). On Android that is when
 * the screen leaves composition (a configuration change disposes and recomposes it while the same
 * ViewModel survives, and the closing is what ends that window); on the iPhone it is also when the
 * shell hides the screen ([com.coparently.app.presentation.common.LocalScreenVisible]): another tab
 * selected, a screen pushed over it, the app in the background. The thread opens again when it is
 * shown again. Public for the app's `ChatScreenVisibilityTest`.
 *
 * @param conversationId The thread on screen.
 * @param viewModel The chat's ViewModel, which writes the marks.
 */
@Composable
fun ChatThreadOpenEffect(conversationId: String, viewModel: ChatViewModel) {
    WhileScreenVisible(
        key = conversationId,
        onVisible = { viewModel.onThreadOpened(conversationId) },
        onHidden = { viewModel.onThreadClosed() }
    )
}
