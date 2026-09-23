package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.domain.chat.ChatSearch
import com.coparently.app.domain.chat.ChatSearchResult
import com.coparently.app.domain.repository.ChatSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-only [ChatSearchRepository]: a `LIKE` for candidates, [ChatSearch] for the decision.
 *
 * The split exists because SQLite's `LIKE` folds ASCII case and nothing else — it cannot find
 * "čas" from "cas" or "Привет" from "привет" — so for any query with a letter in it the database
 * returns the conversation's text messages and the fold in Kotlin decides. The cost is bounded by
 * the one conversation, and it is paid off the main thread after the screen's debounce. The
 * cheaper answer is an FTS4 table, which is a schema change and is recorded as MON-15's later
 * step in docs/ROADMAP.md.
 */
@Singleton
class ChatSearchRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : ChatSearchRepository {

    override suspend fun search(conversationId: String, query: String): ChatSearchResult {
        if (!ChatSearch.isSearchable(query)) return ChatSearchResult.EMPTY
        val candidates = messageDao.searchCandidates(conversationId, ChatSearch.candidatePattern(query))
        // Mapping and folding a long thread is real work; keep it off the caller's (main) thread.
        return withContext(Dispatchers.Default) {
            ChatSearch.search(candidates.map { it.toDomain() }, query)
        }
    }

    override suspend fun countMessagesSince(conversationId: String, sentAtMillis: Long): Int =
        messageDao.countMessagesSince(conversationId, sentAtMillis)
}
