@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.session

import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import platform.Foundation.NSFileManager

/**
 * Removes what an account leaves on the iPhone outside the database: the plain copies of its files
 * (MON-24 phase 3, Task 5), as the app's `DeviceTraces` does on Android, with the same two strengths
 * and the same directories under [AppDirectories]:
 * - [clearAfterSignOut] — the database survives sign-out (a returning parent keeps their history),
 *   and so does `files/chat_outbox`, which holds the files of messages still waiting to send. Every
 *   cached file goes.
 * - [clearAccount] — an account switch clears the database, so the outbox's files belong to
 *   messages that no longer exist; they go too.
 *
 * Android also cancels the notifications on screen. The iPhone posts none yet (no push, no local
 * notification); the task that makes it post one adds their removal here. Best effort: a folder
 * that cannot be deleted is logged by name, never by its files' names, and never stops a sign-out.
 *
 * @property directories the app's folders: [AppDirectories.cache] and [AppDirectories.files]
 */
class IosAccountTraces(private val directories: AppDirectories) : AccountTraces {

    /** What sign-out removes; see the class comment. */
    override suspend fun clearAfterSignOut() {
        withContext(Dispatchers.IO) { CACHE_DIRECTORIES.forEach { delete(Path(directories.cache, it)) } }
    }

    /** What an account switch removes; see the class comment. */
    suspend fun clearAccount() {
        withContext(Dispatchers.IO) {
            CACHE_DIRECTORIES.forEach { delete(Path(directories.cache, it)) }
            delete(Path(directories.files, ChatAttachmentOutbox.OUTBOX_DIRECTORY))
        }
    }

    private fun delete(directory: Path) {
        val files = NSFileManager.defaultManager
        val path = directory.toString()
        if (files.fileExistsAtPath(path) && !files.removeItemAtPath(path, error = null)) {
            logger.w(TAG, "Could not delete everything under ${directory.name}")
        }
    }

    /** The directories, as Android's `DeviceTraces` names them. */
    companion object {
        private const val TAG = "IosAccountTraces"

        /**
         * Under [AppDirectories.cache], Android's list: the downloaded files and the vault's
         * captures (`shared_files`), receipt photos (`receipts`), the last export (`exports`) and
         * payment codes (`payment_codes`). A new directory of the account's files joins this list
         * and Android's.
         */
        val CACHE_DIRECTORIES: List<String> = listOf("shared_files", "receipts", "exports", "payment_codes")
    }
}
