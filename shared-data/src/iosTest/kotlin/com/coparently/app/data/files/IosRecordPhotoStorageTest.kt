@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coparently.app.data.files

import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.buildCoPlanlyDatabase
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.remote.firebase.SignedInAccount
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.platform.Sha256
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [IosRecordPhotoStorage] (MON-24 phase 3, part C, Task 11) over a bucket in memory: the image is
 * re-encoded as Android's `FirebaseImageStorage` stores one, at the path, with the metadata and the
 * reference `RecordPhotoPaths` and `RecordPhotoCodec` define. Each test works in a folder of its own
 * under the temporary directory, on a database of its own.
 */
class IosRecordPhotoStorageTest {

    private val root = NSTemporaryDirectory().trimEnd('/') + "/coplanly-photos-" + NSUUID().UUIDString

    private val directories = object : AppDirectories {
        override val files: Path = Path(root, "files")
        override val cache: Path = Path(root, "cache")
        override val noBackup: Path = Path(root, "no_backup")
    }

    private val database: CoPlanlyDatabase = buildCoPlanlyDatabase(directories)

    private val bucket = MemoryBucket()

    private val storage = SharedFileStorage(bucket)

    private val photos = IosRecordPhotoStorage(
        authService = SignedInAs(UPLOADER),
        userDao = database.userDao(),
        storage = storage,
        cache = SharedFileCache(directories, storage),
        storedRecords = StoredRecordFamilies(
            expenseDao = database.expenseDao(),
            childInfoDao = database.childInfoDao(),
            petDao = database.petDao(),
            eventDao = database.eventDao()
        )
    )

    @AfterTest
    fun removeTheFolder() {
        database.close()
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    @Test
    fun `a 4000 px image is stored at 2048 px as JPEG`() = runBlocking {
        val picked = jpegFile(width = 4000, height = 1000)

        val reference = photos.upload(RecordPhotoKind.PET, RECORD, FAMILY, picked)

        val photo = assertNotNull(RecordPhotoCodec.decode(reference), "Not a record photo reference: $reference")
        assertEquals("image/jpeg", photo.contentType)
        assertTrue(
            Regex("^pet_photos/$FAMILY/$RECORD/[0-9a-f-]{36}\\.jpg$").matches(photo.storagePath),
            "Not the path RecordPhotoPaths builds: ${photo.storagePath}"
        )
        val stored = assertNotNull(bucket.objects[photo.storagePath], "Nothing was stored at ${photo.storagePath}")
        assertEquals("image/jpeg", bucket.contentTypes[photo.storagePath])
        assertEquals(photo.sha256, Sha256.digest(stored).toHex())
        assertEquals(stored.size.toLong(), photo.sizeBytes)
        assertEquals(
            mapOf("uploader" to UPLOADER, "sha256" to photo.sha256),
            bucket.metadata[photo.storagePath]
        )
        assertTrue(stored.size > 2 && stored[0] == 0xFF.toByte() && stored[1] == 0xD8.toByte(), "Not a JPEG")
        assertEquals(2048 to 512, pixelSizeOf(stored))
    }

    @Test
    fun `a small image keeps its size`() = runBlocking {
        val reference = photos.upload(RecordPhotoKind.RECEIPT, RECORD, FAMILY, jpegFile(width = 300, height = 200))

        val photo = assertNotNull(RecordPhotoCodec.decode(reference))
        assertEquals(300 to 200, pixelSizeOf(bucket.objects.getValue(photo.storagePath)))
    }

    @Test
    fun `a delete removes the object the reference names`() = runBlocking {
        val reference = photos.upload(RecordPhotoKind.PET, RECORD, FAMILY, jpegFile(width = 100, height = 100))
        val path = assertNotNull(RecordPhotoCodec.decode(reference)).storagePath

        photos.delete(reference, FAMILY)

        assertTrue(path !in bucket.objects, "The photo is still stored at $path")
    }

    /** A JPEG of [width] × [height] pixels, written under the test's folder; its path. */
    private fun jpegFile(width: Int, height: Int): String {
        val space = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null,
            width.toULong(),
            height.toULong(),
            BITS_PER_COMPONENT,
            0u,
            space,
            CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value
        )
        CGContextSetRGBFillColor(context, 0.3, 0.4, 0.9, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        val image = CGBitmapContextCreateImage(context)
        val data = checkNotNull(UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), 0.9))
        CGImageRelease(image)
        CGContextRelease(context)
        CGColorSpaceRelease(space)
        val folder = Path(root, "picked")
        SystemFileSystem.createDirectories(folder)
        val file = Path(folder, "photo-${width}x$height.jpg")
        SystemFileSystem.sink(file).buffered().use { it.write(data.toByteArray()) }
        return file.toString()
    }

    /** The decoded pixel size of the JPEG [bytes]. */
    private fun pixelSizeOf(bytes: ByteArray): Pair<Int, Int> {
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val image = checkNotNull(UIImage.imageWithData(data)?.CGImage) { "The stored bytes do not decode" }
        return CGImageGetWidth(image).toInt() to CGImageGetHeight(image).toInt()
    }

    private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }

    /** Storage in memory: what was put, with its type and custom metadata. */
    private class MemoryBucket : StorageBucket {
        val objects = mutableMapOf<String, ByteArray>()
        val contentTypes = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, Map<String, String>>()

        override suspend fun customMetadata(path: String, key: String): String? =
            if (path in objects) metadata[path].orEmpty()[key] else null

        override suspend fun putFile(
            path: String,
            file: Path,
            contentType: String,
            customMetadata: Map<String, String>,
            onProgress: (Float) -> Unit
        ) {
            val bytes = SystemFileSystem.source(file).buffered().use { it.readByteArray() }
            putBytes(path, bytes, contentType, customMetadata)
            onProgress(1f)
        }

        override suspend fun putBytes(
            path: String,
            bytes: ByteArray,
            contentType: String,
            customMetadata: Map<String, String>
        ) {
            objects[path] = bytes
            contentTypes[path] = contentType
            metadata[path] = customMetadata
        }

        override suspend fun getFile(path: String, target: Path) {
            SystemFileSystem.sink(target).buffered().use { it.write(objects.getValue(path)) }
        }

        override suspend fun delete(path: String) {
            objects.remove(path)
            contentTypes.remove(path)
            metadata.remove(path)
        }
    }

    private class SignedInAs(private val uid: String) : SignedInUser {
        override fun getCurrentUid(): String = uid
        override fun getCurrentDisplayName(): String? = null
        override fun getCurrentEmail(): String? = null
        override fun getCurrentPhotoUrl(): String? = null
        override fun observeCurrentUid(): Flow<String?> = flowOf(uid)
        override fun getCurrentAccount(): SignedInAccount? = null
    }

    private companion object {
        const val UPLOADER = "alice"
        const val FAMILY = "alice__bob"
        const val RECORD = "pet-1"
        const val BITS_PER_COMPONENT = 8uL
    }
}
