package com.coparently.app.data.files

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.tasks.await
import kotlinx.io.files.Path
import java.io.File
import java.io.IOException

/**
 * [StorageBucket] on the Firebase Storage SDK: the calls `SharedFileStorage` made before it moved to
 * `:shared-data` (MON-24 phase 2, Task 8j), unchanged — `metadata`, `putFile` with its progress
 * listener, `putBytes`, `getFile` and `delete`, by path. No call here asks for a download URL
 * (CLAUDE.md item 31).
 *
 * @param storage The app's instance (an e2e parent's names its app and emulator).
 */
class FirebaseStorageBucket(
    private val storage: FirebaseStorage
) : StorageBucket {

    override suspend fun customMetadata(path: String, key: String): String? = try {
        storage.reference.child(path).metadata.await().getCustomMetadata(key)
    } catch (e: StorageException) {
        if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) null else throw IOException(e)
    }

    override suspend fun putFile(
        path: String,
        file: Path,
        contentType: String,
        customMetadata: Map<String, String>,
        onProgress: (Float) -> Unit
    ) {
        val metadata = metadataOf(contentType, customMetadata)
        val task = storage.reference.child(path).putFile(Uri.fromFile(File(file.toString())), metadata)
        task.addOnProgressListener { snapshot ->
            if (snapshot.totalByteCount > 0) {
                onProgress(snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount)
            }
        }
        task.await()
    }

    override suspend fun putBytes(
        path: String,
        bytes: ByteArray,
        contentType: String,
        customMetadata: Map<String, String>
    ) {
        storage.reference.child(path).putBytes(bytes, metadataOf(contentType, customMetadata)).await()
    }

    override suspend fun getFile(path: String, target: Path) {
        storage.reference.child(path).getFile(File(target.toString())).await()
    }

    override suspend fun delete(path: String) {
        try {
            storage.reference.child(path).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }

    private fun metadataOf(contentType: String, customMetadata: Map<String, String>): StorageMetadata =
        storageMetadata {
            setContentType(contentType)
            customMetadata.forEach { (key, value) -> setCustomMetadata(key, value) }
        }
}
