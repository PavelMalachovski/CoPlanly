package com.coparently.app.presentation.event

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.coparently.app.R
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestCap
import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.calendar.CalendarViewModel
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.common.AnswerByPickSaver
import com.coparently.app.presentation.common.FamilyMemberChips
import com.coparently.app.presentation.common.FamilyMemberRefListSaver
import com.coparently.app.presentation.common.FullScreenImageDialog
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.LocalDatePickerDialog
import com.coparently.app.presentation.common.StickyActionBar
import com.coparently.app.presentation.common.eventTypeLabel
import com.coparently.app.presentation.common.rememberAnswerByOffer
import com.coparently.app.presentation.common.rememberDiscardGuard
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberRecordPhoto
import com.coparently.app.presentation.common.toggling
import com.coparently.app.presentation.components.TimePickerDialog
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.dimensions
import com.coparently.app.presentation.theme.labelLargeEmphasized
import com.coparently.app.utils.ValidationResult
import com.coparently.app.utils.ValidationUtils
import com.coparently.app.utils.localizedDate
import com.coparently.app.utils.shortTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Reminder lead-time options offered in the event form, in minutes before the
 * event start (null = no reminder), paired with the label resource id.
 * Stored as [Event.reminderMinutes].
 */
private val REMINDER_OPTIONS: List<Pair<Int?, Int>> = listOf(
    null to R.string.event_reminder_none,
    10 to R.string.event_reminder_10_min,
    30 to R.string.event_reminder_30_min,
    60 to R.string.event_reminder_1_hour,
    120 to R.string.event_reminder_2_hours,
    1440 to R.string.event_reminder_1_day
)

/**
 * Whether the parent-owner picker (the two animated cards) should render.
 *
 * **Only when the form has no other way to name an owner** (21 October 2026, Zuzana's test: the
 * "Assigned to" choice is gone). Whose day an event falls on is decided for the parent — the
 * family's schedule for the start date, else their own slot ([autoEventOwner]) — so the picker is
 * left only as the escape for an account whose own slot never resolves, which would otherwise sit
 * in front of a Save that stays disabled.
 *
 * A plain function, not inlined into the `if`, so the property that decides it can be pinned in
 * a JVM test — this repo has no Compose UI tests, so an expression that only lives inside the
 * composable is untestable.
 *
 * Gated on [parentsLoaded] first: before `ParentsSource`'s upstream has emitted, `Parents()`
 * reads exactly like an unpaired, unresolved account (`isPaired = false`, no owner), so without
 * this the picker would render on every first composition and vanish the instant the real answer
 * arrives.
 *
 * The second half is deliberately not `parentOwner == null`. [parentOwner] starts null and is
 * only filled by a `LaunchedEffect` that runs *after* the composition in which `parents` first
 * resolves — so for an unpaired account whose slot already resolved, `parentOwner == null` is
 * still true for that one composition, and the picker would flash open for a frame anyway.
 * [currentUserSlot] is the same value that effect is about to assign [parentOwner] to, so
 * falling back to it answers "is there really nobody to choose between yet" without waiting a
 * frame for the effect to catch up: [parentOwner] takes priority once it diverges from
 * [currentUserSlot] (a loaded event, a restored draft, a manual pick), and only when neither is
 * known does the picker stay up to give the user a way to fill it in themselves.
 */
internal fun showParentOwnerSelector(
    parentsLoaded: Boolean,
    parentOwner: String?,
    currentUserSlot: String?
): Boolean = parentsLoaded && (parentOwner ?: currentUserSlot) == null

/**
 * Whose day a new event falls on, chosen for the parent (21 October 2026): the parent the
 * family's schedule gives the start date to, swaps included, else the parent creating it.
 *
 * On the co-parent's day it waits for their answer unless it is marked important
 * ([com.coparently.app.domain.events.EventAcceptanceTransition.required]).
 *
 * @param custodyParent The slot `CustodyResolver` answers for the start date, or null.
 * @param mySlot The signed-in parent's own slot, or null while it resolves.
 */
internal fun autoEventOwner(custodyParent: String?, mySlot: String?): String? = custodyParent ?: mySlot

/**
 * Whether a description typed now will be read by the co-parent when they answer (phase 2, spec
 * §5.2): the same rule as [com.coparently.app.domain.events.EventAcceptanceTransition.required], for
 * a new event — paired, not private, not important, and on the co-parent's days. An edit never asks
 * again, so it answers false; so does an owner or a slot not known yet.
 *
 * @param isNewEvent Whether the form creates an event rather than editing one.
 * @param mySlot The signed-in parent's own slot.
 */
@Suppress("LongParameterList") // the rule's own inputs, each pinned by a test
internal fun descriptionReachesCoParent(
    isNewEvent: Boolean,
    isPaired: Boolean,
    isPrivate: Boolean,
    isImportant: Boolean,
    parentOwner: String?,
    mySlot: String?
): Boolean = isNewEvent && isPaired && !isPrivate && !isImportant &&
    !parentOwner.isNullOrBlank() && !mySlot.isNullOrBlank() && parentOwner != mySlot

