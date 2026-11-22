package com.coparently.app.presentation.home

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
import com.coparently.app.presentation.common.AppIcon
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleLargeEmphasized
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_heading
import com.coparently.app.resources.calendar_names_and
import com.coparently.app.resources.calendar_together_for
import org.jetbrains.compose.resources.stringResource

/**
 * "Your family calendar" and, under it, "Together, for Emma and Leo" — the heading that led the
 * calendar until the November 2026 K1 redesign gave the grid the screen, and leads Home under the
 * wordmark since (board HomeG1). The same strings as the calendar's; the second line is left out
 * with no children. Home scrolls, so it keeps the line at every font scale.
 *
 * @param childNames The children's names, in display order.
 */
@Composable
internal fun HomeHeading(childNames: List<String>) {
    val names = childrenPhrase(childNames, stringResource(Res.string.calendar_names_and))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
        Text(
            text = stringResource(Res.string.calendar_heading),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.semantics { heading() }
        )
        if (names != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
            ) {
                Text(
                    text = stringResource(Res.string.calendar_together_for, names),
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

/**
 * Children's names as one phrase: "Emma", "Emma and Leo", "Emma, Leo and Max", or null for none.
 * The calendar header's rule, kept here since the heading moved to Home.
 *
 * @param names The names, in display order
 * @param and The reader's word for "and", from `calendar_names_and`
 */
internal fun childrenPhrase(names: List<String>, and: String): String? = when (names.size) {
    0 -> null
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " $and " + names.last()
}
