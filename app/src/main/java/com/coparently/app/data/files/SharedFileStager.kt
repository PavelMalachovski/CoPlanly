package com.coparently.app.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.domain.files.toHex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A file copied into this app's own storage, measured and hashed on the way (MON-23).
 *
 * @property file The private copy.
 * @property fileName A safe path segment — see [SharedFilePolicy.safeFileName].
 * @property contentType One of [SharedFilePolicy.CONTENT_TYPES].
 * @property sizeBytes The copy's size.
 * @property sha256 Lowercase hex SHA-256 of the copy.
 */
data class StagedFile(
    val file: File,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String
)

/** The picked file may not be shared, for [rejection]; nothing was uploaded. */
class SharedFileRejectedException(val rejection: SharedFilePolicy.Rejection) :
    IOException("Shared file rejected: $rejection")

/**
 * Copies a picked `content://` file into a directory this app owns.
 *
 * **Why copy at all.** A picker grants a read on the URI for as long as the receiving component
 * lives, not for as long as an upload may take to succeed — a chat attachment waiting in the
 * outbox has to survive a restart, and the digest has to describe exactly the bytes that are
 * uploaded, not whatever the provider serves on the second read. So the file is read once, into
 * a private copy, and hashed while it is read. A file over the cap is abandoned mid-copy rather
 * than read to the end.
 */
@Singleton
class SharedFileStager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Copies [contentUri] into [directory] and returns the copy.
     *
     * @throws SharedFileRejectedException for a type or size the rules would refuse
     * @throws IOException when the file cannot be read
     */
    suspend fun stage(contentUri: String, directory: File): StagedFile = withContext(Dispatchers.IO) {
        val uri = Uri.parse(contentUri)
        val displayName = displayNameOf(uri)
        val contentType = SharedFilePolicy.resolveContentType(
            context.contentResolver.getType(uri),
            displayName.orEmpty()
        ) ?: throw SharedFileRejectedException(SharedFilePolicy.Rejection.TYPE)
        val fileName = SharedFilePolicy.safeFileName(displayName, contentType)
        directory.mkdirs()
        val target = File(directory, fileName)
        val (size, digest) = try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open the picked file")
            input.use { source -> target.outputStream().use { sink -> copyHashing(source, sink) } }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        SharedFilePolicy.check(contentType, size)?.let { rejection ->
            target.delete()
            throw SharedFileRejectedException(rejection)
        }
        StagedFile(target, fileName, contentType, size, digest)
    }

    /** The provider's display name for [uri], or null when it reports none. */
    private fun displayNameOf(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }

    /** Copies [source] to [sink], returning the byte count and the hex SHA-256. */
    private fun copyHashing(source: InputStream, sink: OutputStream): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        var read = source.read(buffer)
        while (read >= 0) {
            total += read
            if (total >= SharedFilePolicy.MAX_BYTES) {
                throw SharedFileRejectedException(SharedFilePolicy.Rejection.SIZE)
            }
            digest.update(buffer, 0, read)
            sink.write(buffer, 0, read)
            read = source.read(buffer)
        }
        return total to digest.digest().toHex()
    }
}
