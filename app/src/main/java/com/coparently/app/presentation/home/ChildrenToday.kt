package com.coparently.app.presentation.home

import com.coparently.app.domain.custody.ChildCustody
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.presentation.common.FamilyMember
import java.time.LocalDate

/**
 * One child, named with the parent they are with today (FAM-4).
 *
 * @property childName What the parents call the child — their own words, never translated.
 * @property slot `"mom"` or `"dad"`; the hero renders it as that parent's **name**, never as a
 *   colour, because a member is a name (FAM-2) and the parent hues already mean the parents.
 */
data class ChildWithParent(val childName: String, val slot: String)

/**
 * The lines Home's handover hero adds on a day the children are not all with the same parent.
 *
 * Empty — the hero stays the one family sentence it has always been — unless the family has at
 * least two children, at least one of them follows a schedule of their own, and that puts them
 * with different parents today: [ChildCustody.whereaboutsOn] holds the rule. The family half is
 * `CustodyResolver`'s, swaps included, with no legacy fallback — the same lookup the hero's own
 * handover walk uses, so the two parts of the card cannot disagree.
 */
internal object ChildrenToday {

    /**
     * @param today The day the hero is about.
     * @param model The agreed pattern with its overrides, or null.
     * @param overrides The family's one-off swaps.
     * @param members The family's children and pets, in display order; pets are ignored.
     */
    fun of(
        today: LocalDate,
        model: CustodyModel?,
        overrides: Map<String, DayOverride>,
        members: List<FamilyMember>
    ): List<ChildWithParent> {
        val children = members.mapNotNull { member ->
            (member.ref as? FamilyMemberRef.Child)?.let { it.id to member.name }
        }
        val names = children.toMap()
        val family = CustodyResolver.resolver(model, overrides, legacy = { null })
        return ChildCustody.whereaboutsOn(today, children.map { it.first }, model?.childOverrides.orEmpty(), family)
            .mapNotNull { where -> names[where.childId]?.let { ChildWithParent(it, where.slot) } }
    }
}
