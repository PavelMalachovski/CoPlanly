package com.coparently.app.domain.chat

/**
 * What the pause-before-sending hint noticed in a draft (MON-19).
 *
 * @property shouting Two or more words written entirely in capitals.
 * @property raisedPunctuation A run of three or more "!" or "?".
 * @property words The draft's own words that matched the locale's list, as typed, in order,
 *   without repeats.
 */
data class ToneNudge(
    val shouting: Boolean = false,
    val raisedPunctuation: Boolean = false,
    val words: List<String> = emptyList()
) {
    /** Whether there is anything to say at all. */
    val isEmpty: Boolean get() = !shouting && !raisedPunctuation && words.isEmpty()
}

/**
 * A purely lexical second look at a draft — capitals, repeated "!!!", and a short list of words
 * that tend to land harder than meant (MON-19).
 *
 * **Not a tone model, and never presented as one.** It is three string tests; it never blocks a
 * send, and neither the draft nor the result is stored, logged or sent anywhere — the composer
 * calls this while rendering and drops the answer with the frame. A real tone check is MON-12,
 * behind SEC-1's proxy, and is a different product decision.
 */
object ToneCheck {

    /** Letters a word needs before capitals count as shouting rather than an initial. */
    const val MIN_SHOUT_LETTERS = 4

    /**
     * All-caps words needed before the hint appears. Two, not one: a single capitalised word is
     * as often an acronym — OSPOD, ASAP, a school's initials — as it is a raised voice.
     */
    const val MIN_SHOUTED_WORDS = 2

    /** "!!!" or "?!?" — three marks in a row. */
    private val RAISED_PUNCTUATION = Regex("[!?]{3,}")

    /** A word: a run of letters, with apostrophes and hyphens inside it. */
    private val WORD = Regex("\\p{L}+(?:['’-]\\p{L}+)*")

    /** The list entry suffix that means "any word starting with this". */
    private const val PREFIX_MARK = '*'

    /**
     * Checks [draft] against the three rules.
     *
     * @param draft What is in the composer.
     * @param wordList The locale's list. An entry ending in `*` matches every word that starts
     *   with it, which is how an inflected language lists one word once ("глуп*" for all its
     *   endings). Case and diacritics are ignored on both sides, via [TextFold].
     */
    fun check(draft: String, wordList: Collection<String>): ToneNudge {
        if (draft.isBlank()) return ToneNudge()
        val words = WORD.findAll(draft).map { it.value }.toList()
        val shouted = words.count { word ->
            word.count(Char::isLetter) >= MIN_SHOUT_LETTERS && word.none(Char::isLowerCase) &&
                word.any(Char::isUpperCase)
        }
        return ToneNudge(
            shouting = shouted >= MIN_SHOUTED_WORDS,
            raisedPunctuation = RAISED_PUNCTUATION.containsMatchIn(draft),
            words = listedWords(words, wordList)
        )
    }

    private fun listedWords(words: List<String>, wordList: Collection<String>): List<String> {
        val entries = wordList.map { it.trim() }.filter { it.isNotEmpty() }
        val exact = entries.filterNot { it.endsWith(PREFIX_MARK) }.map(TextFold::simple).toSet()
        val prefixes = entries.filter { it.endsWith(PREFIX_MARK) }
            .map { TextFold.simple(it.dropLast(1)) }
            .filter { it.isNotEmpty() }
        return words
            .filter { word ->
                val folded = TextFold.simple(word)
                folded in exact || prefixes.any { folded.startsWith(it) }
            }
            .distinctBy(TextFold::simple)
    }
}
