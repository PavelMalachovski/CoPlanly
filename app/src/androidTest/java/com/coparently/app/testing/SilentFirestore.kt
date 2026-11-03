package com.coparently.app.testing

import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FieldPath
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.FirestoreTransaction
import com.coparently.app.data.remote.firebase.QueryResult
import com.coparently.app.data.remote.firebase.ReadSource
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.StoredDocument
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A [FirestoreClient] that never answers: every read, write and listener waits until it is
 * cancelled, and nothing is classified as a Firestore failure.
 *
 * It is what `fakeFirebaseModule` gives the data layer when a run has no emulators — the same
 * Firestore the relaxed mocks were (a relaxed mock's `Task` never completes), without the mocks.
 * The client used to be GitLive's wrapper over a relaxed `FirebaseFirestore` mock, and on API 26,
 * where MockK cannot mock a final class, the `DocumentReference` the mock handed out was a real
 * one with no key: the first listener threw a `NullPointerException` out of the SDK and took the
 * screen with it (`ParentRoles`, on every signed-in walk of the main screens).
 */
object SilentFirestore : FirestoreClient {

    override suspend fun get(path: DocumentPath, source: ReadSource): StoredDocument = awaitCancellation()

    override fun observe(path: DocumentPath): Flow<StoredDocument> = flow { awaitCancellation() }

    override suspend fun set(path: DocumentPath, data: Map<String, Any?>, merge: SetMerge) {
        awaitCancellation()
    }

    override suspend fun update(path: DocumentPath, fields: Map<String, Any?>) {
        awaitCancellation()
    }

    override suspend fun update(path: DocumentPath, field: String, value: Any?) {
        awaitCancellation()
    }

    override suspend fun update(path: DocumentPath, field: FieldPath, value: Any?) {
        awaitCancellation()
    }

    override suspend fun increment(path: DocumentPath, field: String, by: Long) {
        awaitCancellation()
    }

    override suspend fun delete(path: DocumentPath) {
        awaitCancellation()
    }

    // An id is made on the phone, with nothing to wait for; nothing written under it ever lands.
    override fun newDocumentId(collection: String): String = "silent-$collection"

    override suspend fun get(query: DocumentQuery, source: ReadSource): List<StoredDocument> =
        awaitCancellation()

    override fun observe(query: DocumentQuery): Flow<List<StoredDocument>> = flow { awaitCancellation() }

    override fun observeWithMetadata(query: DocumentQuery): Flow<QueryResult> = flow { awaitCancellation() }

    override suspend fun <T> transaction(block: suspend FirestoreTransaction.() -> T): T = awaitCancellation()

    override fun errorCodeOf(failure: Throwable): FirestoreErrorCode? = null
}
