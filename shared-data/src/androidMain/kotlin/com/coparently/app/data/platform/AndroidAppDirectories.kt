package com.coparently.app.data.platform

import android.content.Context
import kotlinx.io.files.Path

/**
 * The [Context]'s own directories, read on every access: an e2e parent's `PhoneDirectories`, a
 * `ContextWrapper`, gives each phone its own, and a value read once would outlive the wrapper's
 * answer.
 */
class AndroidAppDirectories(private val context: Context) : AppDirectories {

    override val files: Path get() = Path(context.filesDir.path)

    override val cache: Path get() = Path(context.cacheDir.path)

    override val noBackup: Path get() = Path(context.noBackupFilesDir.path)
}
