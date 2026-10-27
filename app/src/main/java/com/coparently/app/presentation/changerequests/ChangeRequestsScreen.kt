package com.coparently.app.presentation.changerequests

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.ChangeRequestHighlight
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestScope
import com.coparently.app.domain.custody.CustodyPatternDiff
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SwapDirection
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.ChangeRequest
import com.coparently.app.domain.model.ChangeRequestStatus
import com.coparently.app.domain.parentingplan.CitationStatus
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.DaySwapSheet
import com.coparently.app.presentation.calendar.components.isConsecutive
import com.coparently.app.presentation.common.AnswerButtons
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.custody.custodyDiffDescription
import com.coparently.app.presentation.parentingplan.planCitationLine
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyMediumEmphasized
import com.coparently.app.utils.dateWithTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The requests inbox of the family on screen (phase 2, spec §6.2), in three sections: what waits
 * on this parent's answer, what this parent sent and waits on the co-parent, and the history of
 * everything answered or withdrawn. A section with nothing in it is not drawn; an inbox with
 * nothing at all says so.
 *
 * Everything comes from [ChangeRequestViewModel.requests], the source the calendar's banner and
 * Home's Requests row read too, and is drawn from one flat list ([InboxItems]) — the same list a
 * scroll target is looked up in.
 *
 * @param onBack Up navigation
 * @param onOpenEvent Opens the event a change request is about
 * @param linkedEventId Event id carried by a tapped chat card, or null. When set, the newest request
 *   for that event ([ChangeRequestHighlight.forEvent]) is highlighted — its card while pending, its
 *   history row once answered — and scrolled into view; when the event has no request at all, a
 *   snackbar says so once the inbox has loaded.
 * @param openSection A section to scroll to on arrival, or null — Home opens History this way.
 * @param viewModel Change-request state
 */
@Composable
// One screen: its sections, the scroll to a chat card's request on arrival, and the sheet that asks to
// change an agreed swap. Splitting it for the metric would scatter state the screen owns.
@Suppress("LongMethod")
fun ChangeRequestsScreen(
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    linkedEventId: String? = null,
    openSection: InboxSection? = null,
    viewModel: ChangeRequestViewModel = hiltViewModel()
) {
    val state by viewModel.requests.collectAsState()
    val miniMonths by viewModel.miniMonths.collectAsState()
    val swapMonths by viewModel.swapMonths.collectAsState()
    val proposalDiff by viewModel.pendingProposalDiff.collectAsState()
    val proposalCitation by viewModel.pendingProposalCitation.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val parents by viewModel.parents.collectAsState()
    val parentNames = rememberParentNames(parents)
    ShowErrorOnce(errorMessage, onShown = viewModel::clearError)

    val ready = state as? RequestsState.Ready
    val inboxItems = remember(ready?.inbox, ready?.scope) { ready?.let { InboxItems.of(it.inbox, it.scope) }.orEmpty() }
    val highlighted = remember(ready?.changeRequests, linkedEventId) {
        linkedEventId?.let { eventId -> ready?.let { ChangeRequestHighlight.forEvent(it.changeRequests, eventId) } }
    }
    val changingSwap = remember { mutableStateOf<HistoryEntry?>(null) }
    val answers = remember(viewModel, onOpenEvent) {
        InboxAnswers.of(viewModel, onOpenEvent) { changingSwap.value = it }
    }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val missingMessage = stringResource(R.string.change_request_link_missing)

    // Arriving from a chat card, or for a section: scroll once the inbox is Ready — never on its
    // lists merely being empty, which a loading inbox and an empty one both are. Keyed on
    // `ready != null`, which flips once, so answering the highlighted card afterwards does not
    // scroll the list out from under the parent. The target is looked up in the list the column
    // draws, so a pending proposal above it no longer leaves the scroll one card short.
    LaunchedEffect(linkedEventId, openSection, ready != null) {
        if (ready == null) return@LaunchedEffect
        when (val arrival = InboxItems.arrival(inboxItems, linkedEventId, highlighted?.id, openSection)) {
            InboxArrival.Missing -> snackbarHostState.showSnackbar(missingMessage)
            is InboxArrival.ScrollTo -> listState.animateScrollToItem(arrival.index)
            InboxArrival.Stay -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { InboxTopBar(onBack) }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            // Nothing is drawn before the inbox is Ready: an empty state now would be a guess.
            ready == null -> Unit
            ready.inbox.isEmpty -> EmptyState(
                icon = Icons.Default.SwapHoriz,
                title = stringResource(R.string.change_request_empty_title),
                description = stringResource(R.string.change_request_empty_hint),
                modifier = modifier
            )
            else -> InboxList(
                inboxItems = inboxItems,
                cards = InboxCards(
                    scope = ready.scope,
                    miniMonths = miniMonths,
                    swapMonths = swapMonths,
                    parentNames = parentNames,
                    proposalDiff = proposalDiff,
                    proposalCitation = proposalCitation,
                    agreedSplit = ready.settings?.ratio,
                    highlightedRequestId = highlighted?.id,
                    lastChanceInFlight = viewModel.lastChanceInFlight.collectAsState().value
                ),
                answers = answers,
                listState = listState,
                modifier = modifier
            )
        }
    }
    SwapChangeSheet(changingSwap, parentNames, onOffer = viewModel::proposeSwapChange)
}

