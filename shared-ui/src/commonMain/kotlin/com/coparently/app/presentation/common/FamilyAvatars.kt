package com.coparently.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.family.names
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_filter_member_toggle
import com.coparently.app.resources.calendar_settings
import com.coparently.app.resources.children_avatars_description
import com.coparently.app.resources.children_avatars_more
import org.jetbrains.compose.resources.stringResource

/**
 * The first row of Home and of the Calendar: [leading] (the wordmark, the month) and then, at the
 * row's end, [FamilyAvatarsAndGear]. One height and one padding on both screens, so the avatars and
 * the gear stand still when the parent switches between the two tabs (owner, 24 November 2026).
 * Tight on purpose (tester feedback, 12 November 2026): every dp the Calendar's header gives up goes
 * to the month grid; the gear's own padding stands in for the end margin.
 *
 * @param modifier Modifier for the row
 * @param leading What stands before the avatars, given the row's scope so it can take the width
 * @param trailing The avatars and the gear
 */
@Composable
fun AppHeaderRow(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(APP_HEADER_HEIGHT)
            .padding(start = Spacing.L, end = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        trailing()
    }
}

/** The height of [AppHeaderRow], the same on Home and the Calendar. */
val APP_HEADER_HEIGHT = 56.dp

/**
 * The family's children and pets as overlapping avatars, then the settings gear: the right end of
 * Home's wordmark row ([BrandHeader]) and of the Calendar's header, one composable so the two draw
 * the same avatars at the same size and the same gear (owner, 24 November 2026: Home showed the
 * children alone and larger, the Calendar the pets too).
 *
 * Every avatar takes a [ITEM] slot, 40 dp, the Calendar's touch target, and the slots overlap by
 * [OVERLAP], so the 30 dp avatars in their 2 dp ring overlap a little, on both screens alike.
 *
 * With a [filter] (the Calendar) and two members or more, each avatar is a toggle of the member
 * filter, ringed in `primary` while chosen, and more than [MAX_VISIBLE] scroll within the room three
 * take. Otherwise (Home, or a family of one member: FAM-1, a picker for a set of one is design item
 * 8 in miniature) they are a picture of the family, at most [MAX_VISIBLE] slots with a "+n" disc in
 * the last for the rest. Either way the block is as wide on both screens.
 *
 * @param members The children, then the pets, in display order
 * @param onSettings Opens Settings, or null to draw no gear
 * @param filter The member filter the avatars toggle, or null for avatars that only show
 */
@Composable
fun FamilyAvatarsAndGear(
    members: List<FamilyMember>,
    onSettings: (() -> Unit)?,
    filter: MemberFilter? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (filter != null && members.size >= 2) FilterAvatars(members, filter) else PictureAvatars(members)
        onSettings?.let { onClick ->
            IconButton(onClick = onClick) {
                AppIcon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.calendar_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The member filter the Calendar's avatars toggle.
 *
 * @property selected The members the grid is narrowed to; none means everyone
 * @property onToggle Adds a member to the filter or takes them out of it
 */
data class MemberFilter(
    val selected: List<FamilyMemberRef>,
    val onToggle: (FamilyMemberRef) -> Unit
)

@Composable
private fun FilterAvatars(members: List<FamilyMember>, filter: MemberFilter) {
    Row(
        modifier = Modifier
            .widthIn(max = ITEM * MAX_VISIBLE - OVERLAP * (MAX_VISIBLE - 1))
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(-OVERLAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        members.forEach { member ->
            val chosen = filter.selected.names(member.ref)
            val description = stringResource(Res.string.calendar_filter_member_toggle, member.name)
            Box(
                modifier = Modifier
                    .size(ITEM)
                    .clip(CircleShape)
                    .toggleable(value = chosen, role = Role.Checkbox, onValueChange = { filter.onToggle(member.ref) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) { RingedAvatar(member, chosen) }
        }
    }
}

@Composable
private fun PictureAvatars(members: List<FamilyMember>) {
    if (members.isEmpty()) return
    val shown = if (members.size > MAX_VISIBLE) members.take(MAX_VISIBLE - 1) else members
    val overflow = members.size - shown.size
    val description = stringResource(Res.string.children_avatars_description, members.joinToString(", ") { it.name })
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(-OVERLAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        shown.forEach { member ->
            Box(modifier = Modifier.size(ITEM), contentAlignment = Alignment.Center) { RingedAvatar(member, false) }
        }
        if (overflow > 0) {
            Box(modifier = Modifier.size(ITEM), contentAlignment = Alignment.Center) {
                Ring(chosen = false) {
                    InitialDisc(stringResource(Res.string.children_avatars_more, overflow))
                }
            }
        }
    }
}

/** [member]'s avatar in its ring: `primary` while [chosen], else the surface it stands on. */
@Composable
private fun RingedAvatar(member: FamilyMember, chosen: Boolean) {
    Ring(chosen) {
        val child = member.child
        if (child != null) ChildAvatar(child, AVATAR) else InitialDisc(member.name.trim().take(1).uppercase())
    }
}

@Composable
private fun Ring(chosen: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = RING,
                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = CircleShape
            )
            .padding(RING)
    ) { content() }
}

/** A pet's initial, or the "+n" of the members not shown, on a tonal disc. */
@Composable
private fun InitialDisc(text: String) {
    Box(
        modifier = Modifier
            .size(AVATAR)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val style = MaterialTheme.typography.titleSmall
        CappedFontScale(max = fontScaleToFit(AVATAR, style.fontSize, INITIAL_SHARE)) {
            Text(text = text, style = style, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** How many avatars show before the rest scroll (a filter) or become "+n" (a picture). */
private const val MAX_VISIBLE = 3

/** An avatar, the ring around it and the slot it takes: the slot is the Calendar's touch target. */
private val AVATAR = 30.dp
private val RING = 2.dp
private val ITEM = 40.dp

/** How much neighbouring slots overlap: the 34 dp ringed avatars then overlap by 6 dp. */
private val OVERLAP = 12.dp
