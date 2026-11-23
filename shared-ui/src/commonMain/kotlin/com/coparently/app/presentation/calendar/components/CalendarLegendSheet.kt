package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.coparently.app.presentation.common.AdaptiveSheet
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.SheetHeight
import com.coparently.app.presentation.common.nativeControls
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_filters_button
import com.coparently.app.resources.calendar_filters_hiding
import com.coparently.app.resources.calendar_legend_no_schedule
import com.coparently.app.resources.calendar_legend_title
import org.jetbrains.compose.resources.stringResource

/**
 * "What the colours mean" (calendar K1, November 2026), the header's "?": the [CustodyLegend] that
 * used to take a row of the grid card, and under it a Filters row, the way into the filter sheet
 * ([EventTypeFilterSheet]) now that the header has no Filters button. The filters' state and
 * behaviour are the sheet's own and did not change.
 *
 * @param state What the legend explains
 * @param parentNames Resolves a slot to that parent's name
 * @param onFilters Closes this sheet and opens the filters
 * @param onDismiss Called once when the parent closes the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarLegendSheet(
    state: CalendarLegendState,
    parentNames: ParentNames,
    onFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val body: @Composable () -> Unit = {
        CalendarLegendContent(state = state, parentNames = parentNames, onFilters = onFilters)
    }
    AdaptiveSheet(
        onDismiss = onDismiss,
        height = SheetHeight.Medium,
        material = { ModalBottomSheet(onDismissRequest = onDismiss) { body() } },
        content = body,
        scrollable = true
    )
}

/**
 * The legend sheet's body, apart from the sheet so a screenshot can draw it (a sheet opens a
 * window of its own that a node capture does not see).
 *
 * @param state What the legend explains
 * @param parentNames Resolves a slot to that parent's name
 * @param onFilters Opens the filters
 */
@Composable
fun CalendarLegendContent(state: CalendarLegendState, parentNames: ParentNames, onFilters: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.L, vertical = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text(
            text = stringResource(Res.string.calendar_legend_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.semantics { heading() }
        )
        if (state.hasSchedule) {
            CustodyLegend(
                parentNames = parentNames,
                showContactWindows = state.showContactWindows,
                showPendingSwap = state.showPendingSwap
            )
        } else {
            Text(
                text = stringResource(Res.string.calendar_legend_no_schedule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SectionGroup {
            SectionRow(
                title = stringResource(Res.string.calendar_filters_button),
                icon = Icons.Default.FilterList,
                supporting = if (state.filtersActive) stringResource(Res.string.calendar_filters_hiding) else null,
                onClick = onFilters,
                // Under native controls the row draws the system's chevron itself.
                trailing = if (nativeControls() == null) {
                    {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}
