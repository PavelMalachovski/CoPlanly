package com.coparently.app.presentation.changerequests

import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.custody.ChildScheduleOverride
import com.coparently.app.domain.custody.swapChildren
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.common.FamilyMembersSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/**
 * The children a day swap of the family on screen names, by first name (meeting of 25 November
 * 2026, item 3): [swapChildren] over the children [FamilyView.counts] places in that family alone.
 *
 * The children's list a screen draws ([FamilyMembersSource]) also holds this parent's own records of
 * no family, placed "Only me" (CLAUDE.md item 46), which go to nobody: their names must not ride in a
 * push to the co-parent — with two families, perhaps the other family's parent — nor be said to move
 * in a swap they are not part of. So the swap's sentence and its push name only the family's own
 * children, by the rule the family's totals use.
 *
 * @param children The children as the screens list them, in display order.
 * @param view Where each record stands for the family on screen.
 * @param overrides The children's own schedules from the family's custody model.
 */
internal fun swapChildrenOnScreen(
    children: List<ChildInfo>,
    view: FamilyView,
    overrides: List<ChildScheduleOverride>
): List<String> =
    swapChildren(children.filter { view.counts(it.familyId, it.createdByFirebaseUid) }, overrides)

/**
 * Where the calendar's offer and the requests' screens read the children a swap names
 * ([swapChildrenOnScreen]), so the sender's push and the reader's sentence apply one rule. Room
 * only — the children, the signed-in row and the active custody model — and no listener of its own.
 */
class SwapChildrenSource(
    private val members: FamilyMembersSource,
    private val views: FamilyViewSource,
    private val custody: CustodyModelRepository
) {
    /**
     * The names, re-derived as the children, the family on screen or [overrides] change; null while
     * [overrides] is null (the family's custody document not read yet).
     */
    fun observe(overrides: Flow<List<ChildScheduleOverride>?>): Flow<List<String>?> =
        combine(members.observeChildren(), views.observe(), overrides) { children, view, own ->
            own?.let { swapChildrenOnScreen(children, view, it) }
        }.distinctUntilChanged()

    /**
     * The names at the moment of an offer, for its push: one read of each, never a shared stream's
     * `.value` (CLAUDE.md item 17). With none, the push is worded as it always was.
     */
    suspend fun now(): List<String> = swapChildrenOnScreen(
        members.observeChildren().first(),
        views.observe().first(),
        custody.getActiveModelSync()?.childOverrides.orEmpty()
    )
}
