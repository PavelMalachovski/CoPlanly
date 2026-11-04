package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.CallableCode
import com.coparently.app.data.remote.firebase.CallableException
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.domain.feed.CalendarFeedLimitException
import com.coparently.app.domain.feed.CalendarFeedLink
import com.coparently.app.domain.feed.CreatedCalendarFeed
import com.coparently.app.domain.repository.CalendarFeedRepository
import kotlinx.coroutines.CancellationException

/**
 * [CalendarFeedRepository] over the `createCalendarFeed`, `listCalendarFeeds` and
 * `revokeCalendarFeed` callables (`functions/index.js`).
 *
 * Nothing is cached or stored on the device. The one thing worth keeping — a link's URL — is the
 * one thing that must not be kept: it is a bearer credential for the family's calendar, and the
 * parent has already put it where it belongs, in the calendar app they shared it to.
 *
 * Through [CallableFunctions] since MON-24 phase 2 (Task 8d), which on Android is the SDK call this
 * made itself; a refusal arrives as a [CallableException] with the SDK's code.
 */
class CalendarFeedRepositoryImpl(
    private val functions: CallableFunctions
) : CalendarFeedRepository {

    override suspend fun create(familyId: String, language: String): Result<CreatedCalendarFeed> =
        call("createCalendarFeed", mapOf("familyId" to familyId, "locale" to language)) { data ->
            CreatedCalendarFeed(
                feedId = data["feedId"] as? String ?: error("createCalendarFeed returned no feedId"),
                url = data["url"] as? String ?: error("createCalendarFeed returned no url"),
                webcalUrl = data["webcalUrl"] as? String ?: error("createCalendarFeed returned no webcalUrl")
            )
        }

    override suspend fun list(): Result<List<CalendarFeedLink>> =
        call("listCalendarFeeds", emptyMap()) { data ->
            (data["feeds"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toLink() }
        }

    override suspend fun revoke(feedId: String): Result<Unit> =
        call("revokeCalendarFeed", mapOf("feedId" to feedId)) { }

    /** One listed link, or null for an entry without an id — which could not be revoked anyway. */
    private fun Map<*, *>.toLink(): CalendarFeedLink? {
        val feedId = (this["feedId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return CalendarFeedLink(
            feedId = feedId,
            familyId = this["familyId"] as? String ?: "",
            // Callable results cross as JSON, so a number may arrive as Int, Long or Double.
            createdAtMillis = (this["createdAtMillis"] as? Number)?.toLong() ?: 0L,
            lastUsedAtMillis = (this["lastUsedAtMillis"] as? Number)?.toLong() ?: 0L
        )
    }

    private suspend fun <T> call(
        name: String,
        payload: Map<String, Any>,
        parse: (Map<*, *>) -> T
    ): Result<T> = try {
        val result = functions.call(name, payload)
        Result.success(parse((result as? Map<*, *>) ?: emptyMap<String, Any>()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: CallableException) {
        Result.failure(
            if (e.code == CallableCode.RESOURCE_EXHAUSTED) CalendarFeedLimitException() else e
        )
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.failure(e)
    }
}