/**
 * Screen for adding or editing an event.
 * Modernized with Material 3 design, date/time pickers, and validation.
 * Based on Design Roadmap Day 3: Forms & Input.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddEditEventScreen(
    eventId: String?,
    initialDate: LocalDate? = null,
    initialHour: Int? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRequestChange: ((String) -> Unit)? = null,
    viewModel: EventViewModel = hiltViewModel(),
    friendViewModel: com.coparently.app.presentation.friends.FriendViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    // The family's live calendar friends (item 16). Empty for most families, which is why the
    // participation toggle below is absent rather than disabled when there is nobody to name.
    val calendarFriends by friendViewModel.friends.collectAsState()
    // Whose day the start date is, for a new event's owner (autoEventOwner).
    val ownerViewModel: EventOwnerViewModel = hiltViewModel()
    val parentNames = rememberParentNames(viewModel.parents.collectAsState().value)
    // The signed-in parent - the one source of truth for "who am I" already threaded into this
    // screen (see ParentsSource). Nothing here learns the signed-in slot any other way.
    val currentUser = parentNames.parents.me
    // Whether there is a co-parent at all - what decides whether a new event can wait for one.
    // Deliberately not "parentNames.parents.coParent != null": a legacy pair (paired before slot
    // assignment shipped) has a real co-parent but a null Parents.coParent.
    val isPaired = parentNames.parents.isPaired
    // Whether Parents() is a real answer yet. Parents() itself - the synthetic value every
    // stateIn seeds with - is indistinguishable from "an unpaired, unresolved account" without
    // this: both have isPaired = false and me = null. Gating the selector on it too is what
    // stops the two-card block from rendering for one frame and then disappearing on every
    // first composition.
    val parentsLoaded = parentNames.parents.loaded

    // The fields are saveable (D-11): turning the phone used to reload an edited event from Room
    // over the parent's changes. `seeded` below makes the load and the draft restore run once.
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // Yours unless you say otherwise. Hardcoding "mom" here meant every event either parent
    // created was attributed to the same person. Seeded from the signed-in user's slot, which
    // is unknown (null) until ParentsSource resolves it - Save stays disabled until then
    // (see isFormValid) rather than silently falling back to "mom".
    var parentOwner by rememberSaveable { mutableStateOf(currentUser?.slot) }
    var eventType by rememberSaveable { mutableStateOf("general") }
    // Snapshot of the loaded event so saving preserves sync/sharing fields
    var existingEvent by remember { mutableStateOf<Event?>(null) }
    // MVP1 fields: recurrence, reminder, privacy
    var recurrencePattern by rememberSaveable { mutableStateOf<String?>(null) }
    var recurrenceEndDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var showRecurrenceEndPicker by remember { mutableStateOf(false) }
    var reminderMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var isPrivate by rememberSaveable { mutableStateOf(false) }
    var isImportant by rememberSaveable { mutableStateOf(false) }
    // Which calendar friend takes part, or null. Not an owner — see Event.friendParticipates.
    var friendParticipates by rememberSaveable { mutableStateOf<String?>(null) }
    // The family's children and pets, and which of them this event is about. Empty is the whole
    // family — a different question from parentOwner, which is the custody slot whose day it
    // falls on. The picker renders nothing below two members, so nothing is asked of a family
    // with one child.
    val familyMembers by viewModel.familyMembers.collectAsState()
    var forMembers by rememberSaveable(stateSaver = FamilyMemberRefListSaver) {
        mutableStateOf(emptyList<FamilyMemberRef>())
    }
    // Event photo: URL of an already-attached image, and a freshly picked local image
    // (not yet uploaded). Picking a new one supersedes the existing image on save.
    var existingImageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // Custom event types are read from encrypted prefs; load them off the main thread
    // so opening the screen isn't blocked by the (synchronous) decrypt on first composition.
    var customEventTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var startDate by rememberSaveable { mutableStateOf(initialDate ?: LocalDate.now()) }
    var startTime by rememberSaveable(initialHour, initialDate) {
        mutableStateOf(
            if (initialHour != null) {
                LocalTime.of(initialHour, 0)
            } else {
                LocalTime.now()
            }
        )
    }
    var endTime by rememberSaveable(initialHour) {
        mutableStateOf(
            if (initialHour != null) {
                LocalTime.of(initialHour, 0).plusHours(1)
            } else {
                LocalTime.now().plusHours(1)
            }
        )
    }

    // The end date is kept as the days it lies after the start date (a weekend away, an overnight
    // stay), so moving the start date moves the end with it. An edited event keeps its span and,
    // while its end time and end date are untouched, an end it never had. Saving `startDate` with
    // the end time used to cut a multi-day event to one day.
    var endDaySpan by rememberSaveable { mutableIntStateOf(0) }
    var keepNoEnd by rememberSaveable { mutableStateOf(false) }
    // All day saves the start date's midnight and the end date's last minute (`AllDayEvent`); the
    // times stay in the form, hidden, so turning it off gives them back.
    var allDay by rememberSaveable { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val dims = dimensions()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pickedImageUri = uri }
    val snackbarHostState = remember { SnackbarHostState() }
    val appMessages = LocalAppMessages.current
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    // Set when a saved draft was auto-restored into a blank "New Event", so the user
    // gets a subtle, dismissible hint (with a Clear action) instead of a silent prefill.
    var draftRestored by remember { mutableStateOf(false) }

    // Strings used inside non-composable lambdas/effects must be resolved here,
    // in composable scope (stringResource cannot be called in those lambdas).
    val endBeforeStartMessage = stringResource(R.string.event_form_end_before_start)
    val draftRestoredMessage = stringResource(R.string.event_form_draft_restored)
    val draftClearLabel = stringResource(R.string.event_form_clear)
    // The validator's own messages are English; it decides, these say it (CQ-14).
    val titleEmptyMessage = stringResource(R.string.event_form_title_empty)
    val titleTooLongMessage = stringResource(
        R.string.event_form_title_too_long,
        ValidationUtils.MAX_TITLE_LENGTH
    )
    val descriptionTooLongMessage = stringResource(
        R.string.event_form_description_too_long,
        ValidationUtils.MAX_DESCRIPTION_LENGTH
    )

    // Validation states
    var titleError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var showTimeValidationError by remember { mutableStateOf(false) }
    var timeValidationMessage by remember { mutableStateOf("") }

    // Validate title
    fun validateTitle(): Boolean {
        val result = ValidationUtils.validateEventTitle(title)
        titleError = when {
            result is ValidationResult.Success -> null
            title.isBlank() -> titleEmptyMessage
            else -> titleTooLongMessage
        }
        return result is ValidationResult.Success
    }

    // Validate description
    fun validateDescription(): Boolean {
        val result = ValidationUtils.validateDescription(description)
        descriptionError = if (result is ValidationResult.Error) descriptionTooLongMessage else null
        return result is ValidationResult.Success
    }

    // Time validation effect
    LaunchedEffect(startTime, endTime, startDate, endDaySpan, keepNoEnd, allDay) {
        val startDateTime = formStartDateTime(startDate, startTime, allDay)
        val endDateTime = formEndDateTime(startDate, endTime, endDaySpan, keepNoEnd, allDay)

        if (endDateTime != null && !endDateTime.isAfter(startDateTime)) {
            showTimeValidationError = true
            timeValidationMessage = endBeforeStartMessage
        } else {
            showTimeValidationError = false
            timeValidationMessage = ""
        }
    }

    // Validation
    val isTitleValid = title.isNotBlank() && titleError == null
    // parentOwner != null: nothing may quietly fall back to a hardcoded slot again. Not
    // unreachable in practice: UserRepositoryImpl deliberately creates no Room row for a user
    // it cannot name, so `me` (and therefore parentOwner) can stay null indefinitely, not just
    // for the usual first-frame beat. See the selector below and the message next to Save for
    // what the user sees while that's true.
    val isFormValid = isTitleValid && descriptionError == null && !showTimeValidationError && parentOwner != null

    // Load user-defined event types asynchronously (encrypted-prefs read off Main)
    LaunchedEffect(Unit) {
        customEventTypes = withContext(Dispatchers.IO) { viewModel.customEventTypes() }
    }

    // Seed the owner from the signed-in user's slot once ParentsSource resolves it, as long as
    // nothing has claimed the field yet. Not restricted to a brand new event: an existing event
    // whose load already set parentOwner (see the eventId effect below, which assigns
    // unconditionally) makes this a no-op forever after, since the field is then never null
    // again - so it cannot overwrite a loaded owner, a draft restore, or a manual pick,
    // regardless of the order these effects resolve in. Leaving it unrestricted is what lets an
    // existing event whose load *fails* (getEventById returns null, parentOwner never gets set)
    // still end up with an owner once the signed-in user's own slot resolves, instead of being
    // stuck at null forever.
    LaunchedEffect(currentUser) {
        if (parentOwner == null) {
            parentOwner = currentUser?.slot
        }
    }

    // A new event's owner follows its start date: whose day the family's schedule gives it, else
    // the parent's own (21 October 2026 — the "Assigned to" choice is gone). An edit keeps the
    // owner it was saved with; moving it would re-colour, and re-ask about, an agreed event.
    LaunchedEffect(startDate, currentUser?.slot) {
        if (eventId == null) {
            autoEventOwner(ownerViewModel.custodyParentOn(startDate), currentUser?.slot)?.let { parentOwner = it }
        }
    }

    // Load event if editing, or load draft if creating new event
    // Issue 1.3: Draft saving functionality
    // Once per form, not once per composition: after a rotation the saved fields already hold
    // what the parent typed, so the event is re-read only as the snapshot a save copies from.
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(eventId) {
        if (eventId != null) {
            scope.launch {
                val event = viewModel.getEventById(eventId)
                event?.let {
                    existingEvent = it
                    if (seeded) return@let
                    seeded = true
                    title = it.title
                    description = it.description ?: ""
                    parentOwner = it.parentOwner
                    eventType = it.eventType
                    startDate = it.startDateTime.toLocalDate()
                    startTime = it.startDateTime.toLocalTime()
                    endTime = it.endDateTime?.toLocalTime() ?: startTime.plusHours(1)
                    endDaySpan = endDaySpanOf(it)
                    keepNoEnd = it.endDateTime == null
                    allDay = AllDayEvent.isAllDay(it)
                    recurrencePattern = if (it.isRecurring) it.recurrencePattern else null
                    recurrenceEndDate = it.recurrenceEndDate
                    reminderMinutes = it.reminderMinutes
                    isPrivate = it.isPrivate
                    isImportant = it.isImportant
                    friendParticipates = it.friendParticipates
                    forMembers = it.forMembers
                    existingImageUrl = it.imageUrl
                }
            }
        } else if (initialDate == null && initialHour == null && !seeded) {
            // Restore a saved draft only for a blank new event (opened via the "+" button).
            // When the user taps a specific time slot, that slot must be respected — the
            // draft's date/time must not override it (this was the "tap 15:00 -> 09:00" bug).
            seeded = true
            scope.launch {
                val draft = withContext(Dispatchers.IO) { viewModel.loadEventDraft() }
                draft?.let {
                    title = it.title
                    description = it.description
                    parentOwner = it.parentOwner
                    eventType = it.eventType
                    startDate = LocalDate.parse(it.startDate)
                    startTime = LocalTime.parse(it.startTime)
                    endTime = LocalTime.parse(it.endTime)
                    endDaySpan = it.endDaySpan.coerceAtLeast(0)
                    allDay = it.allDay
                    // Only surface the hint when the draft actually carried content the
                    // user would recognise, not for an empty auto-saved shell.
                    if (it.title.isNotBlank() || it.description.isNotBlank()) {
                        draftRestored = true
                    }
                }
            }
        }
    }

    // Notify the user that an unsaved draft was restored, and offer to discard it.
    LaunchedEffect(draftRestored) {
        if (draftRestored) {
            val result = snackbarHostState.showSnackbar(
                message = draftRestoredMessage,
                actionLabel = draftClearLabel,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                title = ""
                description = ""
                parentOwner = currentUser?.slot
                eventType = "general"
                startDate = initialDate ?: LocalDate.now()
                startTime = LocalTime.now()
                endTime = LocalTime.now().plusHours(1)
                endDaySpan = 0
                keepNoEnd = false
                allDay = false
                recurrencePattern = null
                recurrenceEndDate = null
                reminderMinutes = null
                isPrivate = false
                isImportant = false
                friendParticipates = null
                forMembers = emptyList()
                viewModel.clearEventDraft()
            }
            draftRestored = false
        }
    }

    // Auto-save draft when fields change (debounced)
    // Issue 1.3: Draft saving functionality
    LaunchedEffect(title, description, parentOwner, eventType, startDate, startTime, endTime, endDaySpan, allDay) {
        // parentOwner can still be null for a beat while ParentsSource resolves the signed-in
        // slot; a draft needs an owner to be worth anything, so skip the save rather than
        // stamp a placeholder.
        val ownerForDraft = parentOwner
        if (eventId == null && ownerForDraft != null) { // Only save draft for new events
            kotlinx.coroutines.delay(1000) // Debounce 1 second
            viewModel.saveEventDraft(
                EventDraft(
                    title = title,
                    description = description,
                    parentOwner = ownerForDraft,
                    eventType = eventType,
                    startDate = startDate.toString(),
                    startTime = startTime.toString(),
                    endTime = endTime.toString(),
                    endDaySpan = endDaySpan,
                    allDay = allDay
                )
            )
        }
    }

    // Asked before an edit is dropped (D-11). A new event needs no question: its draft is saved
    // as the parent types and comes back on the next "+", which is what Issue 1.3 built.
    val formFields = EventFields(
        title = title,
        description = description,
        parentOwner = parentOwner,
        eventType = eventType,
        startDate = startDate,
        startTime = startTime,
        endTime = endTime,
        endDaySpan = endDaySpan,
        allDay = allDay,
        recurrencePattern = recurrencePattern,
        recurrenceEndDate = recurrenceEndDate,
        reminderMinutes = reminderMinutes,
        isPrivate = isPrivate,
        isImportant = isImportant,
        friendParticipates = friendParticipates,
        forMembers = forMembers,
        imageUrl = existingImageUrl,
        newImage = pickedImageUri?.toString()
    )
    val editsUnsaved = existingEvent?.let { EventFields.of(it) != formFields } ?: false
    // A new event on the co-parent's days waits for their answer (phase 3a): the parent is asked when
    // it should be answered by, under the same rule that tells them the description will be read.
    val waitsForCoParent =
        descriptionReachesCoParent(eventId == null, isPaired, isPrivate, isImportant, parentOwner, currentUser?.slot)
    var answerBy by rememberSaveable(stateSaver = AnswerByPickSaver) { mutableStateOf(AnswerByPick.DEFAULT) }
    val leave = rememberDiscardGuard(dirty = editsUnsaved && !isSaving && !isDeleting, onLeave = onCancel)

    // Shared save routine for the top-bar check and the sticky bottom Save button
    val performSave: () -> Unit = performSave@{
        // Run both validations (no short-circuit) so each field shows its error
        val isTitleValidated = validateTitle()
        val isDescriptionValidated = validateDescription()
        val isValidated = isTitleValidated && isDescriptionValidated
        if (!isFormValid || !isValidated || isSaving) {
            return@performSave
        }
        // isFormValid already guarantees parentOwner != null, but it is a mutable Compose state
        // var and cannot be smart-cast from that boolean, so pin it to a local val here for the
        // Event construction below. The elvis branch is unreachable given the guard above.
        val ownerSlot = parentOwner ?: return@performSave
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isSaving = true
        scope.launch {
            try {
                // Preserve sync/sharing fields of the loaded event when editing;
                // rebuilding from defaults would wipe sharedWith/permissions
                val base = existingEvent
                // Resolve the id up front so a newly picked image can be uploaded under
                // the same event id before the event is persisted.
                val resolvedId = base?.id ?: eventId ?: UUID.randomUUID().toString()

                // Resolve the final image reference: a freshly picked image is uploaded (best
                // effort — a failure keeps the previous image and still saves the event);
                // clearing or replacing the image removes the old object from storage, since
                // every upload is a new object (L-4). A legacy download URL is dropped here: it
                // was never shown, and `purgeLegacyPhotoPaths` removes its object.
                val originalImageUrl = base?.imageUrl
                val keptImage = existingImageUrl?.takeIf { RecordPhotoCodec.isReference(it) }
                var imageUploadFailed = false
                val finalImageUrl = if (pickedImageUri != null) {
                    try {
                        viewModel.uploadEventImage(resolvedId, base?.familyId, pickedImageUri.toString())
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        // Log the cause: this failure used to be swallowed silently, which left
                        // "Photo upload failed" as the only evidence anywhere in the system.
                        android.util.Log.e("CoPlanlyUpload", "Event image upload failed", e)
                        imageUploadFailed = true
                        keptImage
                    }
                } else {
                    keptImage
                }
                if (originalImageUrl != null && finalImageUrl != originalImageUrl) {
                    viewModel.deleteEventImage(originalImageUrl, base?.familyId)
                }

                val event = (
                    base ?: Event(
                        id = resolvedId,
                        title = "",
                        startDateTime = LocalDateTime.now(),
                        eventType = "general",
                        parentOwner = ownerSlot,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                    ).copy(
                    title = title,
                    description = description.ifEmpty { null },
                    startDateTime = formStartDateTime(startDate, startTime, allDay),
                    endDateTime = formEndDateTime(startDate, endTime, endDaySpan, keepNoEnd, allDay),
                    eventType = eventType,
                    parentOwner = ownerSlot,
                    isRecurring = recurrencePattern != null,
                    recurrencePattern = recurrencePattern,
                    recurrenceEndDate = recurrenceEndDate,
                    reminderMinutes = reminderMinutes,
                    isPrivate = isPrivate,
                    isImportant = isImportant,
                    friendParticipates = friendParticipates,
                    forMembers = forMembers,
                    imageUrl = finalImageUrl,
                    updatedAt = LocalDateTime.now()
                )

                if (eventId == null) {
                    viewModel.createEvent(event, answerBy.takeIf { waitsForCoParent })
                } else {
                    viewModel.updateEvent(event)
                }

                // Clear draft after successful save
                if (eventId == null) {
                    viewModel.clearEventDraft()
                }

                // Leave immediately. `showSnackbar` suspends until the snackbar is dismissed or
                // times out, so confirming the save here used to hold the editor open for the
                // snackbar's full duration — about 4.5s normally and 14.5s when a photo upload
                // had also failed — long after the event itself was written. The saved event is
                // its own confirmation once the calendar is back.
                //
                // The upload warning still has to reach the user, so it goes out through the
                // app's message host, which survives navigation (D-25; it was a Toast). This
                // matches AddExpenseScreen's receipt-upload warning.
                if (imageUploadFailed) {
                    appMessages?.show(context.getString(R.string.event_form_photo_upload_failed))
                }
                onSave()
            } catch (e: Exception) {
                // Re-enable Save before the snackbar, not after: this suspends for the
                // snackbar's lifetime and used to leave the button dead in the meantime.
                isSaving = false
                snackbarHostState.showSnackbar(
                    // Not `e.message`: that is the exception's English text (CQ-14).
                    message = context.getString(R.string.event_form_save_failed),
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    Scaffold(
        // A back gesture over unsaved edits shrinks the form before it asks (D-11).
        modifier = Modifier.then(leave.backPreview),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (eventId == null) {
                            stringResource(R.string.event_form_title_new)
                        } else {
                            stringResource(R.string.event_form_title_edit)
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        leave()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.event_form_cancel)
                        )
                    }
                },
                actions = {
                    // Propose a time change to the co-parent (only for existing shared events)
                    if (eventId != null && onRequestChange != null && existingEvent?.isPrivate == false) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRequestChange(eventId)
                            },
                            enabled = !isDeleting && !isSaving
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = stringResource(R.string.event_form_cd_request_change)
                            )
                        }
                    }

                    // Delete button (only when editing existing event)
                    if (eventId != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showDeleteDialog = true
                            },
                            enabled = !isDeleting && !isSaving
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.event_form_cd_delete_event),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Saving is done via the sticky "Save event" button at the bottom;
                    // a duplicate check-mark here was two controls for one action.
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        bottomBar = {
            // Sticky Save: the primary action stays reachable without scrolling
            // back to the top-bar check icon on this long form
            StickyActionBar(
                label = stringResource(R.string.event_form_save),
                onClick = performSave,
                enabled = isFormValid && !isDeleting,
                busy = isSaving,
                // parentOwner == null is not just a first-frame beat: an account whose
                // profile UserRepositoryImpl declined to name (see isFormValid) can sit here
                // indefinitely. Save being merely disabled would leave no clue why - this
                // says what's missing, next to the selector that (per showParentOwnerSelector) is
                // showing precisely so there's something to do about it.
                notice = if (parentOwner == null) {
                    {
                        Text(
                            text = stringResource(R.string.event_form_owner_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    null
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(dims.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dims.paddingMedium + dims.paddingSmall / 2)
        ) {
            // Title Section
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotEmpty()) {
                        validateTitle()
                    } else {
                        titleError = null
                    }
                },
                label = { Text(stringResource(R.string.event_form_field_title_label)) },
                placeholder = { Text(stringResource(R.string.event_form_field_title_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Title,
                        contentDescription = stringResource(R.string.event_form_cd_title_icon)
                    )
                },
                isError = titleError != null,
                supportingText = if (titleError != null) {
                    {
                        Text(
                            text = titleError ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )

            // Description Section
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    validateDescription()
                },
                label = { Text(stringResource(R.string.event_form_field_description_label)) },
                placeholder = { Text(stringResource(R.string.event_form_field_description_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = stringResource(R.string.event_form_cd_description_icon)
                    )
                },
                isError = descriptionError != null,
                supportingText = when {
                    descriptionError != null -> {
                        {
                            Text(
                                text = descriptionError ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    // The co-parent reads this when they answer the event (phase 2, spec §5.2),
                    // so the parent writing it is told before they write for themselves alone.
                    descriptionReachesCoParent(
                        isNewEvent = eventId == null,
                        isPaired = isPaired,
                        isPrivate = isPrivate,
                        isImportant = isImportant,
                        parentOwner = parentOwner,
                        mySlot = currentUser?.slot
                    ) -> {
                        {
                            Text(
                                stringResource(
                                    R.string.event_form_description_coparent_hint,
                                    parentNames.labelFor(parentOwner)
                                )
                            )
                        }
                    }
                    else -> null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.buttonHeight * 2.14f), // ~120dp for compact
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus() // Clear focus when Done is pressed
                    }
                )
            )

            // Parent Owner Selection with animated cards, only as the escape for an account whose
            // own slot has not resolved: everyone else has their owner chosen for them from the
            // start date (autoEventOwner). See showParentOwnerSelector's KDoc.
            if (showParentOwnerSelector(parentsLoaded, parentOwner, currentUser?.slot)) {
                Text(
                    text = stringResource(R.string.event_form_assigned_to),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M)
                ) {
                    // Slots have an order, so when a card resolves to nobody a caption of
                    // "first"/"second" (never "You"/"Co-parent" positionally - an unpaired
                    // account's slot may be either one) tells the two cards apart. parentLabel
                    // itself is untouched: it still answers "who is this" and "we do not know"
                    // is the right answer to that question.
                    val slotFirst = stringResource(R.string.parent_label_slot_first)
                    val slotSecond = stringResource(R.string.parent_label_slot_second)
                    listOf(
                        "mom" to slotFirst,
                        "dad" to slotSecond
                    ).forEach { (value, ordinalLabel) ->
                        val label = if (parentNames.isKnown(value)) {
                            parentNames.labelFor(value)
                        } else {
                            ordinalLabel
                        }
                        val isSelected = parentOwner == value
                        // A short tween, like every other selection in the app: this was the
                        // one bouncy spring, an overshoot no other control makes (D-25).
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1f,
                            animationSpec = tween(Motion.SHORT_MS),
                            label = "parentOwnerScale"
                        )

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(dims.buttonHeight * 1.43f) // ~80dp for compact
                                .semantics {
                                    role = Role.RadioButton
                                    selected = isSelected
                                    contentDescription = context.getString(
                                        R.string.event_form_cd_assign_to,
                                        label,
                                        context.getString(
                                            if (isSelected) {
                                                R.string.event_form_cd_selected
                                            } else {
                                                R.string.event_form_cd_not_selected
                                            }
                                        )
                                    )
                                }
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                parentOwner = value
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    when (value) {
                                        "mom" -> ParentColors.container("mom", alpha = 0.2f)
                                        "dad" -> ParentColors.container("dad", alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            // Unified selected state for both parents: a thin 1.5dp accent
                            // outline over a tonal fill, no drop shadow. Previously the
                            // selected chip used a 2dp border + 4dp elevation, which — with
                            // the more saturated DadBlue — read as a heavy, un-Material box
                            // that didn't match the MomPink chip.
                            border = if (isSelected) {
                                BorderStroke(
                                    1.5.dp,
                                    when (value) {
                                        "mom" -> ParentColors.fill("mom")
                                        "dad" -> ParentColors.fill("dad")
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            } else {
                                null
                            },
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Spacing.M),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // One glyph for both slots. It used to be Face for slot 1 and
                                // Person for slot 2 - not a matched pair, and slot 2 was already
                                // indistinguishable from "unknown" because Person was the fallback
                                // too. A per-parent glyph is a third channel asserting exactly the
                                // distinction this app stopped asserting; the name and the colour
                                // carry the identity.
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.event_form_cd_label_icon, label),
                                    tint = when (value) {
                                        "mom" -> ParentColors.fill("mom")
                                        "dad" -> ParentColors.fill("dad")
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(IconSizes.Large)
                                )
                                Spacer(modifier = Modifier.height(dims.paddingSmall / 2))
                                Text(
                                    text = label,
                                    style = if (isSelected) {
                                        MaterialTheme.typography.labelLargeEmphasized
                                    } else {
                                        MaterialTheme.typography.labelLarge
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Event Type Selection
            Text(
                text = stringResource(R.string.event_form_event_type),
                style = MaterialTheme.typography.titleMedium
            )

            // Default types plus user-defined types created in the calendar filter sheet
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                val allTypes = (CalendarViewModel.DEFAULT_EVENT_TYPES + customEventTypes)
                    .map { it to eventTypeLabel(it) }

                allTypes.forEach { (value, label) ->
                    FilterChip(
                        selected = eventType == value,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            eventType = value
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            if (eventType == value) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.event_form_cd_selected_icon),
                                    modifier = Modifier.size(IconSizes.Small)
                                )
                            }
                        },
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(
                                R.string.event_form_cd_event_type,
                                label,
                                context.getString(
                                    if (eventType == value) {
                                        R.string.event_form_cd_selected
                                    } else {
                                        R.string.event_form_cd_not_selected
                                    }
                                )
                            )
                        }
                    )
                }
            }

            // Date & Time Section
            Text(
                text = stringResource(R.string.event_form_date_time),
                style = MaterialTheme.typography.titleMedium
            )

            // All day: the times leave the form, and the event covers whole days, from the start
            // date's midnight to the end date's last minute (`formStartDateTime`, `formEndDateTime`).
            val allDayLabel = stringResource(R.string.event_all_day)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = allDayLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = allDay,
                    // Named for TalkBack, like the Important switch below.
                    modifier = Modifier.semantics { contentDescription = allDayLabel },
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        allDay = it
                    }
                )
            }

            EventDateCard(
                label = stringResource(R.string.event_form_start_date),
                date = startDate,
                description = stringResource(
                    R.string.event_form_cd_select_date,
                    startDate.format(localizedDate("yMMMMEEEEd"))
                ),
                onClick = { showDatePicker = true }
            )
            // The start date until the parent picks another: a weekend away, a holiday at the
            // grandparents', a night at a friend's. Moving the start date moves it along.
            val endDate = startDate.plusDays(endDaySpan.toLong())
            EventDateCard(
                label = stringResource(R.string.event_form_end_date),
                date = endDate,
                description = stringResource(
                    R.string.event_form_cd_select_end_date,
                    endDate.format(localizedDate("yMMMMEEEEd"))
                ),
                onClick = { showEndDatePicker = true }
            )

            // Time validation error display
            if (showTimeValidationError) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.event_form_cd_error),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(IconSizes.Standard)
                        )
                        Text(
                            text = timeValidationMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Time Pickers, for an event with times: an all-day one has none to pick
            if (!allDay) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M)
                ) {
                    // Start Time
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp) // Ensure minimum touch target
                            .semantics {
                                role = Role.Button
                                contentDescription = context.getString(
                                    R.string.event_form_cd_select_start_time,
                                    startTime.format(shortTime())
                                )
                            },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showStartTimePicker = true
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dims.paddingMedium),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = stringResource(R.string.event_form_cd_time_picker_icon),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(IconSizes.Standard)
                            )
                            Spacer(modifier = Modifier.height(dims.paddingSmall))
                            Text(
                                text = stringResource(R.string.event_form_start_time),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = startTime.format(shortTime()),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    // End Time
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp) // Ensure minimum touch target
                            .semantics {
                                role = Role.Button
                                contentDescription = context.getString(
                                    R.string.event_form_cd_select_end_time,
                                    endTime.format(shortTime())
                                )
                            },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showEndTimePicker = true
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dims.paddingMedium),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = stringResource(R.string.event_form_cd_time_picker_icon),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(IconSizes.Standard)
                            )
                            Spacer(modifier = Modifier.height(dims.paddingSmall))
                            Text(
                                text = stringResource(R.string.event_form_end_time),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = endTime.format(shortTime()),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }

            // Repeat Section
            Text(
                text = stringResource(R.string.event_form_repeat),
                style = MaterialTheme.typography.titleMedium
            )

            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                listOf(
                    null to stringResource(R.string.event_repeat_none),
                    "daily" to stringResource(R.string.event_repeat_daily),
                    "weekly" to stringResource(R.string.event_repeat_weekly),
                    "biweekly" to stringResource(R.string.event_repeat_biweekly),
                    "monthly" to stringResource(R.string.event_repeat_monthly)
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = recurrencePattern == value,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            recurrencePattern = value
                            if (value == null) recurrenceEndDate = null
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            if (recurrencePattern == value) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.event_form_cd_selected_icon),
                                    modifier = Modifier.size(IconSizes.Small)
                                )
                            }
                        }
                    )
                }
            }

            // Recurrence end date (optional, only for recurring events)
            if (recurrencePattern != null) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showRecurrenceEndPicker = true
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.event_form_repeat_until),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = recurrenceEndDate?.format(localizedDate("yMMMMEEEEd"))
                                    ?: stringResource(R.string.event_form_repeat_forever),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (recurrenceEndDate != null) {
                            TextButton(onClick = { recurrenceEndDate = null }) {
                                Text(stringResource(R.string.event_form_clear))
                            }
                        }
                    }
                }
            }

            // Reminder Section
            Text(
                text = stringResource(R.string.event_form_reminder),
                style = MaterialTheme.typography.titleMedium
            )

            // Picking a reminder is the moment notifications become relevant —
            // request POST_NOTIFICATIONS here (not on app start)
            val reminderPermissionRequester =
                com.coparently.app.presentation.common.rememberNotificationPermissionRequester()
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                REMINDER_OPTIONS.forEach { (value, labelRes) ->
                    FilterChip(
                        selected = reminderMinutes == value,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (value == null) {
                                reminderMinutes = null
                            } else {
                                reminderPermissionRequester.request { reminderMinutes = value }
                            }
                        },
                        label = { Text(stringResource(labelRes)) },
                        leadingIcon = {
                            if (reminderMinutes == value) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.event_form_cd_selected_icon),
                                    modifier = Modifier.size(IconSizes.Small)
                                )
                            }
                        }
                    )
                }
            }

            // Important Section. Deliberately a statement and not an obligation: the switch
            // says the co-parent is expected, and nothing here blocks saving or chases them.
            // The stronger reading — the co-parent must confirm attendance — is the event
            // acceptance machinery pointed at a different question, and lives there.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dims.paddingMedium),
                    verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall * 1.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.event_form_important_title),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                // Names the co-parent of the family on screen, whose presence the
                                // mark requires (21 October 2026), and stays generic while unpaired.
                                val expected = parentNames.parents.coParent
                                Text(
                                    text = if (expected != null) {
                                        stringResource(
                                            R.string.event_form_important_description_named,
                                            parentNames.labelForUid(expected.uid)
                                        )
                                    } else {
                                        stringResource(R.string.event_form_important_description)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val importantLabel = stringResource(R.string.event_form_important_title)
                        Switch(
                            checked = isImportant,
                            // Named for TalkBack: a bare switch beside its label reads as
                            // "Switch, off" with nothing to say what it toggles.
                            modifier = Modifier.semantics { contentDescription = importantLabel },
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isImportant = it
                            }
                        )
                    }
                    // Only once the switch is on: the helper says what the mark will mean, and
                    // printing it while the flag is off would explain a state the event is not in.
                    if (isImportant) {
                        Text(
                            text = stringResource(R.string.event_form_important_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FamilyMemberChips(
                members = familyMembers,
                selected = forMembers,
                onToggle = { forMembers = forMembers.toggling(it) },
                label = R.string.event_form_for_members,
                modifier = Modifier.fillMaxWidth()
            )

            // Who else takes part (item 16). Only when the family has admitted a friend: a
            // toggle naming nobody would be a control for a relationship that does not exist.
            calendarFriends.firstOrNull()?.let { friend ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.friend_event_participates,
                                friend.name
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        val participatesLabel = stringResource(
                            R.string.friend_event_participates,
                            friend.name
                        )
                        Switch(
                            checked = friendParticipates == friend.friendUid,
                            modifier = Modifier.semantics { contentDescription = participatesLabel },
                            onCheckedChange = { on ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                friendParticipates = if (on) friend.friendUid else null
                            }
                        )
                    }
                }
            }

            // Privacy Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dims.paddingMedium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall * 1.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.event_form_private_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.event_form_private_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val privateLabel = stringResource(R.string.event_form_private_title)
                    Switch(
                        checked = isPrivate,
                        modifier = Modifier.semantics { contentDescription = privateLabel },
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isPrivate = it
                        }
                    )
                }
            }

            // Event Photo Section
            // A stored photo is drawn only for the family's two parents (L-4); a legacy
            // download URL is not drawn at all, so the section offers to attach one instead.
            val storedPhoto = rememberRecordPhoto(
                reference = existingImageUrl,
                kind = RecordPhotoKind.EVENT,
                recordId = existingEvent?.id ?: eventId.orEmpty(),
                recordFamilyId = existingEvent?.familyId
            )
            EventPhotoSection(
                displayImage = pickedImageUri ?: storedPhoto,
                enabled = !isSaving && !isDeleting,
                onPickPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemovePhoto = {
                    pickedImageUri = null
                    existingImageUrl = null
                }
            )

            // Pickup Confirmation Section (existing events only)
            if (eventId != null) {
                val confirmedBy = existingEvent?.pickupConfirmedBy
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (confirmedBy != null) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dims.paddingMedium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall * 1.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (confirmedBy != null) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.event_form_pickup),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (confirmedBy != null) {
                                        val confirmerName = parentNames.labelFor(confirmedBy)
                                        stringResource(R.string.event_form_pickup_confirmed_by, confirmerName)
                                    } else {
                                        stringResource(R.string.event_form_pickup_not_confirmed)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (confirmedBy != null) {
                                    viewModel.undoPickupConfirmation(eventId)
                                    existingEvent = existingEvent?.copy(
                                        pickupConfirmedBy = null,
                                        pickupConfirmedAt = null
                                    )
                                } else {
                                    viewModel.confirmPickup(eventId)
                                    existingEvent = existingEvent?.copy(
                                        pickupConfirmedBy = parentOwner,
                                        pickupConfirmedAt = LocalDateTime.now()
                                    )
                                }
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (confirmedBy != null) {
                                        R.string.event_form_pickup_undo
                                    } else {
                                        R.string.event_form_pickup_confirm
                                    }
                                )
                            )
                        }
                    }
                }
            }

            if (waitsForCoParent) {
                EventDeadline(
                    start = formStartDateTime(startDate, startTime, allDay),
                    coParentName = parentNames.labelFor(parentOwner),
                    selected = answerBy,
                    onSelect = { answerBy = it }
                )
            }
        }
    }

    // Recurrence end date picker
    if (showRecurrenceEndPicker) {
        LocalDatePickerDialog(
            initialDate = recurrenceEndDate ?: startDate.plusMonths(3),
            confirmLabel = stringResource(R.string.event_form_ok),
            dismissLabel = stringResource(R.string.event_form_cancel),
            onConfirm = { recurrenceEndDate = it },
            onDismiss = { showRecurrenceEndPicker = false }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        LocalDatePickerDialog(
            initialDate = startDate,
            confirmLabel = stringResource(R.string.event_form_ok),
            dismissLabel = stringResource(R.string.event_form_cancel),
            onConfirm = { startDate = it },
            onDismiss = { showDatePicker = false }
        )
    }

    // End date picker: the span is what is kept, so a later start date carries the end along
    if (showEndDatePicker) {
        LocalDatePickerDialog(
            initialDate = startDate.plusDays(endDaySpan.toLong()),
            confirmLabel = stringResource(R.string.event_form_ok),
            dismissLabel = stringResource(R.string.event_form_cancel),
            onConfirm = {
                endDaySpan = endDaySpanFor(startDate, it)
                keepNoEnd = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }

    // Start Time Picker
    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = startTime,
            onTimeSelected = { selectedTime ->
                startTime = selectedTime
                // Auto-adjust end time if it's before start time (on a same-day event: an
                // overnight one ends earlier in the day than it starts)
                if (endDaySpan == 0 && endTime.isBefore(startTime)) {
                    endTime = startTime.plusHours(1)
                }
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    // End Time Picker
    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = endTime,
            onTimeSelected = { selectedTime ->
                endTime = selectedTime
                keepNoEnd = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && eventId != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                }
            },
            title = {
                Text(stringResource(R.string.event_form_delete_dialog_title))
            },
            text = {
                Text(stringResource(R.string.event_form_delete_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            try {
                                val event = viewModel.getEventById(eventId)
                                event?.let {
                                    viewModel.deleteEvent(it)
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.event_form_event_deleted),
                                        duration = SnackbarDuration.Short
                                    )
                                    kotlinx.coroutines.delay(500)
                                    onSave() // Navigate back
                                } ?: run {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.event_form_not_found),
                                        duration = SnackbarDuration.Short
                                    )
                                    isDeleting = false
                                    showDeleteDialog = false
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.event_form_delete_failed),
                                    duration = SnackbarDuration.Long
                                )
                                isDeleting = false
                                showDeleteDialog = false
                            }
                        }
                    },
                    enabled = !isDeleting
                ) {
                    Text(stringResource(R.string.event_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    },
                    enabled = !isDeleting
                ) {
                    Text(stringResource(R.string.event_form_cancel))
                }
            }
        )
    }
}

/**
 * One of the form's two dates, the start and the end: its label, the day with its month in full, and
 * a tap that opens the date picker. One anatomy for both, so the end date reads as the start's twin.
 *
 * @param description What TalkBack reads: the action and the day it would change.
 */
