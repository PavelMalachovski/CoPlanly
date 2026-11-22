// One file for the dashboard's frame: the screen, its loading and unpaired pages, the section
// dispatch and the stat tiles. The cards themselves live in files of their own.
@file:Suppress("TooManyFunctions")

package com.coparently.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.BrandHeader
import com.coparently.app.presentation.common.FamilySwitcherChip
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.ScreenBar
import com.coparently.app.presentation.common.TwoPanes
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.common.rememberTwoPane
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.common.shellDrawsBars
import com.coparently.app.presentation.components.SkeletonBox
import com.coparently.app.presentation.expenses.PendingSettleUp
import com.coparently.app.presentation.expenses.SettleUpAsk
import com.coparently.app.presentation.expenses.SettleUpDialog
import com.coparently.app.presentation.family.UnassignedRecordsPrompt
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.payments.PaymentsViewModel
import com.coparently.app.presentation.payments.SettleUpSource
import com.coparently.app.presentation.payments.rememberSettleUpSource
import com.coparently.app.presentation.stickers.ParentRolePrompt
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.headlineSmallEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.family_group_money
import com.coparently.app.resources.home_pairing_cta
import com.coparently.app.resources.home_pairing_prompt
import com.coparently.app.resources.home_pairing_title
import com.coparently.app.resources.home_section_this_week
import com.coparently.app.resources.home_updates_title
import com.coparently.app.resources.nav_home
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home dashboard. At-a-glance co-parenting state, in variant A of the November 2026 redesign:
 * the co-parent's updates first, what waits on either parent, this week as seven day tiles, the
 * next handover, and the Money card, where the month is settled up. The today card lives on the
 * Calendar since the same redesign.
 *
 * @param onOpenEvent Opens an event by id
 * @param onOpenChangeRequests Opens the requests inbox
 * @param onOpenRequestHistory Opens the requests inbox at its History
 * @param onOpenSettings Opens settings
 * @param onNavigateToPairing Opens the pairing screen
 * @param onOpenExpenses Opens Expenses — the Money card's deep link
 * @param onOpenDay Opens the calendar's Day view on a day — a week tile's tap
 * @param onOpenUnassignedRecords Opens "Records without a family" from the one-time question about
 *   them; null asks nothing
 * @param viewModel Screen state
 * @param putOffAsks Which pop-up asks this parent already dealt with; the Requests row records them too
 * @param paymentsViewModel The family on screen's payments, for the Money card's settle-up
 */
