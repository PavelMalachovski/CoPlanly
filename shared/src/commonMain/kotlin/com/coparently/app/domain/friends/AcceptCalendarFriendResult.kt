package com.coparently.app.domain.friends

/**
 * What `acceptCalendarFriendInvitation` returns: the pair whose calendar the caller may now
 * read, and the instant that access ends.
 *
 * @property familyParents Exactly two UIDs — the friend queries events by them.
 * @property expiresAtMillis Epoch millis, always positive; see [CalendarFriendPolicy].
 */
data class AcceptCalendarFriendResult(
    val familyParents: List<String>,
    val expiresAtMillis: Long
)
