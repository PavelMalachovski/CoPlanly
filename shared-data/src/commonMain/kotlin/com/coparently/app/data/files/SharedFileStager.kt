package com.coparently.app.data.files

import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.platform.Sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path

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
    val file: Path,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String
)

/** The picked file may not be shared, for [rejection]; nothing was uploaded. */
class SharedFileRejectedException(val rejection: SharedFilePolicy.Rejection) :
    IOException("Shared file rejected: $rejection")

/**
 * Copies a picked file into a directory this app owns.
 *
 * **Why copy at all.** A picker grants a read on the file for as long as the receiving component
 * lives, not for as long as an upload may take to succeed — a chat attachment waiting in the
 * outbox has to survive a restart, and the digest has to describe exactly the bytes that are
 * uploaded, not whatever the provider serves on the second read. So the file is read once, into
 * a private copy, and hashed while it is read. A file over the cap is abandoned mid-copy rather
 * than read to the end.
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8j). What the picked file is and how it is read are
 * the platform's [pickedFiles] (a `content://` URI through the `ContentResolver` on Android,
 * `AndroidPickedFiles` in the app); the copy, the cap and the digest are decided here.
 */
class SharedFileStager(
    private val pickedFiles: PickedFiles
) {

    /**
     * Copies [contentUri] into [directory] and returns the copy.
     *
     * @throws SharedFileRejectedException for a type or size the rules would refuse
     * @throws IOException when the file cannot be read
     */
    suspend fun stage(contentUri: String, directory: Path): StagedFile = withContext(Dispatchers.IO) {
        val displayName = pickedFiles.displayNameOf(contentUri)
        val contentType = SharedFilePolicy.resolveContentType(
            pickedFiles.typeOf(contentUri),
            displayName.orEmpty()
        ) ?: throw SharedFileRejectedException(SharedFilePolicy.Rejection.TYPE)
        val fileName = SharedFilePolicy.safeFileName(displayName, contentType)
        LocalFiles.createDirectories(directory)
        val target = Path(directory, fileName)
        val (size, digest) = try {
            pickedFiles.open(contentUri).buffered().use { source ->
                LocalFiles.sinkOf(target).use { sink -> copyHashing(source, sink) }
            }
        } catch (e: IOException) {
            LocalFiles.delete(target)
            throw e
        }
        SharedFilePolicy.check(contentType, size)?.let { rejection ->
            LocalFiles.delete(target)
            throw SharedFileRejectedException(rejection)
        }
        StagedFile(target, fileName, contentType, size, digest)
    }

    /** Copies [source] to [sink], returning the byte count and the hex SHA-256. */
    private fun copyHashing(source: Source, sink: Sink): Pair<Long, String> {
        val buffer = ByteArray(LocalFiles.BUFFER_BYTES)
        var total = 0L
        val digest = Sha256.digestOf { update ->
            var read = source.readAtMostTo(buffer)
            while (read >= 0) {
                total += read
                if (total >= SharedFilePolicy.MAX_BYTES) {
                    throw SharedFileRejectedException(SharedFilePolicy.Rejection.SIZE)
                }
                update(buffer, 0, read)
                sink.write(buffer, 0, read)
                read = source.readAtMostTo(buffer)
            }
        }
        return total to digest.toHex()
    }
}

/**
 * A file the parent picked, as the platform's picker hands it over (MON-24 phase 2, Task 8j): on
 * Android a `content://` URI the `ContentResolver` reads (`AndroidPickedFiles` in the app).
 */
interface PickedFiles {

    /** What the file's provider says its type is, or null when it says nothing. */
    fun typeOf(contentUri: String): String?

    /** The file's display name as its provider reports it, else the URI's last segment, else null. */
    fun displayNameOf(contentUri: String): String?

    /**
     * The file's bytes, for one read.
     *
     * @throws IOException when the file cannot be opened
     */
    fun open(contentUri: String): RawSource
}
