// One file for the dashboard: its sections are private composables, and their light/dark previews
// (UX-13) must sit beside them to reach them, which is what takes the count past the threshold.
@file:Suppress("TooManyFunctions")

package com.coparently.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.domain.home.WeekEntry
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.presentation.calendar.components.DayAgendaCard
import com.coparently.app.presentation.changerequests.ChangeRequestViewModel
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.FamilySwitcherChip
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.components.SkeletonBox
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.previewParentNames
import com.coparently.app.utils.shortTime
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** A date in the locale's order for [skeleton], then the locale's short time (D-18). */
private fun LocalDateTime.dateAndTime(skeleton: String): String =
    "${format(localizedDate(skeleton))} · ${format(shortTime())}"

/**
 * Whether [event] reads as an all-day one: it starts at midnight and ends at a midnight, or has
 * no end. The model has no all-day flag, and a Google import of a birthday or a school holiday
 * arrives as exactly this shape, which Home used to print as "12:00 AM" (D-18).
 */
private fun looksAllDay(event: Event): Boolean =
    event.startDateTime.toLocalTime() == LocalTime.MIDNIGHT &&
        (event.endDateTime?.toLocalTime() ?: LocalTime.MIDNIGHT) == LocalTime.MIDNIGHT

/** Strength of the parent-hue wash behind the handover hero. */
private const val HERO_TINT_ALPHA = 0.16f

/** Below this a balance is settled — matches the Expenses screen, so the two never disagree. */
private const val SETTLED_EPSILON = 0.01

/**
 * From this font scale the two stat tiles stack instead of sharing a row. Side by side each has
 * about 128 dp for its text on a 411 dp phone, which "CZK3,540.00" in the tile's bold title
 * style fills at 130 %.
 */
private const val STACK_TILES_FONT_SCALE = 1.3f

/**
 * Home dashboard — the first screen. At-a-glance co-parenting state: the next
 * custody handover, this month's spend and unread messages, this week's events,
 * and the recent changes the co-parent made.
 *
 * Layout follows the August 2026 design refresh: the handover is a hero card carrying its own
 * next action, the two stat tiles are deep links rather than dead-end numbers, and the feeds
 * below drop the card-per-row chrome in favour of a timeline rail and one grouped list.
 *
 * @param onOpenEvent Opens an event by id
 * @param onOpenChangeRequests Opens the change-request inbox
 * @param onOpenContacts Opens the contacts list
 * @param onOpenChildInfo Opens the child records
 * @param onOpenPets Opens the pet records
 * @param onOpenSettings Opens settings
 * @param onNavigateToPairing Opens the pairing screen
 * @param onOpenExpenses Switches to the Expenses tab — the spend tile's deep link
 * @param onOpenChat Switches to the Chat tab — the unread tile's deep link
 * @param onAddEvent Opens the new-event form — the empty week's way forward
 * @param viewModel Screen state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// One callback per navigation target this dashboard links to; the body is one linear column of
// sections, so splitting it would only move the length into a second file.
@Suppress("LongParameterList", "LongMethod")
fun HomeScreen(
    onOpenEvent: (String) -> Unit,
    onOpenChangeRequests: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenChildInfo: () -> Unit,
    onOpenPets: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenChat: () -> Unit,
    onAddEvent: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    changeRequestViewModel: ChangeRequestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val caresFor by viewModel.caresFor.collectAsState()
    val today by rememberToday()
    val parentNames = rememberParentNames(viewModel.parents.collectAsState().value)
    val pendingProposal by changeRequestViewModel.pendingProposal.collectAsState()
    val pendingProposalDiff by changeRequestViewModel.pendingProposalDiff.collectAsState()
    val pendingProposalCitation by changeRequestViewModel.pendingProposalCitation.collectAsState()

    // The swap and proposal dialogs live here, and until this existed their refusals did not:
    // `ChangeRequestViewModel` wrote every failure into `errorMessage`, and only the inbox
    // screen read it. Answering from Home therefore looked like it had worked — the dialog
    // closed either way — while the co-parent went on waiting.
    val snackbarHostState = remember { SnackbarHostState() }
    val changeRequestError by changeRequestViewModel.errorMessage.collectAsState()
    val errorContext = LocalContext.current
    LaunchedEffect(changeRequestError) {
        changeRequestError?.let { message ->
            snackbarHostState.showSnackbar(message.asString(errorContext))
            changeRequestViewModel.clearError()
        }
    }

    // An event tapped anywhere on the dashboard opens its preview first, like the calendar.
    val openEvent: (String) -> Unit = { eventId ->
        viewModel.openPreview(eventId, onMissing = { onOpenEvent(eventId) })
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = today.format(localizedDate("EEEEMMMMd")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Only with two families or more (M-8); nothing at all with one.
                    FamilySwitcherChip()
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            )
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
                Dashboard(
                    state = state,
                    parentNames = parentNames,
                    hasPendingProposal = pendingProposal != null,
                    contentPadding = padding,
                    onOpenEvent = openEvent,
                    onOpenChangeRequests = onOpenChangeRequests,
                    onOpenContacts = onOpenContacts,
                    onOpenChildInfo = onOpenChildInfo,
                    onOpenPets = onOpenPets,
                    caresFor = caresFor,
                    onOpenExpenses = onOpenExpenses,
                    onOpenChat = onOpenChat,
                    onAddEvent = onAddEvent
                )
                AwaitingDialogs(
                    state = state,
                    parentNames = parentNames,
                    proposal = pendingProposal?.let {
                        ProposalAsk(it, pendingProposalDiff, pendingProposalCitation)
                    },
                    actions = AwaitingActions(
                        onAcceptSwap = { group ->
                            changeRequestViewModel.decideSwapGroup(group, accept = true)
                        },
                        onDeclineSwap = { group ->
                            changeRequestViewModel.decideSwapGroup(group, accept = false)
                        },
                        onAcceptProposal = changeRequestViewModel::acceptProposal,
                        onDeclineProposal = changeRequestViewModel::declineProposal,
                        onOpenChangeRequests = onOpenChangeRequests
                    )
                )
            }
        }
    }
}

/**
 * What the page draws before it knows whether there is a co-parent.
 *
 * The shapes match the dashboard it is about to become — a handover hero, two stat tiles, a run
 * of week rows — so the layout does not jump when the answer arrives. It deliberately contains
 * no text and no numbers: the defect it replaces is a screen that **asserted facts** it did not
 * have, and a skeleton that guessed at content would be the same mistake with rounded corners.
 *
 * @param modifier Modifier applied to the page
 */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // The handover hero.
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        // The two stat tiles, side by side as they render.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }

        // The week.
        repeat(4) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

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
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_pairing_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.home_pairing_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(onClick = onNavigateToPairing, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_pairing_cta))
        }
    }
}

