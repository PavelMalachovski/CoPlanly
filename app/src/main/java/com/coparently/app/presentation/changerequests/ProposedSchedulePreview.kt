package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.custody.labelRes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.localizedDate
import java.time.YearMonth

/**
 * What a proposed custody schedule looks like, before the parent answers it: its pattern's name,
 * the day it starts, and its first month as the mini month draws custody.
 *
 * The diff line above it says which days move, but only when the two patterns can be compared;
 * a change of pattern ("week on, week off" to "2-2-3") had no description at all, and the parent
 * was asked to accept a schedule they could not see (tester report, October 2026). The month is
 * the proposal's own — its seasonal layers included, because `getCustodyFor` answers from them —
 * never the agreed one.
 *
 * @param proposal The proposal waiting for an answer.
 * @param parentNames Names each parent in the mini month's legend.
 */
@Composable
fun ProposedSchedulePreview(
    proposal: CustodyProposal,
    parentNames: ParentNames,
    modifier: Modifier = Modifier
) {
    val today = rememberToday().value
    val model = proposal.model
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
        Text(
            text = stringResource(
                R.string.custody_proposal_preview,
                stringResource(model.modelType.labelRes()),
                model.startDate.format(localizedDate("yMMMd"))
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        MiniMonth(model = month, parentNames = parentNames, today = today)
    }
}
