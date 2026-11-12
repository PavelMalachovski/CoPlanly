package com.coparently.app.presentation.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.presentation.calendar.CalendarViewMode
import com.coparently.app.presentation.common.BrandHeader
import com.coparently.app.presentation.common.stacksAtLargeFont
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_filters_button
import com.coparently.app.resources.calendar_heading
import com.coparently.app.resources.calendar_names_and
import com.coparently.app.resources.calendar_together_for
import com.coparently.app.resources.calendar_viewmode_day
import com.coparently.app.resources.calendar_viewmode_month
import com.coparently.app.resources.calendar_viewmode_week
import org.jetbrains.compose.resources.stringResource

/**
 * The calendar's header (redesign, November 2026), three rows over the lavender ground:
 * the brand row (wordmark, the children's avatars, the gear), "Your family calendar" with
 * "Together, for Emma and Leo" under it, and the Month/Week/Day control with Filters.
 *
 * It replaced the one-row bar whose month title was also the view picker and the way to a date
 * (design item 5 of the August 2026 refresh). The month title, Today and the arrows now head the
 * grid's own card ([CalendarMonthBar]); tapping that title still opens "Jump to date". The view
 * picker became a visible segmented control, because a parent new to the app never found it in
 * the title.
 *
 * @param children The family on screen's children, in display order. With none, the together
 *   line is left out.
 * @param viewMode The view the grid shows
 * @param onViewModeChange Called with the mode picked in the segmented control
 * @param onFiltersClick Opens the filter sheet
 * @param filtersActive Whether any filter is narrowing the calendar
 * @param onSettingsClick Opens Settings, or null to draw no gear
 */
@Composable
@Suppress("LongParameterList") // the header's actions are this component's API surface
fun CalendarHeader(
    children: List<ChildInfo>,
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit,
    onFiltersClick: () -> Unit,
    filtersActive: Boolean = false,
    onSettingsClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Tight on purpose (tester feedback, 12 November 2026): every dp the header gives up
            // goes to the month grid under it.
            .padding(horizontal = Spacing.L, vertical = Spacing.XS),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        BrandHeader(children = children, onSettingsClick = onSettingsClick)
        HeadingLines(childNames = children.map { it.childName })
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ViewModeControl(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(Spacing.S))
            FiltersButton(onClick = onFiltersClick, active = filtersActive)
        }
    }
}

/**
 * "Your family calendar" and, under it, "Together, for Emma and Leo". The second line is left
 * out with no children, and from font scale 1.3, where the header would otherwise take a third
 * of a phone's height before the grid starts.
 */
@Composable
private fun HeadingLines(childNames: List<String>) {
    val together = togetherFor(childNames)
    Column {
        Text(
            text = stringResource(Res.string.calendar_heading),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.semantics { heading() }
        )
        if (together != null && !stacksAtLargeFont()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                Text(
                    text = together,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSizes.Inline)
                )
            }
        }
    }
}

/** "Together, for Emma and Leo", or null for a family without children. */
@Composable
internal fun togetherFor(childNames: List<String>): String? {
    val names = childrenList(childNames, stringResource(Res.string.calendar_names_and)) ?: return null
    return stringResource(Res.string.calendar_together_for, names)
}

/**
 * Children's names as one phrase: "Emma", "Emma and Leo", "Emma, Leo and Max", or null for
 * none. Pure, so the joining is tested without a composition.
 *
 * @param names The names, in display order
 * @param and The reader's word for "and", from `calendar_names_and`
 */
internal fun childrenList(names: List<String>, and: String): String? = when (names.size) {
    0 -> null
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " $and " + names.last()
}

/** Month, Week and Day as one segmented control: the view is a choice always in sight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeControl(
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = CalendarViewMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == viewMode,
                onClick = { onViewModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                // No check mark at any size, so the label has the segment's whole width: three
                // labels already fit badly at 200 % in German. A label, not money, so it may end
                // in an ellipsis (design item 15).
                icon = {},
                label = { Text(text = viewModeLabel(mode), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/**
 * The filter action, a round icon button: outlined while nothing is filtered, filled tonal
 * while a filter narrows the calendar, so an active filter is visible without opening the sheet.
 *
 * @param onClick Opens the filter sheet
 * @param active Whether any filter is currently narrowing the calendar
 */
@Composable
private fun FiltersButton(onClick: () -> Unit, active: Boolean) {
    val description = stringResource(Res.string.calendar_filters_button)
    if (active) {
        FilledTonalIconButton(onClick = onClick) {
            Icon(Icons.Default.FilterList, contentDescription = description)
        }
    } else {
        OutlinedIconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Display label for a calendar view mode. */
@Composable
private fun viewModeLabel(mode: CalendarViewMode): String = when (mode) {
    CalendarViewMode.MONTH -> stringResource(Res.string.calendar_viewmode_month)
    CalendarViewMode.WEEK -> stringResource(Res.string.calendar_viewmode_week)
    CalendarViewMode.DAY -> stringResource(Res.string.calendar_viewmode_day)
}
