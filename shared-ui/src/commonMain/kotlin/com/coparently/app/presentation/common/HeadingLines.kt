package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.coparently.app.presentation.calendar.components.togetherFor
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_heading
import org.jetbrains.compose.resources.stringResource

/**
 * "Your family calendar" and, under it, "Together, for Emma and Leo". The second line is left
 * out with no children.
 *
 * The calendar's header drew these until calendar K1 (November 2026), which gave the grid the
 * screen; Home leads with them under the wordmark since (board HomeG1). Home scrolls, so the
 * second line stays at every font scale. Public, so a screen that shows them draws no copy.
 *
 * @param childNames The family on screen's children's names, in display order
 * @param modifier Modifier for the column
 */
@Composable
fun HeadingLines(childNames: List<String>, modifier: Modifier = Modifier) {
    val together = togetherFor(childNames)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
        Text(
            text = stringResource(Res.string.calendar_heading),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.semantics { heading() }
        )
        if (together != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                Text(
                    text = together,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                AppIcon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSizes.Inline)
                )
            }
        }
    }
}
