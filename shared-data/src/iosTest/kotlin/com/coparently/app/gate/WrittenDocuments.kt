@file:OptIn(ExperimentalAtomicApi::class)

package com.coparently.app.gate

import com.coparently.app.data.files.StorageBucket
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.FieldPath
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestoreTransaction
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.StoredDocument
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * Every Firestore document the gate's two phones wrote, as `collection/id` (MON-24 phase 3, Task 7).
 *
 * Kept by [RecordingFirestoreClient], which sits between the production data layer and GitLive's
 * client, so the list is what the production code wrote rather than what a test remembered to name:
 * a document a repository writes that nobody expected still reaches `tools/ios/check-ios-writes.js`,
 * which fails on a collection it has no Android shape for. Safe across threads (Room's dispatcher
 * and the main queue both write), by compare-and-set over an immutable value.
 */
internal class WrittenDocuments {

    private val state = AtomicReference(Recorded())

    /** One write that the server refused, with what the client threw. */
    data class Refusal(val path: String, val failure: String)

    /** One push queued, as the client wrote it. */
    data class QueuedPush(val path: String, val data: Map<String, Any?>)

    private data class Recorded(
        val paths: List<String> = emptyList(),
        val files: List<String> = emptyList(),
        val refused: List<Refusal> = emptyList(),
        val pushes: List<QueuedPush> = emptyList()
    )

    /** The documents written, in the order of their first write, each once. */
    val paths: List<String> get() = state.load().paths

    /** The Storage objects uploaded (part C, Task 10), in the order of their upload, each once. */
    val files: List<String> get() = state.load().files

    /** The writes the server refused. */
    val refused: List<Refusal> get() = state.load().refused

    /** The documents queued in `notification_queue`, with the data written. */
    val pushes: List<QueuedPush> get() = state.load().pushes

    /** Records a write of [path] that landed. */
    fun written(path: DocumentPath) = change {
        if (path.text in it.paths) it else it.copy(paths = it.paths + path.text)
    }

    /** Records a refused write of [path]. */
    fun refused(path: DocumentPath, failure: Throwable) =
        change { it.copy(refused = it.refused + Refusal(path.text, failure.toString())) }

    /** Records an upload of the Storage object at [path] that landed. */
    fun stored(path: String) = change {
        if (path in it.files) it else it.copy(files = it.files + path)
    }

    /** Records a refused upload of the Storage object at [path]. */
    fun refusedFile(path: String, failure: Throwable) =
        change { it.copy(refused = it.refused + Refusal("$STORAGE_PREFIX$path", failure.toString())) }

    /** Records a queued push. */
    fun queued(path: DocumentPath, data: Map<String, Any?>) =
        change { it.copy(pushes = it.pushes + QueuedPush(path.text, data)) }

    /**
     * Writes the list to [file], one `collection/id` per line and then one `storage:<object path>`
     * per Storage object, creating its folder: what the checker reads after the tests.
     */
    fun writeTo(file: String) {
        val path = Path(file)
        path.parent?.let { SystemFileSystem.createDirectories(it) }
        val text = (paths + files.map { STORAGE_PREFIX + it }).joinToString("\n", postfix = "\n")
        SystemFileSystem.sink(path).buffered().use { sink -> sink.writeString(text) }
    }

    private fun change(transform: (Recorded) -> Recorded) {
        while (true) {
            val current = state.load()
            if (state.compareAndSet(current, transform(current))) return
        }
    }

    private val DocumentPath.text: String get() = "$collection/$id"

    companion object {
        /** Marks a Storage object's line in the written list, as `check-ios-writes.js` reads it. */
        const val STORAGE_PREFIX = "storage:"
    }
}

/**
 * The production [FirestoreClient] ([delegate], GitLive's on the iPhone) with every write recorded
 * in [written]: each `set`, `update`, `increment` and transaction `set` once it landed, and each
 * refusal with what was thrown (rethrown unchanged, so the production code sees what it would). Reads
 * go straight through.
 */
internal class RecordingFirestoreClient(
    private val delegate: FirestoreClient,
    private val written: WrittenDocuments
) : FirestoreClient by delegate {

    override suspend fun set(path: DocumentPath, data: Map<String, Any?>, merge: SetMerge) {
        record(path) { delegate.set(path, data, merge) }
        if (path.collection == NOTIFICATION_QUEUE) written.queued(path, data)
    }

    override suspend fun update(path: DocumentPath, fields: Map<String, Any?>) =
        record(path) { delegate.update(path, fields) }

    override suspend fun update(path: DocumentPath, field: String, value: Any?) =
        record(path) { delegate.update(path, field, value) }

    override suspend fun update(path: DocumentPath, field: FieldPath, value: Any?) =
        record(path) { delegate.update(path, field, value) }

    override suspend fun increment(path: DocumentPath, field: String, by: Long) =
        record(path) { delegate.increment(path, field, by) }

    override suspend fun <T> transaction(block: suspend FirestoreTransaction.() -> T): T {
        val inTransaction = mutableListOf<DocumentPath>()
        val result = try {
            delegate.transaction {
                inTransaction.clear()
                RecordingTransaction(this, inTransaction).block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            inTransaction.forEach { written.refused(it, e) }
            throw e
        }
        inTransaction.forEach(written::written)
        return result
    }

    private suspend fun record(path: DocumentPath, write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            written.refused(path, e)
            throw e
        }
        written.written(path)
    }

    /** A transaction whose `set`s are noted in [paths], recorded once the transaction commits. */
    private class RecordingTransaction(
        private val delegate: FirestoreTransaction,
        private val paths: MutableList<DocumentPath>
    ) : FirestoreTransaction {
        override suspend fun get(path: DocumentPath): StoredDocument = delegate.get(path)

        override fun set(path: DocumentPath, data: Map<String, Any?>) {
            paths += path
            delegate.set(path, data)
        }
    }

    private companion object {
        const val NOTIFICATION_QUEUE = "notification_queue"
    }
}

/**
 * The production [StorageBucket] ([delegate], `IosStorageBucket` on the iPhone) with every upload
 * recorded in [written] once it landed, and each refusal with what was thrown (rethrown unchanged).
 * Reads, downloads and deletes go straight through.
 */
internal class RecordingStorageBucket(
    private val delegate: StorageBucket,
    private val written: WrittenDocuments
) : StorageBucket by delegate {

    override suspend fun putFile(
        path: String,
        file: Path,
        contentType: String,
        customMetadata: Map<String, String>,
        onProgress: (Float) -> Unit
    ) = record(path) { delegate.putFile(path, file, contentType, customMetadata, onProgress) }

    override suspend fun putBytes(
        path: String,
        bytes: ByteArray,
        contentType: String,
        customMetadata: Map<String, String>
    ) = record(path) { delegate.putBytes(path, bytes, contentType, customMetadata) }

    private suspend fun record(path: String, upload: suspend () -> Unit) {
        try {
            upload()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            written.refusedFile(path, e)
            throw e
        }
        written.stored(path)
    }
}
