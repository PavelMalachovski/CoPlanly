package com.coparently.app.qa

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import com.coparently.app.testing.iconOnlyControlProblems

/**
 * An amount of money as the app prints it, in any of its five languages: digits grouped with an
 * ASCII, no-break (U+00A0) or narrow no-break (U+202F) space, a dot or a comma, then a currency
 * symbol or code. Czech prints "1 290 Kč" with a no-break space, which a pattern that knows only
 * the ASCII space would miss.
 */
internal val moneyText = Regex("""\d[\d   .,]*\s?(Kč|CZK|€|EUR|\$|USD|zł|PLN|£|GBP)""")

private val anyText = SemanticsMatcher("a node with text") { it.config.contains(SemanticsProperties.Text) }

/**
 * What a human tester would flag on the screen on show, beside whatever the step itself checks
 * (CLAUDE.md design item 15 and the accessibility sweep):
 *
 * - `accessibility: …` — an icon-only control with no name or under 48 dp;
 * - `money cut off: "…"` — an amount, a total or a sentence holding one that is ellipsized or
 *   overflows its box;
 * - `text clipped: "…"` — any other text that overflows its box.
 *
 * A finding never changes a step's verdict; the report lists it beside the step.
 */
internal fun ComposeTestRule.visualFindings(): List<String> {
    val findings = iconOnlyControlProblems().mapTo(mutableListOf()) { "accessibility: $it" }
    onAllNodes(anyText).fetchSemanticsNodes(atLeastOneRootRequired = false).forEach { node ->
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val layout = node.textLayout() ?: return@forEach
        val cut = layout.hasVisualOverflow || (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        if (!cut) return@forEach
        findings += if (moneyText.containsMatchIn(text)) {
            "money cut off: \"$text\""
        } else {
            "text clipped: \"${text.take(CLIPPED_PREFIX)}\""
        }
    }
    return findings.distinct()
}

/** The node's text layout through the semantics action Compose's `Text` exposes, or null. */
private fun SemanticsNode.textLayout(): TextLayoutResult? {
    val action = config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return null
    val results = mutableListOf<TextLayoutResult>()
    return if (action.action?.invoke(results) == true) results.firstOrNull() else null
}

private const val CLIPPED_PREFIX = 40
