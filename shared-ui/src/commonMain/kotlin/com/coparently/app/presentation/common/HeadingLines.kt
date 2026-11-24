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
import com.coparently.app.presentation.calendar.components.childrenList
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_names_and
import com.coparently.app.resources.calendar_together_for
import org.jetbrains.compose.resources.stringResource

/**
 * "Together, for" over the children's names and a heart, under Home's wordmark; nothing with no
 * children.
 *
 * "Your family calendar" led these lines until 24 November 2026 (owner, on the phone: the header
 * read unevenly), and the names now take a line of their own, in the same type as the line above
 * them. The two lines are cut from one translated sentence, `calendar_together_for`, at its
 * placeholder ([togetherForLines]), so no language needs a second string; a language whose names do
 * not end the sentence keeps it on one line.
 *
 * @param childNames The family on screen's children's names, in display order
 * @param modifier Modifier for the column
 */
@Composable
fun HeadingLines(childNames: List<String>, modifier: Modifier = Modifier) {
    val names = childrenList(childNames, stringResource(Res.string.calendar_names_and)) ?: return
    val lines = togetherForLines(stringResource(Res.string.calendar_together_for, NAMES_MARK), names)
    val style = MaterialTheme.typography.bodyLarge
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(Spacing.XXS)
    ) {
        lines.dropLast(1).forEach { line -> Text(text = line, style = style, color = color) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS)
        ) {
            Text(
                text = lines.last(),
                style = style,
                color = color,
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

/**
 * [template] ("Together, for ␟", the sentence with [NAMES_MARK] where the names go) as the lines
 * Home draws: the words before the names, then [names]. One line, the whole sentence, when the
 * names do not end it or nothing comes before them. Pure, so the cut is tested without a
 * composition.
 */
internal fun togetherForLines(template: String, names: String): List<String> {
    val at = template.indexOf(NAMES_MARK)
    val before = if (at > 0) template.substring(0, at).trimEnd() else ""
    val after = if (at >= 0) template.substring(at + NAMES_MARK.length).trim() else ""
    return if (at < 0 || before.isEmpty() || after.isNotEmpty()) {
        listOf(template.replace(NAMES_MARK, names))
    } else {
        listOf(before, names)
    }
}

/** Stands for the names in the formatted sentence; a character no translation contains. */
internal const val NAMES_MARK = "␟"
