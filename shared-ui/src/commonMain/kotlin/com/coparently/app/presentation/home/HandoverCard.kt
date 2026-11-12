package com.coparently.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyMediumEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.home_handover_goes_to
import com.coparently.app.resources.home_handover_goes_to_you
import com.coparently.app.resources.home_handover_in_days
import com.coparently.app.resources.home_handover_today
import com.coparently.app.utils.ReaderDateText
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Home's "Next handover" card (redesign, November 2026, variant A): one compact card in place of
 * the handover hero. "Handover in 4 days", then the day and whom it goes to — "you" when it is the
 * reader (owner request, 28 October 2026) — and, on a day the children are apart, each child with
 * their parent (FAM-4).
 *
 * The card says which **day** the child changes hands and to whom, never at what hour: the schema
 * holds no handover time.
 *
 * Public, not internal, so the app's screenshot tests can render it on its own.
 *
 * @param info The next handover
 * @param parentNames Resolves a slot to that parent's name, or "you"
 * @param childrenToday Each child with their parent today, on a day they are apart
 */
@Composable
fun HandoverCard(
    info: HandoverInfo,
    parentNames: ParentNames,
    childrenToday: List<ChildWithParent> = emptyList()
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.M),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(IconSizes.Large + Spacing.S)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(IconSizes.Standard)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.XXS)
            ) {
                Text(text = handoverHeadline(info), style = MaterialTheme.typography.bodyMediumEmphasized)
                Text(
                    text = ReaderDateText.date(info.date, "EEEdMMM") + " · " + handoverGoesTo(info, parentNames),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChildrenTodayLines(childrenToday, parentNames)
            }
        }
    }
}

/** The card's headline: "Handover today", or in how many days. */
@Composable
private fun handoverHeadline(info: HandoverInfo): String {
    val days = info.daysUntil.toInt()
    return if (days <= 0) {
        stringResource(Res.string.home_handover_today)
    } else {
        pluralStringResource(Res.plurals.home_handover_in_days, days, days)
    }
}

/** Whom the handover's days go to: "Goes to you" when it is the reader (design item 23). */
@Composable
private fun handoverGoesTo(info: HandoverInfo, parentNames: ParentNames): String =
    if (parentNames.isReader(info.toParent)) {
        stringResource(Res.string.home_handover_goes_to_you)
    } else {
        stringResource(Res.string.home_handover_goes_to, parentNames.labelFor(info.toParent))
    }
