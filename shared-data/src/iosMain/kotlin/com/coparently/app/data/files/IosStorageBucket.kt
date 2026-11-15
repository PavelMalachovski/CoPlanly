@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coparently.app.data.files

import cocoapods.FirebaseStorage.FIRStorageErrorCodeDomain
import cocoapods.FirebaseStorage.FIRStorageMetadata
import cocoapods.FirebaseStorage.FIRStorageReference
import cocoapods.FirebaseStorage.FIRStorageTaskStatusProgress
import cocoapods.FirebaseStorage.FIRStorageUploadTask
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.ios
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import kotlinx.io.files.Path
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.create
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [StorageBucket] on the Firebase iOS SDK's Storage (MON-24 phase 3, part C, Task 10): the calls
 * Android's `FirebaseStorageBucket` makes, by path — an object's custom metadata, a file or bytes put
 * with their content type and custom metadata, a download into a file, a delete. No call here asks
 * for a download URL (CLAUDE.md item 31).
 *
 * GitLive 2.7.0's Storage is the instance ([storage]), but each call goes to the pod's
 * `FIRStorageReference` underneath it, through the bindings GitLive ships
 * (`cocoapods.FirebaseStorage`), because GitLive's own calls cannot say what the common code needs:
 * its errors keep only the `NSError`'s text, so a missing object cannot be told from a refusal; it
 * has no download into a file; and its progress flow for a file upload ends a successful upload with
 * an exception.
 *
 * Failures reach the common code as they do on Android: a missing object is null from
 * [customMetadata] and nothing from [delete]; every other failure, a refusal by `storage.rules`
 * included, is a [kotlinx.io.IOException] carrying the `NSError`'s domain, code and description.
 *
 * The custom metadata is handed to the SDK in the order the caller gives it (`uploader`, then
 * `sha256`, as `SharedFileStorage` builds it), but the SDK holds it in an `NSDictionary`, which keeps
 * no order: the rules read the keys by name, so the order on the wire decides nothing.
 *
 * @param storage The app's Storage (a test's names its own app and points it at the emulator).
 */
class IosStorageBucket(
    private val storage: FirebaseStorage
) : StorageBucket {

    override suspend fun customMetadata(path: String, key: String): String? {
        val metadata = suspendCancellableCoroutine<FIRStorageMetadata?> { continuation ->
            reference(path).metadataWithCompletion { metadata, error ->
                when {
                    error == null -> continuation.resume(metadata)
                    error.isObjectNotFound() -> continuation.resume(null)
                    else -> continuation.resumeWithException(error.asIOException(path))
                }
            }
        } ?: return null
        return metadata.customMetadata()?.get(key) as? String
    }

    override suspend fun putFile(
        path: String,
        file: Path,
        contentType: String,
        customMetadata: Map<String, String>,
        onProgress: (Float) -> Unit
    ) {
        val url = NSURL.fileURLWithPath(file.toString())
        upload(path) { completion ->
            reference(path).putFile(url, metadataOf(contentType, customMetadata), completion).also { task ->
                task.observeStatus(FIRStorageTaskStatusProgress) { snapshot ->
                    val progress = snapshot?.progress() ?: return@observeStatus
                    if (progress.totalUnitCount > 0) {
                        onProgress(progress.completedUnitCount.toFloat() / progress.totalUnitCount)
                    }
                }
            }
        }
    }

    override suspend fun putBytes(
        path: String,
        bytes: ByteArray,
        contentType: String,
        customMetadata: Map<String, String>
    ) {
        val data = bytes.toNSData()
        upload(path) { completion ->
            reference(path).putData(data, metadataOf(contentType, customMetadata), completion)
        }
    }

    override suspend fun getFile(path: String, target: Path) {
        val url = NSURL.fileURLWithPath(target.toString())
        suspendCancellableCoroutine { continuation ->
            val task = reference(path).writeToFile(url) { _, error ->
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(error.asIOException(path))
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
        }
    }

    override suspend fun delete(path: String) {
        suspendCancellableCoroutine { continuation ->
            reference(path).deleteWithCompletion { error ->
                if (error == null || error.isObjectNotFound()) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(error.asIOException(path))
                }
            }
        }
    }

    /** The pod's reference to [path], under GitLive's instance. */
    private fun reference(path: String): FIRStorageReference = storage.reference.child(path).ios

    /**
     * Starts an upload with [start], handing it the completion that resumes this call, and cancels
     * the task when the caller is cancelled.
     */
    private suspend fun upload(
        path: String,
        start: (completion: (FIRStorageMetadata?, NSError?) -> Unit) -> FIRStorageUploadTask
    ) {
        suspendCancellableCoroutine { continuation ->
            val task = start { _, error ->
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(error.asIOException(path))
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
        }
    }

    private fun metadataOf(contentType: String, customMetadata: Map<String, String>): FIRStorageMetadata =
        FIRStorageMetadata().apply {
            setContentType(contentType)
            setCustomMetadata(LinkedHashMap<Any?, Any?>(customMetadata))
        }

    private companion object {
        /** `StorageErrorCode.objectNotFound` in Firebase iOS 11.8.0 (`FIRStorageErrorCodeObjectNotFound`). */
        const val OBJECT_NOT_FOUND = -13010L

        fun NSError.isObjectNotFound(): Boolean = domain == FIRStorageErrorCodeDomain && code == OBJECT_NOT_FOUND

        fun NSError.asIOException(path: String): IOException =
            IOException("Storage failed for $path: $domain $code $localizedDescription")

        fun ByteArray.toNSData(): NSData =
            if (isEmpty()) {
                NSData()
            } else {
                usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
            }
    }
}
