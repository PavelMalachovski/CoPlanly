package com.coparently.app.data.platform

import kotlinx.io.files.Path

/**
 * The app's own private directories (MON-24 phase 2, Task 6).
 *
 * On Android they are the `Context`'s `filesDir`, `cacheDir` and `noBackupFilesDir`, read each time
 * they are asked for, so a test's or an e2e parent's `ContextWrapper` still decides them. On the
 * iPhone they are folders in the app's container (`IosAppDirectories`).
 */
interface AppDirectories {

    /** Files the app keeps until it deletes them: the chat outbox's staged attachments. */
    val files: Path

    /** Files the system may delete under storage pressure: verified downloads, exports, staging. */
    val cache: Path

    /**
     * Files that must never reach a backup, because they could not be opened on another device:
     * the sealed preference store on Android.
     */
    val noBackup: Path
}
