package com.coparently.app.presentation.custody

import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType

/**
 * The child the custody editor is scoped to (FAM-4), or none while it edits the family schedule.
 *
 * @property childId `ChildInfo.id`.
 * @property name What the parents call the child, for the title and the hint.
 */
data class ChildScope(val childId: String, val name: String)

/**
 * The pattern this form describes, whatever it is about — the family or one child.
 *
 * One construction for the preview, the family save and a child's save, so the three cannot come
 * to describe different fortnights. A custom pattern with no slot-1 day is still a pattern here —
 * "always with the other parent" is the ordinary case for a child's own schedule — and the
 * preview decides separately whether it has anything to draw.
 */
internal fun CustodySetupUiState.toPatternModel(id: String): CustodyModel = when (selectedModelType) {
    CustodyModelType.WEEK_ON_WEEK_OFF -> CustodyModel.weekOnWeekOff(id, startDate, momFirst)
    CustodyModelType.EVERY_OTHER_WEEKEND -> CustodyModel.everyOtherWeekend(id, startDate, momFirst, midweek)
    CustodyModelType.TWO_TWO_THREE -> CustodyModel.twoTwoThree(id, startDate, momFirst)
    CustodyModelType.THREE_FOUR_FOUR_THREE -> CustodyModel.threeFourFourThree(id, startDate, momFirst)
    CustodyModelType.CUSTOM -> CustodyModel.custom(id, startDate, customPatternDays, customMomDays)
}

/**
 * The form as [childId]'s own schedule: its days, its anchor, and the contact windows its cycle
 * can reach. No type is stored — an override is a cycle, not a preset — so it reopens as a
 * custom pattern with the same days ([scopedTo]).
 */
internal fun CustodySetupUiState.toChildOverride(childId: String): ChildScheduleOverride {
    val model = toPatternModel(childId)
    return ChildScheduleOverride(
        childId = childId,
        patternDays = model.patternDays,
        momDayIndices = model.momDayIndices.filter { it in 0 until model.patternDays }.toSet(),
        startDate = model.startDate,
        contactWindows = contactWindows.filter { it.dayIndex < model.patternDays }
    )
}

/**
 * What the editor opens on for [scope]: the child's own schedule as a custom pattern when they
 * have one, otherwise this family pattern — the natural place to start changing it from.
 */
internal fun CustodyModel.scopedTo(scope: ChildScope?): CustodyModel {
    val override = scope?.let { childOverrideFor(it.childId) } ?: return this
    return CustodyModel.custom(
        id = id,
        startDate = override.startDate,
        patternDays = override.patternDays,
        momDayIndices = override.momDayIndices
    ).copy(contactWindows = override.contactWindows)
}

/**
 * Sends what the editor holds the way it has to go.
 *
 * Scoped to a child, it is that child's override — or, with [followFamily], the removal of it —
 * through `submitChildOverride`, which makes it a proposal for a paired family like any pattern
 * change. Otherwise it is the family pattern, cited to the plan answer when it was opened from one.
 *
 * @throws IllegalStateException when a child's schedule is saved with no family schedule to
 *   override; the ViewModel reports it as a failed save.
 */
internal suspend fun CustodyModelRepository.submitFor(
    state: CustodySetupUiState,
    citation: String?,
    followFamily: Boolean
): PatternSubmission {
    val scope = state.childScope
    if (scope != null) {
        val override = if (followFamily) null else state.toChildOverride(scope.childId)
        return checkNotNull(submitChildOverride(scope.childId, override)) { "No family schedule to override" }
    }
    return when (state.selectedModelType) {
        CustodyModelType.WEEK_ON_WEEK_OFF ->
            createWeekOnWeekOff(state.startDate, state.momFirst, state.contactWindows, citation)
        CustodyModelType.EVERY_OTHER_WEEKEND ->
            createEveryOtherWeekend(state.startDate, state.momFirst, state.midweek, state.contactWindows, citation)
        CustodyModelType.TWO_TWO_THREE ->
            createTwoTwoThree(state.startDate, state.momFirst, state.contactWindows, citation)
        CustodyModelType.THREE_FOUR_FOUR_THREE ->
            createThreeFourFourThree(state.startDate, state.momFirst, state.contactWindows, citation)
        CustodyModelType.CUSTOM -> createCustom(
            state.startDate,
            state.customPatternDays,
            state.customMomDays,
            state.contactWindows,
            citation
        )
    }
}
