package com.coparently.app.domain.friends

import com.coparently.app.domain.guests.GuestGrantPolicy
import java.time.Duration
import java.time.Instant

/**
 * How long a family friend may read the family's calendar, as a parent picks it when inviting.
 *
 * The guest lengths (a week, 30 days, 90 days) and one more: **while the parents are linked**
 * (owner decision, October 2026 — testers asked whether a grandmother's access had to run out).
 * A friend reads only the calendar's non-private events, never the custody schedule, the money,
 * the chat or a child's record, and the grant already ends with its family: `unpairCoParent`
 * deletes every grant naming the ended `familyId`, and either parent can revoke it at any time.
 * So "while linked" is stored as an end far beyond any family's life ([WHILE_LINKED_EXPIRY_MILLIS])
 * — the rules, the callable and the sweep stay as they are, and nothing that reads an expiry has
 * to learn a value that means "none". A child's guest keeps a real end ([com.coparently.app.domain
 * .guests.GuestAccessDuration] has no such entry): a medical record is not a calendar.
 */
enum class FriendAccessDuration(private val duration: Duration?) {

    /** A visit. */
    WEEK(Duration.ofDays(7)),

    /** The default, as for a guest. */
    MONTH(GuestGrantPolicy.DEFAULT_DURATION),

    /** A school term, roughly. */
    QUARTER(Duration.ofDays(90)),

    /** Until the parents unlink or revoke it. */
    WHILE_LINKED(null);

    /** The expiry to put on a grant offered at [from], as epoch milliseconds. */
    fun expiryFrom(from: Instant): Long = duration?.let { from.plus(it).toEpochMilli() } ?: WHILE_LINKED_EXPIRY_MILLIS

    companion object {
        /** What the invite sheet starts on. */
        val DEFAULT = MONTH

        /** The last millisecond of 31 December 9999 UTC: the end a "while linked" grant carries. */
        const val WHILE_LINKED_EXPIRY_MILLIS = 253_402_300_799_999L

        /** Anything this far out was given "while linked"; no dated choice reaches a century. */
        private const val WHILE_LINKED_THRESHOLD_MILLIS = 4_102_444_800_000L // 1 January 2100 UTC

        /** Whether a grant ending at [expiresAtMillis] was given "while linked" rather than a date. */
        fun isWhileLinked(expiresAtMillis: Long): Boolean = expiresAtMillis >= WHILE_LINKED_THRESHOLD_MILLIS
    }
}
