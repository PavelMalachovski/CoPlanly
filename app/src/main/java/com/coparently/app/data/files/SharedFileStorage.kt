package com.coparently.app.data.files

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bytes of vault documents, chat attachments (MON-23) and record photographs (L-4) in Cloud
 * Storage.
 *
 * Addressed by path only. **No download URL is ever requested**: a token URL bypasses
 * `storage.rules` for whoever holds it, and the whole point of the MON-23 blocks is that the
 * rules decide who reads these files. So a reader downloads through the SDK, as themselves.
 */
@Singleton
class SharedFileStorage @Inject constructor(
    private val storage: FirebaseStorage
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
        file: File,
        contentType: String,
        sha256: String,
        uploaderUid: String,
        onProgress: (Float) -> Unit = {}
    ) {
        val ref = storage.reference.child(path)
        if (storedDigest(ref) == sha256) return
        val metadata = storageMetadata {
            setContentType(contentType)
            setCustomMetadata(META_UPLOADER, uploaderUid)
            setCustomMetadata(META_SHA256, sha256)
        }
        val task = ref.putFile(Uri.fromFile(file), metadata)
        task.addOnProgressListener { snapshot ->
            if (snapshot.totalByteCount > 0) {
                onProgress(snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount)
            }
        }
        task.await()
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
        val ref = storage.reference.child(path)
        if (storedDigest(ref) == sha256) return
        val metadata = storageMetadata {
            setContentType(contentType)
            setCustomMetadata(META_UPLOADER, uploaderUid)
            setCustomMetadata(META_SHA256, sha256)
        }
        ref.putBytes(bytes, metadata).await()
    }

    /** True when an object carrying [sha256] is stored at [path]. */
    suspend fun isStored(path: String, sha256: String): Boolean =
        storedDigest(storage.reference.child(path)) == sha256

    /** Downloads [path] into [target]. */
    suspend fun download(path: String, target: File) {
        storage.reference.child(path).getFile(target).await()
    }

    /** Deletes [path]; a missing object is not an error. */
    suspend fun delete(path: String) {
        try {
            storage.reference.child(path).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }

    /** The digest stamped on the object at [ref], or null when there is no object. */
    private suspend fun storedDigest(ref: StorageReference): String? = try {
        ref.metadata.await().getCustomMetadata(META_SHA256)
    } catch (e: StorageException) {
        if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) null else throw IOException(e)
    }

    private companion object {
        /** Custom metadata the Storage rules require on a create: who uploaded it. */
        const val META_UPLOADER = "uploader"

        /** Custom metadata the Storage rules require on a create: the bytes' SHA-256. */
        const val META_SHA256 = "sha256"
    }
}
