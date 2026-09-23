package com.coparently.app.domain.feed

/**
 * One read-only calendar link a parent has made (MON-17), as the server lists it.
 *
 * **There is no URL here, and that is the design.** The link's token is its only credential; the
 * server stores a SHA-256 of it and nothing that could rebuild it, so a listed link can be
 * revoked but never shown again. [CreatedCalendarFeed] is the one moment the URL exists outside
 * the calendar app it was sent to.
 *
 * @property feedId The id this link is listed and revoked by — not the token, and not its hash.
 * @property familyId The family whose calendar it serves.
 * @property createdAtMillis When it was made, epoch millis.
 * @property lastUsedAtMillis When a calendar app last fetched it, epoch millis, refreshed at most
 *   daily. A link unused for 90 days stops working and disappears from the list.
 */
data class CalendarFeedLink(
    val feedId: String,
    val familyId: String,
    val createdAtMillis: Long,
    val lastUsedAtMillis: Long
)

/**
 * A link just made, carrying the URL the server will never return again.
 *
 * @property feedId See [CalendarFeedLink.feedId].
 * @property url The `https://` form, for a calendar that asks for a URL to paste.
 * @property webcalUrl The `webcal://` form of the same link: on an iPhone, tapping it opens the
 *   "Subscribe" sheet rather than downloading a one-off copy, which is why it is the one shared.
 */
data class CreatedCalendarFeed(
    val feedId: String,
    val url: String,
    val webcalUrl: String
)

/** The server refused a new link because this parent already holds the most it allows. */
class CalendarFeedLimitException : Exception("Too many calendar links")
