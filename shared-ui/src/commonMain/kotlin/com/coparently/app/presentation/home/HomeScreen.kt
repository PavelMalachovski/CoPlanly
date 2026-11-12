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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.common.BrandHeader
import com.coparently.app.presentation.common.FamilySwitcherChip
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.TwoPanes
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberTwoPane
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.components.SkeletonBox
import com.coparently.app.presentation.family.UnassignedRecordsPrompt
import com.coparently.app.presentation.stickers.ParentRolePrompt
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.headlineSmallEmphasized
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.home_expenses_tile
import com.coparently.app.resources.home_pairing_cta
import com.coparently.app.resources.home_pairing_prompt
import com.coparently.app.resources.home_pairing_title
import com.coparently.app.resources.home_section_this_week
import com.coparently.app.resources.home_stat_open_chat
import com.coparently.app.resources.home_stat_unread_count
import com.coparently.app.resources.home_updates_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * From this font scale the two stat tiles stack instead of sharing a row. Side by side each has
 * about 128 dp for its text on a 411 dp phone, which "CZK3,540.00" in the tile's bold title
 * style fills at 130 %.
 */
private const val STACK_TILES_FONT_SCALE = 1.3f

/**
 * Home dashboard. At-a-glance co-parenting state, in variant A of the November 2026 redesign:
 * the co-parent's updates first, what waits on either parent, this week as seven day tiles, the
 * next handover, and this month's expenses beside the unread chat. The today card lives on the
 * Calendar since the same redesign.
 *
 * @param onOpenEvent Opens an event by id
 * @param onOpenChangeRequests Opens the requests inbox
 * @param onOpenRequestHistory Opens the requests inbox at its History
 * @param onOpenSettings Opens settings
 * @param onNavigateToPairing Opens the pairing screen
 * @param onOpenExpenses Opens Expenses — the spend tile's deep link
 * @param onOpenChat Switches to the Chat tab — the unread tile's deep link
 * @param onOpenDay Opens the calendar's Day view on a day — a week tile's tap
 * @param onOpenUnassignedRecords Opens "Records without a family" from the one-time question about
 *   them; null asks nothing
 * @param viewModel Screen state
 * @param putOffAsks Which pop-up asks this parent already dealt with; the Requests row records them too
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
    onOpenChat: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenUnassignedRecords: (() -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
    changeRequestViewModel: ChangeRequestViewModel = koinViewModel(),
    putOffAsks: PutOffAsksViewModel = koinViewModel()
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
                        onOpenChat = onOpenChat,
                        onOpenDay = onOpenDay
                    )
                )
                // Asked once per co-parent set (owner decision, October 2026). Composed before the
                // asks below, so a waiting swap or proposal sits on top of it.
                onOpenUnassignedRecords?.let { UnassignedRecordsPrompt(onChoose = it) }
                // How stickers address this parent: asked once, and only an answer closes it.
                ParentRolePrompt()
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
 * @property onOpenChat Switches to the Chat tab
 * @property onOpenDay Opens the calendar's Day view on a day
 */
private class DashboardActions(
    val onOpenEvent: (String) -> Unit,
    val onOpenChangeRequests: () -> Unit,
    val onOpenRequestHistory: () -> Unit,
    val onOpenExpenses: () -> Unit,
    val onOpenChat: () -> Unit,
    val onOpenDay: (LocalDate) -> Unit
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
        HomeSection.UPDATES -> {
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
        HomeSection.TILES -> item {
            StatTiles(
                spend = state.monthSpend,
                balances = state.monthBalances,
                unreadCount = state.unreadCount,
                onOpenExpenses = actions.onOpenExpenses,
                onOpenChat = actions.onOpenChat
            )
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
 * The two dashboard tiles. Both are deep links — tapping the money opens Expenses, tapping the
 * unread count opens Chat.
 *
 * The unread tile disappears at zero rather than sitting there saying "0", which is what it
 * says most of the time; the spend tile then takes the full width.
 *
 * Money is never ellipsised (docs/AUDIT-2026-10-design.md D-1): each currency's total gets a
 * line of its own, a caption that does not fit wraps, and from [STACK_TILES_FONT_SCALE] the
 * tiles stack so each has the full width. They used to share one line per text, which cut
 * "CZK3,540.00 · €145.00" and "You are owed CZK1,770.00" off in English at the default size.
 *
 * Internal rather than private so the JVM screenshot tests (`ScreenshotMatrix` and its
 * subclasses under `app/src/test`) can render it on its own.
 *
 * @param spend This month's total, per currency
 * @param balances This month's settle-up position, per currency
 * @param unreadCount Unread messages across all conversations
 * @param onOpenExpenses Deep link for the spend tile
 * @param onOpenChat Deep link for the unread tile
 */
@Composable
fun StatTiles(
    spend: MonthSpend,
    balances: List<CurrencyBalance>,
    unreadCount: Int,
    onOpenExpenses: () -> Unit,
    onOpenChat: () -> Unit
) {
    val spendTile: @Composable (Modifier) -> Unit = { modifier ->
        StatTile(
            modifier = modifier,
            icon = Icons.Default.Payments,
            // Named, as the Family tab's money card is: two bare totals said nothing about which
            // period they were (UX audit M3).
            label = stringResource(Res.string.home_expenses_tile),
            value = spend.byCurrency.joinToString("\n") { formatMoney(it.amount, it.currency) },
            caption = balanceCaption(balances),
            onClick = onOpenExpenses
        )
    }
    val chatTile: @Composable (Modifier) -> Unit = { modifier ->
        StatTile(
            modifier = modifier,
            icon = Icons.AutoMirrored.Filled.Chat,
            badge = unreadCount,
            value = pluralStringResource(Res.plurals.home_stat_unread_count, unreadCount, unreadCount),
            caption = stringResource(Res.string.home_stat_open_chat),
            onClick = onOpenChat
        )
    }
    if (LocalDensity.current.fontScale >= STACK_TILES_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            spendTile(Modifier.fillMaxWidth())
            if (unreadCount > 0) chatTile(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            spendTile(Modifier.weight(1f))
            if (unreadCount > 0) chatTile(Modifier.weight(1f))
        }
    }
}

@Composable
@Suppress("LongParameterList") // one tile anatomy, expressed as one parameter list
private fun StatTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    caption: String,
    onClick: () -> Unit,
    badge: Int? = null,
    label: String? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (badge != null) {
                BadgedBox(badge = { Badge { Text(badge.toString()) } }) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSizes.Standard)
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSizes.Standard)
                )
            }
            // Neither line is capped: the value is money or a count and the caption says who
            // owes whom, and a cut-off figure is a wrong figure.
            Column(modifier = Modifier.weight(1f)) {
                label?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMediumEmphasized
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
