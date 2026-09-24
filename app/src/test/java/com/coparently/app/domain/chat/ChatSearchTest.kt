package com.coparently.app.domain.chat

import com.coparently.app.domain.model.Message
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chat search's matching rules (MON-15).
 *
 * The database cannot be asked these questions — SQLite's `LIKE` folds ASCII case and nothing
 * else, and the DAO's query shape is not testable without an Android runtime — so everything
 * that decides whether a message matches, and what the reader sees of it, lives in pure code and
 * is pinned here. The first group is the reason the fold exists at all: four of the five
 * languages the app ships in are written with letters `LIKE` gets wrong.
 */
class ChatSearchTest {

    // ---- the fold: case and diacritics ------------------------------------

    @Test
    fun `a query without accents finds the accented word`() {
        assertEquals("čas", matched("Kolik je čas?", "cas"))
    }

    @Test
    fun `an accented query finds the plain word too`() {
        assertEquals("cas", matched("Kolik je cas?", "čas"))
    }

    @Test
    fun `case is ignored in Cyrillic, which LIKE would not do`() {
        assertEquals("Привет", matched("Привет, как дела?", "привет"))
    }

    @Test
    fun `yo and ye are one letter for search`() {
        assertEquals("ёлку", matched("Купили ёлку", "елку"))
    }

    @Test
    fun `an umlaut folds to its base letter`() {
        assertEquals("Mäuse", matched("Die Mäuse sind da", "MAUSE"))
    }

    @Test
    fun `a decomposed accent is highlighted with its letter`() {
        // "c" followed by a combining caron, as some keyboards and pastes produce it.
        val text = "c\u030Cas"
        assertEquals(text, matched(text, "cas"))
    }

    @Test
    fun `a dotted capital I folds without the device locale`() {
        assertEquals("İstanbul", matched("Letíme do İstanbul", "istanbul"))
    }

    // ---- what a full-text index could not find (the MON-15 FTS finding) -----------------
    //
    // An FTS4 `unicode61` index answers token prefixes and folds only what its own tables fold.
    // Measured against SQLite 3.45 (September 2026), `remove_diacritics` 1 and 2 alike: "ick*"
    // does not find "pickup", and "иогурт*", "елка*", "іі*" find nothing where the text says
    // "йогурт", "ёлка", "її". Any prefilter put in front of this search has to keep these, which
    // is why the DAO's candidates are still a `LIKE` (docs/ROADMAP.md MON-15).

    @Test
    fun `a query matches inside a word, not only at its start`() {
        assertEquals("ick", matched("Pickup at five", "ick"))
    }

    @Test
    fun `a short i without its breve finds the letter with it`() {
        assertEquals("йогурт", matched("Купи йогурт", "иогурт"))
        assertEquals("її", matched("Забери її о п'ятій", "ії"))
    }

    @Test
    fun `a message without the query does not match`() {
        assertNull(ChatSearch.findMatch("Uvidíme se v pět", ChatSearch.normalizeQuery("čas")))
    }

    @Test
    fun `whitespace in a query is trimmed and collapsed`() {
        assertEquals("see you", ChatSearch.normalizeQuery("  See   you "))
        assertEquals("see\nyou", matched("see\nyou", "see you"))
    }

    // ---- what is searched at all -----------------------------------------

    @Test
    fun `a single letter is not searched`() {
        assertFalse(ChatSearch.isSearchable("a "))
        assertTrue(ChatSearch.isSearchable("ab"))
        assertEquals(ChatSearchResult.EMPTY, ChatSearch.search(listOf(message("1", "a lot")), "a"))
    }

    @Test
    fun `results keep the newest-first order they were given`() {
        val messages = listOf(message("new", "pickup at five"), message("old", "no pickup"))

        val hits = ChatSearch.search(messages, "PICKUP").hits

        assertEquals(listOf("new", "old"), hits.map { it.message.id })
    }

    @Test
    fun `a capped list says it is capped`() {
        val messages = (0 until ChatSearch.MAX_HITS + 5).map { message("m$it", "ok $it") }

        val result = ChatSearch.search(messages, "ok")

        assertEquals(ChatSearch.MAX_HITS, result.hits.size)
        assertTrue(result.truncated)
        assertEquals("m0", result.hits.first().message.id)
    }

