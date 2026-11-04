package com.coparently.app.presentation.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/*
 * The iPhone's platform capabilities (MON-24 phase 2, Task 11): what the iPhone app's view
 * controller will hand `ProvidePlatform` (phase 3). Until each capability is built there, it is a
 * stand-in that says it is not available, so the shared screens draw no affordance for it (design
 * item 8): camera, QR scanning and dictation until phase 4, the rest until phase 3. A launch that
 * comes anyway does nothing and reports nothing it did not do. Images are not loaded yet either.
 */

/**
 * The capabilities the iPhone has today, with [buildValues] from the iPhone app's own build (its
 * version, and the hosted addresses it ships with).
 */
fun iosPlatformServices(buildValues: AppBuildValues): PlatformServices = PlatformServices(
    shareSheet = NoShareSheet,
    filePicker = NoFilePicker,
    cameraCapture = NoCameraCapture,
    qrScanner = NoQrScanner,
    dictation = NoDictation,
    permissions = NoPermissions,
    openUrl = NoOpenUrl,
    clipboard = NoClipboard,
    buildValues = buildValues,
    images = NoImages,
)

/** A launcher with nothing behind it. */
private val NothingToLaunch = PlatformLauncher { }

/** No share sheet until phase 3 (`UIActivityViewController`). */
private object NoShareSheet : ShareSheet {
    override val available: Boolean = false

    override fun shareText(text: String, chooserTitle: String) = Unit

    override fun shareFile(contentUri: String, mimeType: String, chooserTitle: String) = Unit
}

/** No pickers until phase 3 (`PHPickerViewController`, the document picker). */
private object NoFilePicker : FilePicker {
    override val available: Boolean = false

    @Composable
    override fun rememberImagePicker(onPicked: (contentUri: String) -> Unit): PlatformLauncher = NothingToLaunch

    @Composable
    override fun rememberDocumentPicker(
        contentTypes: List<String>,
        onPicked: (contentUri: String) -> Unit
    ): PlatformLauncher = NothingToLaunch
}

/** No camera until phase 4. */
private object NoCameraCapture : CameraCapture {
    override val available: Boolean = false

    @Composable
    override fun rememberCapture(target: CaptureTarget, onResult: (CaptureResult) -> Unit): PlatformLauncher =
        NothingToLaunch
}

/** No QR scanning until phase 4: the code and the link are how an invitation arrives. */
private object NoQrScanner : QrScanner {
    override val available: Boolean = false

    @Composable
    override fun rememberScanner(onScanned: (code: String) -> Unit): PlatformLauncher = NothingToLaunch
}

/** No dictation until phase 4, beside `NoSpeechDictation` in `platformUiModule`. */
private object NoDictation : Dictation {
    override val available: Boolean = false

    @Composable
    override fun rememberLanguageTag(): String = ""
}

/**
 * No runtime permission can be asked yet: nothing is granted, and a request is refused at once, so
 * a screen that asked anyway takes its "denied" path rather than waiting.
 */
private object NoPermissions : PermissionRequester {
    override val available: Boolean = false

    override fun isGranted(permission: PlatformPermission): Boolean = false

    @Composable
    override fun rememberRequest(permission: PlatformPermission, onDenied: () -> Unit): PermissionRequest =
        PermissionRequest { onDenied() }
}

/** Nothing is handed to another app until phase 3: every call answers that no app took it. */
private object NoOpenUrl : OpenUrl {
    override val available: Boolean = false

    override fun open(url: String): Boolean = false

    override fun openFile(path: String, contentType: String): Boolean = false

    override fun dial(number: String): Boolean = false

    override fun composeEmail(address: String, subject: String, body: String): Boolean = false

    override fun openAppSettings(): Boolean = false
}

/** No clipboard until phase 3 (`UIPasteboard`, marked local-only). */
private object NoClipboard : SensitiveClipboard {
    override val available: Boolean = false

    override fun copySensitive(label: String, text: String) = Unit
}

/**
 * No image loading until phase 3: the image's place keeps its size and shows its placeholder (an
 * account's initial, for example), so the screen around it lays out as it will.
 */
private object NoImages : PlatformImages {
    @Composable
    override fun Image(
        model: Any?,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        fullResolution: Boolean,
        placeholder: (@Composable () -> Unit)?,
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { placeholder?.invoke() }
    }
}
