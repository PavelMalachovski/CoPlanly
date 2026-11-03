package com.coparently.app.data.remote.firebase

import com.coparently.app.domain.InternalSharedApi
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.Transaction
import dev.gitlive.firebase.firestore.code
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dev.gitlive.firebase.firestore.FieldPath as GitLiveFieldPath

/**
 * [FirestoreClient] over GitLive's firebase-kotlin-sdk: on Android the Android SDK the app has
 * always used (GitLive wraps it, call for call), on the iPhone the iOS SDK.
 *
 * What GitLive's common API cannot say goes through a small platform half beside this file
 * (`FirestorePlatform.kt`), never around it:
 * - **Reading a document as plain values.** GitLive decodes into `@Serializable` types; the data
 *   sources read the SDK's own map, leniently, with `as?`. The halves hand over that map with the
 *   SDK's timestamp turned into a [FirestoreTimestamp] ([toStoredDocument]).
 * - **An integer increment.** GitLive's Android `FieldValue.increment(Int)` is the SDK's *double*
 *   increment, which would turn an integer counter into a double; the Android half calls the
 *   SDK's `increment(long)` ([incrementInteger]).
 *
 * Writes go through GitLive's encoder, which hands the SDK a map of the same values: text,
 * booleans, `Int`, `Long` and `Double` as they are, lists and nested maps, nulls kept, and
 * [ServerValue]s and [FirestoreTimestamp]s turned into the SDK's own ([toGitLive]).
 *
 * @param firestore The Firestore instance — on Android, the app's own instance, wrapped (see
 *   `documentStoreOf` in the Android half), so its settings, its emulator host and a test's
 *   replacement all still apply.
 */
@OptIn(InternalSharedApi::class)
class GitLiveFirestoreClient(private val firestore: FirebaseFirestore) : FirestoreClient {

    override suspend fun get(path: DocumentPath, source: ReadSource): StoredDocument =
        reference(path).get(source.toGitLive()).toStoredDocument()

    override fun observe(path: DocumentPath): Flow<StoredDocument> =
        reference(path).snapshots.map { it.toStoredDocument() }

    override suspend fun set(path: DocumentPath, data: Map<String, Any?>, merge: SetMerge) {
        val encoded = plainToGitLive(data)
        val reference = reference(path)
        when (merge) {
            SetMerge.Overwrite -> reference.set(encoded)
            SetMerge.Merge -> reference.set(encoded, merge = true)
            is SetMerge.Fields -> reference.set(encoded, *merge.fields.map { it.toGitLive() }.toTypedArray())
        }
    }

    override suspend fun update(path: DocumentPath, fields: Map<String, Any?>) {
        reference(path).update(plainToGitLive(fields))
    }

    override suspend fun update(path: DocumentPath, field: String, value: Any?) {
        val encoded = toGitLive(value)
        reference(path).updateFields { field to encoded }
    }

    override suspend fun update(path: DocumentPath, field: FieldPath, value: Any?) {
        val encoded = toGitLive(value)
        val fieldPath = field.toGitLive()
        reference(path).updateFields { fieldPath to encoded }
    }

    override suspend fun increment(path: DocumentPath, field: String, by: Long) {
        reference(path).incrementInteger(field, by)
    }

    override suspend fun delete(path: DocumentPath) {
        reference(path).delete()
    }

    override suspend fun get(query: DocumentQuery, source: ReadSource): List<StoredDocument> =
        build(query).get(source.toGitLive()).documents.map { it.toStoredDocument() }

    override fun observe(query: DocumentQuery): Flow<List<StoredDocument>> =
        build(query).snapshots.map { snapshot -> snapshot.documents.map { it.toStoredDocument() } }

    override suspend fun <T> transaction(block: suspend FirestoreTransaction.() -> T): T =
        firestore.runTransaction { GitLiveTransaction(this).block() }

    override fun errorCodeOf(failure: Throwable): FirestoreErrorCode? =
        (failure as? FirebaseFirestoreException)?.code?.name?.let(FirestoreErrorCode::named)

    private fun reference(path: DocumentPath): DocumentReference =
        firestore.collection(path.collection).document(path.id)

    /** [query] as GitLive's: its filters, then its order, then its bound, each in its own order. */
    private fun build(query: DocumentQuery): Query {
        var built: Query = firestore.collection(query.collection)
        query.filters.forEach { filter ->
            val field = filter.field
            val value = toGitLive(filter.value)
            built = built.where {
                when (filter.op) {
                    FilterOp.EQUAL_TO -> field equalTo value
                    FilterOp.ARRAY_CONTAINS -> field contains requireNotNull(value) { "array-contains on null" }
                    FilterOp.GREATER_THAN -> field greaterThan requireNotNull(value) { "> null" }
                    FilterOp.GREATER_THAN_OR_EQUAL_TO -> field greaterThanOrEqualTo requireNotNull(value) { ">= null" }
                    FilterOp.LESS_THAN -> field lessThan requireNotNull(value) { "< null" }
                }
            }
        }
        query.order.forEach { order ->
            built = built.orderBy(order.field, if (order.descending) Direction.DESCENDING else Direction.ASCENDING)
        }
        query.limitToLast?.let { built = built.limitToLast(it) }
        return built
    }

    /** One transaction's reads and writes, through GitLive's. */
    private inner class GitLiveTransaction(private val transaction: Transaction) : FirestoreTransaction {

        override suspend fun get(path: DocumentPath): StoredDocument =
            transaction.get(reference(path)).toStoredDocument()

        override fun set(path: DocumentPath, data: Map<String, Any?>) {
            transaction.set(reference(path), plainToGitLive(data))
        }
    }

    companion object {

        /**
         * A map of plain values as GitLive's encoder takes it; see [toGitLive]. Public for the app's
         * R8 probe alone, which encodes one through GitLive in the minified build.
         */
        @InternalSharedApi
        fun plainToGitLive(data: Map<String, Any?>): Map<String, Any?> =
            data.entries.associateTo(LinkedHashMap()) { (key, value) -> key to toGitLive(value) }

        /**
         * A plain value ([FirestoreValues]) as GitLive's encoder takes it: a [ServerValue] or a
         * [FirestoreTimestamp] as GitLive's own, maps and lists element by element, anything else
         * as it is — the encoder hands it to the SDK as the SDK would have been handed it.
         */
        internal fun toGitLive(value: Any?): Any? = when (value) {
            null -> null
            ServerValue.ServerTimestamp -> FieldValue.serverTimestamp
            ServerValue.Delete -> FieldValue.delete
            is FirestoreTimestamp -> Timestamp(value.seconds, value.nanoseconds)
            is Map<*, *> -> value.entries.associateTo(LinkedHashMap<String, Any?>()) { (key, entry) ->
                requireNotNull(key as? String) { "A Firestore map key must be text, not $key" } to toGitLive(entry)
            }
            is List<*> -> value.map(::toGitLive)
            is Set<*> -> value.map(::toGitLive)
            else -> value
        }

        private fun ReadSource.toGitLive(): Source = when (this) {
            ReadSource.DEFAULT -> Source.DEFAULT
            ReadSource.SERVER -> Source.SERVER
        }

        private fun FieldPath.toGitLive(): GitLiveFieldPath = GitLiveFieldPath(*segments.toTypedArray())
    }
}
