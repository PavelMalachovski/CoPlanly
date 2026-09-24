package com.coparently.app.presentation.custody

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.ContactWindowCodec
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.MidweekContact
import com.coparently.app.domain.parentingplan.PlanReference
import com.coparently.app.domain.parentingplan.PlanScheduleTarget
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.FamilyMembersSource
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.parentingplan.PlanReferenceSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** Keeps the parents flow warm across brief unsubscriptions (config changes). */
private const val PARENTS_STOP_TIMEOUT_MS = 5_000L

/** Days in one week of a custom custody pattern. */
private const val DAYS_PER_WEEK = 7

/** Index of the contact Saturday in the fortnight: day 0 is Monday. */
private const val CONTACT_SATURDAY = 5

/** Index of the contact Sunday. */
private const val CONTACT_SUNDAY = 6

/**
 * ViewModel for custody setup screen.
 * Handles custody model selection and configuration.
 *
 * Opened from an agreed parenting-plan answer (MON-21), it also holds that answer as
 * [CustodySetupUiState.planReference]: the screen quotes it above the form, and a save cites it on
 * the proposal. The form itself is filled by the parent, as always — the answer is never parsed.
 *
 * Scoped to one child (FAM-4, [editSchedule]) it is the same editor for that child's own schedule:
 * it opens on their override, or on the family pattern when they have none, and a save becomes
 * their override — a proposal for a paired family, like any pattern change.
 */