/**
 * The paired dashboard, in spec §3's order.
 *
 * @param state Everything the page draws
 * @param parentNames Resolves a slot to that parent's name
 * @param contentPadding The scaffold's own insets
 * @param onOpenEvent Opens an event by id
 * @param onOpenChangeRequests Opens the change-request inbox
 * @param onOpenContacts Opens the contacts list
 * @param onOpenChildInfo Opens the child records
 * @param onOpenPets Opens the pet records
 * @param onOpenExpenses Switches to the Expenses tab
 * @param onOpenChat Switches to the Chat tab
 * @param onAddEvent Opens the new-event form
 */
@Composable
// One callback per navigation target; the body is one linear column of sections, so splitting
// it would only move the length into a second file.
@Suppress("LongParameterList", "LongMethod")
private fun Dashboard(
    state: HomeUiState.Dashboard,
    parentNames: ParentNames,
    hasPendingProposal: Boolean,
    contentPadding: PaddingValues,
    onOpenEvent: (String) -> Unit,
    onOpenChangeRequests: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenChildInfo: () -> Unit,
    onOpenPets: () -> Unit,
    caresFor: Set<FamilyKind>,
    onOpenExpenses: () -> Unit,
    onOpenChat: () -> Unit,
    onAddEvent: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // First, by owner decision (Aug 2026 walkthrough): the emergency surface — who to
            // call and the child's own record — belongs above the schedule, because the moment
            // it is needed is the moment nobody scrolls. Child and pet records used to be
            // reachable only through Settings; the rows reuse Settings' own titles so the two
            // entrances can never drift apart.
            SectionGroup {
                SectionRow(
                    title = stringResource(R.string.home_contacts),
                    icon = Icons.Default.Contacts,
                    supporting = stringResource(R.string.home_contacts_supporting),
                    onClick = onOpenContacts,
                    trailing = { HomeChevron() }
                )
                // Only what this family actually co-parents. An account that never answered the
                // question reads as both, so nothing an upgrade was already showing disappears.
                if (FamilyKind.CHILDREN in caresFor) {
                    Divider()
                    SectionRow(
                        title = stringResource(R.string.settings_child_info_title),
                        icon = Icons.Default.ChildCare,
                        supporting = stringResource(R.string.settings_child_info_description),
                        onClick = onOpenChildInfo,
                        trailing = { HomeChevron() }
                    )
                }
                if (FamilyKind.PETS in caresFor) {
                    Divider()
                    SectionRow(
                        title = stringResource(R.string.settings_pets_title),
                        icon = Icons.Default.Pets,
                        supporting = stringResource(R.string.settings_pets_description),
                        onClick = onOpenPets,
                        trailing = { HomeChevron() }
                    )
                }
            }
        }

        // Items 4/13 (Aug 2026 walkthrough): whatever waits on this parent's answer — a day
        // swap, an event change, a pending event — must be visible on the main page, with one
        // tap into the inbox that answers it. Day swaps were previously reachable from nowhere.
        val awaitingCount = state.awaitingSwaps.size + state.awaitingRequestCount +
            if (hasPendingProposal) 1 else 0
        if (awaitingCount > 0) {
            item {
                SectionGroup {
                    SectionRow(
                        title = stringResource(R.string.home_awaiting_title),
                        icon = Icons.Default.SwapHoriz,
                        onClick = onOpenChangeRequests,
                        trailing = {
                            Text(
                                text = awaitingCount.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }

        state.nextHandover?.let { handover ->
            item {
                HandoverHero(
                    info = handover,
                    parentNames = parentNames,
                    onConfirm = onOpenChangeRequests,
                    childrenToday = state.childrenToday
                )
            }
        }

        // Today's agenda — the day card that used to sit under the calendar's month grid,
        // moved here so the grid fills its screen. Same composable, so the two surfaces can
        // never drift into different ideas of what a day looks like: date, whose custody day
        // it is, and the whole day's events (a 9:00 appointment is still part of today at
        // 9:05, which is why this is not just the week's first rows repeated).
        item {
            DayAgendaCard(
                date = state.today.date,
                events = state.today.events,
                custody = state.today.dayParent,
                parentNames = parentNames,
                onEventClick = onOpenEvent,
                contactWindows = state.today.contactWindows
            )
        }

        // The week follows the emergency group and the day cards (spec §3 had it lead; the
        // Aug 2026 walkthrough moved the emergency surface above it).
        item { SectionHeader(stringResource(R.string.home_section_this_week)) }
        if (state.week.isEmpty()) {
            // The shared empty state (UX-9), not the `Card { Text }` the refresh outlawed — and
            // with the one thing an empty week asks for.
            item {
                EmptyState(
                    icon = Icons.Default.EventAvailable,
                    title = stringResource(R.string.home_week_empty),
                    actionLabel = stringResource(R.string.home_week_empty_action),
                    onAction = onAddEvent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            itemsIndexed(
                items = state.week,
                // Recurring occurrences share the master event's id, so `HomeWeek` builds a key
                // that carries the occurrence's own start time; the id alone collides.
                key = { _, entry -> entry.key }
            ) { index, entry ->
                TimelineRow(
                    entry = entry,
                    parentNames = parentNames,
                    isLast = index == state.week.lastIndex,
                    onClick = { onOpenEvent(entry.event.id) }
                )
            }
        }

        item {
            // Deliberately `partner?.name` and not `parentNames`, which is what the hero and
            // the timeline above use. The two answer different questions. This header names
            // a *person* - the account this one is paired with - and that identity is known
            // as soon as pairing resolves. The hero names whoever holds a *slot*, and on a
            // pair whose two parents still share slot 1 nobody holds the other one, so it
            // says "Parent" until the backfill separates them.
            //
            // So a legacy pair reads "Olya changed" here and "Today with Parent" above, and
            // that is correct rather than an inconsistency to iron out: degrading this to
            // "Parent" would throw away a fact we hold, and resolving the hero from the
            // partner's name would assert a slot nobody has stored - the guess this whole
            // branch exists to remove.
            SectionHeader(
                state.partner?.name?.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.home_section_partner_changed, it) }
                    ?: stringResource(R.string.home_section_recent_changes)
            )
        }

        if (state.recentChanges.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.home_recent_empty_paired),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                ActivityGroup(
                    items = state.recentChanges,
                    onOpenChangeRequests = onOpenChangeRequests,
                    onOpenEvent = onOpenEvent
                )
            }
        }

        item {
            // Last, as spec §3 asks. The unread tile travels with it rather than being
            // stranded alone at the top: the two are one row, and the Chat tab already
            // carries its own unread badge, so nothing is lost by it sitting here.
            Spacer(modifier = Modifier.size(4.dp))
            StatTiles(
                spend = state.monthSpend,
                balances = state.monthBalances,
                unreadCount = state.unreadCount,
                onOpenExpenses = onOpenExpenses,
                onOpenChat = onOpenChat
            )
        }
    }
}

/** The trailing chevron every navigation row in the top group carries. */
@Composable
private fun HomeChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        // A heading, so TalkBack can jump between Home's sections (D-17).
        modifier = Modifier.semantics { heading() }
    )
}

