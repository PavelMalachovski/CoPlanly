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
import com.coparently.app.presentation.changerequests.AgreementScreen
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.BrandHeader
import com.coparently.app.presentation.common.FamilySwitcherChip
import com.coparently.app.presentation.common.HeadingLines
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
import com.coparently.app.resources.nav_home
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home dashboard (November 2026, board HomeG1): under the wordmark "Your family calendar", then one
 * Requests block — an action card per request waiting on this parent, answered in place or through
 * its Details sheet, and a row per request waiting on the co-parent — then the Money card, where
 * the month is settled up, this week as seven day tiles and the next handover. The co-parent's
 * informational updates live in the inbox "See all" opens; "N new" counts those not opened yet.
 *
 * @param onOpenEvent Opens an event by id (a change request's event, as in the inbox)
 * @param onOpenChangeRequests Opens the requests inbox
 * @param onOpenRequestHistory Opens the requests inbox at its History
 * @param onOpenSettings Opens settings
 * @param onNavigateToPairing Opens the pairing screen
 * @param onOpenExpenses Opens Expenses — the Money card's deep link
 * @param onOpenDay Opens the calendar's Day view on a day — a week tile's tap
 * @param onOpenUnassignedRecords Opens "Records without a family" from the one-time question about
 *   them; null asks nothing
 * @param onOpenAgreement Opens the screen a child support, professional or plan request is answered on
 * @param viewModel Screen state
 * @param putOffAsks Which pop-up asks this parent already dealt with; the Requests row records them too
 * @param paymentsViewModel The family on screen's payments, for the Money card's settle-up
 * @param requestsSeen When this parent last opened the requests from "See all", for "N new"
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
    onOpenAgreement: (AgreementScreen) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    changeRequestViewModel: ChangeRequestViewModel = koinViewModel(),
    putOffAsks: PutOffAsksViewModel = koinViewModel(),
    paymentsViewModel: PaymentsViewModel = koinViewModel(),
    requestsSeen: RequestsSeenViewModel = koinViewModel()
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
    val requests = rememberHomeRequests(changeRequestViewModel, parentNames, onOpenEvent, onOpenAgreement)
    val seenAt by requestsSeen.seenAtMillis.collectAsState()
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
                        ProposalAsk(
                            proposal = it,
                            diff = pendingProposalDiff,
                            citation = pendingProposalCitation,
                            current = requests.ctx?.custody?.model,
                            overrides = requests.ctx?.custody?.dayOverrides.orEmpty(),
                            answerByMillis = requests.proposalDeadlineMillis()
                        )
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
                // "See all" opens the inbox, at History while nothing waits, and counts as a look.
                val onSeeAll = {
                    requestsSeen.markSeen()
                    if (requests.items.isEmpty) onOpenRequestHistory() else openInboxFromRow()
                }
                Dashboard(
                    state = state,
                    parentNames = parentNames,
                    contentPadding = padding,
                    childNames = children.map { it.childName },
                    requests = HomeRequestsSection(
                        state = RequestsBlockState(
                            items = requests.items,
                            forYou = state.requests.waitingOnMe,
                            newCount = unseenCount(state.recentChanges, seenAt, TimeZone.currentSystemDefault())
                        ),
                        ctx = requests.ctx,
                        onSeeAll = onSeeAll
                    ),
                    actions = DashboardActions(
                        onOpenExpenses = onOpenExpenses,
                        onOpenDay = onOpenDay,
                        money = HomeMoney(thisMonth, settleUpSource) { pendingSettleUp = it }
                    )
                )
                HomeRequestsOverlays(requests, changeRequestViewModel)
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
 * The shapes match the dashboard it is about to become — the Requests block, the week's seven
 * tiles and the Money card — so the layout does not jump when the answer arrives. It deliberately
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

        // The Requests block.
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

/** The skeleton's Requests block, about one action card high. */
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
 * The paired dashboard, in the November 2026 order (board HomeG1): the heading, the one Requests
 * block, the Money card, this week's tiles and the next handover — one column on a phone, two
 * panes from 840 dp ([homeSections]).
 *
 * @param state Everything the page draws
 * @param parentNames Resolves a slot to that parent's name
 * @param contentPadding The scaffold's own insets
 * @param childNames The children's names, for the heading's second line
 * @param requests The Requests block
 * @param actions Where each card leads
 */
@Composable
@Suppress("LongParameterList") // the page's state, its names, insets, children, requests and links
private fun Dashboard(
    state: HomeUiState.Dashboard,
    parentNames: ParentNames,
    contentPadding: PaddingValues,
    childNames: List<String>,
    requests: HomeRequestsSection,
    actions: DashboardActions
) {
    val (first, second) = homeSections(twoPane = rememberTwoPane())
    val coParentName = parentNames.labelForUid(state.partner?.id)
    val listModifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    val page = HomePage(state, parentNames, coParentName, childNames, requests, actions)
    val column: @Composable (List<HomeSection>) -> Unit = { list ->
        HomeColumn(listModifier) {
            list.forEach { section -> homeSection(section, page) }
        }
    }
    if (second.isNotEmpty()) {
        TwoPanes(start = { column(first) }, end = { column(second) })
    } else {
        column(first)
    }
}

/** Everything [homeSection] draws from, held together so each section reads what it needs. */
private class HomePage(
    val state: HomeUiState.Dashboard,
    val parentNames: ParentNames,
    val coParentName: String,
    val childNames: List<String>,
    val requests: HomeRequestsSection,
    val actions: DashboardActions
)

/**
 * Where the dashboard's cards lead.
 *
 * @property onOpenExpenses Opens Expenses
 * @property onOpenDay Opens the calendar's Day view on a day
 * @property money The Money card's settle-up
 */
private class DashboardActions(
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
private fun LazyListScope.homeSection(section: HomeSection, page: HomePage) {
    val state = page.state
    when (section) {
        HomeSection.HEADING -> item(key = "heading") { HeadingLines(page.childNames) }
        HomeSection.REQUESTS -> requestsBlock(page.requests.state, page.requests.ctx, page.requests.onSeeAll)
        HomeSection.MONEY -> {
            item { SectionHeader(stringResource(Res.string.family_group_money)) }
            item { HomeMoneyCard(state, page.coParentName, page.actions) }
        }
        HomeSection.THIS_WEEK -> if (state.weekDays.isNotEmpty()) {
            item { SectionHeader(stringResource(Res.string.home_section_this_week)) }
            item { WeekTiles(state.weekDays, page.parentNames, page.actions.onOpenDay) }
        }
        HomeSection.HANDOVER -> state.nextHandover?.let { handover ->
            item { HandoverCard(handover, page.parentNames, state.childrenToday) }
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
