package com.coparently.app.domain.repository

import com.coparently.app.domain.chat.ChatSearchResult

/**
 * Search over one chat thread, and what the thread needs to show a result (MON-15).
 *
 * Its own interface rather than two more members of [MessageRepository], whose every read has a
 * Firestore mirror behind it. Nothing here has one: search reads this device's Room copy and
 * nothing else — a server-side search would need an index that exposes message text to a
 * service, and the mirror already holds the thread.
 */
interface ChatSearchRepository {

    /**
     * The text messages of [conversationId] that contain [query], newest first, ignoring case and
     * diacritics (see `com.coparently.app.domain.chat.ChatSearch`).
     *
     * @param conversationId The deterministic conversation id; nothing outside it is read.
     * @param query What the reader typed.
     */
    suspend fun search(conversationId: String, query: String): ChatSearchResult

    /**
     * How many messages of [conversationId] were sent at or after [sentAtMillis] — the window the
     * thread must hold for that message to be on screen.
     */
    suspend fun countMessagesSince(conversationId: String, sentAtMillis: Long): Int
}