/**
 * The calendar's swap sheet over an accepted swap's days still ahead, while [changing] names one:
 * each day would go back to the parent the agreement took it from, with a note and a deadline, as
 * any offer does.
 */
@Composable
private fun SwapChangeSheet(
    changing: MutableState<HistoryEntry?>,
    parentNames: ParentNames,
    onOffer: (dates: List<LocalDate>, note: String?, answerBy: AnswerByPick?) -> Unit
) {
    val entry = changing.value ?: return
    val onDismiss = { changing.value = null }
    val today = rememberToday().value
    val ahead = remember(entry, today) { entry.swapDays.keys.filter { it >= today }.sorted() }
    if (ahead.isEmpty()) {
        // The last day passed while the sheet was open: nothing is left to change.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    DaySwapSheet(
        dates = ahead,
        custodyFor = entry.swapDays::get,
        parentNames = parentNames,
        onOffer = { dates, note, answerBy ->
            onOffer(dates, note, answerBy)
            onDismiss()
        },
        onDismiss = onDismiss
    )
}

/** Shows why the last answer did not go through, once, then clears it. */
@Composable
private fun ShowErrorOnce(message: UiText?, onShown: () -> Unit) {
    val context = LocalContext.current
    val appMessages = LocalAppMessages.current
    LaunchedEffect(message) {
        message?.let {
            appMessages?.show(it.asString(context))
            onShown()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.change_request_inbox_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.change_request_back)
                )
            }
        }
    )
}

/** The inbox's sections, one card per item, keyed so an answered card leaves without a jump. */
@Composable
private fun InboxList(
    inboxItems: List<InboxItem>,
    cards: InboxCards,
    answers: InboxAnswers,
    listState: LazyListState,
    modifier: Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        items(inboxItems, key = { it.key }) { item -> InboxCard(item, cards, answers) }
    }
}

/**
 * What the inbox's cards are drawn from, collected once by the screen.
 *
 * @property scope The family on screen: who "you" are and who the co-parent is.
 * @property miniMonths Each waiting event's month, by event id.
 * @property swapMonths Each waiting swap's months, by group key.
 * @property proposalDiff What the co-parent's proposal would change; null for this parent's own.
 * @property proposalCitation Where the co-parent's proposal came from (MON-21).
 * @property agreedSplit The family's agreed expense split — what a proposed split would replace —
 *   or null while its document has not arrived.
 * @property highlightedRequestId The request a chat card arrived for, or null.
 * @property lastChanceInFlight The clocks whose last chance is being given right now, by id.
 */
