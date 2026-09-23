package com.coparently.app.presentation.professionals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A professional's read-only calendar of one family (MON-18): four weeks at a time, each day with
 * whose day it is and the family's shared events.
 *
 * A list rather than the parents' grid, and on purpose. The grid is built on Room, which this
 * phone does not hold for somebody else's family, and it is full of affordances — tap to create,
 * long-press to swap, drag to reschedule — that a professional must never be offered (design item
 * 8). Whose day it is is **named**, not coloured: the parents' colours are each parent's own choice
 * (design item 12), which this phone cannot know, and pink for a parent who chose purple is the
 * bug that rule forbids.
 *
 * @param onNavigateUp Returns to the professionals list.
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalCalendarScreen(
    onNavigateUp: () -> Unit,
    viewModel: ProfessionalCalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.professional_calendar_title)) },
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
            ProfessionalCalendarUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            ProfessionalCalendarUiState.Unavailable -> EmptyState(
                icon = Icons.Default.CalendarMonth,
                title = stringResource(R.string.professional_unavailable),
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            is ProfessionalCalendarUiState.Ready -> AgendaList(
                state = state,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun AgendaList(
    state: ProfessionalCalendarUiState.Ready,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = professionalFamilyLabel(state.grant),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.professional_calendar_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WindowPager(onPrevious, onNext)
            }
        }
        items(state.days, key = { it.date.toString() }) { day ->
            DayCard(day, state.grant)
        }
    }
}

@Composable
private fun WindowPager(onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.professional_calendar_previous)
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.professional_calendar_next)
            )
        }
    }
}

/** One day: its date, whose day it is, and its events — or a quiet "no events". */
@Composable
private fun DayCard(day: ProfessionalDay, grant: ProfessionalGrant) {
    val dateText = remember(day.date) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(day.date)
    }
    SectionGroup {
        SectionRow(
            title = dateText,
            trailing = {
                day.custodySlot?.let { slot ->
                    PillChip(
                        label = stringResource(
                            R.string.professional_calendar_custody_with,
                            parentNameForSlot(grant, slot)
                        ),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        if (day.events.isEmpty()) {
            Divider()
            SectionRow(
                title = stringResource(R.string.professional_calendar_no_events),
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            day.events.forEach { event ->
                Divider()
                EventRow(event, grant)
            }
        }
    }
}

/**
 * One event: its time, title and whose it is. Occurrences of a recurring event share the master's
 * id, so nothing here keys on it (CLAUDE.md "Things that are easy to get wrong", item 4).
 */
@Composable
private fun EventRow(event: Event, grant: ProfessionalGrant) {
    val time = remember(event.startDateTime) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(event.startDateTime)
    }
    SectionRow(
        title = event.title,
        supporting = stringResource(
            R.string.professional_calendar_event_supporting,
            time,
            parentNameForSlot(grant, event.parentOwner)
        )
    )
}
