package com.coparently.app.data.files

import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.platform.Sha256
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The file operations `data/files/` and its two callers make, on kotlinx-io's system file system,
 * answered as `java.io.File` answered them before MON-24 phase 2 (Task 8j): a deletion, a rename or
 * a directory that cannot be made reports false or does nothing, as `File.delete()`,
 * `File.renameTo()` and `File.mkdirs()` did, rather than throwing. Reading and writing still throw
 * an `IOException`, as the streams did.
 */
internal object LocalFiles {

    /** Bytes read or hashed at a time, as the app's file copies always read them. */
    const val BUFFER_BYTES = 64 * 1024

    /** Whether anything is at [path]. */
    fun exists(path: Path): Boolean = SystemFileSystem.exists(path)

    /** Makes the directory [path] and its parents, as `File.mkdirs()`: a failure throws nothing. */
    fun createDirectories(path: Path) {
        runCatching { SystemFileSystem.createDirectories(path) }
    }

    /**
     * Deletes the file or empty directory at [path], as `File.delete()`: false when nothing was
     * there or it could not be deleted.
     */
    fun delete(path: Path): Boolean = runCatching { SystemFileSystem.delete(path, mustExist = true) }.isSuccess

    /**
     * Moves [from] to [to], replacing a file already there, as `File.renameTo` does on Android (a
     * POSIX `rename`): false when it could not.
     */
    fun rename(from: Path, to: Path): Boolean = runCatching { SystemFileSystem.atomicMove(from, to) }.isSuccess

    /**
     * Deletes [path] and everything under it, as `File.deleteRecursively`: true when nothing is left,
     * including when nothing was there.
     */
    fun deleteRecursively(path: Path): Boolean {
        val metadata = SystemFileSystem.metadataOrNull(path) ?: return true
        var deleted = true
        if (metadata.isDirectory) {
            val children = runCatching { SystemFileSystem.list(path) }.getOrDefault(emptyList())
            children.forEach { child -> if (!deleteRecursively(child)) deleted = false }
        }
        return (delete(path) || !exists(path)) && deleted
    }

    /** Writes [bytes] to the file at [path], replacing what was there. */
    fun writeBytes(path: Path, bytes: ByteArray) {
        sinkOf(path).use { sink -> sink.write(bytes) }
    }

    /** The file at [path], to read. */
    fun sourceOf(path: Path): Source = SystemFileSystem.source(path).buffered()

    /** The file at [path], to write, replacing what was there. */
    fun sinkOf(path: Path): Sink = SystemFileSystem.sink(path).buffered()

    /** The lowercase hex SHA-256 of the file at [path], read [BUFFER_BYTES] at a time. */
    fun sha256Of(path: Path): String = sourceOf(path).use { source ->
        val buffer = ByteArray(BUFFER_BYTES)
        Sha256.digestOf { update ->
            var read = source.readAtMostTo(buffer)
            while (read >= 0) {
                update(buffer, 0, read)
                read = source.readAtMostTo(buffer)
            }
        }.toHex()
    }
}
