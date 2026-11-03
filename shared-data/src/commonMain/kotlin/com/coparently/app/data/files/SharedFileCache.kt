package com.coparently.app.data.files

import com.coparently.app.data.platform.AppDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.Path

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
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8j), on kotlinx-io paths under [AppDirectories]
 * (`java.io.File` before; a screen that hands a copy to Android opens `File(path.toString())`).
 */
class SharedFileCache(
    private val directories: AppDirectories,
    private val storage: SharedFileStorage
) {

    private val viewRoot: Path get() = Path(directories.cache, VIEW_DIRECTORY, VIEW_SUBDIRECTORY)

    /** A verified local copy of the file at [storagePath], downloading it when there is none. */
    suspend fun localCopy(storagePath: String, fileName: String, sha256: String): Path =
        withContext(Dispatchers.IO) {
            val target = Path(viewRoot, sha256, fileName)
            if (!LocalFiles.exists(target)) {
                val directory = Path(viewRoot, sha256)
                LocalFiles.createDirectories(directory)
                val partial = Path(directory, "$fileName$PARTIAL_SUFFIX")
                storage.download(storagePath, partial)
                if (LocalFiles.sha256Of(partial) != sha256) {
                    LocalFiles.delete(partial)
                    throw SharedFileIntegrityException()
                }
                if (!LocalFiles.rename(partial, target)) throw IOException("Could not keep the downloaded file")
            }
            target
        }

    /**
     * Moves a file this phone has just uploaded into the view cache, so the sender opens their own
     * attachment without downloading bytes they already hold. Its digest was computed as it was
     * staged, which is the check a download would have made.
     */
    suspend fun adopt(file: Path, fileName: String, sha256: String) {
        withContext(Dispatchers.IO) {
            val target = Path(viewRoot, sha256, fileName)
            LocalFiles.createDirectories(Path(viewRoot, sha256))
            if (LocalFiles.exists(target) || !LocalFiles.rename(file, target)) LocalFiles.delete(file)
        }
    }

    /**
     * Keeps [bytes] this phone has just uploaded as the verified copy of [sha256]/[fileName], so
     * the uploader sees their own record photograph (L-4) without downloading it again. The digest
     * was computed from these bytes before the upload, which is the check a download would make.
     */
    suspend fun keep(bytes: ByteArray, fileName: String, sha256: String) {
        withContext(Dispatchers.IO) {
            val target = Path(viewRoot, sha256, fileName)
            if (LocalFiles.exists(target)) return@withContext
            val directory = Path(viewRoot, sha256)
            LocalFiles.createDirectories(directory)
            val partial = Path(directory, "$fileName$PARTIAL_SUFFIX")
            LocalFiles.writeBytes(partial, bytes)
            if (!LocalFiles.rename(partial, target)) LocalFiles.delete(partial)
        }
    }

    /** The cached copy for [sha256]/[fileName] when one is already here, without downloading. */
    fun cached(fileName: String, sha256: String): Path? =
        Path(viewRoot, sha256, fileName).takeIf { LocalFiles.exists(it) }

    private companion object {
        /**
         * Under `cacheDir`, `shared_files/view`; `res/xml/file_paths.xml` names the same directory,
         * and `DeviceTraces` clears `shared_files`.
         */
        const val VIEW_DIRECTORY = "shared_files"
        const val VIEW_SUBDIRECTORY = "view"
        const val PARTIAL_SUFFIX = ".part"
    }
}
