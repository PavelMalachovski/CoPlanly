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
import platform.posix.int32_tVar
import platform.posix.memcpy
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * [RecordPhotoStorage] on the iPhone (MON-24 phase 3, part C, Task 11): what Android's
 * `FirebaseImageStorage` does, with the iPhone's image code.
 *
 * The picked image (a path, or a `file://` address: [IosPickedFiles]' copy, or a photo the camera
 * took) is re-encoded as JPEG at quality 0.85, scaled down by Android's own power-of-two rule
 * ([RecordPhotoEncoding.sampleSize], so the same photo is stored at the same size from both phones),
 * upright (ImageIO applies the photo's orientation), and its SHA-256 taken. The object goes to
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
 * scaled down as Android's `FirebaseImageStorage` subsamples (`inSampleSize` from [sampleSize]: the
 * sample doubles while either side divided by twice the sample is still at least
 * [MAX_DIMENSION_PX]), so a 4000-pixel photo is stored at 2000 and one under 3200 is kept whole;
 * never scaled up. Encoded by UIKit at [JPEG_QUALITY], the quality Android stores.
 */
internal object RecordPhotoEncoding {

    /** Android's `MAX_DIMENSION_PX`: the side the subsampling works down towards. */
    const val MAX_DIMENSION_PX = 1600

    /** UIKit's JPEG quality for a stored photo (Android's `JPEG_QUALITY` 85). */
    const val JPEG_QUALITY = 0.85

    /**
     * The file at [path] re-encoded.
     *
     * @throws IOException when it cannot be read or is not an image
     */
    fun jpeg(path: String): ByteArray {
        val provider = CGDataProviderCreateWithFilename(path) ?: cannot("open", path)
        try {
            val source = CGImageSourceCreateWithDataProvider(provider, null)
                ?: cannot("read", path)
            try {
                val (width, height) = sizeOf(source) ?: cannot("decode", path)
                val image = thumbnail(source, maxOf(width, height) / sampleSize(width, height))
                    ?: cannot("decode", path)
                try {
                    val data = UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), JPEG_QUALITY)
                        ?: cannot("encode", path)
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

    /** Fails the re-encoding of the image at [path] at [step]: open, read, decode or encode. */
    private fun cannot(step: String, path: String): Nothing = throw IOException("Cannot $step image: $path")

    /**
     * Android's `calculateInSampleSize`, line for line: 1, doubled while [width] or [height] divided
     * by twice the sample is still at least [MAX_DIMENSION_PX].
     */
    fun sampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= MAX_DIMENSION_PX || height / (sampleSize * 2) >= MAX_DIMENSION_PX) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /** The image's pixel width and height, before orientation (which swaps, never changes, them). */
    private fun sizeOf(source: platform.ImageIO.CGImageSourceRef): Pair<Int, Int>? {
        val full = CGImageSourceCreateImageAtIndex(source, 0u, null) ?: return null
        return try {
            CGImageGetWidth(full).toInt() to CGImageGetHeight(full).toInt()
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