private data class InboxCards(
    val scope: RequestScope,
    val miniMonths: Map<String, MiniMonthModel>,
    val swapMonths: Map<String, List<MiniMonthModel>>,
    val parentNames: ParentNames,
    val proposalDiff: CustodyPatternDiff?,
    val proposalCitation: CitationStatus,
    val agreedSplit: SplitRatio?,
    val highlightedRequestId: String?,
    val lastChanceInFlight: Set<String> = emptySet()
) {

    /**
     * The clock [item]'s card shows (phase 3a), or null for an item with none. The reader is the
     * proposer or the answering parent by the clock itself; the answering parent's first look is
     * recorded while the request waits on them and they have not opened it, and the proposer's
     * button is offered while the clock says it can be.
     */
    fun clockFor(item: InboxItem, answers: InboxAnswers): CardClock? {
        val timed = when (item) {
            is InboxItem.Proposal -> item.timed
            is InboxItem.Split -> item.timed
            is InboxItem.AwaitingEvent -> item.timed
            is InboxItem.Swap -> item.timed
            is InboxItem.Request -> item.timed
            is InboxItem.Header, is InboxItem.History -> null
        } ?: return null
        val clock = timed.clock
        val role = if (clock.proposerUid == scope.myUid) ClockRole.PROPOSER else ClockRole.RESPONDER
        val otherUid = if (role == ClockRole.PROPOSER) clock.responderUid else clock.proposerUid
        return CardClock(
            timed = timed,
            role = role,
            otherName = parentNames.labelForUid(otherUid),
            onSeen = { answers.markSeen(clock.id) }
                .takeIf { role == ClockRole.RESPONDER && clock.seenAtMillis == null && timed.waitsOnResponder },
            onGiveLastChance = { answers.giveLastChance(clock.id) }
                .takeIf { role == ClockRole.PROPOSER && timed.lastChanceWindowMillis != null },
            lastChanceInFlight = clock.id in lastChanceInFlight
        )
    }
}

/**
 * How the inbox's cards answer, open and withdraw, and act on a request's clock (phase 3a), bound
 * once to the ViewModel.
 */
private data class InboxAnswers(
    val openEvent: (String) -> Unit,
    val answerRequest: (requestId: String, accept: Boolean) -> Unit,
    val withdrawRequest: (requestId: String) -> Unit,
    val answerSwap: (group: DaySwapGroup, accept: Boolean) -> Unit,
    val answerSwapDays: (group: DaySwapGroup, answers: Map<String, Boolean>) -> Unit,
    val withdrawSwap: (group: DaySwapGroup) -> Unit,
    val answerEvent: (eventId: String, accept: Boolean) -> Unit,
    val withdrawEvent: (eventId: String) -> Unit,
    val answerProposal: (accept: Boolean) -> Unit,
    val withdrawProposal: () -> Unit,
    val answerSplit: (accept: Boolean) -> Unit,
    val withdrawSplit: () -> Unit,
    val markSeen: (clockId: String) -> Unit,
    val giveLastChance: (clockId: String) -> Unit,
    val proposeSwapChange: (HistoryEntry) -> Unit
) {
    companion object {
        fun of(
            viewModel: ChangeRequestViewModel,
            onOpenEvent: (String) -> Unit,
            onProposeSwapChange: (HistoryEntry) -> Unit
        ) = InboxAnswers(
            openEvent = onOpenEvent,
            answerRequest = { id, accept -> if (accept) viewModel.accept(id) else viewModel.decline(id) },
            withdrawRequest = viewModel::cancel,
            answerSwap = viewModel::decideSwapGroup,
            answerSwapDays = viewModel::decideSwapDays,
            withdrawSwap = viewModel::withdrawSwapGroup,
            answerEvent = { id, accept -> if (accept) viewModel.acceptEvent(id) else viewModel.declineEvent(id) },
            withdrawEvent = viewModel::withdrawEvent,
            answerProposal = { accept -> if (accept) viewModel.acceptProposal() else viewModel.declineProposal() },
            withdrawProposal = viewModel::withdrawProposal,
            answerSplit = { accept -> if (accept) viewModel.acceptSplit() else viewModel.declineSplit() },
            withdrawSplit = viewModel::withdrawSplit,
            markSeen = viewModel::markSeen,
            giveLastChance = viewModel::giveLastChance,
            proposeSwapChange = onProposeSwapChange
        )
    }
}

/**
 * One item of the inbox's list, drawn as the card its kind is, with its clock in [LocalCardClock]
 * for the card's three clock slots (phase 3a).
 */
@Composable
private fun InboxCard(item: InboxItem, cards: InboxCards, answers: InboxAnswers) {
    CompositionLocalProvider(LocalCardClock provides cards.clockFor(item, answers)) {
        InboxCardContent(item, cards, answers)
    }
}

