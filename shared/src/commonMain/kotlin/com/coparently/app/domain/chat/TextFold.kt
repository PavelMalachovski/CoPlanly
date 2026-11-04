package com.coparently.app.domain.chat

import com.coparently.app.domain.platform.canonicalDecomposition

/**
 * Text folded for matching, with a map back to where each folded character came from.
 *
 * @property text The folded text: lower-cased, diacritics removed, every whitespace character a
 *   single space.
 * @property starts For each index of [text], the index in the original string where the
 *   character it came from starts.
 * @property ends For each index of [text], the index in the original string just past that
 *   character — including any combining marks that followed it, so a match highlights "č" whole
 *   whether it was typed precomposed or as "c" plus a caron.
 */
class FoldedText(val text: String, val starts: IntArray, val ends: IntArray)

/**
 * The one definition of "the same letters" for chat search and the tone nudge.
 *
 * SQLite's `LIKE` folds ASCII case and nothing else, so it cannot find "čas" from "cas" or
 * "Привет" from "привет" — and every locale this app ships in besides English needs exactly that.
 * Folding happens here, in Kotlin, instead: lower-case with the locale-invariant [lowercase] (never
 * the device locale, so a Turkish-locale phone does not turn "I" into a dotless "ı"), decompose to
 * NFD, drop the combining marks. "ä" matches "a", "ё" matches "е", "č" matches "c".
 *
 * What it deliberately does not do: fold "ß" to "ss" (a change of length that a highlight could
 * not map back cleanly), or treat Cyrillic and Latin look-alikes as one letter.
 */
object TextFold {

    /** Nonspacing combining marks — the accents NFD splits off a letter. */
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /**
     * The three no-break spaces: Kotlin's [Char.isWhitespace] counts them, `Character.isWhitespace`
     * — what this fold has always used — does not, so a no-break space stays a character.
     */
    private const val NO_BREAK_SPACES = "\u00A0\u2007\u202F"

    /**
     * Folds [text] and records, for every folded character, the original range it came from.
     *
     * Walks code points rather than chars, so a surrogate pair (an emoji) is one unit and is never
     * split. A code point that folds to nothing — a combining mark on its own — widens the range of
     * the character before it instead of vanishing from the map.
     */
    fun fold(text: String): FoldedText {
        val out = StringBuilder(text.length)
        val starts = ArrayList<Int>(text.length)
        val ends = ArrayList<Int>(text.length)
        var index = 0
        while (index < text.length) {
            val next = index + codePointLength(text, index)
            val piece = foldCodePoint(text.substring(index, next))
            if (piece.isEmpty()) {
                if (ends.isNotEmpty()) ends[ends.lastIndex] = next
            } else {
                piece.forEach { folded ->
                    out.append(folded)
                    starts += index
                    ends += next
                }
            }
            index = next
        }
        return FoldedText(out.toString(), starts.toIntArray(), ends.toIntArray())
    }

    /** The folded text alone, for callers that do not need to map a match back. */
    fun simple(text: String): String = fold(text).text

    /** 2 for a surrogate pair at [index], else 1: what `codePointAt` and `charCount` count. */
    private fun codePointLength(text: String, index: Int): Int =
        if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1

    /** Folds one code point, given as its one or two chars. */
    private fun foldCodePoint(codePoint: String): String {
        if (codePoint.length == 1 && isWhitespace(codePoint[0])) return " "
        return codePoint.lowercase().canonicalDecomposition().replace(COMBINING_MARKS, "")
    }

    /** `Character.isWhitespace`: no code point outside the BMP is whitespace. */
    private fun isWhitespace(char: Char): Boolean = char.isWhitespace() && char !in NO_BREAK_SPACES
}
