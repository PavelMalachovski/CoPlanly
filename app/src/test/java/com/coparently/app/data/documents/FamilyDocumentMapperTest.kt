package com.coparently.app.data.documents

import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.FamilyDocumentPaths
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `family_documents` wire form (MON-23). The key set is closed by `firestore.rules`'
 * `vaultKeysOnly`, so this pins exactly which keys a create writes.
 */
class FamilyDocumentMapperTest {

    private val document = FamilyDocument(
        id = "doc-1",
        familyId = "alice__bob",
        createdByFirebaseUid = "alice",
        title = "Custody order",
        category = DocumentCategory.COURT_ORDER,
        fileName = "order.pdf",
        storagePath = FamilyDocumentPaths.storagePath("alice__bob", "doc-1", "order.pdf"),
        contentType = "application/pdf",
        sizeBytes = 12_345,
        sha256 = "a".repeat(64),
        createdAtMillis = 1_790_000_000_000
    )

    @Test
    fun `a create writes exactly the keys the rule admits, with the family as its audience`() {
        val map = FamilyDocumentMapper.toFirestoreMap(document)
        assertEquals(
            setOf(
                "id", "familyId", "createdByFirebaseUid", "sharedWith", "title", "category", "fileName",
                "storagePath", "contentType", "sizeBytes", "sha256", "createdAtMillis"
            ),
            map.keys
        )
        assertEquals(listOf("alice", "bob"), map["sharedWith"])
        assertEquals("court_order", map["category"])
        assertEquals("family_documents/alice__bob/doc-1/order.pdf", map["storagePath"])
    }

    @Test
    fun `a document reads back as it was written`() {
        val written = FamilyDocumentMapper.toFirestoreMap(document)
        assertEquals(document, FamilyDocumentMapper.fromFirestore("doc-1", written))
    }

    @Test
    fun `a tombstone is not a document`() {
        val data = FamilyDocumentMapper.toFirestoreMap(document) + FamilyDocumentMapper.tombstone("alice", 1L)
        assertNull(FamilyDocumentMapper.fromFirestore("doc-1", data))
    }

    @Test
    fun `a category a newer build wrote reads as other, and a missing path drops the row`() {
        val map = FamilyDocumentMapper.toFirestoreMap(document)
        assertEquals(
            DocumentCategory.OTHER,
            FamilyDocumentMapper.fromFirestore("doc-1", map + ("category" to "tax"))?.category
        )
        assertNull(FamilyDocumentMapper.fromFirestore("doc-1", map - "storagePath"))
    }

    @Test
    fun `the vault lists by category, newest first within one`() {
        val older = document.copy(id = "a", createdAtMillis = 1)
        val newer = document.copy(id = "b", createdAtMillis = 2)
        val school = document.copy(id = "c", category = DocumentCategory.SCHOOL, createdAtMillis = 3)
        assertEquals(
            listOf("b", "a", "c"),
            listOf(school, older, newer).sortedWith(FamilyDocumentMapper.listOrder).map { it.id }
        )
    }
}