@Composable
private fun InboxCardContent(item: InboxItem, cards: InboxCards, answers: InboxAnswers) {
    when (item) {
        is InboxItem.Header -> GroupLabel(text = sectionTitle(item.section, cards))
        is InboxItem.Proposal -> CustodyProposalCard(
            proposal = item.proposal,
            diff = cards.proposalDiff,
            citation = cards.proposalCitation,
            parentNames = cards.parentNames,
            mine = item.mine,
            answers = answers
        )
        is InboxItem.Split -> SplitProposalCard(
            proposal = item.proposal,
            agreed = cards.agreedSplit,
            parentNames = cards.parentNames,
            mine = item.mine,
            answers = answers
        )
        is InboxItem.AwaitingEvent -> AwaitingEventCard(
            event = item.event,
            miniMonth = cards.miniMonths[item.event.id],
            parentNames = cards.parentNames,
            actions = if (item.mine) {
                AwaitingEventWithdrawal(onWithdraw = { answers.withdrawEvent(item.event.id) })
            } else {
                AwaitingEventActions(
                    onAccept = { answers.answerEvent(item.event.id, true) },
                    onDecline = { answers.answerEvent(item.event.id, false) }
                )
            }
        )
        is InboxItem.Swap -> DaySwapCard(
            group = item.group,
            currentUserId = cards.scope.myUid,
            parentNames = cards.parentNames,
            months = cards.swapMonths[item.group.key].orEmpty(),
            answers = answers
        )
        is InboxItem.Request -> ChangeRequestCard(
            request = item.request,
            isIncoming = item.request.requestedTo == cards.scope.myUid,
            onOpenEvent = answers.openEvent,
            onAccept = { answers.answerRequest(item.request.id, true) },
            onDecline = { answers.answerRequest(item.request.id, false) },
            onCancel = { answers.withdrawRequest(item.request.id) },
            isHighlighted = item.request.id == cards.highlightedRequestId
        )
        is InboxItem.History -> RequestHistoryGroup(
            entries = item.entries,
            parentNames = cards.parentNames,
            myUid = cards.scope.myUid,
            highlightedKey = cards.highlightedRequestId?.let { "cr_$it" },
            onProposeChange = answers.proposeSwapChange
        )
    }
}

/** A section's heading; the co-parent's section names them. */
@Composable
private fun sectionTitle(section: InboxSection, cards: InboxCards): String = when (section) {
    InboxSection.WAITING_ON_ME -> stringResource(R.string.request_section_waiting_on_you)
    InboxSection.WAITING_ON_THEM -> stringResource(
        R.string.request_section_waiting_on_them,
        cards.parentNames.labelForUid(cards.scope.coParentUid)
    )
    InboxSection.HISTORY -> stringResource(R.string.request_section_history)
}

/**
 * One day swap in the inbox.
 *
 * Three states, and the difference between them is who is being asked. A swap the co-parent
 * offered carries Accept and Decline; one this parent offered carries Withdraw instead (October
 * 2026), never an answer, because `DayOverrideTransition` and `firestore.rules` both refuse a parent
 * deciding their own offer — rendering those buttons would promise an action the server rejects.
 * An offer answered in part shows the answer so far; once every day is answered or withdrawn it
 * leaves for the history.
 *
 * @param group The offer and where each of its days stands.
 * @param currentUserId This device's own uid, for telling "offered to you" from "offered by you".
 * @param parentNames Resolves a uid or a slot to that person's name — never "Mom" or "Dad".
 * @param months The months its days fall in, each day in the colour of whom it goes to (21 October
 *   2026), or empty while they are computed.
 * @param answers Its Accept and Decline, or its Withdraw.
 */
