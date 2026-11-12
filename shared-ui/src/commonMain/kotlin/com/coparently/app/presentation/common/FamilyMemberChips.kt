package com.coparently.app.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.family.names
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Who is this about" — one chip per child and per pet, multi-select.
 *
 * **Renders nothing for a family with fewer than two members**, and the gate lives here rather
 * than at each call site so it cannot be forgotten by the next screen that grows one. A picker
 * for a set of one asks a question with a single answer, which is design item 8 in miniature:
 * an affordance that promises a choice the family does not have. A family with one child and no
 * pets must see the screen they saw before this existed.
 *
 * Children and pets share one strip because a visit to the vet and a visit to the dentist are
 * the same shape of thing — see [FamilyMemberRef] for why they also share one stored reference.
 *
 * **Nothing selected means "everyone".** On a form that is an untagged record; on a filter it is
 * the unfiltered list. Deselecting the last chip is therefore always a way back out, and no
 * "All" chip is needed to provide one.
 *
 * Deliberately not colour-coded. Pink and blue identify the two parents, teal a calendar friend
 * and neutral grey the weekend; a fifth colour channel would break what
 * `presentation/calendar/DayCellFills.kt` exists to protect. A member is their name.
 *
 * @param members Everyone this family cares for, from [FamilyMembersSource].
 * @param selected The references currently chosen.
 * @param onToggle Called with the chip's reference; the caller adds or removes it.
 * @param label Sits above the strip, so the chips are not an unexplained row of names.
 */
@Composable
fun FamilyMemberChips(
    members: List<FamilyMember>,
    selected: List<FamilyMemberRef>,
    onToggle: (FamilyMemberRef) -> Unit,
    label: StringResource,
    modifier: Modifier = Modifier
) {
    if (members.size < 2) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(text = stringResource(label), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            MemberChips(members, selected, onToggle, avatarSize = IconSizes.Standard)
        }
    }
}

/**
 * The same chips as [FamilyMemberChips], as a **filter above a list or a grid**: the label leads
 * the one scrolling row instead of taking a line of its own, so the strip costs the screen one
 * line (docs/AUDIT-2026-10-design.md D-3). Above the calendar the two lines were part of what
 * pushed the first hour a third of the way down the screen. Renders nothing below two members,
 * for the reason [FamilyMemberChips] gives.
 *
 * @param members Everyone this family cares for, from [FamilyMembersSource].
 * @param selected The references currently chosen; none means "everyone".
 * @param onToggle Called with the chip's reference; the caller adds or removes it.
 * @param label Leads the row, so the chips are not an unexplained row of names.
 * @param compact Draws the chips in a 40 dp row with smaller faces, where every dp of height goes
 *   to something else (the calendar, whose grid takes what the strip leaves).
 */
@Composable
fun FamilyMemberFilterStrip(
    members: List<FamilyMember>,
    selected: List<FamilyMemberRef>,
    onToggle: (FamilyMemberRef) -> Unit,
    label: StringResource,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (members.size < 2) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (compact) {
            // A chip's own height is 32 dp; Material pads its touch target to 48. Here the row
            // gives up 8 of those, so the grid below grows (tester feedback, 12 November 2026).
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides COMPACT_CHIP_TARGET) {
                MemberChips(members, selected, onToggle, avatarSize = IconSizes.Small)
            }
        } else {
            MemberChips(members, selected, onToggle, avatarSize = IconSizes.Standard)
        }
    }
}

/** The compact strip's touch target, between a chip's 32 dp and Material's 48. */
private val COMPACT_CHIP_TARGET = 40.dp

/** One multi-select chip per member, named, never coloured. */
@Composable
private fun MemberChips(
    members: List<FamilyMember>,
    selected: List<FamilyMemberRef>,
    onToggle: (FamilyMemberRef) -> Unit,
    avatarSize: Dp
) {
    members.forEach { member ->
        FilterChip(
            selected = selected.names(member.ref),
            onClick = { onToggle(member.ref) },
            label = { Text(member.name) },
            leadingIcon = member.child?.let { child -> { ChildAvatar(child, avatarSize) } }
        )
    }
}
