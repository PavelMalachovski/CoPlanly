package com.coparently.app.data.session

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.export.ExportFileWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes what an account leaves on the phone outside the encrypted database: the plaintext
 * copies of its files and the notifications it was shown (security audit, October 2026).
 *
 * The database is SQLCipher and the preferences are sealed, but a file the parent opened is
 * not: a child's medical photo, a court order from the vault or a chat attachment is downloaded,
 * checked against its SHA-256 and kept as plain bytes under `cache/shared_files/view` so a viewer
 * app can be handed it. So are a camera capture, a receipt photo, the last exported record and a
 * payment code. Nothing deleted any of them — not sign-out, not an account switch, not account
 * deletion — so the next person on a handed-down phone, or anybody reading its storage, found the
 * previous account's files beside a database encrypted precisely to stop that.
 *
 * Two strengths, because Room's own lifetime differs between them:
 * - [clearAfterSignOut] — Room survives sign-out (a returning parent keeps their history), and so
 *   does `files/chat_outbox`, which holds the files of messages still waiting to send: they go
 *   when this account comes back. Everything re-downloadable or one-off goes.
 * - [clearAccount] — an account switch or deletion clears Room, so the outbox's files belong to
 *   messages that no longer exist; they go too.
 *
 * Both also cancel the notifications on screen: a chat preview or a child's name left in the shade
 * is the previous account's, whoever picks the phone up next.
 *
 * Best effort: a file that cannot be deleted is logged by directory, never by name, and never
 * stops a sign-out or a deletion.
 */
@Singleton
class DeviceTraces @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** What sign-out removes; see the class comment. */
    suspend fun clearAfterSignOut() {
        withContext(Dispatchers.IO) { CACHE_DIRECTORIES.forEach { delete(File(context.cacheDir, it)) } }
        cancelNotifications()
    }

    /** What an account switch or deletion removes; see the class comment. */
    suspend fun clearAccount() {
        withContext(Dispatchers.IO) {
            CACHE_DIRECTORIES.forEach { delete(File(context.cacheDir, it)) }
            delete(File(context.filesDir, ChatAttachmentOutbox.OUTBOX_DIRECTORY))
        }
        cancelNotifications()
    }

    private fun delete(directory: File) {
        if (directory.exists() && !directory.deleteRecursively()) {
            Log.w(TAG, "Could not delete everything under ${directory.name}")
        }
    }

    private fun cancelNotifications() {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
            .onFailure { Log.w(TAG, "Could not cancel the notifications", it) }
    }

    companion object {
        private const val TAG = "DeviceTraces"

        /**
         * Under `cacheDir`: everything `res/xml/file_paths.xml` exposes — the downloaded files and
         * the vault's camera captures and uploads (`shared_files`), receipt photos (`receipts`),
         * the last export ([ExportFileWriter.EXPORT_DIRECTORY]) and payment codes
         * (`payment_codes`). A new directory of the account's files joins this list.
         */
        val CACHE_DIRECTORIES = listOf(
            "shared_files",
            "receipts",
            ExportFileWriter.EXPORT_DIRECTORY,
            "payment_codes"
        )
    }
}