@Composable
private fun EventDateCard(label: String, date: LocalDate, description: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val dims = dimensions()
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp) // Ensure minimum touch target
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.paddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(dims.paddingSmall * 1.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = stringResource(R.string.event_form_cd_calendar_icon),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        // The month in full: Czech abbreviates it to "zář" inside a
                        // date, where a form field has room for "25. září 2026".
                        text = date.format(localizedDate("yMMMMEEEEd")),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.event_form_cd_navigate_date_picker),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * When a new event that waits for the co-parent's answer should be answered by (phase 3a, spec
 * §6.1). The event's start is the cap: the moment the request would change the calendar.
 */
@Composable
private fun EventDeadline(
    start: LocalDateTime,
    coParentName: String,
    selected: AnswerByPick,
    onSelect: (AnswerByPick) -> Unit
) {
    val cap = remember(start) { RequestCap.forEvent(start, System.currentTimeMillis(), ZoneId.systemDefault()) }
    AnswerByField(
        offer = rememberAnswerByOffer(cap),
        selected = selected,
        onSelect = onSelect,
        coParentName = coParentName
    )
}

/**
 * The start the event form saves and validates: [startTime] on [startDate], or the start of
 * [startDate] for an all-day event — the shape [AllDayEvent] reads as all day.
 */
