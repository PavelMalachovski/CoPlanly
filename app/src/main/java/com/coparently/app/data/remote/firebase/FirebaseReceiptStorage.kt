package com.coparently.app.data.remote.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.coparently.app.domain.repository.ReceiptStorage
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.ktx.storageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReceiptStorage] backed by Firebase Cloud Storage.
 *
 * Images are downscaled and recompressed before upload to keep uploads fast and
 * storage usage low; the resulting download URL (with access token) is what gets
 * stored in Firestore, so the other parent can load the photo directly.
 */
@Singleton
class FirebaseReceiptStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage
) : ReceiptStorage {

    override suspend fun uploadReceipt(expenseId: String, localUri: String): String {
        val bytes = withContext(Dispatchers.IO) { compressImage(Uri.parse(localUri)) }
        val ref = storage.reference.child(receiptPath(expenseId))
        val metadata = storageMetadata { contentType = "image/jpeg" }
        ref.putBytes(bytes, metadata).await()
        return ref.downloadUrl.await().toString()
    }

    override suspend fun deleteReceipt(expenseId: String) {
        try {
            storage.reference.child(receiptPath(expenseId)).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }

    private fun receiptPath(expenseId: String) = "receipts/$expenseId.jpg"

    /**
     * Decodes the picked image with subsampling so full-resolution camera photos
     * never load entirely into memory, then re-encodes as JPEG.
     */
    private fun compressImage(uri: Uri): ByteArray {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open receipt image: $uri")

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Cannot decode receipt image: $uri")

        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

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
    }
}