@HiltViewModel
class CustodySetupViewModel @Inject constructor(
    private val custodyModelRepository: CustodyModelRepository,
    parentsSource: ParentsSource,
    savedStateHandle: SavedStateHandle,
    private val planReferenceSource: PlanReferenceSource,
    familyMembersSource: FamilyMembersSource
) : ViewModel() {

    /**
     * Signed-in parent and paired co-parent, for resolving a slot to a name — the "starts
     * first" toggle, the week quick-select buttons and the colour-dot legend all name a person.
     */
    val parents: StateFlow<Parents> = parentsSource.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARENTS_STOP_TIMEOUT_MS), Parents())

    private val _uiState = MutableStateFlow(CustodySetupUiState())
    val uiState: StateFlow<CustodySetupUiState> = _uiState.asStateFlow()

    private val _currentModel = MutableStateFlow<CustodyModel?>(null)
    val currentModel: StateFlow<CustodyModel?> = _currentModel.asStateFlow()

    /**
     * The family's children, for "Different schedule for a child" (FAM-4). A stream for what the
     * screen renders; the section itself decides it appears only at two.
     */
    val children: StateFlow<List<FamilyMember>> = familyMembersSource.observe()
        .map { members -> members.filter { it.ref is FamilyMemberRef.Child } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARENTS_STOP_TIMEOUT_MS), emptyList())

    init {
        loadCurrentModel()
        savedStateHandle.get<String>(ARG_PLAN_QUESTION)?.takeIf { it.isNotBlank() }?.let { loadPlanReference(it) }
    }

    /**
     * Loads the agreed answer the editor was opened from, when it is about the base pattern.
     *
     * A holiday answer opens the same screen but belongs to the seasonal-layer editor, which
     * loads it itself; quoting it above the base form would suggest the base Save cites it.
     */
    private fun loadPlanReference(questionId: String) {
        viewModelScope.launch {
            val reference = try {
                planReferenceSource.referenceFor(questionId)
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                // The editor still works without the quote; the proposal simply cites nothing.
                Log.w(TAG, "Could not read the parenting plan answer", e)
                null
            }
            _uiState.update { state ->
                state.copy(planReference = reference?.takeIf { it.target == PlanScheduleTarget.BASE_PATTERN })
            }
        }
    }

    /**
     * Loads the currently active custody model.
     */
    private fun loadCurrentModel() {
        viewModelScope.launch {
            custodyModelRepository.getActiveModel().collect { model ->
                _currentModel.value = model
                model?.let { updateUiFromModel(it.scopedTo(_uiState.value.childScope)) }
            }
        }
    }

    /**
     * Updates UI state from an existing model.
     */
    private fun updateUiFromModel(model: CustodyModel) {
        _uiState.value = _uiState.value.copy(
            selectedModelType = model.modelType,
            startDate = model.startDate,
            momFirst = when (model.modelType) {
                CustodyModelType.WEEK_ON_WEEK_OFF -> model.momDayIndices.contains(0)
                // By majority, not by day 0. This is the one preset that is not a 50/50 split:
                // the resident holds ten to twelve days of the fortnight and the contact parent
                // two to four, so the larger set is the resident's whatever those days are.
                // "Does slot 1 hold day 0" was true only while the contact set was the two
                // weekend days — a midweek contact on a **Monday**, every week, puts index 0
                // with the contact parent and flipped which parent the form reopened as
                // resident, silently offering to save the schedule the other way round.
                CustodyModelType.EVERY_OTHER_WEEKEND -> model.isResidentSlotOne()
                CustodyModelType.TWO_TWO_THREE -> model.momDayIndices.contains(0)
                CustodyModelType.THREE_FOUR_FOUR_THREE -> model.momDayIndices.contains(0)
                CustodyModelType.CUSTOM -> true
            },
            customPatternDays = model.patternDays,
            customMomDays = model.momDayIndices,
            contactWindows = model.contactWindows
        ).let { state ->
            if (model.modelType == CustodyModelType.EVERY_OTHER_WEEKEND) {
                state.withMidweekFrom(model)
            } else {
                state
            }
        }
    }

    /**
     * Scopes the editor to [child]'s own schedule, or back to the family's with null (FAM-4).
     *
     * The form refills from what the new scope holds — the child's override, else the family
     * pattern — so switching never carries one schedule's half-made edits into another's save.
     */
    fun editSchedule(child: FamilyMember?) {
        val childId = (child?.ref as? FamilyMemberRef.Child)?.id
        val scope = if (child != null && childId != null) ChildScope(childId, child.name) else null
        _uiState.value = _uiState.value.copy(childScope = scope)
        _currentModel.value?.let { updateUiFromModel(it.scopedTo(scope)) }
    }

    /**
     * Selects a model type.
     */
    fun selectModelType(type: CustodyModelType) {
        _uiState.value = _uiState.value.copy(
            selectedModelType = type,
            // Reset custom settings when switching away from custom
            customPatternDays = if (type == CustodyModelType.CUSTOM) _uiState.value.customPatternDays else 14,
            customMomDays = if (type == CustodyModelType.CUSTOM) _uiState.value.customMomDays else emptySet()
        ).withWindowsInCycle()
    }

    /**
     * Sets the start date for the pattern.
     */
    fun setStartDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(startDate = date)
    }

    /**
     * Sets whether mom starts first in the pattern.
     */
    fun setMomFirst(momFirst: Boolean) {
        _uiState.value = _uiState.value.copy(momFirst = momFirst)
    }

    /**
     * Sets the number of days in a custom pattern.
     */
    fun setCustomPatternDays(days: Int) {
        val validDays = days.coerceIn(7, 28) // Reasonable range
        _uiState.value = _uiState.value.copy(
            customPatternDays = validDays,
            // Clear mom days that are out of range
            customMomDays = _uiState.value.customMomDays.filter { it < validDays }.toSet()
        ).withWindowsInCycle()
    }

    /**
     * Toggles a day in the custom pattern for mom.
     */
    fun toggleCustomMomDay(dayIndex: Int) {
        val currentDays = _uiState.value.customMomDays.toMutableSet()
        if (currentDays.contains(dayIndex)) {
            currentDays.remove(dayIndex)
        } else {
            currentDays.add(dayIndex)
        }
        _uiState.value = _uiState.value.copy(customMomDays = currentDays)
    }

    /**
     * Assigns every day of the zero-based [week] of the custom pattern to slot 1 ("mom"),
     * leaving the rest of the pattern alone.
     *
     * The "Week N → name" shortcuts used to call [toggleCustomMomDay] per day, so on a partly
     * assigned week they flipped each day instead of assigning the week.
     */
    fun assignCustomWeekToMom(week: Int) {
        val days = (week * DAYS_PER_WEEK) until ((week + 1) * DAYS_PER_WEEK)
        val inPattern = days.filter { it < _uiState.value.customPatternDays }
        _uiState.value = _uiState.value.copy(customMomDays = _uiState.value.customMomDays + inPattern)
    }

    /** Turns the midweek contact day on or off. */
    fun setMidweekEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(midweekEnabled = enabled)
    }

    /**
     * Picks which weekday the midweek contact falls on.
     *
     * A weekend day is ignored rather than refused loudly: the picker only offers Monday to
     * Friday, so reaching here with one would be a programming error, and [MidweekContact]
     * would throw on construction inside the save.
     */
    fun setMidweekDay(day: DayOfWeek) {
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return
        _uiState.value = _uiState.value.copy(midweekDay = day)
    }

    /** Every week, or only the week that has no contact weekend. */
    fun setMidweekEveryWeek(everyWeek: Boolean) {
        _uiState.value = _uiState.value.copy(midweekEveryWeek = everyWeek)
    }

    /**
     * Adds the contact windows [draft] describes (MON-6b) — one per matching day of the cycle,
     * so "every Wednesday" in a fortnight is two windows. An invalid draft, or one no day of the
     * cycle matches, changes nothing: the dialog refuses to confirm the first, and the second
     * would be a window that can never be drawn.
     */
    fun addContactWindows(draft: ContactWindowDraft) {
        val state = _uiState.value
        val added = draft.toWindows(state.startDate, state.cycleDays)
        if (added.isEmpty()) return
        _uiState.value = state.copy(
            contactWindows = ContactWindowCodec.canonical(state.contactWindows + added)
        )
    }

    /** Removes one contact window. */
    fun removeContactWindow(window: ContactWindow) {
        _uiState.value = _uiState.value.copy(contactWindows = _uiState.value.contactWindows - window)
    }

    /**
     * This state without the windows its cycle cannot reach — after the cycle got shorter, or
     * the pattern type changed to one with a different length. Dropped here rather than kept
     * silently, so what the list shows is what will be saved.
     */
    private fun CustodySetupUiState.withWindowsInCycle(): CustodySetupUiState =
        copy(contactWindows = contactWindows.filter { it.dayIndex < cycleDays })

    /**
     * Saves the custody model configuration.
     *
     * Scoped to a child (FAM-4), it saves that child's own schedule instead — or, with
     * [followFamily], sends the child back to the family schedule, which needs no valid form.
     */
    fun save(followFamily: Boolean = false, onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        if (!state.isValid && !followFamily) return

        _uiState.value = state.copy(isLoading = true)

        // Only a family pattern opened from the plan cites it; everything else saves as it always did.
        val citation = state.planReference?.citationWire?.takeIf { state.childScope == null }
        viewModelScope.launch {
            try {
                val submission = custodyModelRepository.submitFor(state, citation, followFamily)
                _uiState.value = state.copy(
                    isLoading = false,
                    isSaved = true,
                    // Item 7: when paired with an agreed schedule the change is a proposal the
                    // co-parent must accept — the screen says so instead of implying it applied.
                    proposedForApproval = submission == PatternSubmission.PROPOSED
                )
                onSuccess()
            } catch (e: Exception) {
                // The exception's own text is English and technical: it goes to the log.
                Log.w(TAG, "Saving the custody model failed", e)
                _uiState.value = state.copy(
                    isLoading = false,
                    error = UiText.Res(R.string.custody_setup_save_failed)
                )
            }
        }
    }

    /**
     * Clears any error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val TAG = "CustodySetupViewModel"

        /** The navigation argument naming the plan question the editor was opened from (MON-21). */
        const val ARG_PLAN_QUESTION = "planQuestion"
    }
}

