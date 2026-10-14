package com.coparently.app.domain.friends

import com.coparently.app.domain.model.Event

/**
 * One answer from a calendar friend's read of the family's events (phase 2).
 *
 * Read from Firestore through the family-scoped query the rules admit a friend, and never stored
 * in Room: the friend's phone keeps no copy of somebody else's family, as a professional's does
 * not (CLAUDE.md item 29).
 */
sealed interface FriendCalendarRead {

    /** The family's agreed events in the window asked for, occurrences expanded, sorted by start. */
    data class Events(val events: List<Event>) : FriendCalendarRead

    /**
     * The rules refused the read: the grant was revoked, or it lapsed. A retry would be refused
     * the same way, so the screen says the access ended instead of offering one.
     */
    data object AccessEnded : FriendCalendarRead

    /** Anything else — no connection, a server error, a family id that names nobody. Retryable. */
    data object Failed : FriendCalendarRead
}