internal fun formStartDateTime(startDate: LocalDate, startTime: LocalTime, allDay: Boolean): LocalDateTime =
    if (allDay) startDate.atStartOfDay() else LocalDateTime.of(startDate, startTime)

/**
 * The end the event form saves and validates: [endTime] on the end date, [endDaySpan] days after
 * [startDate], or none while [keepNoEnd] (an event loaded without an end whose end time and end date
 * the parent has not set). An all-day event ends at the end date's last minute, as the Google and
 * school imports close a day. A new event has a span of 0, so an end at or before its start is still
 * refused rather than read as the next day: an overnight event picks the next day as its end date.
 */
internal fun formEndDateTime(
    startDate: LocalDate,
    endTime: LocalTime,
    endDaySpan: Int,
    keepNoEnd: Boolean,
    allDay: Boolean = false
): LocalDateTime? {
    val endDate = startDate.plusDays(endDaySpan.toLong())
    return when {
        keepNoEnd -> null
        allDay -> endDate.atTime(AllDayEvent.LAST_MINUTE)
        else -> LocalDateTime.of(endDate, endTime)
    }
}

/**
 * How many days [event]'s end date lies after its start date; 0 for a same-day event or no end.
 * An all-day event counts to its last day ([AllDayEvent.lastDay]), so one an older import closed at
 * the next midnight opens on the day it really ends.
 */
