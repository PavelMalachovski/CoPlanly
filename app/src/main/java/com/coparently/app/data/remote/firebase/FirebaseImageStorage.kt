package com.coparently.app.data.remote.firebase

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.files.RecordPhoto
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.files.RecordPhotoPaths
import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.repository.RecordPhotoStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecordPhotoStorage] backed by Firebase Cloud Storage (L-4).
 *
 * Images are downscaled and recompressed to JPEG before upload to keep uploads fast and storage
 * usage low. The object goes to `{prefix}/{familyId}/{recordId}/{random}.jpg` — or, while the
 * uploader has no co-parent, `{prefix}/solo_{uid}/{recordId}/{random}.jpg` — stamped with its
 * uploader and SHA-256, which `storage.rules` requires. **No download URL is ever requested**:
 * what the record stores is a `RecordPhotoCodec` reference, and a reader downloads through the
 * SDK as themselves (`RecordPhotoFetcher`), so the rule decides who sees a photograph for as long
 * as it exists.
 *
 * The bytes are also kept in [SharedFileCache] under their digest, so the uploader's own screen
 * shows the photograph without fetching it back.
 */
@Singleton
class FirebaseImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: FirebaseAuthService,
    private val userDao: UserDao,
    private val storage: SharedFileStorage,
    private val cache: SharedFileCache
) : RecordPhotoStorage {

    override suspend fun upload(
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        localUri: String
    ): String {
        val uid = authService.getCurrentUser()?.uid ?: throw IOException("Not signed in")
        // The same family the repositories stamp a new record with (`familyId ?: FamilyKey.orNull(
        // uid, partnerId)`), so the photograph and its record name one family.
        val familyId = recordFamilyId?.takeIf { it.isNotBlank() }
            ?: FamilyKey.orNull(uid, userDao.getUserById(uid)?.partnerId)
        val bytes = withContext(Dispatchers.IO) { compressImage(Uri.parse(localUri)) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        val objectName = "${UUID.randomUUID()}.$EXTENSION"
        val path = RecordPhotoPaths.build(kind, familyId, uid, recordId, objectName)
        storage.uploadBytes(path, bytes, CONTENT_TYPE, sha256, uid)
        cache.keep(bytes, objectName, sha256)
        return RecordPhotoCodec.encode(RecordPhoto(path, CONTENT_TYPE, bytes.size.toLong(), sha256))
    }

    override suspend fun delete(reference: String, recordFamilyId: String?) {
        val photo = RecordPhotoCodec.decode(reference) ?: return
        val parsed = RecordPhotoPaths.parse(photo.storagePath) ?: return
        val uid = authService.getCurrentUser()?.uid ?: throw IOException("Not signed in")
        RecordPhotoPaths.candidatesFor(photo, parsed.kind, parsed.recordId, recordFamilyId, uid)
            .forEach { path -> storage.delete(path) }
    }

    /**
     * Decodes the picked image with subsampling so full-resolution camera photos
     * never load entirely into memory, then re-encodes as JPEG.
     */
    private fun compressImage(uri: Uri): ByteArray {
        val resolver = context.contentResolver

        // `decodeStream` returns null by contract when inJustDecodeBounds is set — it only
        // fills in the Options. So the null check belongs on the *stream*; testing the decode
        // result instead rejected every image ever picked, which is why attaching a photo has
        // never worked. An undecodable file still fails, on the second pass below.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openStreamOrThrow(uri).use { BitmapFactory.decodeStream(it, null, bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = resolver.openStreamOrThrow(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Cannot decode image: $uri")

        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Opens [uri] for reading, failing loudly rather than returning null to the caller. */
    private fun ContentResolver.openStreamOrThrow(uri: Uri): InputStream =
        openInputStream(uri) ?: throw IOException("Cannot open image: $uri")

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= MAX_DIMENSION_PX || height / (sampleSize * 2) >= MAX_DIMENSION_PX) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        const val MAX_DIMENSION_PX = 1600
        const val JPEG_QUALITY = 85
        const val CONTENT_TYPE = "image/jpeg"
        const val EXTENSION = "jpg"
    }
}