/**
 * Reads a saved every-other-weekend model's midweek day back into the form.
 *
 * The model stores only which fortnight indices belong to slot 1, so the midweek day is
 * recovered rather than stored: take the contact parent's days, drop the two weekend
 * indices, and whatever weekday is left is the one that was chosen. Re-opening the screen
 * has to show the schedule the family actually has — a form that silently reset the toggle
 * would turn "save" into "remove the midweek day".
 */
private fun CustodySetupUiState.withMidweekFrom(model: CustodyModel): CustodySetupUiState {
    val residentIsSlotOne = model.isResidentSlotOne()
    val contactDays = if (residentIsSlotOne) {
        (0 until model.patternDays).toSet() - model.momDayIndices
    } else {
        model.momDayIndices
    }
    val midweekIndices = contactDays - setOf(CONTACT_SATURDAY, CONTACT_SUNDAY)
    val day = midweekIndices.minOrNull()?.let { DayOfWeek.of((it % DAYS_PER_WEEK) + 1) }
        ?: return copy(midweekEnabled = false)
    return copy(
        midweekEnabled = true,
        midweekDay = day,
        midweekEveryWeek = midweekIndices.size > 1
    )
}

/**
 * Whether slot 1 is the parent the child lives with, for a resident/contact pattern.
 *
 * By share of the fortnight, which is the definition, rather than by any particular index:
 * every index the contact parent holds is negotiable — the two weekend days plus an optional
 * midweek day that may itself be a Monday — while "holds most of the cycle" is exactly what
 * makes a parent the resident one. Only meaningful for a pattern that is not a 50/50 split.
 *
 * Counts only indices the pattern can reach, the stance `CustodyModel.complemented` already
 * takes: `getCustodyFor` reduces a date modulo `patternDays` and never sees an index outside
 * the cycle, so counting one would answer about days the calendar does not paint — and could
 * invert the form for a document written by an older or foreign build, which is the exact
 * failure this function exists to prevent. It also keeps a zero `patternDays` from making an
 * empty set the majority.
 */