@Composable
private fun DaySwapCard(
    group: DaySwapGroup,
    currentUserId: String,
    parentNames: ParentNames,
    months: List<MiniMonthModel>,
    answers: InboxAnswers
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val first = group.swaps.first()
    val offeredByMe = first.override.requestedBy == currentUserId

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            CardClockBanner()
            TitleWithClockChip(
                if (group.dayCount == 1) {
                    group.firstDate.format(dateFormatter)
                } else if (!isConsecutive(group.swaps.map { it.date })) {
                    group.swaps.joinToString(", ") { it.date.format(dateFormatter) }
                } else {
                    stringResource(
                        R.string.day_swap_range,
                        group.firstDate.format(dateFormatter),
                        group.lastDate.format(dateFormatter)
                    )
                }
            )
            Text(
                text = daySwapSentence(group, offeredByMe, parentNames),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            first.override.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }
            // An offer answered in part (21 October 2026) still waits, and says how each answered day went.
            val waiting = group.swaps.any { it.override.isPending }
            Text(
                text = stringResource(
                    daySwapStatusLabel(if (waiting) DayOverrideStatus.PENDING else first.override.status)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (waiting) AnsweredDays(group)
            SwapMonths(months, parentNames)
            CardClockDetails()
            DaySwapButtons(group, currentUserId, answers)
            if (LocalCardClock.current?.hidesAnswers != true) {
                SwapDayAnswers(group, currentUserId, parentNames) { answers.answerSwapDays(group, it) }
            }
        }
    }
}

/**
 * Who offered the days to whom, with the count in the sentence, so one card answers "how many days"
 * without the reader counting rows — the thing five separate cards made impossible.
 */
@Composable
private fun daySwapSentence(group: DaySwapGroup, offeredByMe: Boolean, parentNames: ParentNames): String {
    val first = group.swaps.first()
    val offerer = first.override.requestedBy
    // Which way the days go, from the offer itself (second audit: "Bob is offering you a day" over
    // a day Bob was asking for).
    val direction = group.direction(
        offeredByReader = offeredByMe,
        readerSlot = parentNames.parents.me?.slot,
        coParentSlot = parentNames.parents.coParent?.slot
    )
    val (mine, theirs) = when (direction) {
        SwapDirection.GIVES ->
            R.plurals.day_swap_inbox_offered_by_you_days to R.plurals.day_swap_inbox_offered_to_you_days
        SwapDirection.ASKS -> R.plurals.day_swap_inbox_asked_by_you_days to R.plurals.day_swap_inbox_asked_of_you_days
        // "Proposes swapping" claims no direction, which is all an unknown slot allows.
        SwapDirection.BOTH, SwapDirection.UNKNOWN ->
            R.plurals.day_swap_inbox_swap_by_you_days to R.plurals.day_swap_inbox_swap_with_you_days
    }
    val resource = if (offeredByMe) mine else theirs
    // The other parent: whom the offerer's days go to, or who offered them.
    val other = if (offeredByMe) {
        parentNames.parents.coParent?.uid?.let { parentNames.labelForUid(it) }
            ?: parentNames.labelFor(first.override.toParent)
    } else {
        parentNames.labelForUid(offerer)
    }
    return pluralStringResource(resource, group.dayCount, group.dayCount, other)
}

/**
 * The swap card's buttons: Accept and Decline while any day waits on this parent — hidden while the
 * card's clock says the next move is the offering parent's (phase 3a) — or Withdraw while any day of
 * their own offer is still unanswered.
 */
@Composable
private fun DaySwapButtons(group: DaySwapGroup, currentUserId: String, answers: InboxAnswers) {
    val hidesAnswers = LocalCardClock.current?.hidesAnswers == true
    when {
        group.awaitsAnswerFrom(currentUserId) -> if (!hidesAnswers) {
            AnswerButtons(
                acceptLabel = stringResource(R.string.day_swap_accept),
                declineLabel = stringResource(R.string.day_swap_decline),
                onAccept = { answers.answerSwap(group, true) },
                onDecline = { answers.answerSwap(group, false) }
            )
        }
        group.withdrawableBy(currentUserId) -> TextButton(onClick = { answers.withdrawSwap(group) }) {
            Text(stringResource(R.string.change_request_withdraw))
        }
    }
}

/**
 * A pending custody-pattern proposal.
 *
 * The co-parent's carries what it would change, where it came from, and Accept and Decline. This
 * parent's own, under "Waiting for <name>", says that it waits and carries Withdraw (October 2026):
 * nobody decides their own proposal (the transition and `firestore.rules` both refuse it), but the
 * one who made it may take it back. The change and the citation are derived for the co-parent's
 * alone.
 *
 * @param mine Whether this parent proposed it.
 * @param answers Its Accept and Decline, or its Withdraw.
 */
