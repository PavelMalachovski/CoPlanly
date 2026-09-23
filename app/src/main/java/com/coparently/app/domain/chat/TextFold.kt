package com.coparently.app.domain.chat

import java.text.Normalizer
import java.util.Locale

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
 * Folding happens here, in Kotlin, instead: lower-case with [Locale.ROOT] (never the device
 * locale, so a Turkish-locale phone does not turn "I" into a dotless "ı"), decompose to NFD, drop
 * the combining marks. "ä" matches "a", "ё" matches "е", "č" matches "c".
 *
 * What it deliberately does not do: fold "ß" to "ss" (a change of length that a highlight could
 * not map back cleanly), or treat Cyrillic and Latin look-alikes as one letter.
 */
object TextFold {

    /** Nonspacing combining marks — the accents NFD splits off a letter. */
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

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
            val codePoint = text.codePointAt(index)
            val next = index + Character.charCount(codePoint)
            val piece = foldCodePoint(codePoint)
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

    private fun foldCodePoint(codePoint: Int): String {
        if (Character.isWhitespace(codePoint)) return " "
        val lower = String(Character.toChars(codePoint)).lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    }
}