@Composable
// One callback per navigation target this dashboard links to; the body is one linear column of
// sections, so splitting it would only move the length into a second file.
@Suppress("LongParameterList", "LongMethod")
fun HomeScreen(
    onOpenEvent: (String) -> Unit,
    onOpenChangeRequests: () -> Unit,
    onOpenRequestHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenUnassignedRecords: (() -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
    changeRequestViewModel: ChangeRequestViewModel = koinViewModel(),
    putOffAsks: PutOffAsksViewModel = koinViewModel(),
    paymentsViewModel: PaymentsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val children by viewModel.children.collectAsState()
    val parentNames = rememberParentNames(viewModel.parents.collectAsState().value)
    val pendingProposal by changeRequestViewModel.pendingProposal.collectAsState()
    val pendingProposalDiff by changeRequestViewModel.pendingProposalDiff.collectAsState()
    val pendingProposalCitation by changeRequestViewModel.pendingProposalCitation.collectAsState()
    val pendingSplit by changeRequestViewModel.pendingSplit.collectAsState()
    val agreedSplit by changeRequestViewModel.agreedSplit.collectAsState()
    val swapMonths by changeRequestViewModel.swapMonths.collectAsState()
    // Settle up on the Money card (November 2026), for the month its figures are of.
    val settleUpSource = rememberSettleUpSource(paymentsViewModel)
    val thisMonth = rememberToday().value.yearMonth
    var pendingSettleUp by remember { mutableStateOf<PendingSettleUp?>(null) }

    // The swap and proposal dialogs live here, and until this existed their refusals did not:
    // `ChangeRequestViewModel` wrote every failure into `errorMessage`, and only the inbox
    // screen read it. Answering from Home therefore looked like it had worked — the dialog
    // closed either way — while the co-parent went on waiting.
    val snackbarHostState = remember { SnackbarHostState() }
    val changeRequestError by changeRequestViewModel.errorMessage.collectAsState()
    val textResolver = rememberUiTextResolver()
    LaunchedEffect(changeRequestError) {
        changeRequestError?.let { message ->
            snackbarHostState.showSnackbar(message.asString(textResolver))
            changeRequestViewModel.clearError()
        }
    }
    // "I paid" or "Money arrived" that did not land says so, as on Expenses.
    val paymentError by paymentsViewModel.error.collectAsState()
    LaunchedEffect(paymentError) {
        paymentError?.let { message ->
            snackbarHostState.showSnackbar(message.asString(textResolver))
            paymentsViewModel.errorShown()
        }
    }

    // An event tapped anywhere on the dashboard opens its preview first, like the calendar.
    // The tapped occurrence's start travels with the id (audit L-6): every occurrence of a
    // recurring event shares its series' id, so the id alone previewed the first one on screen.
    // Edit still opens the series by id, as the calendar's preview does.
    val openEvent: (String, LocalDateTime?) -> Unit = { eventId, occurrenceStart ->
        viewModel.openPreview(eventId, occurrenceStart, onMissing = { onOpenEvent(eventId) })
    }
    val previewEvent by viewModel.previewEvent.collectAsState()
    previewEvent?.let { event ->
        com.coparently.app.presentation.event.EventPreviewSheet(
            event = event,
            parentNames = parentNames,
            onEdit = {
                viewModel.closePreview()
                onOpenEvent(event.id)
            },
            // No delete here: Home has no delete-with-undo, and the editor one tap away does.
            onDelete = null,
            onDismiss = viewModel::closePreview
        )
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (shellDrawsBars()) {
                // On the iPhone too the wordmark header leads Home (MON-24 phase 3, part C): the
                // shell hides its navigation bar over Home's root, while "Home" still names the
                // screen to the back button of a screen pushed over it.
                AppTopBar(ScreenBar(title = stringResource(Res.string.nav_home), hidden = true))
            }
            // The wordmark header, as on the Calendar (redesign, November 2026, variant A), with
            // the family switcher under it: it draws nothing with one family (M-8).
            Column(modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S)) {
                BrandHeader(children = children, onSettingsClick = onOpenSettings)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FamilySwitcherChip()
                }
            }
        }
    ) { padding ->
        when (val state = uiState) {
            HomeUiState.Loading -> HomeSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            HomeUiState.AskForCoParent -> PairingInvitation(
                onNavigateToPairing = onNavigateToPairing,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            is HomeUiState.Dashboard -> {
                val homeAsks = HomeAsks(
                    proposal = pendingProposal?.let {
                        ProposalAsk(it, pendingProposalDiff, pendingProposalCitation)
                    },
                    split = pendingSplit?.let { SplitAsk(it, agreedSplit) },
                    swapMonths = swapMonths
                )
                // Opening the inbox from the Requests row deals with the requests as the pop-up's
                // Review does: they count as seen, and only a new one asks again (new-user review).
                val openInboxFromRow = {
                    val queue = HomeAskQueue.of(homeAsks, state.awaitingSwaps, state.awaitingRequestIds)
                    queue.filterIsInstance<HomeAsk.Requests>().firstOrNull()?.let { requestsAsk ->
                        putOffAsks.putOff(requestsAsk.keys, queue.flatMapTo(mutableSetOf()) { it.keys })
                    }
                    onOpenChangeRequests()
                }
                Dashboard(
                    state = state,
                    parentNames = parentNames,
                    contentPadding = padding,
                    actions = DashboardActions(
                        // A feed row names the event, not one occurrence of it.
                        onOpenEvent = { eventId -> openEvent(eventId, null) },
                        onOpenChangeRequests = openInboxFromRow,
                        onOpenRequestHistory = onOpenRequestHistory,
                        onOpenExpenses = onOpenExpenses,
                        onOpenDay = onOpenDay,
                        money = HomeMoney(thisMonth, settleUpSource) { pendingSettleUp = it }
                    )
                )
                // Asked once per co-parent set (owner decision, October 2026). Composed before the
                // asks below, so a waiting swap or proposal sits on top of it.
                onOpenUnassignedRecords?.let { UnassignedRecordsPrompt(onChoose = it) }
                // How stickers address this parent: asked once, and only an answer closes it.
                ParentRolePrompt()
                pendingSettleUp?.let { pending ->
                    // The question Expenses asks, which posts the settle-up card for the record.
                    SettleUpDialog(
                        pending = pending,
                        coParentName = parentNames.coParentLabel(),
                        onDismiss = { pendingSettleUp = null },
                        onSent = { pendingSettleUp = null }
                    )
                }
                AwaitingDialogs(
                    state = state,
                    parentNames = parentNames,
                    asks = homeAsks,
                    putOffAsks = putOffAsks,
                    actions = AwaitingActions(
                        onAcceptSwap = { group ->
                            changeRequestViewModel.decideSwapGroup(group, accept = true)
                        },
                        onDeclineSwap = { group ->
                            changeRequestViewModel.decideSwapGroup(group, accept = false)
                        },
                        onAcceptProposal = changeRequestViewModel::acceptProposal,
                        onDeclineProposal = changeRequestViewModel::declineProposal,
                        onOpenChangeRequests = onOpenChangeRequests,
                        onSwapSeen = { group -> changeRequestViewModel.markSeen(RequestDeadlineId.forSwap(group)) },
                        onProposalSeen = changeRequestViewModel::markProposalSeen,
                        onAcceptSplit = changeRequestViewModel::acceptSplit,
                        onDeclineSplit = changeRequestViewModel::declineSplit,
                        onSplitSeen = changeRequestViewModel::markSplitSeen,
                        onAnswerSwapDays = changeRequestViewModel::decideSwapDays
                    )
                )
            }
        }
    }
}

