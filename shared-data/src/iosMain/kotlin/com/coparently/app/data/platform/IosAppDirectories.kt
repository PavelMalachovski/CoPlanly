@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Folders in the app's own container, created on first use.
 *
 * [files] and [noBackup] sit under Application Support, which the user never sees, and [noBackup]
 * is marked excluded from iCloud and computer backups (`NSURLIsExcludedFromBackupKey`), as Android's
 * `noBackupFilesDir` is by definition. [cache] is under Caches, which iOS may empty when storage
 * runs low, as Android may empty `cacheDir`.
 */
class IosAppDirectories : AppDirectories {

    override val files: Path by lazy { Path(directory(NSApplicationSupportDirectory, "files")) }

    override val cache: Path by lazy { Path(directory(NSCachesDirectory, "cache")) }

    override val noBackup: Path by lazy {
        val path = directory(NSApplicationSupportDirectory, "no_backup")
        val excluded = NSURL.fileURLWithPath(path).setResourceValue(
            NSNumber(bool = true),
            forKey = NSURLIsExcludedFromBackupKey,
            error = null
        )
        if (!excluded) logger.w(TAG, "Could not exclude no_backup from backups")
        Path(path)
    }

    /** [child] under the user-domain [base] directory, created if missing. */
    private fun directory(base: NSSearchPathDirectory, child: String): String {
        val root = NSSearchPathForDirectoriesInDomains(base, NSUserDomainMask, true).first() as String
        val path = "$root/$child"
        val created = NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        if (!created) logger.w(TAG, "Could not create the $child directory")
        return path
    }

    private companion object {
        const val TAG = "IosAppDirectories"
    }
}
