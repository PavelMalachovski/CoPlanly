package com.coparently.app.presentation.calendar

/**
 * Whether a laid-out title was broken inside a word.
 *
 * Compose wraps at word boundaries while a word fits on a line, and cuts a word that does not
 * wherever the line runs out. In a week column about seven characters wide that turned
 * "Plavání" into "Plaván" over "í" (the UI tour, Czech and German). The event block asks this of
 * its title's layout and, when the answer is yes, draws the title on one line with an ellipsis
 * instead, which says less but never splits a word.
 *
 * A break is at a word boundary when the line ends in whitespace or a hyphen, or the next line
 * starts with whitespace. The last line is not asked about: it ends the text or its ellipsis.
 *
 * @param text The title as laid out.
 * @param lineEnds The offset just past each line's last character, in line order
 *   (`TextLayoutResult.getLineEnd(i)`), whitespace included.
 * @return True when any line but the last ends between two characters of one word.
 */
internal fun breaksInsideWord(text: CharSequence, lineEnds: List<Int>): Boolean =
    lineEnds.dropLast(1).any { end ->
        end in 1 until text.length && !isBreakOpportunity(text[end - 1], text[end])
    }

private fun isBreakOpportunity(before: Char, after: Char): Boolean =
    before.isWhitespace() || before == '-' || before == '‐' || after.isWhitespace()