/**
 * What the page draws before it knows whether there is a co-parent.
 *
 * The shapes match the dashboard it is about to become — the Updates card, the week's seven tiles
 * and the two stat tiles — so the layout does not jump when the answer arrives. It deliberately
 * contains no text and no numbers: the defect it replaces is a screen that **asserted facts** it
 * did not have, and a skeleton that guessed at content would be the same mistake with rounded
 * corners.
 *
 * @param modifier Modifier applied to the page
 */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Spacer(modifier = Modifier.height(Spacing.S))

        // The Updates card.
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(SKELETON_UPDATES_HEIGHT)
                .clip(MaterialTheme.shapes.large)
        )

        // This week's tiles, and then the two stat tiles, side by side as they render.
        listOf(SKELETON_WEEK_TILES to SKELETON_WEEK_TILE_HEIGHT, 2 to SKELETON_STAT_TILE_HEIGHT)
            .forEach { (count, height) ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    repeat(count) {
                        SkeletonBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(height)
                                .clip(MaterialTheme.shapes.medium)
                        )
                    }
                }
            }
    }
}

/** The skeleton's Updates card, about three rows high. */
private val SKELETON_UPDATES_HEIGHT = 180.dp

/** The skeleton's day tiles: seven, as the week draws them. */
private const val SKELETON_WEEK_TILES = 7
private val SKELETON_WEEK_TILE_HEIGHT = 64.dp
private val SKELETON_STAT_TILE_HEIGHT = 72.dp

/**
 * The whole unpaired page: a short explanation and one button.
 *
 * Everything else the dashboard shows depends on there being a second parent — there is no
 * handover without one, no balance to settle, and no changes for them to have made — so the
 * page says the one thing that would fill the rest instead of arranging hollow shells around it.
 *
 * @param onNavigateToPairing Opens the pairing screen
 * @param modifier Modifier applied to the page
 */
@Composable
private fun PairingInvitation(
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.XL),
        verticalArrangement = Arrangement.spacedBy(Spacing.L, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.home_pairing_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.home_pairing_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onNavigateToPairing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.home_pairing_cta))
        }
    }
}

/**
 * The paired dashboard, in variant A's order (redesign, November 2026): Updates, the open requests,
 * this week's tiles, the next handover and the money and chat tiles — one column on a phone, two
 * panes from 840 dp ([homeSections]).
 *
 * @param state Everything the page draws
 * @param parentNames Resolves a slot to that parent's name
 * @param contentPadding The scaffold's own insets
 * @param actions Where each card leads
 */
@Composable
private fun Dashboard(
    state: HomeUiState.Dashboard,
    parentNames: ParentNames,
    contentPadding: PaddingValues,
    actions: DashboardActions
) {
    val requests = state.requests
    val (first, second) = homeSections(
        hasOpenRequests = requests.waitingOnMe > 0 || requests.waitingOnThem > 0,
        twoPane = rememberTwoPane()
    )
    val coParentName = parentNames.labelForUid(state.partner?.id)
    val listModifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    val column: @Composable (List<HomeSection>) -> Unit = { list ->
        HomeColumn(listModifier) {
            list.forEach { section -> homeSection(section, state, parentNames, coParentName, actions) }
        }
    }
    if (second.isNotEmpty()) {
        TwoPanes(start = { column(first) }, end = { column(second) })
    } else {
        column(first)
    }
}

