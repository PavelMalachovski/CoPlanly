package com.coparently.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.common.ParentNames

/**
 * The handover hero's per-child lines (FAM-4): "Anya is with Olya today", one per child, on a day
 * the children are not all with the same parent.
 *
 * Names on both sides and no colour at all — not a dot, not a tint. A member is a name (FAM-2),
 * and the hue beside a parent's name already means that parent; a child drawn in it would read as
 * a third thing the colour channel cannot say. Renders nothing for an empty list, which is every
 * day in every family without two children and an override.
 *
 * @param children From [ChildrenToday], in the family's display order.
 * @param parentNames Resolves a slot to that parent's name.
 */
@Composable
internal fun ChildrenTodayLines(children: List<ChildWithParent>, parentNames: ParentNames) {
    if (children.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(LINE_SPACING)) {
        children.forEach { child ->
            Text(
                text = stringResource(
                    R.string.home_child_with_parent_today,
                    child.childName,
                    parentNames.labelFor(child.slot)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Space between two children's lines. */
private val LINE_SPACING = 2.dp
