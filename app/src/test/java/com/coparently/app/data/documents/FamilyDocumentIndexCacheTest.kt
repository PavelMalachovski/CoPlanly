package com.coparently.app.data.documents

import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import com.coparently.app.data.local.entity.FamilyDocumentCacheEntity
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.VaultListing
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Room cache of the vault index (MON-23, schema 43): what a server answer stores, what the
 * screen gets when the listener cannot reach the server, and that one family never sees another's.
 */
class FamilyDocumentIndexCacheTest {

    private val dao = FakeCacheDao()
    private val cache = FamilyDocumentIndexCache(dao)

    @Test
    fun `a server answer is listed as current and replaces the family's cached rows`() = runTest {
        dao.upsertAll(listOf(live(document("gone")).toCacheRow()))

        val emitted = cache.listing(FAMILY, flowOf(VaultIndexEvent.FromServer(listOf(live(ORDER))))).toList()

        assertEquals(listOf<VaultListing?>(VaultListing(listOf(ORDER))), emitted)
        assertEquals(listOf("order"), dao.rows.map { it.id })
    }

    @Test
    fun `a failed listener shows the last server answer, marked as possibly out of date`() = runTest {
        val emitted = cache.listing(
            FAMILY,
            flowOf(VaultIndexEvent.FromServer(listOf(live(ORDER))), VaultIndexEvent.Failed)
        ).toList()

        assertEquals(
            listOf(VaultListing(listOf(ORDER)), VaultListing(listOf(ORDER), possiblyOutdated = true)),
            emitted
        )
    }

    @Test
    fun `with nothing cached a failed listener is unavailable, never an empty vault`() = runTest {
        val emitted = cache.listing(FAMILY, flowOf(VaultIndexEvent.Failed)).toList()

        assertEquals(1, emitted.size)
        assertNull(emitted.single())
    }

    @Test
    fun `Firestore's own cache shows the stored copy, and says nothing when there is none`() = runTest {
        assertTrue(cache.listing(FAMILY, flowOf(VaultIndexEvent.FromLocalCache)).toList().isEmpty())

        dao.upsertAll(listOf(live(ORDER).toCacheRow()))
        assertEquals(
            listOf<VaultListing?>(VaultListing(listOf(ORDER), possiblyOutdated = true)),
            cache.listing(FAMILY, flowOf(VaultIndexEvent.FromLocalCache)).toList()
        )
    }

    @Test
    fun `a tombstone is stored as deleted and never listed, live or cached`() = runTest {
        val tombstoned = FamilyDocumentMapper.IndexEntry(document("letter"), deletedAtMillis = 5L)

        val emitted = cache.listing(
            FAMILY,
            flowOf(VaultIndexEvent.FromServer(listOf(live(ORDER), tombstoned)), VaultIndexEvent.Failed)
        ).toList()

        assertEquals(listOf(ORDER), emitted[0]?.documents)
        assertEquals(listOf(ORDER), emitted[1]?.documents)
        assertEquals(5L, dao.rows.single { it.id == "letter" }.deletedAtMillis)
    }

    @Test
    fun `one family's cache is never listed for another`() = runTest {
        val otherFamily = "alice__carol"
        cache.listing(otherFamily, flowOf(VaultIndexEvent.FromServer(listOf(live(OTHER_FAMILY_ORDER))))).toList()

        assertNull(cache.listing(FAMILY, flowOf(VaultIndexEvent.Failed)).toList().single())

        cache.listing(FAMILY, flowOf(VaultIndexEvent.FromServer(listOf(live(ORDER))))).toList()
        assertEquals(setOf("order", "carol-order"), dao.rows.map { it.id }.toSet())
    }

    @Test
    fun `a server row naming another family is neither cached nor listed under this one`() = runTest {
        val emitted = cache.listing(
            FAMILY,
            flowOf(VaultIndexEvent.FromServer(listOf(live(ORDER), live(OTHER_FAMILY_ORDER))))
        ).toList()

        assertEquals(listOf(ORDER), emitted.single()?.documents)
        assertEquals(listOf("order"), dao.rows.map { it.id })
    }

    @Test
    fun `a cached row reads back as the document it was written from`() {
        assertEquals(ORDER, live(ORDER).toCacheRow().toDocument())
    }

    private fun live(document: FamilyDocument) = FamilyDocumentMapper.IndexEntry(document, deletedAtMillis = null)

    /** A DAO over a list, with Room's semantics for the four calls the cache makes. */
    private class FakeCacheDao : FamilyDocumentCacheDao {
        val rows = mutableListOf<FamilyDocumentCacheEntity>()

        override suspend fun liveIn(familyId: String): List<FamilyDocumentCacheEntity> =
            rows.filter { it.familyId == familyId && it.deletedAtMillis == null }

        override suspend fun clearFamily(familyId: String) {
            rows.removeAll { it.familyId == familyId }
        }

        override suspend fun upsertAll(rows: List<FamilyDocumentCacheEntity>) {
            val ids = rows.map { it.id }.toSet()
            this.rows.removeAll { it.id in ids }
            this.rows += rows
        }
    }

    private companion object {
        const val FAMILY = "alice__bob"

        fun document(id: String, familyId: String = FAMILY) = FamilyDocument(
            id = id,
            familyId = familyId,
            createdByFirebaseUid = "alice",
            title = "Custody order",
            category = DocumentCategory.COURT_ORDER,
            fileName = "order.pdf",
            storagePath = "family_documents/$familyId/$id/order.pdf",
            contentType = "application/pdf",
            sizeBytes = 10,
            sha256 = "a".repeat(64),
            createdAtMillis = 1
        )

        val ORDER = document("order")
        val OTHER_FAMILY_ORDER = document("carol-order", familyId = "alice__carol")
    }
}
