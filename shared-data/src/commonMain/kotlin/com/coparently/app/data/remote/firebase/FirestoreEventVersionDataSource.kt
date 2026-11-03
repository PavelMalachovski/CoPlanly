package com.coparently.app.data.remote.firebase

import com.coparently.app.data.versions.EventVersionDocument

/**
 * `event_versions` — the immutable revisions of events (MON-4).
 *
 * Two operations and no more: create one revision, and read the ones a parent may see. There is
 * no update and no delete here because `firestore.rules` allows neither to anybody, and a method
 * that could only ever fail would sit here named like ordinary API.
 */
class FirestoreEventVersionDataSource(
    private val firestore: FirestoreClient
) {
    private fun revision(versionId: String) = DocumentPath(EventVersionDocument.COLLECTION, versionId)

    /**
     * Writes one revision, stamping [EventVersionDocument.RECORDED_AT] with the server's clock.
     *
     * `set()` on a fresh id is a create. On an id that already exists it is an *update*, which
     * the rule refuses — so a retry of a revision whose acknowledgement was lost fails here, and
     * the caller answers that with [exists] rather than treating it as a refusal.
     *
     * @param versionId The document id, minted once on the device and kept across retries.
     * @param document The revision, from [EventVersionDocument.document].
     */
    suspend fun create(versionId: String, document: Map<String, Any?>) {
        firestore.set(revision(versionId), document + (EventVersionDocument.RECORDED_AT to ServerValue.ServerTimestamp))
    }

    /**
     * The Firestore code [failure] carries, or null when it is not a Firestore failure: what
     * `EventVersionRecorder` tells a refusal from an outage by (MON-24 phase 2, Task 8b, which moved
     * it off the Android SDK's exception type).
     */
    fun errorCodeOf(failure: Throwable): FirestoreErrorCode? = firestore.errorCodeOf(failure)

    /**
     * Whether the server already holds [versionId] — asked of the server, never the cache.
     *
     * The cache would answer yes for a write this device queued a moment ago and the server
     * refused, which is exactly the case this question exists to tell apart.
     */
    suspend fun exists(versionId: String): Boolean =
        firestore.get(revision(versionId), ReadSource.SERVER).exists

    /**
     * Every revision [uid] may read, as parsed revisions — the export's one query.
     *
     * Filtered on `sharedWith` because the read rule is keyed on it (CLAUDE.md item 12). Not
     * bounded by date: a revision that moved an event into the range was saved while the event
     * sat somewhere else, and the export needs the event's whole history. Exports are rare and
     * user-initiated, so reading one family's revisions once is the right price for that.
     *
     * Read from the server, never the cache: a record assembled from whatever this phone happened
     * to have cached would be a record of this phone.
     */
    suspend fun readableBy(uid: String): List<EventVersionDocument.Parsed> =
        firestore.get(
            DocumentQuery(EventVersionDocument.COLLECTION).whereArrayContains(EventVersionDocument.SHARED_WITH, uid),
            ReadSource.SERVER
        )
            .mapNotNull { snapshot ->
                val data = snapshot.data ?: return@mapNotNull null
                EventVersionDocument.Parsed.from(
                    versionId = snapshot.id,
                    data = data,
                    recordedAtMillis = snapshot.timestamp(EventVersionDocument.RECORDED_AT)?.toEpochMillis()
                )
            }
}
