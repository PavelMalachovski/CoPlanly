package com.coparently.app.data.remote.firebase

import com.coparently.app.data.requests.RequestDeadlineDocument
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `request_deadlines` — the clock of each request that waits on the other parent (phase 3a).
 *
 * The wire form is [RequestDeadlineDocument]'s; the rules decide which parent may write which
 * field. This class is the Firestore calls and nothing else: no retry, no timeout and no
 * containment of a failure — that is the caller's, which is `RequestDeadlineRepositoryImpl`. The one
 * failure it reads for itself is the denial a missing clock is answered with ([get]).
 *
 * **Every list query is keyed on `participants`**, because the read rule is (CLAUDE.md item 12):
 * Firestore judges a query by its structure, so one without `array-contains` on the caller's uid is
 * refused outright. The family and the deadline are second filters, and `firestore.indexes.json`
 * holds a composite index for each pairing.
 *
 * **Every write after the create is an `update()` of the fields one phone owns.** A `set()` over an
 * existing id is an update the rules refuse unless it is one of their five shapes, so a retried
 * create fails rather than rewriting a clock.
 *
 * **The server's times are read with [ServerTimestampBehavior.ESTIMATE]**: a write this phone has
 * made and the server has not yet stamped reads as this phone's estimate rather than as nothing.
 */
@Singleton
class FirestoreRequestDeadlineDataSource @Inject constructor(
    firestore: FirebaseFirestore
) {
    private val collection = firestore.collection(RequestDeadlineDocument.COLLECTION)

    /**
     * The clocks of [familyId] that [myUid] is a party to, live.
     *
     * The flow *fails* when the listener reports an error — a denial, or an offline device with a
     * cold cache — rather than swallowing it, as `FirestoreCustodyDataSource.observeCustody` does.
     * Containment belongs to the caller, which retries with backoff.
     */
    fun observeFamily(myUid: String, familyId: String): Flow<List<RequestDeadline>> = callbackFlow {
        val registration = collection
            .whereArrayContains(RequestDeadlineDocument.PARTICIPANTS, myUid)
            .whereEqualTo(RequestDeadlineDocument.FAMILY_ID, familyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // The flow's own close, not this class's: a clock is closed by `close(id)` below.
                    this@callbackFlow.close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toDeadline() })
            }
        awaitClose { registration.remove() }
    }

    /**
     * The clocks [myUid] is a party to whose deadline is at or after [sinceMillis], read once —
     * the sweep that re-arms the steps of every family, not only the one on screen.
     */
    suspend fun openFor(myUid: String, sinceMillis: Long): List<RequestDeadline> =
        collection
            .whereArrayContains(RequestDeadlineDocument.PARTICIPANTS, myUid)
            .whereGreaterThanOrEqualTo(RequestDeadlineDocument.ANSWER_BY_MILLIS, sinceMillis)
            .get()
            .await()
            .documents
            .mapNotNull { it.toDeadline() }

    /**
     * The clock with this [id], or null when there is none or this build cannot read it.
     *
     * A clock that does not exist answers `PERMISSION_DENIED`, not "not found": the read rule keys on
     * the stored `participants`, and a document that is not there has none. It reads as no clock.
     * Any other failure propagates.
     */
    suspend fun get(id: String): RequestDeadline? =
        try {
            collection.document(id).get().await().toDeadline()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
        }

    /**
     * Creates the clock with [fields] from [RequestDeadlineDocument.createFields], and stamps
     * `createdAt` with the server's clock — the rule requires it to be `request.time`.
     *
     * `set()` on an id that exists is an update, which the rules refuse; a second create fails.
     */
    suspend fun create(id: String, fields: Map<String, Any>) {
        collection.document(id)
            .set(fields + (RequestDeadlineDocument.CREATED_AT to FieldValue.serverTimestamp()))
            .await()
    }

    /** Updates the named [fields] of the clock [id], leaving the rest of the document as it is. */
    suspend fun update(id: String, fields: Map<String, Any>) {
        collection.document(id).update(fields).await()
    }

    /**
     * Records the last chance: its end, and the server's time for when it was given. The rule makes
     * it a write of its own, so it cannot ride on another.
     */
    suspend fun giveLastChance(id: String, untilMillis: Long) {
        update(
            id,
            mapOf(
                RequestDeadlineDocument.LAST_CHANCE_AT to FieldValue.serverTimestamp(),
                RequestDeadlineDocument.LAST_CHANCE_UNTIL_MILLIS to untilMillis
            )
        )
    }

    /**
     * Closes the clock as "No answer", with the server's time for when. The outcome is the only
     * state a clock stores.
     */
    suspend fun close(id: String) {
        update(
            id,
            mapOf(
                RequestDeadlineDocument.OUTCOME to RequestClockOutcome.NO_ANSWER.wire,
                RequestDeadlineDocument.CLOSED_AT to FieldValue.serverTimestamp()
            )
        )
    }

    /**
     * Counts one more reminder shown. An increment, so two phones' reports — or one phone's retry —
     * add up instead of overwriting, and a clock with no count yet starts at one.
     */
    suspend fun reportShown(id: String) {
        update(id, mapOf(RequestDeadlineDocument.REMINDERS_SHOWN to FieldValue.increment(1)))
    }

    /**
     * This document as a clock, or null when it is missing or this build cannot read it
     * ([RequestDeadlineDocument.parse]).
     *
     * The three server times are read apart from the document's map, with the estimate for a stamp
     * the server has not yet made, and with `get` rather than `getTimestamp`: this runs inside a
     * snapshot listener, where a value of another type must read as absent, not throw.
     */
    private fun DocumentSnapshot.toDeadline(): RequestDeadline? {
        val data = data ?: return null
        return RequestDeadlineDocument.parse(
            id = id,
            document = data,
            times = RequestDeadlineDocument.ServerTimes(
                createdAtMillis = serverTimeMillis(RequestDeadlineDocument.CREATED_AT),
                lastChanceAtMillis = serverTimeMillis(RequestDeadlineDocument.LAST_CHANCE_AT),
                closedAtMillis = serverTimeMillis(RequestDeadlineDocument.CLOSED_AT)
            )
        )
    }

    /** The server timestamp in [field] as epoch millis; null when it is absent or not a timestamp. */
    private fun DocumentSnapshot.serverTimeMillis(field: String): Long? =
        (get(field, ServerTimestampBehavior.ESTIMATE) as? Timestamp)?.toDate()?.time
}
