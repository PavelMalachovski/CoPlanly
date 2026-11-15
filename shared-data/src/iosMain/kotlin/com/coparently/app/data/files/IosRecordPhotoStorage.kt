@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.files

import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.versions.EventVersionDocument
import com.coparently.app.domain.files.RecordPhoto
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.files.RecordPhotoPaths
import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.platform.Sha256
import com.coparently.app.domain.repository.RecordPhotoStorage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGDataProviderCreateWithFilename
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.NSData
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithDataProvider
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import platform.posix.int32_tVar
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [RecordPhotoStorage] on the iPhone (MON-24 phase 3, part C, Task 11): what Android's
 * `FirebaseImageStorage` does, with the iPhone's image code.
 *
 * The picked image (a path, or a `file://` address: [IosPickedFiles]' copy, or a photo the camera
 * took) is re-encoded as JPEG at quality 0.85 with its longest side at most 2048 pixels, upright
 * (ImageIO applies the photo's orientation), and its SHA-256 taken. The object goes to
 * [RecordPhotoPaths.build]'s path under the record's family, or under the uploader's own `solo_`
 * folder while there is none, through [SharedFileStorage], which stamps the `uploader` and `sha256`
 * metadata `storage.rules` requires. **No download URL is ever requested**: the record keeps the
 * [RecordPhotoCodec] reference this returns. The bytes are kept in [SharedFileCache] under their
 * digest, so the uploader sees the photo without fetching it back. A delete is
 * [RecordPhotoPaths.candidatesFor]'s paths, as on Android.
 *
 * @param authService Who is signed in: the uploader.
 * @param userDao The signed-in parent's row: their co-parents, for a record that names no family.
 * @param storage Cloud Storage by path.
 * @param cache The verified local copies.
 * @param storedRecords Which family a stored record names.
 */
class IosRecordPhotoStorage(
    private val authService: SignedInUser,
    private val userDao: UserDao,
    private val storage: SharedFileStorage,
    private val cache: SharedFileCache,
    private val storedRecords: StoredRecordFamilies
) : RecordPhotoStorage {

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun upload(
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        localUri: String
    ): String {
        val uid = authService.getCurrentUid() ?: throw IOException("Not signed in")
        val familyId = ownerFamily(kind, recordId, recordFamilyId, uid)
        val bytes = withContext(Dispatchers.IO) { RecordPhotoEncoding.jpeg(IosPickedFiles.pathOf(localUri)) }
        val sha256 = Sha256.digest(bytes).toHex()
        val objectName = "${Uuid.random()}.$EXTENSION"
        val path = RecordPhotoPaths.build(kind, familyId, uid, recordId, objectName)
        storage.uploadBytes(path, bytes, CONTENT_TYPE, sha256, uid)
        cache.keep(bytes, objectName, sha256)
        return RecordPhotoCodec.encode(RecordPhoto(path, CONTENT_TYPE, bytes.size.toLong(), sha256))
    }

    /**
     * The family whose folder the photograph of [recordId] goes to, or null for [uid]'s own `solo_`
     * folder: [RecordPhotoPaths.uploadFamily] over what this phone knows, as Android decides it.
     */
    private suspend fun ownerFamily(
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        uid: String
    ): String? {
        recordFamilyId?.takeIf { it.isNotBlank() }?.let { return it }
        val stored = storedRecords.of(kind, recordId)
        val me = userDao.getUserById(uid)
        return RecordPhotoPaths.uploadFamily(
            recordFamilyId = stored?.familyId,
            storedRecord = stored != null,
            uploaderUid = uid,
            coParents = EventVersionDocument.decodeAudience(me?.partnerIdsJson ?: "[]"),
            selectedPartner = me?.partnerId
        )
    }

    override suspend fun delete(reference: String, recordFamilyId: String?) {
        val photo = RecordPhotoCodec.decode(reference) ?: return
        val parsed = RecordPhotoPaths.parse(photo.storagePath) ?: return
        val uid = authService.getCurrentUid() ?: throw IOException("Not signed in")
        RecordPhotoPaths.candidatesFor(photo, parsed.kind, parsed.recordId, recordFamilyId, uid)
            .forEach { path -> storage.delete(path) }
    }

    private companion object {
        const val CONTENT_TYPE = "image/jpeg"
        const val EXTENSION = "jpg"
    }
}

/**
 * A picked image as the JPEG a record photo is stored as: ImageIO's thumbnail of the file, upright,
 * with its longest side at most [MAX_DIMENSION_PX] (a smaller image keeps its size), encoded by
 * UIKit at [JPEG_QUALITY]. Android's `FirebaseImageStorage` stores the same quality.
 */
internal object RecordPhotoEncoding {

    /** The longest side of a stored photo, in pixels. */
    const val MAX_DIMENSION_PX = 2048

    /** UIKit's JPEG quality for a stored photo. */
    const val JPEG_QUALITY = 0.85

    /**
     * The file at [path] re-encoded.
     *
     * @throws IOException when it cannot be read or is not an image
     */
    fun jpeg(path: String): ByteArray {
        val provider = CGDataProviderCreateWithFilename(path) ?: throw IOException("Cannot open image: $path")
        try {
            val source = CGImageSourceCreateWithDataProvider(provider, null)
                ?: throw IOException("Cannot read image: $path")
            try {
                val longest = longestSideOf(source) ?: throw IOException("Cannot decode image: $path")
                val image = thumbnail(source, minOf(longest, MAX_DIMENSION_PX))
                    ?: throw IOException("Cannot decode image: $path")
                try {
                    val data = UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), JPEG_QUALITY)
                        ?: throw IOException("Cannot encode image: $path")
                    return data.toByteArray()
                } finally {
                    CGImageRelease(image)
                }
            } finally {
                CFRelease(source)
            }
        } finally {
            CGDataProviderRelease(provider)
        }
    }

    /** The image's longer side in pixels, before orientation (which swaps, never changes, them). */
    private fun longestSideOf(source: platform.ImageIO.CGImageSourceRef): Int? {
        val full = CGImageSourceCreateImageAtIndex(source, 0u, null) ?: return null
        return try {
            maxOf(CGImageGetWidth(full), CGImageGetHeight(full)).toInt()
        } finally {
            CGImageRelease(full)
        }
    }

    /** ImageIO's upright thumbnail of [source] with its longer side [maxPixels]. */
    private fun thumbnail(source: platform.ImageIO.CGImageSourceRef, maxPixels: Int) = memScoped {
        val options = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        )
        val size = alloc<int32_tVar>().apply { value = maxPixels }
        val number = CFNumberCreate(null, kCFNumberIntType, size.ptr)
        try {
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
            CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, number)
            CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
        } finally {
            number?.let { CFRelease(it) }
            options?.let { CFRelease(it) }
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).also { bytes ->
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
        }
    }
}
