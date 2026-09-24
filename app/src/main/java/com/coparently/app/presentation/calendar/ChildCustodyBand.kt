package com.coparently.app.presentation.calendar

import com.coparently.app.domain.custody.ChildCustody
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import java.time.LocalDate

/**
 * The three custody questions the grid asks, bound once.
 *
 * @property custody Whose day a date is — the band and the handover diagonal.
 * @property proposed What a pending proposal would make of a date, or null — the preview overlay.
 * @property windows The contact windows worth drawing on a date.
 * @property followsFamily False while the band is one child's own schedule (FAM-4). The family's
 *   one-off swaps then neither move the band nor can be offered from it: a swap is offered and
 *   answered against the family schedule, so the grid must not mark or start one over a band
 *   that is not that schedule.
 */
internal data class GridCustody(
    val custody: (LocalDate) -> String?,
    val proposed: (LocalDate) -> String?,
    val windows: (LocalDate) -> List<ContactWindow>,
    val followsFamily: Boolean = true
)

/**
 * Which custody band the grid draws (FAM-4).
 *
 * The family schedule — [family], built from `CustodyResolver` as always — unless the member
 * filter (FAM-3) narrows to **exactly one child who follows a schedule of their own**; then that
 * child's. Anything else — no filter, two children, a pet, a child with no override — keeps the
 * family band, so a family with one child, or whose children all move together, sees the grid it
 * always saw. No new colour either way: the band is still a parent's tint.
 */
internal object ChildCustodyBand {

    /**
     * @param model The agreed pattern, carrying the children's overrides, or null.
     * @param proposal The pattern a pending proposal offers, or null.
     * @param filter The members the calendar is narrowed to.
     * @param family The family's answers, from `CustodyResolver`.
     */
    fun of(
        model: CustodyModel?,
        proposal: CustodyModel?,
        filter: List<FamilyMemberRef>,
        family: GridCustody
    ): GridCustody {
        val override = ChildCustody.overrideForFilter(filter, model?.childOverrides.orEmpty()) ?: return family
        // A proposal that drops this child's override previews the family pattern it proposes.
        val proposed: (LocalDate) -> String? = if (proposal == null) {
            { _ -> null }
        } else {
            ChildCustody.resolver(proposal.childOverrideFor(override.childId)) { date -> proposal.getCustodyFor(date) }
        }
        return GridCustody(
            custody = ChildCustody.resolver(override, family.custody),
            proposed = proposed,
            windows = ChildCustody.contactWindowsResolver(override),
            followsFamily = false
        )
    }
}
