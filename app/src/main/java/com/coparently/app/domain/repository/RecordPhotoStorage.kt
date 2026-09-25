package com.coparently.app.domain.repository

import com.coparently.app.domain.files.RecordPhotoKind

/**
 * Remote storage for the photographs a record carries — a child's medical photos, a pet's photos,
 * an expense's receipt and an event's photo (L-4).
 *
 * One interface for the four, because since L-4 they have one shape: every upload is a new object
 * under the record's folder in its family (or, before the uploader has a family, in their own
 * `solo_` folder), and every stored reference is a `RecordPhotoCodec` string naming the path, type,
 * size and SHA-256 — never a download URL. Replacing a photograph is an upload plus a delete of
 * the old object; nothing is ever overwritten.
 */
interface RecordPhotoStorage {

    /**
     * Uploads the image behind [localUri] as a photograph of the record [recordId].
     *
     * @param kind Which kind of record it belongs to; decides the Storage prefix.
     * @param recordId The record's id.
     * @param recordFamilyId The record's stored `familyId`, or null for a record that has none
     *   yet (a new one). Null falls back to the family the repositories stamp a new record with —
     *   the signed-in user and their co-parent — and, while there is no co-parent, to the
     *   uploader's own folder.
     * @param localUri Content URI string of the picked image on this device.
     * @return The reference to store on the record (`RecordPhotoCodec`).
     */
    suspend fun upload(
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        localUri: String
    ): String

    /**
     * Deletes the object [reference] names — in the family's folder, and in the uploader's own
     * folder when that is this user's.
     *
     * **Callers that keep a list must delete before dropping the reference, and must not drop it
     * if this fails**: a dropped reference to a surviving object is an image nobody can see and
     * nobody can delete. A legacy value (a download URL or a flat path from before L-4) is not a
     * reference; this returns without touching Storage — `purgeLegacyPhotoPaths` removes those
     * objects server-side — so the caller may drop it.
     *
     * Safe to call when the object is already gone.
     *
     * @param reference The stored reference.
     * @param recordFamilyId The record's stored `familyId`, or null.
     */
    suspend fun delete(reference: String, recordFamilyId: String?)
}
