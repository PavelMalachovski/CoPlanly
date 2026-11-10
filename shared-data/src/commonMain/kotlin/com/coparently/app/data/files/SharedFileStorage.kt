package com.coparently.app.data.files

import kotlinx.io.files.Path

/**
 * The bytes of vault documents, chat attachments (MON-23) and record photographs (L-4) in Cloud
 * Storage.
 *
 * Addressed by path only. **No download URL is ever requested**: a token URL bypasses
 * `storage.rules` for whoever holds it, and the whole point of the MON-23 blocks is that the
 * rules decide who reads these files. So a reader downloads through the SDK, as themselves —
 * [StorageBucket] has no call that could answer a URL.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8j). What the rules require of a create (who
 * uploaded it, the bytes' digest) and the idempotence across a lost response are decided here; the
 * SDK calls are the platform's [bucket] (the Firebase Storage SDK on Android, `FirebaseStorageBucket`
 * in the app).
 */
class SharedFileStorage(
    private val bucket: StorageBucket
) {

    /**
     * Uploads [file] to [path], stamped with its uploader and digest — both required by the rule.
     *
     * Idempotent across a lost response: when an object with the same digest is already at
     * [path], this returns without uploading. A second upload would be refused anyway, since the
     * rule allows no overwrite.
     *
     * @param onProgress Fraction transferred, 0 to 1, as the upload reports it.
     */
    // Each parameter is a distinct fact the rule checks; a holder type would only rename them.
    @Suppress("LongParameterList")
    suspend fun upload(
        path: String,
        file: Path,
        contentType: String,
        sha256: String,
        uploaderUid: String,
        onProgress: (Float) -> Unit = {}
    ) {
        if (storedDigest(path) == sha256) return
        bucket.putFile(path, file, contentType, metadataOf(uploaderUid, sha256), onProgress)
    }

    /**
     * Uploads [bytes] to [path], stamped like [upload] — for a record photograph, which is
     * re-encoded in memory rather than staged as a file (L-4). Same idempotence across a lost
     * response, and the same refusal of an overwrite by the rule.
     */
    suspend fun uploadBytes(
        path: String,
        bytes: ByteArray,
        contentType: String,
        sha256: String,
        uploaderUid: String
    ) {
        if (storedDigest(path) == sha256) return
        bucket.putBytes(path, bytes, contentType, metadataOf(uploaderUid, sha256))
    }

    /** True when an object carrying [sha256] is stored at [path]. */
    suspend fun isStored(path: String, sha256: String): Boolean = storedDigest(path) == sha256

    /** Downloads [path] into [target]. */
    suspend fun download(path: String, target: Path) {
        bucket.getFile(path, target)
    }

    /** Deletes [path]; a missing object is not an error. */
    suspend fun delete(path: String) {
        bucket.delete(path)
    }

    /** The digest stamped on the object at [path], or null when there is no object. */
    private suspend fun storedDigest(path: String): String? = bucket.customMetadata(path, META_SHA256)

    /** The custom metadata a create carries, in the order the app has always set it. */
    private fun metadataOf(uploaderUid: String, sha256: String): Map<String, String> =
        linkedMapOf(META_UPLOADER to uploaderUid, META_SHA256 to sha256)

    private companion object {
        /** Custom metadata the Storage rules require on a create: who uploaded it. */
        const val META_UPLOADER = "uploader"

        /** Custom metadata the Storage rules require on a create: the bytes' SHA-256. */
        const val META_SHA256 = "sha256"
    }
}

/**
 * The Storage bucket's few calls, as [SharedFileStorage] makes them (MON-24 phase 2, Task 8j).
 *
 * Paths only, and **nothing that answers a download URL** (CLAUDE.md item 31): a reader fetches an
 * object as the signed-in user, so `storage.rules` decides. On Android it is the Firebase Storage
 * SDK (`FirebaseStorageBucket` in the app); the iPhone's is still to come (`NoStorageBucket`
 * until then).
 */
interface StorageBucket {

    /**
     * The custom metadata value [key] of the object at [path]: null when no object is stored
     * there, or when it carries no such key.
     *
     * @throws kotlinx.io.IOException when the bucket could not answer.
     */
    suspend fun customMetadata(path: String, key: String): String?

    /**
     * Uploads the file at [file] to [path] with [contentType] and [customMetadata], reporting the
     * fraction transferred to [onProgress] as the upload does.
     */
    suspend fun putFile(
        path: String,
        file: Path,
        contentType: String,
        customMetadata: Map<String, String>,
        onProgress: (Float) -> Unit
    )

    /** Uploads [bytes] to [path] with [contentType] and [customMetadata]. */
    suspend fun putBytes(path: String, bytes: ByteArray, contentType: String, customMetadata: Map<String, String>)

    /** Downloads the object at [path] into the file [target]. */
    suspend fun getFile(path: String, target: Path)

    /** Deletes the object at [path]; a missing object is not an error. */
    suspend fun delete(path: String)
}
