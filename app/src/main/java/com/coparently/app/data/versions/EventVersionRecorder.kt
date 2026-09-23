package com.coparently.app.data.versions

import android.util.Log
import com.coparently.app.data.local.dao.EventVersionOutboxDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.entity.EventVersionOutboxEntity
import com.coparently.app.data.remote.firebase.FirestoreEventVersionDataSource
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps every saved revision of a shared event (MON-4; CLAUDE.md item 25).
 *
 * Two halves. [record] runs on the save path and only writes Room, so it cannot fail for want of
 * a network and a parent's save never waits on it. [flush] uploads what [record] queued — right
 * after each save, and again on every sync — and deletes a row only once the server has it.
 *
 * **Private events never reach [record]'s outbox** (item 3). The check is here as well as at the
 * call sites, because a revision is a copy of the event on its way to Firestore, and item 3 is
 * about exactly that.
 */
@Singleton
class EventVersionRecorder @Inject constructor(
    private val outboxDao: EventVersionOutboxDao,
    private val userDao: UserDao,
    private val remote: FirestoreEventVersionDataSource
) {
    private val flushLock = Mutex()

    /** Outlives any screen, like `ParentsSource`'s: a revision's upload is not a screen's work. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Queues one revision of an event, as it was just saved.
     *
     * Never throws: a revision that cannot be queued is logged and dropped, because a parent's
     * event must not fail to save because its history could not be written. That is the one way
     * a revision can go missing, and it takes a failed local database write to reach it.
     *
     * @param snapshot The event document exactly as `EventRepositoryImpl.toFirestoreMap()` built
     *   it for this save — plus the tombstone fields on a delete.
     * @param isPrivate Whether the event is private. A private event records nothing.
     * @param audience The event's audience as this save computed it.
     */
    // Each argument is a field of the revision; see `EventVersionDocument.document`.
    @Suppress("LongParameterList")
    suspend fun record(
        eventId: String,
        kind: EventVersionKind,
        editorUid: String,
        isPrivate: Boolean,
        audience: List<String>,
        familyId: String?,
        snapshot: Map<String, Any?>,
        deviceTimeMillis: Long = System.currentTimeMillis()
    ) {
        if (isPrivate || editorUid.isBlank()) return
        try {
            outboxDao.insert(
                EventVersionOutboxEntity(
                    id = UUID.randomUUID().toString(),
                    eventId = eventId,
                    kind = kind.wire,
                    editorUid = editorUid,
                    deviceTimeMillis = deviceTimeMillis,
                    snapshotJson = EventVersionDocument.encodeSnapshot(snapshot),
                    audienceJson = EventVersionDocument.encodeAudience(audience),
                    familyId = familyId,
                    attempts = 0
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Revision of event $eventId could not be queued", e)
        }
    }

    /**
     * Uploads every queued revision [editorUid] saved, oldest first.
     *
     * Never throws. Stops at the first failure that is not a refusal — offline, the rest would
     * fail the same way, and trying each one is only noise. A refusal is counted against its row
     * and the pass moves on; a row refused [MAX_REFUSALS] times stays in Room and is not tried
     * again, because it is still this device's record of what the parent saved and the export
     * prints it as one the server never accepted.
     *
     * @param editorUid The signed-in account. Rows another account queued on this device are
     *   left alone — the rule refuses a revision whose author is not the caller.
     */
    suspend fun flush(editorUid: String) {
        if (editorUid.isBlank()) return
        try {
            flushLock.withLock {
                val liveAudience = entitled(editorUid)
                for (row in outboxDao.pending(editorUid, MAX_REFUSALS)) {
                    if (!upload(row, liveAudience)) break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Revision outbox not flushed; it stays queued for the next sync", e)
        }
    }

    /**
     * [flush], without making the caller wait for it.
     *
     * The save path's call. A Firestore write does not complete until the server acknowledges it,
     * so awaiting the upload would hold a parent's save open for as long as the phone is offline —
     * for a write the next sync will make anyway.
     */
    fun flushInBackground(editorUid: String) {
        scope.launch { flush(editorUid) }
    }

    /** Every revision [editorUid] saved that the server does not have, for the export. */
    suspend fun undelivered(editorUid: String): List<EventVersionOutboxEntity> =
        outboxDao.undelivered(editorUid)

    /**
     * Uploads one row; returns false when the pass should stop (the server is unreachable).
     *
     * The audience is **narrowed** to live pairing state here, never widened: the rule requires
     * every uid in it to be the caller or a current co-parent, so a revision queued before an
     * unpair would otherwise be refused for ever. The caller is always kept, because the rule
     * requires them in it and because a parent's own history must stay readable to them.
     */
    private suspend fun upload(row: EventVersionOutboxEntity, liveAudience: Set<String>): Boolean {
        val kind = EventVersionKind.fromWire(row.kind)
        if (kind == null) {
            // Written by a newer build this one cannot speak for; leave it for that build.
            outboxDao.recordRefusal(row.id)
            return true
        }
        val audience = (listOf(row.editorUid) + EventVersionDocument.decodeAudience(row.audienceJson))
            .filter { it in liveAudience }
            .distinct()
        val document = EventVersionDocument.document(
            eventId = row.eventId,
            kind = kind,
            editorUid = row.editorUid,
            deviceTimeMillis = row.deviceTimeMillis,
            audience = audience,
            familyId = row.familyId,
            snapshot = EventVersionDocument.decodeSnapshot(row.snapshotJson)
        )
        return try {
            // Bounded: a Firestore write does not complete until the server acknowledges it, so
            // offline this would hold the flush — and the sync waiting behind it — indefinitely.
            // A write that times out is still queued in Firestore's own cache; the next pass finds
            // it landed through `exists()` below.
            val landed = withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { remote.create(row.id, document) } != null
            if (landed) outboxDao.delete(row.id) else Log.w(TAG, "Revision ${row.id} not acknowledged yet")
            landed
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            onRefusal(row, e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w(TAG, "Revision ${row.id} not uploaded; it stays queued", e)
            false
        }
    }

    /**
     * Answers a Firestore failure: a lost acknowledgement, a refusal, or an outage.
     *
     * `PERMISSION_DENIED` on a create is also what a *second* create of the same id looks like —
     * the rule sees an update and refuses it — so the server is asked whether it already holds
     * the revision before the row is counted as refused.
     */
    private suspend fun onRefusal(row: EventVersionOutboxEntity, e: FirebaseFirestoreException): Boolean {
        if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            Log.w(TAG, "Revision ${row.id} not uploaded (${e.code}); it stays queued", e)
            return false
        }
        val alreadyThere = runCatching { remote.exists(row.id) }.getOrDefault(false)
        if (alreadyThere) {
            outboxDao.delete(row.id)
        } else {
            Log.w(TAG, "Revision ${row.id} of event ${row.eventId} refused by the server", e)
            outboxDao.recordRefusal(row.id)
        }
        return true
    }

    /**
     * The caller and their co-parents — the only uids the rule lets a revision's audience name.
     *
     * Every co-parent, not only the selected family's: a revision of an event in the other family
     * must keep that family's parent, and `partnerId` names only the one on screen (M-8).
     */
    private suspend fun entitled(uid: String): Set<String> {
        val me = userDao.getUserById(uid)
        val partners = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]") +
            listOfNotNull(me?.partnerId)
        return (partners.filter { it.isNotBlank() } + uid).toSet()
    }

    companion object {
        private const val TAG = "EventVersionRecorder"

        /**
         * How many refusals a revision gets before it stops being retried.
         *
         * A refusal can be transient — the first upload can race `ensureProfile`, and the rule
         * reads the caller's profile to bound the audience — so one is not enough. Ten is many
         * syncs' worth, and past that the rule is saying no for a reason a retry will not change.
         */
        const val MAX_REFUSALS = 10

        /** How long one upload may wait for the server before the pass gives up until next time. */
        private const val UPLOAD_TIMEOUT_MS = 15_000L
    }
}
