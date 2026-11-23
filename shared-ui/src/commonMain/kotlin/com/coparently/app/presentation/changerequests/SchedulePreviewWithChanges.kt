package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.custody.CustodyHistory
import com.coparently.app.domain.custody.CustodyProposal
import com.coparently.app.domain.custody.CustodyResolver
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.common.AdaptiveSegmented
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.rememberToday
import com.coparently.app.presentation.custody.labelRes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.labelLargeEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.custody_proposal_preview
import com.coparently.app.resources.schedule_preview_now
import com.coparently.app.resources.schedule_preview_proposed
import com.coparently.app.resources.schedule_preview_show
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.unbreakableDate
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * A proposed custody schedule on a mini month of the reader's own calendar (November 2026, board
 * HomeScheduleDialog): its pattern's name and start, a Proposed | Now switch, and the month drawn in
 * the proposed pattern or the agreed one. In the proposed one each day whose parent would change is
 * ringed in the colour of whom it would go to ([scheduleChangeMonth]), with a legend line saying so;
 * the days before the proposal would take effect if accepted today keep the current schedule and
 * are never ringed, because an accepted change keeps the past (item 30).
 * No count of days or nights per parent: no score between the parents.
 *
 * The month is the first the proposal reaches from today, as [ProposedSchedulePreview] draws it.
 * Accepted day swaps stand on both sides, as they stand above any pattern ([CustodyResolver]), so a
 * swapped day never reads as a change. Home's Details sheet and its proposal pop-up draw it.
 *
 * @param proposal The proposal waiting for an answer.
 * @param current The schedule agreed now, or null when there is none: every day of the proposal then
 *   reads as new and "Now" draws no band.
 * @param overrides The family's day swaps, keyed by ISO date.
 * @param parentNames Names each parent in the legend.
 * @param background What the switch sits on, for the iPhone's native control (design item 1).
 */
@Composable
@Suppress("LongParameterList") // the proposal, what it replaces, the swaps over both, the names and the ground
fun SchedulePreviewWithChanges(
    proposal: CustodyProposal,
    current: CustodyModel?,
    overrides: Map<String, DayOverride>,
    parentNames: ParentNames,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    val today = rememberToday().value
    val model = proposal.model
    // An accept today keeps the days before this as history (CustodyHistory, item 30).
    val effectiveFrom = CustodyHistory.effectiveFrom(model, acceptedOn = today)
    val month = effectiveFrom.yearMonth
    var showProposed by rememberSaveable(proposal.proposedAt) { mutableStateOf(true) }
    val shown = remember(model, current, overrides, effectiveFrom, showProposed) {
        scheduleChangeMonth(
            month = month,
            proposed = CustodyResolver.resolver(model, overrides, legacy = { null }),
            current = CustodyResolver.resolver(current, overrides, legacy = { null }),
            showProposed = showProposed,
            effectiveFrom = effectiveFrom
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(
            text = stringResource(
                Res.string.custody_proposal_preview,
                stringResource(model.modelType.labelRes()),
                unbreakableDate(ReaderDateText.date(model.startDate, "yMMMd"))
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        ProposedOrNow(showProposed, background) { showProposed = it }
        Text(
            text = ReaderDateText.month(month, "yMMMM"),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MiniMonth(model = shown, parentNames = parentNames, today = today)
    }
}

/**
 * The Proposed | Now switch, the system's segmented control on the iPhone (design item 1).
 *
 * On Android Material's segmented buttons, with no check mark, while both labels fit their half on
 * one line; where they do not (German or Ukrainian at 150 % and more) two filter chips that wrap
 * as wholes, because a segment broke its label inside the word ("Vorgeschlag|en") and grew taller
 * than its neighbour (text review, November 2026).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposedOrNow(showProposed: Boolean, background: Color, onShowProposed: (Boolean) -> Unit) {
    val labels = listOf(
        stringResource(Res.string.schedule_preview_proposed),
        stringResource(Res.string.schedule_preview_now)
    )
    val selected = if (showProposed) 0 else 1
    AdaptiveSegmented(
        labels = labels,
        selectedIndex = selected,
        onSelect = { onShowProposed(it == 0) },
        modifier = Modifier.fillMaxWidth(),
        background = background,
        accessibilityLabel = stringResource(Res.string.schedule_preview_show)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelLarge
            val room = with(LocalDensity.current) { (maxWidth / labels.size - SEGMENT_LABEL_INSET).roundToPx() }
            val fits = labels.all { label ->
                measurer.measure(label, style, maxLines = 1, softWrap = false).size.width <= room
            }
            if (fits) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = index == selected,
                            onClick = { onShowProposed(index == 0) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                            icon = {},
                            label = { Text(label, maxLines = 1, softWrap = false) }
                        )
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.XS)
                ) {
                    labels.forEachIndexed { index, label ->
                        FilterChip(
                            selected = index == selected,
                            onClick = { onShowProposed(index == 0) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * What a segment keeps around its label: Material's 12 dp content padding on each side, and its
 * outline.
 */
private val SEGMENT_LABEL_INSET = 26.dp
