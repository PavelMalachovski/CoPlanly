package com.coparently.app.presentation.custody

import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.ContactWindowCodec
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType

/** The id a schedule built only to be compared carries; a save mints its own. */
private const val CANDIDATE_ID = "candidate"

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
 * The schedule a save of this form would put to the co-parent, built as the repository builds it,
 * so the deadline block can cap the proposal before it is sent (phase 3a, spec §4.2) — or null when
 * a save would have nothing to send: a child's own schedule with no family schedule [active] under it.
 *
 * The family pattern is the one `createXxx` builds — [toPatternModel]'s factories, with the contact
 * windows its cycle can reach in canonical order, as `withWindows` keeps them — carrying [active]'s
 * seasonal layers and children's schedules, unreadable entries included, as `withActiveLayers`
 * carries them. A child's schedule is [active] with that child's override replaced, as
 * `submitChildOverride` builds it.
 *
 * @param active The agreed schedule of the family on screen, or null when it has none.
 */
internal fun CustodySetupUiState.candidateModel(active: CustodyModel?): CustodyModel? {
    val scope = childScope
    if (scope != null) {
        return active?.let { agreed ->
            val others = agreed.childOverrides.filterNot { it.childId == scope.childId }
            agreed.copy(childOverrides = others + toChildOverride(scope.childId))
        }
    }
    val pattern = toPatternModel(CANDIDATE_ID)
    return pattern.copy(
        contactWindows = ContactWindowCodec.canonical(contactWindows.filter { it.dayIndex < pattern.patternDays }),
        seasonalLayers = active?.seasonalLayers.orEmpty(),
        unreadableLayers = active?.unreadableLayers.orEmpty(),
        childOverrides = active?.childOverrides.orEmpty(),
        unreadableChildOverrides = active?.unreadableChildOverrides.orEmpty()
    )
}

/**
 * Sends what the editor holds the way it has to go.
 *
 * Scoped to a child, it is that child's override — or, with [followFamily], the removal of it —
 * through `submitChildOverride`, which makes it a proposal for a paired family like any pattern
 * change. Otherwise it is the family pattern, cited to the plan answer when it was opened from one.
 *
 * @param answerBy The deadline the parent chose for the co-parent's answer, or null when the form
 *   showed no deadline block; it applies only when the save is a proposal (phase 3a).
 * @throws IllegalStateException when a child's schedule is saved with no family schedule to
 *   override; the ViewModel reports it as a failed save.
 */
internal suspend fun CustodyModelRepository.submitFor(
    state: CustodySetupUiState,
    citation: String?,
    followFamily: Boolean,
    answerBy: AnswerByPick?
): PatternSubmission {
    val scope = state.childScope
    if (scope != null) {
        val override = if (followFamily) null else state.toChildOverride(scope.childId)
        return checkNotNull(submitChildOverride(scope.childId, override, answerBy)) { "No family schedule to override" }
    }
    val windows = state.contactWindows
    return when (state.selectedModelType) {
        CustodyModelType.WEEK_ON_WEEK_OFF ->
            createWeekOnWeekOff(state.startDate, state.momFirst, windows, citation, answerBy)
        CustodyModelType.EVERY_OTHER_WEEKEND ->
            createEveryOtherWeekend(state.startDate, state.momFirst, state.midweek, windows, citation, answerBy)
        CustodyModelType.TWO_TWO_THREE ->
            createTwoTwoThree(state.startDate, state.momFirst, windows, citation, answerBy)
        CustodyModelType.THREE_FOUR_FOUR_THREE ->
            createThreeFourFourThree(state.startDate, state.momFirst, windows, citation, answerBy)
        CustodyModelType.CUSTOM -> createCustom(
            state.startDate,
            state.customPatternDays,
            state.customMomDays,
            windows,
            citation,
            answerBy
        )
    }
}
