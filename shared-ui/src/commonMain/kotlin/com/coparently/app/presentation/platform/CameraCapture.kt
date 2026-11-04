package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable

/**
 * The camera, for one photo: a receipt, or a document for the vault.
 *
 * The launcher asks for the camera permission first when it is not granted yet, and takes the
 * photo once it is. [available] is false on a phone with no camera at all, where the screen offers
 * the picker alone.
 */
interface CameraCapture : PlatformCapability {

    /**
     * A launcher that takes one photo into [target]'s place.
     *
     * The place survives the camera app taking this activity down while it runs, so the photo is
     * not lost when it comes back.
     *
     * @param onResult Receives what came of it
     */
    @Composable
    fun rememberCapture(target: CaptureTarget, onResult: (CaptureResult) -> Unit): PlatformLauncher
}

/** Where a photo is put before the app uploads it. */
enum class CaptureTarget {

    /** A receipt, read on the phone for its amount (CLAUDE.md item 10) and attached to an expense. */
    RECEIPT,

    /** A file for the vault, staged and uploaded like any picked file (MON-23). */
    SHARED_FILE,
}

/** What came of one [CameraCapture] launch. */
sealed interface CaptureResult {

    /** The photo was taken; [contentUri] is where it is, as text. */
    data class Taken(val contentUri: String) : CaptureResult

    /** The parent closed the camera without a photo. */
    data object Cancelled : CaptureResult

    /** No camera app could be opened. */
    data object NoCameraApp : CaptureResult

    /** The camera permission was refused. */
    data object PermissionDenied : CaptureResult
}
