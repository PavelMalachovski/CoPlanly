package com.coparently.app.presentation.custody

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.localizedDate

/**
 * What the custody screen opens on: one row per [CustodySection], each saying what it holds now.
 *
 * @param uiState The form's state, for the pattern's and the windows' summaries
 * @param layers The family's seasonal schedules, history layers left out
 * @param childCount How many children the family has; the children's row appears at two
 * @param childrenWithOwnSchedule How many of them follow a schedule of their own
 * @param coParentName Who a change is sent to, or null when nobody is: then nothing is sent
 * @param onOpen Opens a part
 */
@Composable
@Suppress("LongParameterList") // one summary per row, and where each row goes
internal fun CustodyOverview(
    uiState: CustodySetupUiState,
    layers: List<SeasonalLayer>,
    childCount: Int,
    childrenWithOwnSchedule: Int,
    coParentName: String?,
    onOpen: (CustodySection) -> Unit
) {
    val none = stringResource(R.string.custody_overview_none)
    Column(
        modifier = Modifier.padding(vertical = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        SectionGroup {
            SectionRow(
                title = stringResource(R.string.custody_overview_pattern),
                icon = Icons.Default.Schedule,
                supporting = stringResource(
                    R.string.custody_overview_pattern_summary,
                    stringResource(uiState.selectedModelType.labelRes()),
                    uiState.startDate.format(localizedDate("MMMEd"))
                ),
                onClick = { onOpen(CustodySection.PATTERN) },
                trailing = { Chevron() }
            )
            Divider()
            SectionRow(
                title = stringResource(R.string.custody_windows_title),
                icon = Icons.Default.DateRange,
                supporting = uiState.contactWindows.size.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.custody_overview_windows, it, it)
                } ?: none,
                onClick = { onOpen(CustodySection.WINDOWS) },
                trailing = { Chevron() }
            )
            Divider()
            SectionRow(
                title = stringResource(R.string.seasonal_title),
                icon = Icons.Default.EventRepeat,
                supporting = layers.map { it.name }.filter { it.isNotBlank() }.joinToString(", ").ifEmpty { none },
                onClick = { onOpen(CustodySection.SEASONAL) },
                trailing = { Chevron() }
            )
            // At two children, never at one (FAM-1): a picker for a set of one is no choice.
            if (childCount >= 2) {
                Divider()
                ChildrenRow(childrenWithOwnSchedule) { onOpen(CustodySection.CHILDREN) }
            }
        }
        coParentName?.let { name ->
            InlineBanner(
                text = stringResource(R.string.custody_overview_sent_separately, name),
                tone = BannerTone.INFO
            )
        }
    }
}

/** The children's row: how many follow a schedule of their own, or that all follow the family's. */
@Composable
private fun ChildrenRow(childrenWithOwnSchedule: Int, onClick: () -> Unit) {
    SectionRow(
        title = stringResource(R.string.custody_child_schedules_title),
        icon = Icons.Default.ChildCare,
        supporting = if (childrenWithOwnSchedule > 0) {
            pluralStringResource(
                R.plurals.custody_overview_children_own,
                childrenWithOwnSchedule,
                childrenWithOwnSchedule
            )
        } else {
            stringResource(R.string.custody_overview_children_same)
        },
        onClick = onClick,
        trailing = { Chevron() }
    )
}

/** The row's "opens a screen" mark. */
@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
