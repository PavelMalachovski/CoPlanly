package com.coparently.app.data.remote.firebase

import com.coparently.app.domain.expenses.FamilySettings
import com.coparently.app.domain.expenses.SplitRatio
import com.coparently.app.domain.expenses.SplitRatioDecision
import com.coparently.app.domain.expenses.SplitRatioOutcome
import com.coparently.app.domain.expenses.SplitRatioProposal
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the one money-agreement document a pair shares.
 *
 * Shaped after [FirestoreCustodyDataSource], down to the constraints its rule imposes:
 * `participants` is stored pre-sorted, is compared with order-sensitive equality on update, and
 * must be present on every write, so a non-merge `set()` that omitted it would be denied.
 *
 * **Read by document id only.** `firestore.rules` grants `allow get`, deliberately not
 * `allow read`, so a `whereArrayContains("participants", uid)` query would be rejected outright
 * rather than quietly working — which keeps CLAUDE.md item 12's trap structurally impossible
 * here instead of merely conventionally avoided.
 *
 * Every timestamp is **epoch millis**. Not the naive `LocalDateTime` `SharedCustody` uses: that
 * field decides which phone's document survives and its zone-less ordering is a known defect
 * (SEC-4), and a greenfield document costs nothing to get right.
 */
@Singleton
class FirestoreFamilySettingsDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val collection = firestore.collection(COLLECTION)

    /**
     * Observes the pair's settings, emitting `null` while the document does not exist.
     *
     * Fails the flow on a listener error rather than swallowing it, exactly as the custody
     * source does: containment belongs to the caller, which retries with backoff.
     *
     * @param documentId The pair's id, from `CustodyKey.of`.
     */
    fun observeSettings(documentId: String): Flow<FamilySettings?> = callbackFlow {
        val subscription = collection
            .document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.data?.toSettings())
            }
        awaitClose { subscription.remove() }
    }

    /**
     * One read of the pair's settings, or null when there is no document yet.
     *
     * @param documentId The pair's id.
     * @param source Where to read from: Firestore's default (the server, or the cache offline), or
     *   [Source.SERVER] for a decision that must not rest on a cached copy — the end of an expense
     *   split proposal's clock — which then throws offline.
     */
    suspend fun getSettings(documentId: String, source: Source = Source.DEFAULT): FamilySettings? {
        val document = collection.document(documentId)
        // The default read stays the plain get() every existing caller (and its test doubles) uses.
        val snapshot = (if (source == Source.DEFAULT) document.get() else document.get(source)).await()
        return snapshot.data?.toSettings()
    }

    /**
     * Reads the pair's settings and writes [transform]'s answer back, in one transaction, so a
     * proposal answered, withdrawn or replaced a moment before is never overwritten by a write built
     * on an older copy. Firestore runs [transform] again on the newer document when the one it read
     * changes before the write, so [transform] must be pure. It needs the server, so it fails
     * offline; the caller tries again later.
     *
     * @param documentId The pair's id.
     * @param transform The document as it stands, or null when there is none or it cannot describe
     *   an agreement, to the document to write — a whole document, as [setSettings] writes it — or
     *   null to write nothing.
     * @return The document as written, or null when [transform] answered null and nothing was written.
     */
    suspend fun transact(documentId: String, transform: (FamilySettings?) -> FamilySettings?): FamilySettings? {
        val document = collection.document(documentId)
        return firestore.runTransaction<FamilySettings?> { transaction ->
            val current = transaction.get(document).data?.toSettings()
            transform(current)?.also { next -> transaction.set(document, next.toDocument()) }
        }.await()
    }

    /**
     * Writes the whole document.
     *
     * A whole-document `set()` rather than a merge, matching the custody source: a key omitted
     * from the map is what clears it, and a merge would leave a withdrawn proposal in place
     * forever.
     *
     * @param documentId The pair's id.
     * @param settings What to store; its `participants` must already be sorted.
     */
    suspend fun setSettings(documentId: String, settings: FamilySettings) {
        collection.document(documentId).set(settings.toDocument()).await()
    }

    private fun FamilySettings.toDocument(): Map<String, Any> = buildMap {
        put("participants", participants)
        put("momShareBasisPoints", ratio.momShareBasisPoints)
        put("lastModifiedBy", lastModifiedBy)
        put("lastModifiedAtMillis", lastModifiedAtMillis)
        // Omitted rather than written as null when absent: omission is what clears a withdrawn
        // proposal under a whole-document `set()`.
        proposal?.let { put("proposal", it.toMap()) }
        lastDecision?.let { put("lastDecision", it.toMap()) }
    }

    private fun SplitRatioProposal.toMap(): Map<String, Any> = mapOf(
        "momShareBasisPoints" to ratio.momShareBasisPoints,
        "proposedBy" to proposedBy,
        "proposedAtMillis" to proposedAtMillis
    )

    private fun SplitRatioDecision.toMap(): Map<String, Any> = mapOf(
        "outcome" to outcome.name,
        "by" to by,
        "atMillis" to atMillis,
        "proposalAtMillis" to proposalAtMillis
    )

    /**
     * The document as it comes back, or null when it cannot describe an agreement.
     *
     * Every number crossing Firestore arrives as a `Long`, never an `Int` — the wire format has
     * one integer type — so each is narrowed through `Number` rather than cast. A
     * `ClassCastException` raised inside a snapshot listener is not something the caller's
     * `retryWhen` can see; it is a crash.
     */
    private fun Map<String, Any?>.toSettings(): FamilySettings? {
        val participants = (this["participants"] as? List<*>)?.mapNotNull { it as? String }
            ?: return null
        if (participants.size != PARTICIPANT_COUNT) return null
        val ratio = SplitRatio.fromStored((this["momShareBasisPoints"] as? Number)?.toInt())
            ?: return null
        return FamilySettings(
            ratio = ratio,
            participants = participants,
            lastModifiedBy = (this["lastModifiedBy"] as? String).orEmpty(),
            lastModifiedAtMillis = (this["lastModifiedAtMillis"] as? Number)?.toLong() ?: 0L,
            proposal = (this["proposal"] as? Map<*, *>)?.toProposal(),
            lastDecision = (this["lastDecision"] as? Map<*, *>)?.toDecision()
        )
    }

    private fun Map<*, *>.toProposal(): SplitRatioProposal? {
        val ratio = SplitRatio.fromStored((this["momShareBasisPoints"] as? Number)?.toInt())
            ?: return null
        val by = (this["proposedBy"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return SplitRatioProposal(
            ratio = ratio,
            proposedBy = by,
            proposedAtMillis = (this["proposedAtMillis"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun Map<*, *>.toDecision(): SplitRatioDecision? {
        val outcome = (this["outcome"] as? String)
            ?.let { name -> SplitRatioOutcome.entries.firstOrNull { it.name == name } }
            ?: return null
        return SplitRatioDecision(
            outcome = outcome,
            by = (this["by"] as? String).orEmpty(),
            atMillis = (this["atMillis"] as? Number)?.toLong() ?: 0L,
            proposalAtMillis = (this["proposalAtMillis"] as? Number)?.toLong() ?: 0L
        )
    }

    private companion object {
        const val COLLECTION = "family_settings"

        /** A pair is exactly two uids; anything else cannot be a family agreement. */
        const val PARTICIPANT_COUNT = 2
    }
}