/**
 * Where the dashboard's cards lead.
 *
 * @property onOpenEvent Opens an event by id
 * @property onOpenChangeRequests Opens the requests inbox
 * @property onOpenRequestHistory Opens the requests inbox at its History
 * @property onOpenExpenses Opens Expenses
 * @property onOpenDay Opens the calendar's Day view on a day
 * @property money The Money card's settle-up
 */
private class DashboardActions(
    val onOpenEvent: (String) -> Unit,
    val onOpenChangeRequests: () -> Unit,
    val onOpenRequestHistory: () -> Unit,
    val onOpenExpenses: () -> Unit,
    val onOpenDay: (LocalDate) -> Unit,
    val money: HomeMoney
)

/**
 * What the Money card settles: [month]'s balance, from [source], and where asking the co-parent
 * leads — the settle-up question ([SettleUpDialog]).
 */
private class HomeMoney(
    val month: YearMonth,
    val source: SettleUpSource,
    val onAskToSettle: (PendingSettleUp) -> Unit
)

/** One of [HomeSection]'s cards, as list items; nothing for a section with nothing to show. */
private fun LazyListScope.homeSection(
    section: HomeSection,
    state: HomeUiState.Dashboard,
    parentNames: ParentNames,
    coParentName: String,
    actions: DashboardActions
) {
    when (section) {
        // With no co-parent there is nobody for an update to come from: an empty card would say
        // "Nothing new from Parent", so it is left out until there is something to list.
        HomeSection.UPDATES -> if (state.partner != null || state.recentChanges.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.home_updates_title)) }
            item {
                UpdatesCard(
                    items = state.recentChanges,
                    coParentName = coParentName,
                    onOpenChangeRequests = actions.onOpenChangeRequests,
                    onOpenRequestHistory = actions.onOpenRequestHistory,
                    onOpenEvent = actions.onOpenEvent
                )
            }
        }
        HomeSection.OPEN_REQUESTS -> item {
            OpenRequestsCard(state.requests, coParentName, actions.onOpenChangeRequests)
        }
        HomeSection.THIS_WEEK -> if (state.weekDays.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.home_section_this_week)) }
            item { WeekTiles(state.weekDays, parentNames, actions.onOpenDay) }
        }
        HomeSection.HANDOVER -> state.nextHandover?.let { handover ->
            item { HandoverCard(handover, parentNames, state.childrenToday) }
        }
        HomeSection.MONEY -> {
            item { SectionHeader(stringResource(Res.string.family_group_money)) }
            item { HomeMoneyCard(state, coParentName, actions) }
        }
    }
}

/** One of Home's scrolling columns, with the page's padding and rhythm. */
@Composable
private fun HomeColumn(modifier: Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
        content = content
    )
}

/** A section's heading, which TalkBack can jump between (D-17). */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * The Money card for the family on screen: this month's figures and one settle-up panel per
 * currency with something to settle, read from the same payments the Expenses header reads.
 */
@Composable
private fun HomeMoneyCard(state: HomeUiState.Dashboard, coParentName: String, actions: DashboardActions) {
    val money = actions.money
    val panels = state.monthBalances.mapNotNull { currencyBalance ->
        money.source.stateFor(
            netForMe = currencyBalance.balance.netForCurrentUser,
            splitKnown = currencyBalance.balance.splitKnown,
            currency = currencyBalance.currency,
            month = money.month
        )
    }
    MoneyCard(
        month = money.month,
        spend = state.monthSpend,
        balances = state.monthBalances,
        panels = panels,
        coParentName = coParentName,
        payeeIban = money.source.payeeIban,
        actionsFor = { panel ->
            // The family on screen: Home's figures are its own (`MonthMoney`).
            money.source.actionsFor(panel, familyId = null) {
                val net = state.monthBalances.firstOrNull { it.currency == panel.currency }
                    ?.balance?.netForCurrentUser ?: return@actionsFor
                money.onAskToSettle(
                    PendingSettleUp(SettleUpAsk(net, panel.currency), money.month, money.source.familyOnScreen)
                )
            }
        },
        onOpenExpenses = actions.onOpenExpenses
    )
}
