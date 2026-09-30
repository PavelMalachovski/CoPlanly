package com.coparently.app.presentation.calendar

import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.presentation.common.FamilyMember
import java.util.Locale

/**
 * Who an event is about, as its chip in Day and Week view marks it (FAM-5, owner decision
 * 2026-09-30).
 *
 * A small neutral disc at the start of the chip carrying a member's initial — never a colour:
 * pink and blue are the parent slots, teal is a calendar friend, grey is the weekend, and a member
 * is a name (CLAUDE.md, `FamilyMemberRef`). The chip's description names every member, so a
 * screen reader hears what the letter abbreviates.
 *
 * @property initial The first member's first letter, upper-cased, followed by "+" when the event
 *   names more than one member ("E", "E+"). One letter and a sign rather than several letters:
 *   a week column is about fifty dp wide and the title must keep what room there is.
 * @property names Every named member, in the order the event lists them, for the description.
 */
data class EventMemberMark(
    val initial: String,
    val names: List<String>
) {
    /** How the mark is worked out; pure, so the JVM tests reach every rule. */
    companion object {
        /** A family needs this many members before any chip is marked — "appears at two". */
        const val MIN_FAMILY_MEMBERS = 2

        /** Appended to the initial when the event names more than one member. */
        const val MORE_SUFFIX = "+"

        /**
         * The mark for an event naming [forMembers] in a family of [members], or null when there
         * is nothing to mark:
         * - a family of fewer than [MIN_FAMILY_MEMBERS] — with one child every event is about that
         *   child, and a mark on each would say nothing (the "appears at two, never at one" rule);
         * - an event naming nobody, which is about the whole family, not about everybody;
         * - references this family does not hold (a deleted child, or `FamilyMemberRef.Unknown`
         *   from a newer build), which have no name to show — they are kept on the record, only
         *   not drawn.
         */
        fun of(
            forMembers: List<FamilyMemberRef>,
            members: List<FamilyMember>,
            locale: Locale = Locale.getDefault()
        ): EventMemberMark? {
            if (members.size < MIN_FAMILY_MEMBERS || forMembers.isEmpty()) return null
            val byRef = members.associateBy { it.ref }
            val names = forMembers.distinct()
                .mapNotNull { ref -> byRef[ref]?.name?.trim()?.takeIf { it.isNotEmpty() } }
            val first = names.firstOrNull() ?: return null
            val letter = String(Character.toChars(first.codePointAt(0))).uppercase(locale)
            val initial = if (names.size > 1) letter + MORE_SUFFIX else letter
            return EventMemberMark(initial = initial, names = names)
        }
    }
}