internal fun endDaySpanOf(event: Event): Int {
    val lastDay = if (AllDayEvent.isAllDay(event)) {
        AllDayEvent.lastDay(event)
    } else {
        event.endDateTime?.toLocalDate() ?: return 0
    }
    return endDaySpanFor(event.startDateTime.toLocalDate(), lastDay).coerceAtLeast(0)
}

/**
 * The span an end date picked in the form gives an event starting on [startDate]: the days
 * between them. A day before the start gives a negative span on purpose: the end then lies before
 * the start, and the form says so and keeps Save off rather than quietly moving either date.
 */
internal fun endDaySpanFor(startDate: LocalDate, endDate: LocalDate): Int =
    ChronoUnit.DAYS.between(startDate, endDate).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

/**
 * The values the event form edits, compared to tell whether leaving would drop an edit (D-11).
 * [of] mirrors the edit mode's prefill exactly, so an untouched form compares equal.
 */
private data class EventFields(
    val title: String,
    val description: String,
    val parentOwner: String?,
    val eventType: String,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val endDaySpan: Int,
    val allDay: Boolean,
    val recurrencePattern: String?,
    val recurrenceEndDate: LocalDate?,
    val reminderMinutes: Int?,
    val isPrivate: Boolean,
    val isImportant: Boolean,
    val friendParticipates: String?,
    val forMembers: List<FamilyMemberRef>,
    val imageUrl: String?,
    val newImage: String?
) {
    /** The form's values for [event], as the edit mode prefills them. */
    companion object {
        fun of(event: Event): EventFields {
            val start = event.startDateTime.toLocalTime()
            return EventFields(
                title = event.title,
                description = event.description ?: "",
                parentOwner = event.parentOwner,
                eventType = event.eventType,
                startDate = event.startDateTime.toLocalDate(),
                startTime = start,
                endTime = event.endDateTime?.toLocalTime() ?: start.plusHours(1),
                endDaySpan = endDaySpanOf(event),
                allDay = AllDayEvent.isAllDay(event),
                recurrencePattern = if (event.isRecurring) event.recurrencePattern else null,
                recurrenceEndDate = event.recurrenceEndDate,
                reminderMinutes = event.reminderMinutes,
                isPrivate = event.isPrivate,
                isImportant = event.isImportant,
                friendParticipates = event.friendParticipates,
                forMembers = event.forMembers,
                imageUrl = event.imageUrl,
                newImage = null
            )
        }
    }
}

