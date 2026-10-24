package com.coparently.app.presentation.custody

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.theme.Spacing
import java.time.YearMonth

/**
 * A schedule shown as the month it starts in — the custody wash and who has which day — under a
 * one-line description (new-user review, October 2026: the schedule is what a parent installs the
 * app for, and a row of 14 chips did not show it the way the calendar will).
 *
 * Draws the month holding today or [model]'s start, whichever is later, through the one small
 * calendar ([MiniMonth]), so the editor, onboarding and a waiting proposal all preview a schedule
 * the same way. Redraws whenever [model] changes.
 *
 * @param model The schedule to preview; it need not be saved
 * @param parentNames Names each parent in the legend
 * @param description What the schedule is, in a sentence, or null for none
 */
@Composable
internal fun SchedulePreviewMonth(
    model: CustodyModel,
    parentNames: ParentNames,
    description: String?,
    modifier: Modifier = Modifier
) {
    val today = rememberToday().value
    val from = maxOf(today, model.startDate)
    val month = remember(model, from) {
        MiniMonthModel.build(
            month = YearMonth.from(from),
            custodyFor = model::getCustodyFor,
            events = emptyList(),
            marks = emptyMap()
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MiniMonth(model = month, parentNames = parentNames, today = today)
    }
}