private fun CustodyModel.isResidentSlotOne(): Boolean =
    momDayIndices.count { it in 0 until patternDays } * 2 > patternDays

/**
 * UI state for custody setup screen.
 */
data class CustodySetupUiState(
    val selectedModelType: CustodyModelType = CustodyModelType.WEEK_ON_WEEK_OFF,
    val startDate: LocalDate = LocalDate.now(),
    val momFirst: Boolean = true,
    val customPatternDays: Int = 14,
    val customMomDays: Set<Int> = emptySet(),
    /**
     * Whether `výhradní péče se stykem` also gives the contact parent a midweek day.
     *
     * Off by default: a midweek day here is a **whole** day, overnight included, because the
     * model assigns a date to exactly one parent. Most orders say "afternoon". Defaulting it on
     * would hand over an overnight nobody agreed to.
     */
    val midweekEnabled: Boolean = false,
    /** Which weekday the midweek contact falls on. Monday to Friday. */
    val midweekDay: DayOfWeek = DayOfWeek.WEDNESDAY,
    /** True for both weeks of the fortnight; false for the week without the contact weekend. */
    val midweekEveryWeek: Boolean = true,
    /**
     * Contact windows on top of the whole days (MON-6b), for every pattern type. Not a
     * replacement for the midweek day above, and not converted from it: that one is a whole day
     * with the overnight, and a saved schedule that has one keeps it exactly as it was.
     */
    val contactWindows: List<ContactWindow> = emptyList(),
    /**
     * The agreed parenting-plan answer this editor was opened from (MON-21), quoted above the
     * form and cited on the proposal a save makes; null when opened any other way.
     */
    val planReference: PlanReference? = null,
    /** The child this editor is scoped to (FAM-4), or null while it edits the family schedule. */
    val childScope: ChildScope? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    /** True when the save was sent to the co-parent as a proposal rather than applied. */
    val proposedForApproval: Boolean = false,
    /** Why the last save failed, resolved by the screen (CQ-14); null when nothing failed. */
    val error: UiText? = null
) {
    /**
     * Validates the current state.
     */
    val isValid: Boolean
        get() = when (selectedModelType) {
            // A child's own schedule may give every day to slot 2 — "always with the other parent"
            // is the ordinary case there (FAM-4); the family pattern still needs one slot-1 day.
            CustodyModelType.CUSTOM -> customPatternDays > 0 && (customMomDays.isNotEmpty() || childScope != null)
            else -> true
        }

    /**
     * The midweek contact this state describes, or null when the pattern has none.
     *
     * Null for every model other than [CustodyModelType.EVERY_OTHER_WEEKEND] even when the flag
     * happens to be set: switching model type must not smuggle a midweek day into a pattern that
     * has no notion of one.
     */
    val midweek: MidweekContact?
        get() = if (selectedModelType == CustodyModelType.EVERY_OTHER_WEEKEND && midweekEnabled) {
            MidweekContact(midweekDay, midweekEveryWeek)
        } else {
            null
        }

    /**
     * The slot that starts the pattern, and the slot that follows it.
     *
     * Slots, not names and not text: this is a ViewModel, it has no `Context`, and the preview
     * sentence is a localized resource the screen formats with the parents' actual names. It
     * used to be a hardcoded English string here that said "Mom" and "Dad" outright, on the one
     * screen this branch rewrote to show names — which is exactly the sentence a reader would
     * have trusted least, sitting two rows under "Starts first: Olya".
     */
    val firstSlot: String get() = if (momFirst) "mom" else "dad"
    val secondSlot: String get() = if (momFirst) "dad" else "mom"

    /**
     * Days in the cycle the selected pattern repeats on: the custom length, or the fortnight
     * every preset is built on. What a contact window's day index is a position in.
     */
    val cycleDays: Int
        get() = if (selectedModelType == CustodyModelType.CUSTOM) customPatternDays else PRESET_CYCLE_DAYS

    private companion object {
        /** Every preset pattern repeats over a fortnight. */
        const val PRESET_CYCLE_DAYS = 14
    }
}
