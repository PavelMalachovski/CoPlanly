package com.coparently.app.domain.repository

import com.coparently.app.domain.feed.CalendarFeedLink
import com.coparently.app.domain.feed.CreatedCalendarFeed

/**
 * The read-only calendar links a parent hands to an iPhone or any other calendar app (MON-17).
 *
 * Every call goes through a Cloud Function: `calendar_feeds` is closed to clients in
 * `firestore.rules`, because its document ids are hashes of the links' only credential. Failures
 * come back as `Result.failure`; a refused create for having too many links carries
 * [com.coparently.app.domain.feed.CalendarFeedLimitException].
 */
interface CalendarFeedRepository {

    /**
     * Makes a new link to [familyId]'s calendar. The server checks the caller is a parent in it.
     *
     * @param language The app language, so the few words the feed writes itself ("With Alice")
     *   are in it.
     */
    suspend fun create(familyId: String, language: String): Result<CreatedCalendarFeed>

    /** This parent's live links, newest first, across every family they are in. */
    suspend fun list(): Result<List<CalendarFeedLink>>

    /** Ends the link [feedId]. Revoking one that is already gone succeeds. */
    suspend fun revoke(feedId: String): Result<Unit>
}
