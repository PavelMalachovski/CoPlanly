package com.coparently.app.presentation.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.ErrorState
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.professionals.ProfessionalDay
import com.coparently.app.presentation.professionals.WindowPager
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.shortTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A family friend's read-only calendar of one family (phase 2, spec §4.4): four weeks at a time,
 * each day with the family's shared, agreed events, who added each, and whether the friend takes
 * part.
 *
 * The professional's list rather than the parents' grid, for the professional's reasons: the grid
 * is built on Room, which this phone does not hold for somebody else's family, and on affordances
 * a reader must never be offered. And no "whose day": a friend cannot read the custody schedule,
 * so nothing here could say it honestly.
 *
 * @param onNavigateUp Returns to the friend's list of families.
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendCalendarScreen(
    onNavigateUp: () -> Unit,
    viewModel: FriendCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val ready = uiState as? FriendCalendarUiState.Ready
    var opened by remember { mutableStateOf<Event?>(null) }
    // An ended access shows no event, the open one included.
    LaunchedEffect(ready == null) {
        if (ready == null) opened = null
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.friend_calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            FriendCalendarUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            FriendCalendarUiState.AccessEnded -> EmptyState(
                icon = Icons.Default.EventBusy,
                title = stringResource(R.string.friend_calendar_access_ended),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            FriendCalendarUiState.Failed -> ErrorState(
                message = stringResource(R.string.friend_calendar_failed),
                onRetry = viewModel::retry,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            is FriendCalendarUiState.Ready -> FriendAgenda(
                state = state,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onOpenEvent = { opened = it },
                modifier = Modifier.padding(padding)
            )
        }
    }
    opened?.let { event ->
        FriendEventSheet(event = event, grant = ready?.grant, onDismiss = { opened = null })
    }
}

/** An event's details in a sheet that opens fully, as every sheet in the app does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendEventSheet(event: Event, grant: CalendarFriendGrant?, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        FriendEventDetails(
            event = event,
            addedBy = grant.addedBy(event),
            takesPart = grant.takesPart(event)
        )
    }
}

@Composable
private fun FriendAgenda(
    state: FriendCalendarUiState.Ready,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenEvent: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                state.grant?.familyNames()?.let { (first, second) ->
                    Text(
                        text = stringResource(R.string.friend_calendar_family, first, second),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = stringResource(R.string.friend_calendar_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WindowPager(onPrevious, onNext)
            }
        }
        items(state.days, key = { it.date.toString() }) { day ->
            FriendAgendaDay(day, state.grant, onOpenEvent)
        }
    }
}

/**
 * One day of a friend's calendar: its date, then its events — or a quiet "No events".
 *
 * Occurrences of a recurring event share the master's id, so nothing here keys on it (CLAUDE.md
 * "Things that are easy to get wrong", item 4).
 *
 * @param day The day and its events.
 * @param grant This family's grant, or null before it has arrived; it names the parents.
 * @param onOpenEvent Opens an event's details.
 */
@Composable
internal fun FriendAgendaDay(day: ProfessionalDay, grant: CalendarFriendGrant?, onOpenEvent: (Event) -> Unit) {
    val dateText = remember(day.date) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(day.date)
    }
    SectionGroup {
        SectionRow(title = dateText, modifier = Modifier.semantics { heading() })
        if (day.events.isEmpty()) {
            Divider()
            SectionRow(
                title = stringResource(R.string.professional_calendar_no_events),
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            day.events.forEach { event ->
                Divider()
                FriendEventRow(event, grant, onClick = { onOpenEvent(event) })
            }
        }
    }
}

/** One event: its title, its time and who added it, and "You take part" when it names the friend. */
@Composable
private fun FriendEventRow(event: Event, grant: CalendarFriendGrant?, onClick: () -> Unit) {
    // "All day" rather than the midnight start an all-day event is stored with, as the parents'
    // preview says it.
    val time = if (AllDayEvent.isAllDay(event)) {
        stringResource(R.string.event_all_day)
    } else {
        event.startDateTime.format(shortTime())
    }
    val addedBy = grant.addedBy(event)
    SectionRow(
        title = event.title,
        supporting = if (addedBy == null) {
            time
        } else {
            stringResource(
                R.string.friend_calendar_event_supporting,
                time,
                stringResource(R.string.friend_calendar_added_by, addedBy)
            )
        },
        onClick = onClick,
        trailing = if (grant.takesPart(event)) {
            { TakesPartChip() }
        } else {
            null
        },
        stackTrailingAtLargeFont = true
    )
}

/** The two parents' first names in the grant's own order, or null unless it names both. */
private fun CalendarFriendGrant.familyNames(): Pair<String, String>? {
    val names = familyParents.mapNotNull { parentNames[it] }
    return if (names.size == 2) names[0] to names[1] else null
}

/** The first name of the parent who added [event], when the grant names them. */
private fun CalendarFriendGrant?.addedBy(event: Event): String? =
    event.createdByFirebaseUid?.let { uid -> this?.parentNames?.get(uid) }

/** Whether [event] names this grant's friend as taking part. */
private fun CalendarFriendGrant?.takesPart(event: Event): Boolean =
    this != null && event.friendParticipates == friendUid
