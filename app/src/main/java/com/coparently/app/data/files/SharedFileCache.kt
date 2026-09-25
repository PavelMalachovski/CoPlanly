package com.coparently.app.data.files

import android.content.Context
import com.coparently.app.domain.files.toHex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** The downloaded bytes are not the ones whose digest the record carries; nothing is shown. */
class SharedFileIntegrityException : IOException("Downloaded file does not match its recorded SHA-256")

/**
 * Local copies of shared files for viewing (MON-23), in `cache/shared_files/view/{sha256}/{name}`
 * — the directory `file_paths.xml` exposes to the `FileProvider`, so a viewer app gets a one-off
 * read grant on exactly the file it was handed.
 *
 * **A copy exists only once it has been checked.** A download goes to a `.part` file, is hashed,
 * and is renamed into place only when the hash is the recorded one; so a copy found here needs no
 * second check, and a file that does not match what a parent filed or sent is never opened as if
 * it did. The cache is the system's to clear — nothing here is the record, the server is.
 */
@Singleton
class SharedFileCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: SharedFileStorage
) {

    private val viewRoot: File get() = File(context.cacheDir, VIEW_DIRECTORY)

    /** A verified local copy of the file at [storagePath], downloading it when there is none. */
    suspend fun localCopy(storagePath: String, fileName: String, sha256: String): File =
        withContext(Dispatchers.IO) {
            val target = File(File(viewRoot, sha256), fileName)
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                val partial = File(target.parentFile, "$fileName$PARTIAL_SUFFIX")
                storage.download(storagePath, partial)
                if (sha256Of(partial) != sha256) {
                    partial.delete()
                    throw SharedFileIntegrityException()
                }
                if (!partial.renameTo(target)) throw IOException("Could not keep the downloaded file")
            }
            target
        }

    /**
     * Moves a file this phone has just uploaded into the view cache, so the sender opens their own
     * attachment without downloading bytes they already hold. Its digest was computed as it was
     * staged, which is the check a download would have made.
     */
    suspend fun adopt(file: File, fileName: String, sha256: String) {
        withContext(Dispatchers.IO) {
            val target = File(File(viewRoot, sha256), fileName)
            target.parentFile?.mkdirs()
            if (target.exists() || !file.renameTo(target)) file.delete()
        }
    }

    /**
     * Keeps [bytes] this phone has just uploaded as the verified copy of [sha256]/[fileName], so
     * the uploader sees their own record photograph (L-4) without downloading it again. The digest
     * was computed from these bytes before the upload, which is the check a download would make.
     */
    suspend fun keep(bytes: ByteArray, fileName: String, sha256: String) {
        withContext(Dispatchers.IO) {
            val target = File(File(viewRoot, sha256), fileName)
            if (target.exists()) return@withContext
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "$fileName$PARTIAL_SUFFIX")
            partial.writeBytes(bytes)
            if (!partial.renameTo(target)) partial.delete()
        }
    }

    /** The cached copy for [sha256]/[fileName] when one is already here, without downloading. */
    fun cached(fileName: String, sha256: String): File? =
        File(File(viewRoot, sha256), fileName).takeIf { it.exists() }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().toHex()
    }

    private companion object {
        /** Under `cacheDir`; `res/xml/file_paths.xml` names the same directory. */
        const val VIEW_DIRECTORY = "shared_files/view"
        const val PARTIAL_SUFFIX = ".part"
        const val BUFFER_BYTES = 64 * 1024
    }
}
