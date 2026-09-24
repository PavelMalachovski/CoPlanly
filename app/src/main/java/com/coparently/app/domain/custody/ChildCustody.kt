package com.coparently.app.domain.custody

import com.coparently.app.domain.family.FamilyMemberRef
import java.time.LocalDate

/**
 * Where one child is on a day, for the places that must name children apart (FAM-4).
 *
 * @property childId `ChildInfo.id`
 * @property slot `"mom"` or `"dad"` — rendered as that parent's **name**, never as a colour: a
 *   member is a name (FAM-2), and the parent hues are already spent on the parents.
 */
data class ChildWhereabouts(val childId: String, val slot: String)

/**
 * The per-child half of "whose day is this?" (FAM-4), beside [CustodyResolver] rather than inside
 * it: the family question keeps its one answer, and only the three places FAM-4 names ask this.
 *
 * **It appears at two, never at one** (FAM-1). Every function here answers exactly what the family
 * schedule answers unless the family has at least two children *and* at least one override — a
 * family of one child, or of several who all move together, sees nothing new anywhere.
 */
object ChildCustody {

    /**
     * The override the calendar grid follows: the one belonging to the child the member filter
     * (FAM-3) narrows to, when it narrows to **exactly one member and that member is a child with
     * an override**. Anything else — no filter, two children, a pet, a child with no override —
     * returns null, and the grid keeps the family's custody band.
     */
    fun overrideForFilter(
        filter: List<FamilyMemberRef>,
        overrides: List<ChildScheduleOverride>
    ): ChildScheduleOverride? {
        val child = filter.singleOrNull() as? FamilyMemberRef.Child ?: return null
        return overrides.firstOrNull { it.childId == child.id }
    }

    /**
     * Whose day a date is for the grid: [override]'s answer when there is one, otherwise [family]
     * — `CustodyResolver.resolver`'s result, swaps and layers included — unchanged.
     *
     * An override answers alone: neither the family's accepted swaps nor its seasonal layers move
     * an overridden child (see [ChildScheduleOverride]).
     */
    fun resolver(
        override: ChildScheduleOverride?,
        family: (LocalDate) -> String?
    ): (LocalDate) -> String? {
        if (override == null) return family
        return { date -> override.custodyFor(date) }
    }

    /**
     * The override's contact windows worth drawing — [CustodyResolver.contactWindowsResolver]'s
     * rule, applied to the child's own cycle: a window naming the parent who already has the day
     * is dropped.
     */
    fun contactWindowsResolver(override: ChildScheduleOverride): (LocalDate) -> List<ContactWindow> {
        if (override.contactWindows.isEmpty()) return { emptyList() }
        return { date -> override.contactWindowsOn(date).filter { it.parent != override.custodyFor(date) } }
    }

    /**
     * Where each child is on [date], for Home's handover hero — or an **empty list** when the hero
     * should stay the single family sentence it has always been.
     *
     * Non-empty only when [childIds] holds at least two children, at least one of them has an
     * override, and the children do not all resolve to the same parent that day. A child whose day
     * resolves to nothing is left out; fewer than two answers left is no disagreement to show.
     *
     * @param childIds The family's children, in display order
     * @param overrides The family's readable overrides
     * @param family Whose day a date is under the family schedule, swaps and layers included
     */
    fun whereaboutsOn(
        date: LocalDate,
        childIds: List<String>,
        overrides: List<ChildScheduleOverride>,
        family: (LocalDate) -> String?
    ): List<ChildWhereabouts> {
        if (childIds.size < 2 || overrides.none { it.childId in childIds }) return emptyList()
        val byChild = overrides.associateBy { it.childId }
        val answers = childIds.mapNotNull { id ->
            resolver(byChild[id], family)(date)?.let { ChildWhereabouts(id, it) }
        }
        val disagree = answers.size >= 2 && answers.map { it.slot }.distinct().size > 1
        return if (disagree) answers else emptyList()
    }
}
