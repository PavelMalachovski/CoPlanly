package com.coparently.app.presentation.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.coparently.app.R
import com.coparently.app.data.files.SharedFileIntegrityException
import com.coparently.app.data.files.SharedFileRejectedException
import com.coparently.app.domain.files.SharedFilePolicy
import java.io.File
import java.util.UUID

/**
 * The sentence for a shared-file failure: the policy's reason when the file was refused before
 * upload, the integrity sentence when a download did not match its digest, [fallback] otherwise.
 * Never `e.message` (CQ-14).
 */
fun sharedFileError(error: Throwable, @StringRes fallback: Int): UiText = when {
    error is SharedFileRejectedException && error.rejection == SharedFilePolicy.Rejection.TYPE ->
        UiText.Res(R.string.shared_file_error_type)
    error is SharedFileRejectedException -> UiText.Res(R.string.shared_file_error_size)
    error is SharedFileIntegrityException -> UiText.Res(R.string.shared_file_error_integrity)
    else -> UiText.Res(fallback)
}

/**
 * Opens a shared file (MON-23) in whatever viewer the phone has, through this app's
 * `FileProvider` with a one-off read grant — never a `file://` URI, never a download URL.
 *
 * @return false when no installed app can open [contentType], so the caller can say so.
 */
fun openSharedFile(context: Context, file: File, contentType: String): Boolean {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, contentType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(view)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/**
 * A fresh `content://` target for the camera, under `cache/shared_files/capture/` (the directory
 * `file_paths.xml` exposes). The photo is staged and uploaded from there like any picked file.
 */
fun newSharedFileCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "shared_files/capture").apply { mkdirs() }
    val file = File(directory, "photo-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
