package com.coparently.app.presentation.friends

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.coparently.app.R
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.titleSmallEmphasized

/**
 * What a family friend can do, beside what a co-parent can (phase 2, spec §4.2) — the answer to
 * the tester who added a friend and could not tell the two apart.
 *
 * **Every cell states a rule, not a wish**, and must change when the rule does:
 * - *Calendar*: a co-parent views and changes; a family friend only views, through the last
 *   disjunct of the `events` read rule, `isCalendarFriendOf` in `firestore.rules` — never a
 *   private event (none reaches Firestore, CLAUDE.md item 3), and no write rule names a friend.
 * - *Custody schedule, Expenses, Chat, Children's records*: a friend reads none of
 *   `custody_models`, `expenses`, `conversations`/`messages` or `child_info`; no rule over them
 *   admits a calendar friend.
 * - *How long*: a co-parent for as long as the two are linked; a friend for one of the lengths
 *   the invite offers, read from the same list as its chips ([durationChoicesText]), and the
 *   grant lapses at `expiresAtMillis`.
 *
 * Each row is one node for TalkBack ("Expenses: co-parent Yes, family friend No"), so a reader is
 * never left to piece a row together from three cells. From [STACK_FONT_SCALE] the columns turn
 * into lines under each row's label, because three columns of large text wrap a word to a line.
 *
 * @param modifier Placement in the parent's layout.
 */
@Composable
internal fun FriendComparisonTable(modifier: Modifier = Modifier) {
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    SectionGroup(modifier = modifier) {
        if (!stacked) HeaderRow()
        ROWS.forEachIndexed { index, row ->
            if (!stacked || index > 0) Divider()
            ComparisonRow(row, stacked)
        }
    }
}

/** One cell's content: words, a yes or a no, or the friend's lengths. */
private sealed interface Cell {
    data class Words(@StringRes val res: Int) : Cell
    data class Mark(val yes: Boolean) : Cell
    data object Durations : Cell
}

private data class Comparison(@StringRes val label: Int, val coParent: Cell, val friend: Cell)

private val ROWS = listOf(
    Comparison(
        R.string.friend_compare_calendar,
        Cell.Words(R.string.friend_compare_views_and_changes),
        Cell.Words(R.string.friend_compare_only_views)
    ),
    Comparison(R.string.friend_compare_custody, Cell.Mark(yes = true), Cell.Mark(yes = false)),
    Comparison(R.string.friend_compare_expenses, Cell.Mark(yes = true), Cell.Mark(yes = false)),
    Comparison(R.string.friend_compare_chat, Cell.Mark(yes = true), Cell.Mark(yes = false)),
    Comparison(R.string.friend_compare_children, Cell.Mark(yes = true), Cell.Mark(yes = false)),
    Comparison(R.string.friend_compare_how_long, Cell.Words(R.string.friend_compare_while_linked), Cell.Durations)
)

/** From this font scale the table's columns stack under each row's label. */
private const val STACK_FONT_SCALE = 1.3f

/** The label column is a little wider than either answer column. */
private const val LABEL_WEIGHT = 1.2f

@Composable
private fun HeaderRow() {
    TableRow {
        Spacer(modifier = Modifier.weight(LABEL_WEIGHT))
        HeaderCell(stringResource(R.string.friend_compare_column_coparent))
        HeaderCell(stringResource(R.string.friend_compare_column_friend))
    }
}

@Composable
private fun ComparisonRow(row: Comparison, stacked: Boolean) {
    val label = stringResource(row.label)
    val coParent = row.coParent.text()
    val friend = row.friend.text()
    val description = stringResource(R.string.friend_compare_row_desc, label, coParent, friend)
    val oneNode = Modifier.clearAndSetSemantics { contentDescription = description }
    if (stacked) {
        Column(
            modifier = oneNode
                .fillMaxWidth()
                .padding(horizontal = Spacing.L, vertical = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS)
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmallEmphasized)
            StackedCell(stringResource(R.string.friend_compare_column_coparent), row.coParent, coParent)
            StackedCell(stringResource(R.string.friend_compare_column_friend), row.friend, friend)
        }
    } else {
        TableRow(modifier = oneNode) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmallEmphasized,
                modifier = Modifier.weight(LABEL_WEIGHT)
            )
            AnswerCell(row.coParent, coParent)
            AnswerCell(row.friend, friend)
        }
    }
}

@Composable
private fun TableRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.L, vertical = Spacing.M),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.Top,
        content = content
    )
}

@Composable
private fun RowScope.HeaderCell(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun RowScope.AnswerCell(cell: Cell, text: String) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        verticalAlignment = Alignment.Top
    ) {
        MarkIcon(cell)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One answer under its column's name, as the stacked layout draws it. */
@Composable
private fun StackedCell(column: String, cell: Cell, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        verticalAlignment = Alignment.Top
    ) {
        MarkIcon(cell)
        Text(
            text = "$column: $text",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** A check or a cross before a yes or a no, in neutral tones; nothing before words. */
@Composable
private fun MarkIcon(cell: Cell) {
    if (cell !is Cell.Mark) return
    Icon(
        imageVector = if (cell.yes) Icons.Default.Check else Icons.Default.Close,
        contentDescription = null,
        tint = if (cell.yes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.XXS).size(IconSizes.Small)
    )
}

@Composable
private fun Cell.text(): String = when (this) {
    is Cell.Words -> stringResource(res)
    is Cell.Mark -> stringResource(if (yes) R.string.friend_compare_yes else R.string.friend_compare_no)
    Cell.Durations -> durationChoicesText()
}
