package com.coparently.app.domain.chat

import com.coparently.app.domain.model.Message

/**
 * The part of a message a search result shows: the match with a little context either side.
 *
 * @property text What to render, already carrying a leading and/or trailing "…" when the message
 *   was cut.
 * @property matchStart Start of the match within [text].
 * @property matchEnd End of the match within [text], exclusive.
 */
data class SearchSnippet(val text: String, val matchStart: Int, val matchEnd: Int)

/** One message that matched, with the snippet to show for it. */
data class ChatSearchHit(val message: Message, val snippet: SearchSnippet)

/**
 * What a search found.
 *
 * @property hits Matching messages, newest first, at most [ChatSearch.MAX_HITS].
 * @property truncated Whether more messages matched than [hits] holds — the screen says so rather
 *   than letting a capped list pass for the whole answer.
 */
data class ChatSearchResult(val hits: List<ChatSearchHit>, val truncated: Boolean) {
    companion object {
        /** Nothing found, or nothing asked. */
        val EMPTY = ChatSearchResult(emptyList(), truncated = false)
    }
}

/**
 * Search over one conversation's messages (MON-15) — pure, so every rule here is unit-tested.
 *
 * **Local only.** The input is this device's Room copy of the thread; nothing here, and nothing
 * that calls it, asks Firestore. A server-side search would need an index that shows message text
 * to a service, and the mirror already holds the thread.
 *
 * Matching is a substring test over [TextFold]ed text, so case and diacritics do not matter in
 * any of the five languages. SQLite's `LIKE` cannot do that (it folds ASCII case only), which is
 * why the database is asked only for *candidates* — see [candidatePattern] — and the decision is
 * made here.
 */
object ChatSearch {

    /** Folded characters a query needs before it is searched; one letter matches half a thread. */
    const val MIN_QUERY_LENGTH = 2

    /** Most hits returned; more than a screen of them means the query should be longer. */
    const val MAX_HITS = 100

    /** Characters of context kept before a match, before trimming to a word boundary. */
    const val CONTEXT_BEFORE = 24

    /** Characters of context kept after a match, before trimming to a word boundary. */
    const val CONTEXT_AFTER = 64

    /** First code point outside ASCII. */
    private const val ASCII_LIMIT = 128

    private const val ELLIPSIS = "…"

    /** The escape character the DAO's `LIKE … ESCAPE '\'` clause names. */
    const val LIKE_ESCAPE = '\\'

    /**
     * The query as it is matched: folded, trimmed, and with runs of whitespace collapsed to one
     * space.
     */
    fun normalizeQuery(query: String): String =
        TextFold.simple(query).trim().replace(Regex(" +"), " ")

    /** Whether [query] is long enough to search at all. */
    fun isSearchable(query: String): Boolean = normalizeQuery(query).length >= MIN_QUERY_LENGTH

    /**
     * The `LIKE` pattern the DAO narrows the conversation with before [search] decides.
     *
     * It must never exclude a message [search] would accept, so it is a real substring pattern
     * only when `LIKE` and the fold agree on every character of the query: ASCII digits and
     * punctuation ("15:00", "50%", "2026"). Anything with a letter or a space in it gets `%` —
     * every text message of the conversation — because `LIKE` would miss "čas" for "cas" and
     * "Привет" for "привет". The one thing the narrow pattern can still miss is a message whose
     * *stored* text carries a combining mark on a digit or a canonically decomposing punctuation
     * character (the Greek question mark), neither of which a keyboard produces.
     *
     * `%`, `_` and the escape character itself are escaped, so "50%" searches for a percent sign
     * rather than for anything starting with 50.
     */
    fun candidatePattern(query: String): String {
        val trimmed = query.trim()
        val exact = trimmed.isNotEmpty() && trimmed.all { char ->
            char.code < ASCII_LIMIT && !char.isLetter() && !char.isWhitespace()
        }
        return if (exact) "%${escapeLike(trimmed)}%" else "%"
    }

    /** [text] with every `LIKE` wildcard, and the escape character, escaped by [LIKE_ESCAPE]. */
    fun escapeLike(text: String): String = buildString(text.length) {
        text.forEach { char ->
            if (char == '%' || char == '_' || char == LIKE_ESCAPE) append(LIKE_ESCAPE)
            append(char)
        }
    }

    /**
     * Where [normalizedQuery] first occurs in [text], as a range of [text]'s own indices, or null.
     *
     * @param normalizedQuery A query already passed through [normalizeQuery].
     */
    fun findMatch(text: String, normalizedQuery: String): IntRange? {
        if (normalizedQuery.isEmpty()) return null
        val folded = TextFold.fold(text)
        val at = folded.text.indexOf(normalizedQuery)
        if (at < 0) return null
        return folded.starts[at] until folded.ends[at + normalizedQuery.length - 1]
    }

    /**
     * The match in [text] with up to [CONTEXT_BEFORE]/[CONTEXT_AFTER] characters either side,
     * trimmed to whole words where the context allows, line breaks shown as spaces.
     *
     * @param match A range [findMatch] returned for [text].
     */
    fun snippet(text: String, match: IntRange): SearchSnippet {
        val flat = buildString(text.length) {
            text.forEach { append(if (it.isWhitespace()) ' ' else it) }
        }
        var from = (match.first - CONTEXT_BEFORE).coerceAtLeast(0)
        var to = (match.last + 1 + CONTEXT_AFTER).coerceAtMost(flat.length)
        if (from > 0) {
            val space = flat.indexOf(' ', from)
            from = if (space in from until match.first) space + 1 else from.alignedBackward(flat)
        }
        if (to < flat.length) {
            val space = flat.lastIndexOf(' ', to - 1)
            to = if (space > match.last) space else to.alignedForward(flat)
        }
        val prefix = if (from > 0) ELLIPSIS else ""
        val suffix = if (to < flat.length) ELLIPSIS else ""
        return SearchSnippet(
            text = prefix + flat.substring(from, to) + suffix,
            matchStart = prefix.length + match.first - from,
            matchEnd = prefix.length + match.last + 1 - from
        )
    }

    /**
     * Every message of [messages] that contains [query], newest first as given, capped at
     * [MAX_HITS].
     *
     * @param messages The conversation's candidates, newest first.
     * @param query What the reader typed.
     */
    fun search(messages: List<Message>, query: String): ChatSearchResult {
        val normalized = normalizeQuery(query)
        if (normalized.length < MIN_QUERY_LENGTH) return ChatSearchResult.EMPTY
        val hits = messages.asSequence()
            .mapNotNull { message ->
                findMatch(message.content, normalized)?.let { ChatSearchHit(message, snippet(message.content, it)) }
            }
            .take(MAX_HITS + 1)
            .toList()
        return ChatSearchResult(hits.take(MAX_HITS), truncated = hits.size > MAX_HITS)
    }

    /** This index, moved back one if it would split a surrogate pair. */
    private fun Int.alignedBackward(text: String): Int =
        if (this > 0 && Character.isLowSurrogate(text[this])) this - 1 else this

    /** This end index, moved forward one if it would split a surrogate pair. */
    private fun Int.alignedForward(text: String): Int =
        if (this < text.length && Character.isLowSurrogate(text[this])) this + 1 else this
}
