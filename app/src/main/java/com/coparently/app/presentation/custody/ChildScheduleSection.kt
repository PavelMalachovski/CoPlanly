package com.coparently.app.presentation.custody

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.presentation.common.FamilyMember
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.dimensions

/**
 * "Different schedule for a child" (FAM-4), under the family's own editor.
 *
 * **It appears at two, never at one** (FAM-1): with a single child the family schedule *is* that
 * child's, and a row offering to override it would be a picker for a set of one. One row per
 * child, saying whether they follow the family schedule or one of their own; a tap opens the same
 * pattern editor this screen is, scoped to that child (`CustodySetupViewModel.editSchedule`).
 *
 * @param children The family's children, in display order. Pets are not offered: a custody
 *   schedule is about children.
 * @param childrenWithOwnSchedule Ids of the children who already follow a schedule of their own.
 * @param onEdit Opens the editor on one child.
 */
@Composable
fun ChildSchedulesSection(
    children: List<FamilyMember>,
    childrenWithOwnSchedule: Set<String>,
    onEdit: (FamilyMember) -> Unit
) {
    val kids = children.mapNotNull { member -> (member.ref as? FamilyMemberRef.Child)?.let { member to it.id } }
    if (kids.size < 2) return
    Column(modifier = Modifier.padding(vertical = dimensions().paddingMedium)) {
        GroupLabel(text = stringResource(R.string.custody_child_schedules_title))
        Text(
            text = stringResource(R.string.custody_child_schedules_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        SectionGroup {
            kids.forEachIndexed { index, (child, childId) ->
                if (index > 0) Divider()
                val ownSchedule = childId in childrenWithOwnSchedule
                SectionRow(
                    title = child.name,
                    icon = Icons.Default.ChildCare,
                    supporting = stringResource(
                        if (ownSchedule) {
                            R.string.custody_child_own_schedule
                        } else {
                            R.string.custody_child_family_schedule
                        }
                    ),
                    onClick = { onEdit(child) }
                )
            }
        }
    }
}

/**
 * What the editor says above the form while it is scoped to one child (FAM-4): whose schedule this
 * is, that the family's seasonal schedules and one-off swaps do not move them, and — when they
 * already have one — the way back to the family schedule.
 *
 * @param childName The child the form is about.
 * @param hasOwnSchedule Whether the child already follows a schedule of their own.
 * @param onFollowFamily Sends the child back to the family schedule.
 */
@Composable
fun ChildScopeHeader(childName: String, hasOwnSchedule: Boolean, onFollowFamily: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = dimensions().paddingSmall)) {
        Text(
            text = stringResource(R.string.custody_child_scope_hint, childName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions().paddingSmall)
        )
        if (hasOwnSchedule) {
            SectionGroup {
                SectionRow(
                    title = stringResource(R.string.custody_child_follow_family),
                    icon = Icons.Default.ChildCare,
                    onClick = onFollowFamily
                )
            }
        }
    }
}
