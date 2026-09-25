package com.coparently.app.presentation.expenses

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.coparently.app.domain.model.Expense
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.FamilyMemberFilterStrip
import com.coparently.app.presentation.common.FamilySwitcherChip
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.ListSkeleton
import com.coparently.app.presentation.common.Loadable
import com.coparently.app.presentation.common.ScrollAwareFab
import com.coparently.app.presentation.common.TwoPanes
import com.coparently.app.presentation.common.monthPagingTransition
import com.coparently.app.presentation.common.rememberFabScrollVisibility
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberTwoPane
import com.coparently.app.presentation.common.valueOrNull
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.Spacing
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How much empty space the scrolling page keeps under its last row.
 *
 * The Add button floats over the content, so without this the bottom of the analytics ledger
 * would come to rest underneath it. Sized for a standard 56 dp FAB plus its 16 dp margin, and
 * a little air on top of that.
 */
private val FAB_CLEARANCE = 88.dp

/**
 * Gives the month its own vertical scroll — but only where what it holds can be measured without
 * a bounded height.
 *
 * **The analytics view scrolls as a page, the list does not.** `ExpenseList` is a `LazyColumn`,
 * and the empty-month branch is a `weight(1f)` placeholder; measuring either with an infinite
 * maximum height is a crash, not a layout quirk. So the page scrolls on the analytics branch of a
 * month that has expenses, and nowhere else.
 *
 * Without it, `ExpenseAnalytics` scrolled inside whatever was left under the banners, one summary
 * card per currency and the switcher — on a month holding two currencies, a couple of hundred dp
 * for a pie, a table and a ledger, with the figures under the arc unreachable.
 *
 * It is a named extension rather than a conditional at the call site because the two halves belong
 * together: whoever changes what either branch renders has to find this rule, and it is one `if`
 * standing between a working screen and a crash.
 *
 * @param showAnalytics Whether the analytics view is the one showing
 * @param hasExpenses Whether this month has anything to draw
 * @param state Scroll state for the page
 */
private fun Modifier.scrollsAsPage(
    showAnalytics: Boolean,
    hasExpenses: Boolean,
    state: ScrollState
): Modifier = if (showAnalytics && hasExpenses) verticalScroll(state) else this

