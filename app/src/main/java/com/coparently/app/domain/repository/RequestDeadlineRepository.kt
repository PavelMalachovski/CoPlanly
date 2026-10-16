package com.coparently.app.domain.repository

import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestStart
import kotlinx.coroutines.flow.Flow

/**
 * The clocks of requests that wait on the other parent: reading them, starting one, and the five
 * writes each phone makes as a request is received, opened, reminded about, given a last chance and
 * closed (phase 3a, spec §5.1).
 *
 * **Nothing here throws, and nothing here waits long on the server** (cancellation aside, which is
 * never swallowed). A read that cannot be answered reads as "no clock"; a write answers whether it
 * was accepted. A write the server has not answered after ten seconds counts as written, because
 * offline Firestore keeps it queued and sends it later, and a background job must not hang until the
 * system stops it (ruling 14). A write the server refused counts as not written. A request whose
 * clock was never written behaves as one without a clock.
 *
 * Which parent may write which field is the rules' to decide; callers check their role first, so a
 * refusal here is not how a wrong role is found out.
 */
interface RequestDeadlineRepository {

    /** The signed-in parent's uid, or null when nobody is signed in. */
    fun signedInUid(): String?

    /**
     * The clocks of [familyId] that [myUid] is a party to, live. A listener that keeps failing is
     * retried a few times with a growing pause and then reads as no clocks, rather than failing.
     *
     * Only the family it is asked for: which one is the caller's choice, usually the family on
     * screen, and a clock of another family is never mixed in.
     */
    fun observeFamily(myUid: String, familyId: String): Flow<List<RequestDeadline>>

    /**
     * The clocks of every family that [myUid] is a party to whose deadline is at or after
     * [sinceMillis], read once. Empty when they cannot be read.
     */
    suspend fun openFor(myUid: String, sinceMillis: Long): List<RequestDeadline>

    /**
     * The clock with this [id], or null when there is none — and when it cannot be read: the rules
     * answer a clock that does not exist with a denial, as they would one the caller may not read.
     */
    suspend fun get(id: String): RequestDeadline?

    /**
     * Writes the clock for [start] as the signed-in parent, then schedules its deadline and its end.
     *
     * @return The clock as written, with no creation time yet — the server stamps that — or null
     *   when it was not written: nobody is signed in, or the rules refused it. Nothing is scheduled
     *   then.
     */
    suspend fun start(start: RequestStart): RequestDeadline?

    /**
     * Records that this phone received [clock] and what it planned, once and in one write.
     *
     * @return Whether the write was accepted, or is queued.
     */
    suspend fun markDelivered(clock: RequestDeadline, deliveredAtMillis: Long, reminderTimesMillis: List<Long>): Boolean

    /**
     * Records that the answering parent opened the request, once.
     *
     * @return Whether the write was accepted, or is queued.
     */
    suspend fun markSeen(clock: RequestDeadline, seenAtMillis: Long): Boolean

    /**
     * Counts one more reminder shown.
     *
     * @return Whether the write was accepted, or is queued.
     */
    suspend fun reportReminderShown(clock: RequestDeadline): Boolean

    /**
     * Records the last chance the proposer gave, which ends at [untilMillis]; the server stamps when.
     *
     * @return Whether the write was accepted, or is queued.
     */
    suspend fun giveLastChance(clock: RequestDeadline, untilMillis: Long): Boolean

    /**
     * Closes the clock as "No answer"; the server stamps when.
     *
     * @return Whether the write was accepted, or is queued.
     */
    suspend fun close(clock: RequestDeadline): Boolean
}