    @Test
    fun `an uncapped list says it is whole`() {
        val result = ChatSearch.search(listOf(message("1", "ok")), "ok")

        assertEquals(1, result.hits.size)
        assertFalse(result.truncated)
    }

    // ---- the LIKE prefilter and its escaping -------------------------------

    @Test
    fun `a query with a letter asks the database for the whole conversation`() {
        // LIKE would miss "čas" for "cas" and "Привет" for "привет"; the fold decides instead.
        assertEquals("%", ChatSearch.candidatePattern("cas"))
        assertEquals("%", ChatSearch.candidatePattern("привет"))
        assertEquals("%", ChatSearch.candidatePattern("a_b"))
    }

    @Test
    fun `a space also widens the prefilter, because the fold treats a line break as one`() {
        assertEquals("%", ChatSearch.candidatePattern("15 00"))
    }

    @Test
    fun `digits and punctuation narrow the prefilter to a substring`() {
        assertEquals("%15:00%", ChatSearch.candidatePattern(" 15:00 "))
    }

    @Test
    fun `LIKE wildcards in a query are escaped so they mean themselves`() {
        assertEquals("%50\\%%", ChatSearch.candidatePattern("50%"))
        assertEquals("%1\\_2%", ChatSearch.candidatePattern("1_2"))
        assertEquals("%\\\\%", ChatSearch.candidatePattern("\\"))
    }

    @Test
    fun `escaping covers both wildcards and the escape character`() {
        assertEquals("100\\%\\_\\\\", ChatSearch.escapeLike("100%_\\"))
        assertEquals("plain", ChatSearch.escapeLike("plain"))
    }

    // ---- the snippet -----------------------------------------------------

    @Test
    fun `a short message is shown whole with the match marked`() {
        val snippet = snippetFor("See you at five", "five")

        assertEquals("See you at five", snippet.text)
        assertEquals("five", snippet.text.substring(snippet.matchStart, snippet.matchEnd))
    }

    @Test
    fun `a long message is cut to whole words around the match`() {
        val text = "word ".repeat(20) + "target" + " tail".repeat(30)

        val snippet = snippetFor(text, "target")

        assertEquals("target", snippet.text.substring(snippet.matchStart, snippet.matchEnd))
        assertTrue(snippet.text.startsWith("…word"), snippet.text)
        assertTrue(snippet.text.endsWith("tail…"), snippet.text)
        assertTrue(
            snippet.text.length <= ChatSearch.CONTEXT_BEFORE + "target".length + ChatSearch.CONTEXT_AFTER + 2,
            "the window must stay bounded: ${snippet.text.length}"
        )
    }

    @Test
    fun `a match at the very start gets no leading ellipsis`() {
        val snippet = snippetFor("target" + " tail".repeat(30), "target")

        assertEquals(0, snippet.matchStart)
        assertFalse(snippet.text.startsWith("…"))
    }

    @Test
    fun `line breaks are shown as spaces`() {
        val snippet = snippetFor("first line\nsecond line", "second")

        assertFalse(snippet.text.contains('\n'))
        assertEquals("second", snippet.text.substring(snippet.matchStart, snippet.matchEnd))
    }

    @Test
    fun `a cut never splits an emoji in half`() {
        val text = "x" + "\uD83D\uDE00".repeat(20) + "y" + "abc"

        val snippet = snippetFor(text, "abc")

        assertFalse(Character.isLowSurrogate(snippet.text[1]), "the text after the ellipsis starts mid-emoji")
        assertEquals("abc", snippet.text.substring(snippet.matchStart, snippet.matchEnd))
    }

    @Test
    fun `the snippet marks the original accented text`() {
        val snippet = snippetFor("Kolik je čas?", "cas")

        assertEquals("čas", snippet.text.substring(snippet.matchStart, snippet.matchEnd))
    }

    private fun matched(text: String, query: String): String {
        val range = assertNotNull(ChatSearch.findMatch(text, ChatSearch.normalizeQuery(query)))
        return text.substring(range.first, range.last + 1)
    }

    private fun snippetFor(text: String, query: String): SearchSnippet {
        val range = assertNotNull(ChatSearch.findMatch(text, ChatSearch.normalizeQuery(query)))
        return ChatSearch.snippet(text, range)
    }

    private fun message(id: String, content: String) = Message(
        id = id,
        conversationId = "c",
        senderId = "u1",
        senderName = "Alice",
        content = content
    )
}
