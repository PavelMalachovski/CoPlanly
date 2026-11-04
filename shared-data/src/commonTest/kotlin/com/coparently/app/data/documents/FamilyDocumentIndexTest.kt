package com.coparently.app.data.documents

import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import com.coparently.app.data.local.entity.FamilyDocumentCacheEntity
import com.coparently.app.data.remote.firebase.DocumentPath
import com.coparently.app.data.remote.firebase.DocumentQuery
import com.coparently.app.data.remote.firebase.FirestoreErrorCode
import com.coparently.app.data.remote.firebase.QueryResult
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient
import com.coparently.app.data.remote.firebase.RecordingFirestoreClient.Operation
import com.coparently.app.data.remote.firebase.SetMerge
import com.coparently.app.data.remote.firebase.StoredDocument
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.VaultListing
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The vault index's listener and writes (MON-23) through the Firestore seam since MON-24 phase 2
 * (Task 8d): the query keyed on the reader and the family, metadata changes included, this phone's
 * unconfirmed write skipped, Firestore's own cache told apart from the server, a failure that
 * leaves the screen's flow open, and the create and the tombstone as `set()` and `update()`.
 */
class FamilyDocumentIndexTest {

    private val snapshots = MutableSharedFlow<QueryResult>()
    private val firestore = RecordingFirestoreClient().apply { queryResultFlow = { snapshots } }
    private val index = FamilyDocumentIndex(firestore, FamilyDocumentIndexCache(EmptyCacheDao()))

    @Test
    fun `the listener asks for the reader's documents of the family with metadata changes`() = runTest {
        backgroundScope.launch { index.observe(ME, FAMILY).toList(mutableListOf()) }
        runCurrent()

        assertEquals(
            listOf<Operation>(
                Operation.ObserveQueryWithMetadata(
                    DocumentQuery("family_documents")
                        .whereArrayContains("sharedWith", ME)
                        .whereEqualTo("familyId", FAMILY)
                )
            ),
            firestore.operations
        )
    }

    @Test
    fun `an unconfirmed write and the cache's own answer say nothing and the server's is listed`() =
        runTest {
            val seen = mutableListOf<VaultListing?>()
            backgroundScope.launch { index.observe(ME, FAMILY).toList(seen) }
            runCurrent()

            snapshots.emit(QueryResult(listOf(stored(ORDER)), hasPendingWrites = true, isFromCache = true))
            snapshots.emit(QueryResult(listOf(stored(ORDER)), hasPendingWrites = false, isFromCache = true))
            runCurrent()
            assertTrue(seen.isEmpty())

            snapshots.emit(QueryResult(listOf(stored(ORDER)), hasPendingWrites = false, isFromCache = false))
            runCurrent()
            assertEquals(listOf<VaultListing?>(VaultListing(listOf(ORDER))), seen)
        }

    @Test
    fun `a failed listener reads as unavailable and the flow stays open`() = runTest {
        firestore.queryResultFlow = null
        firestore.failNext(RecordingFirestoreClient.Failure(FirestoreErrorCode.PERMISSION_DENIED))
        val seen = mutableListOf<VaultListing?>()
        val collecting = backgroundScope.launch { index.observe(ME, FAMILY).toList(seen) }
        runCurrent()

        assertEquals(listOf<VaultListing?>(null), seen)
        assertTrue(collecting.isActive)
    }

    @Test
    fun `a create is a whole-document set and a delete a tombstone update`() = runTest {
        index.write(ORDER)
        index.tombstone(ORDER.id, deletedBy = ME, atMillis = 5L)

        assertEquals(
            listOf(
                Operation.Set(
                    DocumentPath("family_documents", ORDER.id),
                    FamilyDocumentMapper.toFirestoreMap(ORDER),
                    SetMerge.Overwrite
                ),
                Operation.Update(
                    DocumentPath("family_documents", ORDER.id),
                    mapOf("deletedAtMillis" to 5L, "deletedBy" to ME)
                )
            ),
            firestore.writes
        )
    }

    private fun stored(document: FamilyDocument) =
        StoredDocument(document.id, FamilyDocumentMapper.toFirestoreMap(document))

    /** A cache with nothing stored, which stores nothing. */
    private class EmptyCacheDao : FamilyDocumentCacheDao {
        override suspend fun liveIn(familyId: String): List<FamilyDocumentCacheEntity> = emptyList()

        override suspend fun clearFamily(familyId: String) = Unit

        override suspend fun upsertAll(rows: List<FamilyDocumentCacheEntity>) = Unit
    }

    private companion object {
        const val ME = "alice"
        const val FAMILY = "alice__bob"

        val ORDER = FamilyDocument(
            id = "order",
            familyId = FAMILY,
            createdByFirebaseUid = ME,
            title = "Custody order",
            category = DocumentCategory.COURT_ORDER,
            fileName = "order.pdf",
            storagePath = "family_documents/$FAMILY/order/order.pdf",
            contentType = "application/pdf",
            sizeBytes = 10,
            sha256 = "ab",
            createdAtMillis = 1L
        )
    }
}
