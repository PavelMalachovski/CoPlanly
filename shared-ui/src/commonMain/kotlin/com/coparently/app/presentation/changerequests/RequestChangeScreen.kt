package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.common.AnswerByPickSaver
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.LocalDateSaver
import com.coparently.app.presentation.common.LocalTimeSaver
import com.coparently.app.presentation.common.NullableLocalTimeSaver
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberAnswerByOffer
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.components.TimePickerDialog
import com.coparently.app.presentation.event.plusHour
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.change_request_back
import com.coparently.app.resources.change_request_cancel
import com.coparently.app.resources.change_request_currently
import com.coparently.app.resources.change_request_form_title
import com.coparently.app.resources.change_request_from_time
import com.coparently.app.resources.change_request_note_label
import com.coparently.app.resources.change_request_ok
import com.coparently.app.resources.change_request_proposed_new_time
import com.coparently.app.resources.change_request_send
import com.coparently.app.resources.change_request_to_time
import com.coparently.app.resources.change_request_to_unset
import com.coparently.app.utils.ReaderDateText
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

/**
 * Form for proposing a new date/time for [eventId] to the co-parent.
 *
 * It no longer takes a conversation id. The chat card used to be posted only when one was passed
 * — that is, only when the request was started from the chat screen — so a change proposed from
 * the calendar reached the thread not at all. `ActivityAnnouncer` resolves the pair's thread from
 * the two uids itself, so there is nothing left for a caller to supply or to forget.
 */
@Suppress("LongMethod") // Compose screen: state wiring + Scaffold in one place
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestChangeScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: RequestChangeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coParentName = rememberParentNames(viewModel.parents.collectAsState().value).coParentLabel()

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    LaunchedEffect(uiState) {
        if (uiState is RequestChangeUiState.Saved) onBack()
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.change_request_form_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(Res.string.change_request_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is RequestChangeUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is RequestChangeUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(Spacing.XXL),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message.asString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is RequestChangeUiState.Ready, is RequestChangeUiState.Sending -> {
                val event = when (state) {
                    is RequestChangeUiState.Ready -> state.event
                    is RequestChangeUiState.Sending -> state.event
                    else -> return@Scaffold
                }
                RequestChangeForm(
                    event = event,
                    isSending = state is RequestChangeUiState.Sending,
                    coParentName = coParentName,
                    onSubmit = { start, end, note, answerBy ->
                        viewModel.submit(event, start, end, note, answerBy)
                    },
                    modifier = Modifier.padding(padding)
                )
            }

            is RequestChangeUiState.Saved -> Unit // navigating away
        }
    }
}

@Suppress("LongMethod") // Compose form: fields + three picker dialogs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestChangeForm(
    event: Event,
    isSending: Boolean,
    coParentName: String,
    onSubmit: (LocalDateTime, LocalDateTime?, String?, AnswerByPick) -> Unit,
    modifier: Modifier = Modifier
) {
    // Saveable, so a rotation keeps what the parent proposed and wrote (D-11's rule). Each field is
    // kept as its ISO text (`DateTimeSavers`), never Java-serialised into the Bundle.
    var proposedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(event.startDateTime.date) }
    var proposedStartTime by rememberSaveable(stateSaver = LocalTimeSaver) { mutableStateOf(event.startDateTime.time) }
    var proposedEndTime by rememberSaveable(stateSaver = NullableLocalTimeSaver) {
        mutableStateOf(event.endDateTime?.time)
    }
    var note by rememberSaveable { mutableStateOf("") }
    var answerBy by rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }
    // The cap follows the proposed time, so it is worked out when that changes and not per frame.
    val proposedStart = LocalDateTime(proposedDate, proposedStartTime)
    val cap = remember(event.startDateTime, proposedStart) {
        RequestCap.forChange(
            event.startDateTime,
            proposedStart,
            Clock.System.now().toEpochMilliseconds(),
            TimeZone.currentSystemDefault()
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.L)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium
                )
                // The resource takes the date and the time as two arguments; the end time,
                // when the event has one, is appended to the second one as a range.
                val currentTime = ReaderDateText.time(event.startDateTime) +
                    (event.endDateTime?.let { " – " + ReaderDateText.time(it) } ?: "")
                Text(
                    text = stringResource(
                        Res.string.change_request_currently,
                        ReaderDateText.date(event.startDateTime.date, DATE_SKELETON),
                        currentTime
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(Res.string.change_request_proposed_new_time),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(ReaderDateText.date(proposedDate, DATE_SKELETON))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            OutlinedButton(
                onClick = { showStartTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(
                        Res.string.change_request_from_time,
                        ReaderDateText.time(proposedStartTime)
                    )
                )
            }
            OutlinedButton(
                onClick = { showEndTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                val endTime = proposedEndTime
                Text(
                    if (endTime != null) {
                        stringResource(Res.string.change_request_to_time, ReaderDateText.time(endTime))
                    } else {
                        stringResource(Res.string.change_request_to_unset)
                    }
                )
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(stringResource(Res.string.change_request_note_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        AnswerByField(
            offer = rememberAnswerByOffer(cap),
            selected = answerBy,
            onSelect = { answerBy = it },
            coParentName = coParentName
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        Button(
            onClick = {
                val end = proposedEndTime?.let { LocalDateTime(proposedDate, it) }
                onSubmit(proposedStart, end, note, answerBy)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(Res.string.change_request_send))
            }
        }
    }

    if (showDatePicker) {
        LocalDatePickerDialog(
            initialDate = proposedDate,
            confirmLabel = stringResource(Res.string.change_request_ok),
            dismissLabel = stringResource(Res.string.change_request_cancel),
            onConfirm = { proposedDate = it },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = proposedStartTime,
            onTimeSelected = {
                proposedStartTime = it
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = proposedEndTime ?: proposedStartTime.plusHour(),
            onTimeSelected = {
                proposedEndTime = it
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

/** The proposed day's skeleton: "Wed, May 13, 2026". */
private const val DATE_SKELETON = "yMMMEEEd"
