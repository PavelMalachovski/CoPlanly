package com.coparently.app.data.remote.firebase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [FirestoreClient] for tests: an in-memory store that records every operation, as the data
 * sources asked for it, so a test can say *which* operation a data source made — a `set()` or an
 * `update()`, from the server or not — and not only what ended up stored.
 *
 * Reads answer from [documents] (a missing path is a document that does not exist) and queries
 * from [queryAnswer]. A listener emits once, what a read would answer, unless the test sets
 * [documentFlow] or [queryFlow]. [failNext] makes the next operation throw.
 */
class RecordingFirestoreClient : FirestoreClient {

    /** One operation, as it reached the client. */
    sealed interface Operation {
        /** A one-shot read of [path] from [source]. */
        data class Get(val path: DocumentPath, val source: ReadSource) : Operation

        /** A one-shot read of [query] from [source]. */
        data class Query(val query: DocumentQuery, val source: ReadSource) : Operation

        /** A listener on [path]. */
        data class ObserveDocument(val path: DocumentPath) : Operation

        /** A listener on [query]. */
        data class ObserveQuery(val query: DocumentQuery) : Operation

        /** A `set()`; [inTransaction] when a transaction made it. */
        data class Set(
            val path: DocumentPath,
            val data: Map<String, Any?>,
            val merge: SetMerge,
            val inTransaction: Boolean = false
        ) : Operation

        /** An `update()` of a map of dotted fields. */
        data class Update(val path: DocumentPath, val fields: Map<String, Any?>) : Operation

        /** An `update()` of one field, named by a dotted path or by segments. */
        data class UpdateField(val path: DocumentPath, val field: String?, val fieldPath: FieldPath?, val value: Any?) :
            Operation

        /** An integer increment. */
        data class Increment(val path: DocumentPath, val field: String, val by: Long) : Operation

        /** A delete. */
        data class Delete(val path: DocumentPath) : Operation

        /** A transaction began. */
        data object Transaction : Operation
    }

    /** A Firestore failure with a status, as [errorCodeOf] classifies the SDK's. */
    class Failure(val code: FirestoreErrorCode, message: String = code.name) : Exception(message)

    /** Every operation, in order. */
    val operations = mutableListOf<Operation>()

    /** The stored documents. */
    val documents = mutableMapOf<DocumentPath, StoredDocument>()

    /** What a query answers. */
    var queryAnswer: (DocumentQuery) -> List<StoredDocument> = { emptyList() }

    /** What a document listener emits. */
    var documentFlow: ((DocumentPath) -> Flow<StoredDocument>)? = null

    /** What a query listener emits. */
    var queryFlow: ((DocumentQuery) -> Flow<List<StoredDocument>>)? = null

    private var pendingFailure: Throwable? = null

    /** Makes the next operation throw [failure] instead of doing anything. */
    fun failNext(failure: Throwable) {
        pendingFailure = failure
    }

    /** Stores [data] at [path] as the server would hand it back, or removes it for null. */
    fun put(path: DocumentPath, data: Map<String, Any?>?, hasPendingWrites: Boolean = false) {
        if (data == null) {
            documents.remove(path)
        } else {
            documents[path] = StoredDocument(path.id, data, hasPendingWrites = hasPendingWrites)
        }
    }

    /** The writes, without the reads. */
    val writes: List<Operation>
        get() = operations.filter {
            it is Operation.Set || it is Operation.Update || it is Operation.UpdateField ||
                it is Operation.Increment || it is Operation.Delete
        }

    /** The data of the last `set()` of [path]. */
    fun lastSet(path: DocumentPath): Map<String, Any?> =
        operations.filterIsInstance<Operation.Set>().last { it.path == path }.data

    override suspend fun get(path: DocumentPath, source: ReadSource): StoredDocument {
        record(Operation.Get(path, source))
        return documents[path] ?: StoredDocument(path.id, null)
    }

    override fun observe(path: DocumentPath): Flow<StoredDocument> {
        operations += Operation.ObserveDocument(path)
        return documentFlow?.invoke(path) ?: flow {
            takeFailure()?.let { throw it }
            emit(documents[path] ?: StoredDocument(path.id, null))
        }
    }

    override suspend fun set(path: DocumentPath, data: Map<String, Any?>, merge: SetMerge) {
        record(Operation.Set(path, data, merge))
        store(path, data, merge)
    }

    override suspend fun update(path: DocumentPath, fields: Map<String, Any?>) {
        record(Operation.Update(path, fields))
        val existing = documents[path]?.data ?: throw Failure(FirestoreErrorCode.NOT_FOUND)
        documents[path] = StoredDocument(path.id, existing + fields)
    }

    override suspend fun update(path: DocumentPath, field: String, value: Any?) {
        record(Operation.UpdateField(path, field, null, value))
    }

    override suspend fun update(path: DocumentPath, field: FieldPath, value: Any?) {
        record(Operation.UpdateField(path, null, field, value))
    }

    override suspend fun increment(path: DocumentPath, field: String, by: Long) {
        record(Operation.Increment(path, field, by))
    }

    override suspend fun delete(path: DocumentPath) {
        record(Operation.Delete(path))
        documents.remove(path)
    }

    /** How many ids [newDocumentId] has made: the next is `auto-<n + 1>`. */
    private var idsMade = 0

    override fun newDocumentId(collection: String): String = "auto-${++idsMade}"

    override suspend fun get(query: DocumentQuery, source: ReadSource): List<StoredDocument> {
        record(Operation.Query(query, source))
        return queryAnswer(query)
    }

    override fun observe(query: DocumentQuery): Flow<List<StoredDocument>> {
        operations += Operation.ObserveQuery(query)
        return queryFlow?.invoke(query) ?: flow {
            takeFailure()?.let { throw it }
            emit(queryAnswer(query))
        }
    }

    override suspend fun <T> transaction(block: suspend FirestoreTransaction.() -> T): T {
        record(Operation.Transaction)
        val client = this
        return object : FirestoreTransaction {
            override suspend fun get(path: DocumentPath): StoredDocument = client.get(path, ReadSource.SERVER)

            override fun set(path: DocumentPath, data: Map<String, Any?>) {
                operations += Operation.Set(path, data, SetMerge.Overwrite, inTransaction = true)
                store(path, data, SetMerge.Overwrite)
            }
        }.block()
    }

    override fun errorCodeOf(failure: Throwable): FirestoreErrorCode? = (failure as? Failure)?.code

    private fun record(operation: Operation) {
        operations += operation
        takeFailure()?.let { throw it }
    }

    private fun takeFailure(): Throwable? = pendingFailure.also { pendingFailure = null }

    private fun store(path: DocumentPath, data: Map<String, Any?>, merge: SetMerge) {
        val stored = when (merge) {
            SetMerge.Overwrite -> data
            SetMerge.Merge, is SetMerge.Fields -> documents[path]?.data.orEmpty() + data
        }
        documents[path] = StoredDocument(path.id, stored)
    }
}