@Composable
@Suppress("LongParameterList") // the proposal, what it changes, where it came from, whose, the answers
private fun CustodyProposalCard(
    proposal: CustodyProposal,
    diff: CustodyPatternDiff?,
    citation: CitationStatus,
    parentNames: ParentNames,
    mine: Boolean,
    answers: InboxAnswers
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            CardClockBanner()
            TitleWithClockChip(stringResource(R.string.custody_proposal_inbox_title))
            if (mine) {
                Text(
                    text = stringResource(R.string.custody_proposal_inbox_body_yours),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.event_acceptance_waiting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CardClockDetails()
                TextButton(onClick = answers.withdrawProposal) {
                    Text(stringResource(R.string.change_request_withdraw))
                }
            } else {
                ProposalToAnswer(
                    proposal,
                    diff,
                    citation,
                    parentNames,
                    onAccept = { answers.answerProposal(true) },
                    onDecline = { answers.answerProposal(false) }
                )
            }
        }
    }
}

/** The co-parent's proposal: who proposed it, what it changes, where it came from, two answers. */
@Composable
@Suppress("LongParameterList") // the proposal, what it changes, where it came from, two answers
private fun ProposalToAnswer(
    proposal: CustodyProposal,
    diff: CustodyPatternDiff?,
    citation: CitationStatus,
    parentNames: ParentNames,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Text(
        text = stringResource(
            R.string.custody_proposal_inbox_body,
            parentNames.labelForUid(proposal.proposedBy)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // What it would actually change. Answering a schedule without being told which days
    // move is the complaint this card and the Home dialog both existed to produce.
    custodyDiffDescription(diff, parentNames)?.let { description ->
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    // What it looks like, always: the line above is missing when the pattern itself changes.
    ProposedSchedulePreview(proposal, parentNames)
    // Where it came from (MON-21): the agreed plan answer it cites, or that the answer has
    // changed since. A proposal citing nothing — an older build's — shows nothing here.
    planCitationLine(citation)?.let { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    CardClockDetails()
    // Past the deadline Accept waits for a last chance, but Decline stays: until the proposer's
    // phone withdraws it, the proposal blocks every schedule change this parent could make.
    val declineOnly = LocalCardClock.current?.declineOnly == true
    AnswerButtons(
        acceptLabel = stringResource(R.string.custody_proposal_accept),
        declineLabel = stringResource(R.string.custody_proposal_decline),
        onAccept = onAccept.takeUnless { declineOnly },
        onDecline = onDecline
    )
}

/**
 * A proposed expense split: who proposed it, the split agreed now and the one proposed, each share
 * beside the name of the parent who pays it.
 *
 * The co-parent's carries Accept and Decline — past its deadline Decline alone, like a custody
 * proposal ([CardClock.declineOnly]): until the proposer's phone withdraws it, it stops this parent
 * proposing a split of their own. This parent's own, under "Waiting for <name>", carries Withdraw:
 * nobody decides their own proposal, but the one who made it may take it back.
 *
 * @param agreed The split agreed now, or null while it is not known — then only the proposal shows.
 * @param mine Whether this parent proposed it.
 */
@Composable
private fun SplitProposalCard(
    proposal: SplitRatioProposal,
    agreed: SplitRatio?,
    parentNames: ParentNames,
    mine: Boolean,
    answers: InboxAnswers
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            CardClockBanner()
            TitleWithClockChip(stringResource(R.string.split_request_title))
            Text(
                text = if (mine) {
                    stringResource(R.string.split_request_body_yours)
                } else {
                    stringResource(R.string.split_request_body_theirs, parentNames.labelForUid(proposal.proposedBy))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Money is never cut off (design item 15): both lines wrap rather than end in an ellipsis.
            agreed?.let { now ->
                Text(
                    text = stringResource(R.string.split_request_now, splitSharesText(now, parentNames)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = stringResource(R.string.split_request_proposed, splitSharesText(proposal.ratio, parentNames)),
                style = MaterialTheme.typography.bodyMediumEmphasized
            )
            if (mine) {
                Text(
                    text = stringResource(R.string.event_acceptance_waiting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CardClockDetails()
            SplitProposalButtons(mine, answers)
        }
    }
}

/** The split card's buttons: the proposer's Withdraw, or the answering parent's Accept and Decline. */
@Composable
private fun SplitProposalButtons(mine: Boolean, answers: InboxAnswers) {
    if (mine) {
        TextButton(onClick = answers.withdrawSplit) {
            Text(stringResource(R.string.expenses_split_proposal_withdraw))
        }
        return
    }
    // Past the deadline Accept waits for a last chance, but Decline stays (CardClock.declineOnly).
    val declineOnly = LocalCardClock.current?.declineOnly == true
    AnswerButtons(
        acceptLabel = stringResource(R.string.change_request_accept),
        declineLabel = stringResource(R.string.change_request_decline),
        onAccept = { answers.answerSplit(true) }.takeUnless { declineOnly },
        onDecline = { answers.answerSplit(false) }
    )
}

/**
 * A card's title, with the clock's "No answer" chip at the end of its line when the proposer's
 * request is past its deadline (phase 3a, spec §6.4).
 */
@Composable
private fun TitleWithClockChip(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        CardClockChip()
    }
}

/**
 * Maps the domain status to its display string resource.
 *
 * [ChangeRequestStatus.displayName] stays hardcoded English on the domain enum on purpose — the
 * domain layer has no `Context` and must not grow one — so the screen does the mapping itself.
 */
@StringRes
private fun statusLabel(status: ChangeRequestStatus): Int = when (status) {
    ChangeRequestStatus.PENDING -> R.string.change_request_status_pending
    ChangeRequestStatus.ACCEPTED -> R.string.change_request_status_accepted
    ChangeRequestStatus.DECLINED -> R.string.change_request_status_declined
    ChangeRequestStatus.CANCELLED -> R.string.change_request_status_cancelled
}

/**
 * One change request: event title, current -> proposed time, optional note,
 * status chip and the actions available for it.
 *
 * @param isHighlighted Whether this is the request a chat card linked to; tints the card
 *   container so it stands out once scrolled into view.
 */
@Suppress("LongMethod", "LongParameterList") // card layout + one callback per action
@Composable
fun ChangeRequestCard(
    request: ChangeRequest,
    isIncoming: Boolean,
    onOpenEvent: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    isHighlighted: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenEvent(request.eventId) },
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        val clock = LocalCardClock.current
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            CardClockBanner()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.eventTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // **Queued beats the status (CQ-20.** A row that is not synced is one this
                // device has written and not yet delivered: the co-parent does not have it, and
                // saying "Pending" — or "Accepted" for a reply made offline — claims something
                // the app cannot know. Only this device's own writes can be unsynced; a request
                // mirrored down from the co-parent always arrives marked synced.
                //
                // The clock icon is the one `MessagesList` already uses for a message that has
                // not left, so the two surfaces say "not delivered" the same way.
                // Display-only pills, not disabled chips: a disabled AssistChip renders greyed
                // out, which read as "this request is unavailable" rather than as its status.
                if (clock?.showsNoAnswer == true) {
                    // Past the deadline the clock's "No answer" says where it stands; "Pending"
                    // beside it would contradict it.
                    CardClockChip()
                } else if (request.syncedToFirestore) {
                    PillChip(
                        label = stringResource(statusLabel(request.status)),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PillChip(
                        label = stringResource(R.string.change_request_status_queued),
                        icon = Icons.Default.Schedule,
                        iconDescription = stringResource(R.string.change_request_status_queued_desc),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.change_request_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = request.currentStartDateTime.format(dateWithTime("MMMEd")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = stringResource(R.string.change_request_proposed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = request.proposedStartDateTime.format(dateWithTime("MMMEd")),
                        style = MaterialTheme.typography.bodyMediumEmphasized
                    )
                }
            }

            request.note?.let { note ->
                Text(
                    text = stringResource(R.string.change_request_note_quoted, note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CardClockDetails()

            if (request.status == ChangeRequestStatus.PENDING) {
                // Past the deadline the answering parent's next move waits on a last chance.
                if (isIncoming && clock?.hidesAnswers != true) {
                    AnswerButtons(
                        acceptLabel = stringResource(R.string.change_request_accept),
                        declineLabel = stringResource(R.string.change_request_decline),
                        onAccept = onAccept,
                        onDecline = onDecline
                    )
                } else if (!isIncoming) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.change_request_withdraw))
                    }
                }
            }
        }
    }
}
