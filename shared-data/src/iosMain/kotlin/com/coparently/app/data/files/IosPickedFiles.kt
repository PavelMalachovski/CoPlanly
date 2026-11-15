@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.files

import com.coparently.app.data.platform.IosAppDirectories
import com.coparently.app.data.platform.logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UniformTypeIdentifiers.UTType

/**
 * [PickedFiles] on the iPhone (MON-24 phase 3, part C, Task 11).
 *
 * A picked item's "content URI" here is the absolute path of the copy the picker made with
 * [IosPickedFiles.keep], in `picked/<uuid>/<sanitised name>` under the app's cache directory
 * (`AppDirectories.cache`, so sign-out, an account switch and deletion remove it with the rest of
 * the account's files: `IosAccountTraces.CACHE_DIRECTORIES`): the system's pickers hand out
 * a file only for the length of their callback (the photo picker) or in the app's own inbox (the
 * document picker), so the app keeps its own copy, as Android's `content://` URI stays readable for
 * the grant's life. A photo the camera took (`receipts/`, `shared_files/capture/` under the same
 * cache directory) is a path too, and a `file://` address is read as its path.
 *
 * A copy stays until the cache is wiped or iOS purges it: the same path is shown as a preview,
 * staged, uploaded and described by different consumers, none of which is the last one for sure.
 *
 * - [typeOf] answers from the extension, through `UTType`'s preferred MIME type.
 * - [displayNameOf] answers the original name, which [keep] writes beside the copy, else the path's
 *   last segment.
 * - [open] reads the file.
 *
 * @param caches The app's cache directory (`AppDirectories.cache`; a test names its own).
 */
class IosPickedFiles(private val caches: String) : PickedFiles {

    override fun typeOf(contentUri: String): String? {
        val name = pathOf(contentUri).substringAfterLast('/')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isEmpty()) return null
        return UTType.typeWithFilenameExtension(extension)?.preferredMIMEType
    }

    override fun displayNameOf(contentUri: String): String? {
        val path = pathOf(contentUri)
        val original = originalNameFile(path)?.let { file ->
            runCatching { SystemFileSystem.source(file).buffered().use { it.readString() } }.getOrNull()
        }
        return original?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/').takeIf { it.isNotEmpty() }
    }

    override fun open(contentUri: String): RawSource {
        val path = Path(pathOf(contentUri))
        if (!SystemFileSystem.exists(path)) throw IOException("Cannot open the picked file")
        return SystemFileSystem.source(path)
    }

    /**
     * Copies the file at [source] (a path, or a `file://` address) into a folder of its own under
     * `picked/`, named [originalName] made safe ([sanitise]), and records [originalName]
     * beside the folder for [displayNameOf]. Answers the copy's absolute path: the picked item's
     * content URI.
     *
     * Nothing is written outside `picked/<uuid>/`: the name keeps no `/`, and a name of dots
     * alone becomes [FALLBACK_NAME].
     *
     * @throws IOException when the copy could not be made
     */
    fun keep(source: String, originalName: String): String {
        val id = NSUUID().UUIDString
        val root = "$caches/$PICKED_DIRECTORY"
        val folder = "$root/$id"
        val target = "$folder/${sanitise(originalName)}"
        val manager = NSFileManager.defaultManager
        val created = manager.createDirectoryAtPath(
            folder,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        if (!created) throw IOException("Could not make a folder for the picked file")
        val copied = manager.copyItemAtPath(pathOf(source), toPath = target, error = null)
        if (!copied) {
            manager.removeItemAtPath(folder, error = null)
            throw IOException("Could not copy the picked file")
        }
        runCatching {
            SystemFileSystem.sink(Path("$root/$id$NAME_SUFFIX")).buffered().use { it.writeString(originalName) }
        }.onFailure { logger.w(TAG, "The picked file's name was not kept", it) }
        return target
    }

    /** The sidecar [keep] wrote for the copy at [path], or null when [path] is not such a copy. */
    private fun originalNameFile(path: String): Path? {
        val root = "$caches/$PICKED_DIRECTORY/"
        if (!path.startsWith(root)) return null
        val id = path.removePrefix(root).substringBefore('/', missingDelimiterValue = "")
        if (id.isEmpty()) return null
        return Path("$root$id$NAME_SUFFIX").takeIf { SystemFileSystem.exists(it) }
    }

    /** Public for the pickers in `:shared-ui` and the tests. */
    companion object {
        /** The folder under the cache directory that holds the picked copies. */
        const val PICKED_DIRECTORY = "picked"

        /** What a name made only of characters that are not kept (or of dots) becomes. */
        const val FALLBACK_NAME = "file"

        /** The longest name kept, in characters; a longer one keeps its end, the extension. */
        private const val MAX_NAME_LENGTH = 120

        private const val NAME_SUFFIX = ".name"
        private const val TAG = "IosPickedFiles"

        /**
         * [name] made safe for one path segment: letters (accents included, composed or not),
         * digits, spaces, `.`, `-` and `_` are kept, anything else, `/` included, becomes `_`. A
         * result of dots alone, or empty, is [FALLBACK_NAME]; a long one keeps its last
         * [MAX_NAME_LENGTH] characters.
         */
        fun sanitise(name: String): String {
            val kept = name.map { char ->
                when {
                    char.isLetterOrDigit() || char == ' ' || char == '.' || char == '-' || char == '_' -> char
                    char.category == CharCategory.NON_SPACING_MARK -> char
                    else -> '_'
                }
            }.joinToString("").takeLast(MAX_NAME_LENGTH)
            return if (kept.all { it == '.' }) FALLBACK_NAME else kept
        }

        /** [contentUri] as a path: a `file://` address's path, else the text itself. */
        fun pathOf(contentUri: String): String =
            if (contentUri.startsWith(FILE_SCHEME)) {
                NSURL.URLWithString(contentUri)?.path ?: contentUri.removePrefix(FILE_SCHEME)
            } else {
                contentUri
            }

        /** The app's cache directory, as `platformDataModule`'s `AppDirectories` names it. */
        fun appCache(): String = IosAppDirectories().cache.toString()

        /** The picked files in [appCache], for the pickers, which run outside the Koin graph's reach. */
        fun forApp(): IosPickedFiles = IosPickedFiles(appCache())

        private const val FILE_SCHEME = "file://"
    }
}
