package com.coparently.app.presentation.common

import com.coparently.app.domain.family.FamilyMemberRef

/**
 * [ref] added to this list, or removed when it is already there.
 *
 * The toggle every caller of `FamilyMemberChips` needs, in one place so none of them reaches for
 * a `MutableList` and mutates state Compose is holding. In `:shared-ui` since MON-24 phase 2 (Task
 * 10, slice 10d), out of the app's `FamilyMemberChips.kt`, because `ExpenseViewModel`'s member
 * filter toggles through it too.
 */
fun List<FamilyMemberRef>.toggling(ref: FamilyMemberRef): List<FamilyMemberRef> =
    if (contains(ref)) filterNot { it == ref } else this + ref