/**
 * The handover hero: who has the child now, when it changes hands, and the one action that
 * belongs to that fact.
 *
 * The wash runs from the current parent's hue into the next parent's, so the card itself shows
 * the direction of the handover before a word is read.
 *
 * Internal rather than private so the JVM screenshot tests (`ScreenshotMatrix` and its
 * subclasses under `app/src/test`) can render it on its own.
 *
 * @param info Next handover
 * @param onConfirm Opens the change-request inbox, where a handover is actually acted on
 */
@Composable
@Suppress("LongMethod") // one card: gradient, headline, chips and action read as a single block
internal fun HandoverHero(
    info: HandoverInfo,
    parentNames: ParentNames,
    onConfirm: () -> Unit,
    childrenToday: List<ChildWithParent> = emptyList()
) {
    val fromColor = ParentColors.fill(info.fromParent)
    val toColor = ParentColors.fill(info.toParent)
    val headline = when (info.daysUntil) {
        0L -> stringResource(
            R.string.home_handover_hero_today,
            parentNames.labelFor(info.toParent)
        )
        1L -> stringResource(
            R.string.home_handover_hero_tomorrow,
            parentNames.labelFor(info.toParent)
        )
        else -> pluralStringResource(
            R.plurals.home_handover_hero_in_days,
            info.daysUntil.toInt(),
            parentNames.labelFor(info.toParent),
            info.daysUntil.toInt()
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            fromColor.copy(alpha = HERO_TINT_ALPHA),
                            toColor.copy(alpha = HERO_TINT_ALPHA)
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(fromColor)
                )
                Text(
                    text = stringResource(
                        R.string.home_handover_current,
                        parentNames.labelFor(info.fromParent)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    lineHeight = 32.sp
                ),
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip(
                    label = info.date.format(localizedDate("EEEMMMd")),
                    container = ParentColors.container(info.toParent, alpha = 0.2f),
                    contentColor = ParentColors.text(info.toParent)
                )
                PillChip(
                    label = stringResource(R.string.home_handover_review),
                    onClick = onConfirm
                )
            }
            ChildrenTodayLines(childrenToday, parentNames)
        }
    }
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
internal fun StatTiles(
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
            value = pluralStringResource(R.plurals.home_stat_unread_count, unreadCount, unreadCount),
            caption = stringResource(R.string.home_stat_open_chat),
            onClick = onOpenChat
        )
    }
    if (LocalDensity.current.fontScale >= STACK_TILES_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spendTile(Modifier.fillMaxWidth())
            if (unreadCount > 0) chatTile(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spendTile(Modifier.weight(1f))
            if (unreadCount > 0) chatTile(Modifier.weight(1f))
        }
    }
}