/**
 * Event photo picker/preview. [displayImage] may be a [Uri] (freshly picked) or a
 * `ViewablePhoto` (already attached, fetched as the signed-in parent) — Coil renders both.
 * Shows an attach button when empty, or a preview with change/remove controls when a photo is
 * present.
 */
@Composable
private fun EventPhotoSection(
    displayImage: Any?,
    enabled: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    val dims = dimensions()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dims.paddingSmall)
        ) {
            Text(
                text = stringResource(R.string.event_form_photo),
                style = MaterialTheme.typography.titleSmall
            )
            if (displayImage == null) {
                OutlinedButton(
                    onClick = onPickPhoto,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.size(dims.paddingSmall))
                    Text(stringResource(R.string.event_form_attach_photo))
                }
            } else {
                var viewingPhoto by rememberSaveable { mutableStateOf(false) }
                if (viewingPhoto) {
                    FullScreenImageDialog(
                        model = displayImage,
                        contentDescription = stringResource(R.string.event_form_photo),
                        onDismiss = { viewingPhoto = false }
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = displayImage,
                        contentDescription = stringResource(R.string.image_viewer_open),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { viewingPhoto = true }
                    )
                    FilledTonalIconButton(
                        onClick = onRemovePhoto,
                        enabled = enabled,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(dims.paddingSmall)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.event_form_cd_remove_photo)
                        )
                    }
                }
                TextButton(
                    onClick = onPickPhoto,
                    enabled = enabled
                ) {
                    Text(stringResource(R.string.event_form_change_photo))
                }
            }
        }
    }
}
