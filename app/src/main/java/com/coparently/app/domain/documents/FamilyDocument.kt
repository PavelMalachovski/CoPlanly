package com.coparently.app.domain.documents

/**
 * What a vault document is about, in the order the vault screen lists them (MON-23).
 *
 * @property wire The stored value — part of the Firestore schema (`firestore.rules`'
 *   `validVaultCategory` lists the same five) and never renamed.
 */
enum class DocumentCategory(val wire: String) {
    COURT_ORDER("court_order"),
    SCHOOL("school"),
    MEDICAL("medical"),
    IDENTITY("identity"),
    OTHER("other");

    companion object {
        /** The category stored as [wire], or [OTHER] for a value a newer build wrote. */
        fun fromWire(wire: String?): DocumentCategory = entries.firstOrNull { it.wire == wire } ?: OTHER
    }
}

/**
 * One file a parent filed for the family: a court order, a school letter, a passport scan.
 *
 * **Shared by definition.** A vault document has no private form (unlike an event, CLAUDE.md
 * item 3): it is written for a family, readable by that family's two parents, and the screen says
 * so before anything is added. It is metadata only — the bytes live in Cloud Storage at
 * [storagePath], and nothing here is a download URL.
 *
 * @property id Document id, and the folder the file sits in.
 * @property familyId `FamilyKey.of` the two parents — never blank.
 * @property createdByFirebaseUid Who filed it; the only person who can rename or delete it.
 * @property title What the parent called it.
 * @property category Which group it is listed under.
 * @property fileName The file's name, a safe path segment.
 * @property storagePath `family_documents/{familyId}/{id}/{fileName}` — see [FamilyDocumentPaths].
 * @property contentType One of `SharedFilePolicy.CONTENT_TYPES`.
 * @property sizeBytes Size of the stored file.
 * @property sha256 Lowercase hex SHA-256 of the stored file; a download is checked against it.
 * @property createdAtMillis When it was filed, epoch millis.
 */
data class FamilyDocument(
    val id: String,
    val familyId: String,
    val createdByFirebaseUid: String,
    val title: String,
    val category: DocumentCategory,
    val fileName: String,
    val storagePath: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAtMillis: Long
)

/** The one place a vault path is spelled; `firestore.rules` rebuilds the same string. */
object FamilyDocumentPaths {

    /** Where the file of document [docId] in family [familyId] is stored. */
    fun storagePath(familyId: String, docId: String, fileName: String): String =
        "family_documents/$familyId/$docId/$fileName"
}
