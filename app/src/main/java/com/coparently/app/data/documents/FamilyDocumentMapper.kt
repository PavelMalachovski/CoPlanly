package com.coparently.app.data.documents

import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.family.FamilyKey

/**
 * The `family_documents/{id}` wire form, in both directions (MON-23).
 *
 * The key set is closed on the server — `firestore.rules`' `vaultKeysOnly` refuses anything
 * else — so a field added here has to be added there in the same change, or every upload is
 * refused.
 */
object FamilyDocumentMapper {

    /** The document as it is created. [FamilyDocument.familyId]'s two uids are the audience. */
    fun toFirestoreMap(document: FamilyDocument): Map<String, Any> {
        val members = FamilyKey.membersOf(document.familyId)
        requireNotNull(members) { "A vault document needs a family" }
        return mapOf(
            "id" to document.id,
            "familyId" to document.familyId,
            "createdByFirebaseUid" to document.createdByFirebaseUid,
            "sharedWith" to listOf(members.first, members.second),
            "title" to document.title,
            "category" to document.category.wire,
            "fileName" to document.fileName,
            "storagePath" to document.storagePath,
            "contentType" to document.contentType,
            "sizeBytes" to document.sizeBytes,
            "sha256" to document.sha256,
            "createdAtMillis" to document.createdAtMillis
        )
    }

    /** The fields a tombstone writes with `update()` — never `set()` (CLAUDE.md item 14). */
    fun tombstone(deletedBy: String, atMillis: Long): Map<String, Any> =
        mapOf("deletedAtMillis" to atMillis, "deletedBy" to deletedBy)

    /**
     * The live document stored under [id], or null for a tombstone or a document missing a field
     * the vault needs — skipped, not allowed to fail the whole list.
     */
    fun fromFirestore(id: String, data: Map<String, Any?>?): FamilyDocument? {
        val fields = data?.takeIf { it["deletedAtMillis"] == null } ?: return null
        val familyId = fields.text("familyId")
        val creator = fields.text("createdByFirebaseUid")
        val storagePath = fields.text("storagePath")
        val sha256 = fields.text("sha256")
        if (listOf(familyId, creator, storagePath, sha256).any { it.isBlank() }) return null
        return FamilyDocument(
            id = id,
            familyId = familyId,
            createdByFirebaseUid = creator,
            title = fields.text("title"),
            category = DocumentCategory.fromWire(fields["category"] as? String),
            fileName = fields.text("fileName"),
            storagePath = storagePath,
            contentType = fields.text("contentType"),
            sizeBytes = (fields["sizeBytes"] as? Number)?.toLong() ?: 0L,
            sha256 = sha256,
            createdAtMillis = (fields["createdAtMillis"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun Map<String, Any?>.text(key: String): String = (this[key] as? String).orEmpty()

    /** The order the vault lists them in: by category, then newest first. */
    val listOrder: Comparator<FamilyDocument> =
        compareBy<FamilyDocument> { it.category.ordinal }.thenByDescending { it.createdAtMillis }
}