/**
 * Expense list screen — a top-level bottom-navigation destination.
 *
 * Leads with one card carrying the month, the who-paid-what split and the settle-up balance,
 * then a segmented control choosing between two views of that month: the **list** (budget chips
 * and this month's expenses) or the **analytics** (a pie by category and a sorted table).
 *
 * Both views share one month control: in the summary card on the list, and as the same line on
 * its own above the analytics, which leave the balance cards to the list so that the chart is on
 * the first screen (docs/AUDIT-2026-10-design.md D-6). Once the list scrolls past the cards, the
 * month folds into [CollapsedMonthSummary], pinned over the list. Analytics is deliberately not a
 * route of its own: it would need a second month control, and the two could drift — a parent
 * looking at August's chart and September's list, with nothing on screen saying so.
 *
 * The August 2026 refresh merged the standalone month navigator into the summary card (the
 * screen used to spend three stacked headers before the first row) and surfaced budgets here
 * instead of leaving them behind an unlabelled top-bar icon.
 *
 * @param onAddExpense Opens the add-expense form
 * @param onEditExpense Opens an expense for editing
 * @param onOpenSettings Opens settings
 * @param onSettleUp Called with a drafted settle-up message, which the user then sends
 * @param viewModel Expense state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// The body is one screen with two views of a month and the banners above them; the
// callbacks are its navigation surface. Both are inherent, and splitting either for the
// metric alone would scatter state this screen owns across functions that share it.
@Suppress("LongParameterList", "LongMethod")
fun ExpenseScreen(
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onSettleUp: (String) -> Unit = {},
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val expensesState by viewModel.expenses.collectAsState()
    val expenses = expensesState.valueOrNull.orEmpty()
    // One value, not three. The month and its figures travel together so the outgoing half of a
    // month slide renders the month it is leaving — see `MonthOfExpenses`.
    val monthOfExpenses by viewModel.monthOfExpenses.collectAsState()
    val roleByUid by viewModel.roleByUid.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val parentNames = rememberParentNames(viewModel.parents.collectAsState().value)
    val familyMembers by viewModel.familyMembers.collectAsState()
    val memberFilter by viewModel.memberFilter.collectAsState()
    val breakdowns by viewModel.breakdowns.collectAsState()
    val selectedBreakdown by viewModel.selectedBreakdown.collectAsState()
    val analyticsPayers by viewModel.analyticsPayers.collectAsState()
    val analyticsPayer by viewModel.analyticsPayer.collectAsState()

    // Which of the two views the month is shown in. `rememberSaveable`, so a rotation does not
    // silently drop a parent back to the list they had switched away from.
    var showAnalytics by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val pendingRatioProposal by viewModel.pendingRatioProposal.collectAsState()
    val myPendingRatioProposal by viewModel.myPendingRatioProposal.collectAsState()
    // Plain `remember`: putting the banner off is for this visit to the screen. A dismissal that
    // survived the process would quietly turn "later" into "never", and the co-parent would go
    // on waiting for an answer that was never coming.
    var ratioProposalDismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.expenses_deleted)
    val undoLabel = stringResource(R.string.expenses_deleted_undo)

    // A refused answer has to say so. The banner is the only route to accepting or declining a
    // split, and the ViewModel used to report the refusal into `saveState`, which belongs to the
    // Add Expense form and nothing here reads — so the tap looked like it had simply done
    // nothing while the co-parent went on waiting.
    val answerFailedMessage = stringResource(R.string.expenses_split_answer_failed)
    LaunchedEffect(Unit) {
        viewModel.ratioAnswerFailed.collect {
            snackbarHostState.showSnackbar(answerFailedMessage)
        }
    }

    // Delete now, offer Undo — the same shape EventListScreen uses. The receipt photo is only
    // purged once the window closes, because a deleted photo cannot be brought back and Undo
    // has to restore the expense intact.
    val deleteWithUndo: (Expense) -> Unit = { expense ->
        viewModel.deleteExpense(expense.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreExpense(expense)
            } else if (expense.receiptUrl != null) {
                viewModel.purgeReceipt(expense)
            }
        }
    }

    val fabVisibility = rememberFabScrollVisibility()
    val twoPane = rememberTwoPane()
    // A month or a view that replaces the content starts at its top, so the button comes back.
    LaunchedEffect(monthOfExpenses.month, showAnalytics) { fabVisibility.show() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.expenses_title)) },
                actions = {
                    // Budgets used to live behind an unlabelled piggy-bank icon here. They are
                    // now visible on the screen itself as a chip strip, so this action is gone
                    // rather than duplicated.
                    // Only with two families or more (M-8). A ledger is the screen where being in
                    // the wrong family costs most: an expense is recorded against the one shown.
                    FamilySwitcherChip()
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            // Leaves while the list moves forward (R-1): the amounts are the rows' trailing
            // column, exactly where the button floats, so something was always under it.
            ScrollAwareFab(fabVisibility) {
                FloatingActionButton(onClick = onAddExpense) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.expenses_add))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(fabVisibility.connection)
        ) {
            // The money screen is where a change to how money divides belongs. A banner, not a
            // modal: every other agreement in this app is an inline banner plus an inbox card,
            // and a dialog that steals focus on open would be a new visual language for the one
            // feature least in need of one.
            pendingRatioProposal?.takeIf { !ratioProposalDismissed }?.let { proposal ->
                SplitRatioProposalBanner(
                    proposal = proposal,
                    onAccept = { viewModel.decideRatioProposal(accept = true) },
                    onDecline = { viewModel.decideRatioProposal(accept = false) },
                    // "Later" leaves it pending, so it is still the co-parent's open question
                    // and still in the inbox. An answer that quietly meant "no" is the silent
                    // outcome this whole family of features exists to remove.
                    onLater = { ratioProposalDismissed = true }
                )
            }
            // The other side of the same conversation (UX-17), and it sits here rather than in
            // Settings so both halves live where the money does. Not dismissible: this is the
            // parent's own open question, and there is nothing to defer — the way out of it is
            // the co-parent answering or this parent taking it back.
            myPendingRatioProposal?.let { proposal ->
                SplitRatioWaitingBanner(
                    proposal = proposal,
                    onWithdraw = viewModel::withdrawRatioProposal
                )
            }
            if (expensesState is Loadable.Loading) {
                ListSkeleton(modifier = Modifier.weight(1f))
            } else if (expenses.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = stringResource(R.string.expenses_empty_title),
                    description = stringResource(R.string.expenses_empty_description),
                    actionLabel = stringResource(R.string.expenses_add),
                    onAction = onAddExpense,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                // Months change with the calendar's animation, from the calendar's constants —
                // see `MonthPaging`. Not a pager, and the comment on `monthSwipe` says why: the
                // rows own a horizontal gesture of their own (swipe to delete), so a pager
                // wrapping the list would fight it. This animates the *result* of the gesture
                // instead, which is the half a parent actually sees.
                AnimatedContent(
                    targetState = monthOfExpenses,
                    transitionSpec = { monthPagingTransition(initialState.month, targetState.month) },
                    label = "expenses-month",
                    modifier = Modifier.weight(1f)
                ) { shownMonth ->
                    val monthExpenses = shownMonth.expenses
                    // Remembered inside the month's own content slot, so paging to another
                    // month opens at the top rather than at this one's offset, while
                    // switching List/Analytics within a month comes back where it was.
                    val pageScroll = rememberScrollState()
                    val listState = rememberLazyListState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollsAsPage(showAnalytics && !twoPane, monthExpenses.isNotEmpty(), pageScroll)
                    ) {
                        val balancesByCurrency = shownMonth.balances
                        val monthNavigation = MonthNavigation(
                            label = rememberMonthLabel(shownMonth.month),
                            expenseCount = monthExpenses.size,
                            onPrevious = viewModel::showPreviousMonth,
                            onNext = viewModel::showNextMonth
                        )

                        if (monthExpenses.isEmpty()) {
                            // The switcher has to stay reachable, or a month with no expenses becomes a
                            // dead end you cannot page out of. Other months may still hold expenses
                            // (e.g. an older receipt), so this is a per-month empty note, not the
                            // global empty state handled above.
                            MonthSwitcherBar(
                                navigation = monthNavigation,
                                modifier = Modifier
                                    .monthSwipe(monthNavigation)
                                    .padding(horizontal = Spacing.L, vertical = Spacing.S)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    // Swipe-to-delete is why the populated list below is never a swipe
                                    // surface — there are no rows here to conflict with the gesture, so
                                    // this empty-month placeholder can safely carry month navigation too.
                                    .monthSwipe(monthNavigation),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.expenses_month_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val monthLabel = remember(shownMonth.month) {
                                shownMonth.month.month
                                    .getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.getDefault())
                                    .replaceFirstChar { it.uppercase() }
                            }
                            // The month's summary cards. One card per currency present this month — the
                            // app does no FX conversion, so a mixed-currency month is shown as separate
                            // honest totals rather than one wrong sum. Only the first card carries the
                            // month switcher; repeating it per currency would switch the same month N
                            // times.
                            val summaryCards: @Composable () -> Unit = {
                                balancesByCurrency.forEachIndexed { index, currencyBalance ->
                                    ExpenseSummaryHeader(
                                        balance = currencyBalance.balance,
                                        currency = currencyBalance.currency,
                                        parentNames = parentNames,
                                        onSettleUp = onSettleUp,
                                        monthLabel = monthLabel,
                                        modifier = Modifier
                                            .then(
                                                if (index == 0) {
                                                    Modifier.monthSwipe(monthNavigation)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .padding(horizontal = Spacing.L, vertical = Spacing.XS),
                                        monthNavigation = monthNavigation.takeIf { index == 0 }
                                    )
                                }
                            }
                            // One month control, two views of it. A separate analytics route would
                            // need its own month control, and the two could drift — a parent looking
                            // at August's chart and September's list with nothing on screen saying so.
                            val viewSwitcher: @Composable () -> Unit = {
                                ViewSwitcher(
                                    showAnalytics = showAnalytics,
                                    onSelect = { showAnalytics = it },
                                    modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)
                                )
                            }

                            // The list and its member filter, shared by the one-column page and the
                            // wide page's second pane.
                            val memberFilterItem: LazyListScope.() -> Unit = {
                                // Renders nothing below two members, so a family with one
                                // child sees the screen they always saw. Nothing selected is
                                // the whole month, which is how a parent gets back out.
                                item {
                                    FamilyMemberFilterStrip(
                                        members = familyMembers,
                                        selected = memberFilter,
                                        onToggle = viewModel::toggleMemberFilter,
                                        label = R.string.expenses_filter_members,
                                        modifier = Modifier.padding(
                                            horizontal = Spacing.L,
                                            vertical = Spacing.XS
                                        )
                                    )
                                }
                            }
                            val expenseList: @Composable (LazyListScope.() -> Unit) -> Unit = { header ->
                                ExpenseList(
                                    expenses = monthExpenses,
                                    roleByUid = roleByUid,
                                    parentNames = parentNames,
                                    onDelete = deleteWithUndo,
                                    onExpenseClick = { onEditExpense(it.id) },
                                    // Only the creator edits or deletes an expense. A row whose creator
                                    // was never recorded (pre-schema-23, or written signed-out) stays
                                    // editable by both — all this device can honestly say about it.
                                    canModify = { expense ->
                                        expense.createdByFirebaseUid == null ||
                                            expense.createdByFirebaseUid == currentUserId
                                    },
                                    // The same clearance the analytics branch takes, for the same
                                    // reason: without it the last expense comes to rest under the
                                    // Add button and the list will not scroll any further.
                                    bottomClearance = FAB_CLEARANCE,
                                    header = header,
                                    state = listState,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (twoPane) {
                                // From 840 dp (release audit R-8) the month is two panes: its
                                // summary and where the money went beside its expenses, both
                                // always on screen, so the List/Analytics switch has nothing to
                                // switch and the summary never needs to fold into a pinned line.
                                TwoPanes(
                                    start = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            summaryCards()
                                            ExpenseAnalytics(
                                                breakdown = selectedBreakdown,
                                                currencies = breakdowns.map { it.currency },
                                                payers = analyticsPayers,
                                                selectedPayer = analyticsPayer,
                                                parentNames = parentNames,
                                                expenses = monthExpenses,
                                                roleByUid = roleByUid,
                                                onSelectCurrency = viewModel::selectAnalyticsCurrency,
                                                onSelectPayer = viewModel::selectAnalyticsPayer,
                                                modifier = Modifier.padding(bottom = Spacing.L)
                                            )
                                        }
                                    },
                                    end = { expenseList(memberFilterItem) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else if (showAnalytics) {
                                // The month line, not the summary cards (docs/AUDIT-2026-10-design.md
                                // D-6). With two currencies the cards reached 60% of the screen and
                                // the chart started below the fold, so the tab looked empty. Who owes
                                // whom is the list's question; this view answers where the money went,
                                // and its table carries the totals. The line is the one the empty
                                // month shows, in the same place.
                                MonthSwitcherBar(
                                    navigation = monthNavigation,
                                    modifier = Modifier
                                        .monthSwipe(monthNavigation)
                                        .padding(horizontal = Spacing.L, vertical = Spacing.S)
                                )
                                viewSwitcher()
                                ExpenseAnalytics(
                                    breakdown = selectedBreakdown,
                                    currencies = breakdowns.map { it.currency },
                                    payers = analyticsPayers,
                                    selectedPayer = analyticsPayer,
                                    parentNames = parentNames,
                                    expenses = monthExpenses,
                                    roleByUid = roleByUid,
                                    onSelectCurrency = viewModel::selectAnalyticsCurrency,
                                    onSelectPayer = viewModel::selectAnalyticsPayer,
                                    // No weight: the page scrolls, so this is as tall as
                                    // it needs to be, and the clearance keeps the last
                                    // ledger row from coming to rest under the Add button.
                                    modifier = Modifier.padding(bottom = FAB_CLEARANCE)
                                )
                            } else {
                                // The summary cards and the switcher are the list's first item, so
                                // they scroll away with it (see `ExpenseList`'s header). Once they
                                // have, the month folds into one pinned line — month, pager and the
                                // totals — so a parent deep in the list still sees which month it is
                                // and can page it without scrolling back (D-6).
                                val summaryScrolledAway by remember(listState) {
                                    derivedStateOf { listState.firstVisibleItemIndex > 0 }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    expenseList {
                                        item {
                                            Column {
                                                summaryCards()
                                                viewSwitcher()
                                            }
                                        }
                                        memberFilterItem()
                                    }
                                    // Qualified: this Box sits in a Column, and the bare name would
                                    // resolve to ColumnScope's overload, which cannot be called here.
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = summaryScrolledAway,
                                        enter = fadeIn(tween(Motion.SHORT_MS)),
                                        exit = fadeOut(tween(Motion.SHORT_MS)),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(horizontal = Spacing.L)
                                    ) {
                                        CollapsedMonthSummary(
                                            navigation = monthNavigation,
                                            totals = balancesByCurrency.map { currencyBalance ->
                                                currencyFormat(currencyBalance.currency)
                                                    .format(currencyBalance.balance.total)
                                            },
                                            modifier = Modifier.monthSwipe(monthNavigation)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * List or Analytics, over the same month.
 *
 * @param showAnalytics Whether the analytics view is the one showing
 * @param onSelect Switches view
 * @param modifier Modifier applied to the control
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSwitcher(
    showAnalytics: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // The row itself is labelled: two unlabelled-in-context buttons announce "List" and
    // "Analytics" with nothing saying what they switch.
    val label = stringResource(R.string.expense_analytics_view_label)
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
    ) {
        SegmentedButton(
            selected = !showAnalytics,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) {
            Text(stringResource(R.string.expense_analytics_tab_list))
        }
        SegmentedButton(
            selected = showAnalytics,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) {
            Text(stringResource(R.string.expense_analytics_tab_analytics))
        }
    }
}

/** The selected month formatted as "August 2026", capitalised for the current locale. */
@Composable
private fun rememberMonthLabel(month: YearMonth): String = remember(month) {
    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

/**
 * This parent proposed a split and is waiting for an answer (UX-17).
 *
 * `SplitRatioTransition.withdraw` was written and unit-tested when the feature landed and then
 * called by nothing, so the proposer had no sign anything was pending and no way to take a
 * mistake back — while the custody schedule, which is the same shape, has had the whole thing
 * all along.
 *
 * Deliberately quieter than [SplitRatioProposalBanner]: a surface tint rather than a filled
 * button, because this banner asks nothing of the reader. The only action is the one that
 * undoes it.
 *
 * @param proposal What this parent put forward.
 * @param onWithdraw Takes it back; the agreed ratio never moved, so nothing else changes.
 */
@Composable
private fun SplitRatioWaitingBanner(
    proposal: SplitRatioProposal,
    onWithdraw: () -> Unit
) {
    InlineBanner(
        title = stringResource(R.string.expenses_split_proposal_waiting_title),
        text = stringResource(
            R.string.expenses_split_proposal_waiting_body,
            proposal.ratio.momPercent,
            proposal.ratio.dadPercent
        ),
        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
        actions = {
            TextButton(onClick = onWithdraw) {
                Text(stringResource(R.string.expenses_split_proposal_withdraw))
            }
        }
    )
}

/**
 * The co-parent has proposed a different split, and this parent has to answer.
 *
 * Confirm, Decline, Later — the three the reporter asked for, and Later is deliberately not an
 * answer: it hides the banner for this visit and leaves the proposal pending, so it is still in
 * the inbox and still the co-parent's open question.
 *
 * The proposed figure is shown, and the currently agreed one beside it, because "70/30" means
 * nothing without knowing what it is replacing.
 *
 * @param proposal What the co-parent put forward.
 * @param onAccept Agrees; the new split prices expenses recorded from then on.
 * @param onDecline Turns it down; nothing changes.
 * @param onLater Hides the banner without answering.
 */
@Composable
private fun SplitRatioProposalBanner(
    proposal: SplitRatioProposal,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onLater: () -> Unit
) {
    InlineBanner(
        title = stringResource(R.string.expenses_split_proposal_title),
        text = stringResource(
            R.string.expenses_split_proposal_body,
            proposal.ratio.momPercent,
            proposal.ratio.dadPercent
        ),
        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
        tone = BannerTone.ATTENTION,
        actions = {
            Button(onClick = onAccept) {
                Text(stringResource(R.string.expenses_split_proposal_confirm))
            }
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.expenses_split_proposal_decline))
            }
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.expenses_split_proposal_later))
            }
        }
    )
}