/**
 * The settle-up line under the spend figure: "You are owed 29.85", "You owe 29.85", or
 * "All settled".
 *
 * Only balances whose split could be worked out are reported — while unpaired there is one
 * parent on record and a debt figure would be invented.
 *
 * A month mixing currencies can owe in one direction in CZK and the other in USD, and the app
 * does no FX conversion, so there is no honest single sentence for that. Rather than joining
 * amounts under whichever direction happened to come first, this reports the **largest** single
 * balance and lets the Expenses screen — one tap away, and where this tile links — lay out the
 * per-currency detail.
 */
@Composable
private fun balanceCaption(balances: List<CurrencyBalance>): String {
    val largest = balances
        .filter { it.balance.splitKnown && abs(it.balance.netForCurrentUser) >= SETTLED_EPSILON }
        .maxByOrNull { abs(it.balance.netForCurrentUser) }
        ?: return stringResource(R.string.home_stat_settled)

    val amount = formatMoney(abs(largest.balance.netForCurrentUser), largest.currency)
    return if (largest.balance.netForCurrentUser > 0) {
        stringResource(R.string.home_stat_owed_to_you, amount)
    } else {
        stringResource(R.string.home_stat_you_owe, amount)
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
    badge: Int? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
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

/**
 * One row of the child's week: a parent-coloured node on a vertical rail, with the event beside
 * it and an exclamation mark when the co-parent is expected.
 *
 * **The colour and the words name the same parent** — the one whose custody day the event falls
 * on, which is the question this row exists to answer. When no arrangement answers for that date
 * the row falls back to the event's own owner rather than going colourless: a rail of grey dots
 * says nothing, and the owner is a fact the app does hold. The words drop the "'s day" clause in
 * that case, because that is the part that would be a guess.
 *
 * Internal rather than private so the JVM screenshot tests (`ScreenshotMatrix` and its
 * subclasses under `app/src/test`) can render it on its own.
 *
 * @param entry The row
 * @param parentNames Resolves a slot to that parent's name
 * @param isLast Whether this is the final row, which drops the trailing connector
 * @param onClick Opens the event
 */
@Composable
@Suppress("LongMethod") // rail, node, title, time and custody words: one row
internal fun TimelineRow(
    entry: WeekEntry,
    parentNames: ParentNames,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val event = entry.event
    val dotSlot = entry.dayParent ?: event.parentOwner
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.width(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ParentColors.fill(dotSlot))
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Column(modifier = Modifier.padding(bottom = 6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The mark carries its own description rather than none: an unexplained glyph
                // is worse than no glyph for anyone not reading the screen, and what it means —
                // that the co-parent is expected — is not guessable from an exclamation mark.
                if (event.isImportant) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = stringResource(R.string.event_important_mark_description),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(IconSizes.Inline)
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val timeLabel = if (looksAllDay(event)) {
                event.startDateTime.format(localizedDate("EEEd"))
            } else {
                event.startDateTime.dateAndTime("EEEd")
            }
            Text(
                text = entry.dayParent
                    ?.let { stringResource(R.string.home_timeline_meta, timeLabel, parentNames.labelFor(it)) }
                    ?: timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The co-parent's recent changes as one grouped list. A change *request* carries an inline
 * "Review" action, because it is the only entry in this feed that is waiting on the reader.
 *
 * @param items Activity entries, newest first
 * @param onOpenChangeRequests Opens the change-request inbox
 * @param onOpenEvent Opens an event by id
 */
@Composable
private fun ActivityGroup(
    items: List<ActivityItem>,
    onOpenChangeRequests: () -> Unit,
    onOpenEvent: (String) -> Unit
) {
    SectionGroup {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (item.isChangeRequest) onOpenChangeRequests() else onOpenEvent(item.eventId)
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.kind.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSizes.Small)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(item.kind.labelRes(), item.title),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.timestamp.dateAndTime("dMMM"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.isChangeRequest) {
                    PillChip(
                        label = stringResource(R.string.home_activity_review),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onOpenChangeRequests
                    )
                }
            }
            if (index != items.lastIndex) Divider()
        }
    }
}

/**
 * Formats an amount in the reader's own conventions, in the currency the record was entered in.
 *
 * The locale and the currency are separate decisions and this needs both: the *currency* comes
 * from the expense (a Czech family may still record something in EUR), while the *formatting* —
 * decimal comma, thin space between thousands, symbol after the number — belongs to whoever is
 * reading. Pinned to `Locale.US`, this rendered `CZK 1,234.56` on the "this month" tile of a
 * Czech user's home screen, where `1 234,56 Kč` is what they expect on a figure two parents are
 * about to settle between them.
 */
private fun formatMoney(amount: Double, currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { format.currency = Currency.getInstance(currencyCode) }
    return format.format(amount)
}

private fun ActivityKind.icon(): ImageVector = when (this) {
    ActivityKind.EVENT_CREATED -> Icons.Default.Add
    ActivityKind.EVENT_UPDATED -> Icons.Default.Edit
    ActivityKind.PICKUP_CONFIRMED -> Icons.Default.CheckCircle
    ActivityKind.CHANGE_REQUESTED -> Icons.Default.SwapHoriz
}

private fun ActivityKind.labelRes(): Int = when (this) {
    ActivityKind.EVENT_CREATED -> R.string.home_activity_event_created
    ActivityKind.EVENT_UPDATED -> R.string.home_activity_event_updated
    ActivityKind.PICKUP_CONFIRMED -> R.string.home_activity_pickup_confirmed
    ActivityKind.CHANGE_REQUESTED -> R.string.home_activity_change_requested
}

@LightDarkPreviews
@Composable
private fun HandoverHeroPreview() {
    PreviewWrapper {
        HandoverHero(
            info = HandoverInfo(
                date = LocalDate.now().plusDays(1),
                daysUntil = 1L,
                fromParent = "mom",
                toParent = "dad"
            ),
            parentNames = previewParentNames,
            onConfirm = {}
        )
    }
}

@LightDarkPreviews
@Composable
private fun StatTilesPreview() {
    PreviewWrapper {
        StatTiles(
            spend = MonthSpend(byCurrency = listOf(CurrencyAmount(currency = "CZK", amount = 4_250.0))),
            balances = emptyList(),
            unreadCount = 2,
            onOpenExpenses = {},
            onOpenChat = {}
        )
    }
}
