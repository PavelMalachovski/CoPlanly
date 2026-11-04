package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Every platform capability the screens read, in one value the shell builds once (MON-24 phase 2,
 * Task 11).
 *
 * One constructor with no defaults, so a shell cannot leave a capability out: the compiler refuses
 * it. On Android `MainActivity` builds it from today's Android code (`ProvideAndroidPlatform` in the
 * app); on the iPhone `iosPlatformServices()` answers with stand-ins until phase 3 and 4.
 */
@Suppress("LongParameterList") // one parameter per capability, with no default, on purpose (above)
class PlatformServices(
    val shareSheet: ShareSheet,
    val filePicker: FilePicker,
    val cameraCapture: CameraCapture,
    val qrScanner: QrScanner,
    val dictation: Dictation,
    val permissions: PermissionRequester,
    val openUrl: OpenUrl,
    val clipboard: SensitiveClipboard,
    val buildValues: AppBuildValues,
) {

    /** One provided value per local in [PlatformLocals.all], in that order. */
    fun providedValues(): Array<ProvidedValue<*>> = arrayOf(
        LocalShareSheet provides shareSheet,
        LocalFilePicker provides filePicker,
        LocalCameraCapture provides cameraCapture,
        LocalQrScanner provides qrScanner,
        LocalDictation provides dictation,
        LocalPermissionRequester provides permissions,
        LocalOpenUrl provides openUrl,
        LocalSensitiveClipboard provides clipboard,
        LocalAppBuildValues provides buildValues,
    )
}

/** Provides every capability in [services] to [content]: the shell's one call. */
@Composable
fun ProvidePlatform(services: PlatformServices, content: @Composable () -> Unit) {
    CompositionLocalProvider(*services.providedValues(), content = content)
}

/**
 * A capability read where no shell provided it.
 *
 * A screen composed without [ProvidePlatform] fails here, loudly, rather than drawing as if the phone
 * could do nothing: a capability that quietly read "not available" would hide a feature in
 * production and no test would notice. A test that composes a screen provides the platform as the
 * app does (the screenshot frame does).
 */
private fun notProvided(name: String): Nothing =
    error("$name was read outside ProvidePlatform: the shell (MainActivity) provides every platform capability")

/** The share sheet. */
val LocalShareSheet = staticCompositionLocalOf<ShareSheet> { notProvided("LocalShareSheet") }

/** The photo and document pickers. */
val LocalFilePicker = staticCompositionLocalOf<FilePicker> { notProvided("LocalFilePicker") }

/** The camera. */
val LocalCameraCapture = staticCompositionLocalOf<CameraCapture> { notProvided("LocalCameraCapture") }

/** The QR scanner. */
val LocalQrScanner = staticCompositionLocalOf<QrScanner> { notProvided("LocalQrScanner") }

/** Voice typing's platform half. */
val LocalDictation = staticCompositionLocalOf<Dictation> { notProvided("LocalDictation") }

/** The runtime permissions. */
val LocalPermissionRequester =
    staticCompositionLocalOf<PermissionRequester> { notProvided("LocalPermissionRequester") }

/** Links, files, the dialler, mail and the app's settings. */
val LocalOpenUrl = staticCompositionLocalOf<OpenUrl> { notProvided("LocalOpenUrl") }

/** The clipboard for credentials. */
val LocalSensitiveClipboard =
    staticCompositionLocalOf<SensitiveClipboard> { notProvided("LocalSensitiveClipboard") }

/** The build's values. */
val LocalAppBuildValues = staticCompositionLocalOf<AppBuildValues> { notProvided("LocalAppBuildValues") }

/** The platform's locals, for the test that the shell's provider covers each of them. */
object PlatformLocals {

    /** Every local [PlatformServices.providedValues] provides, in its order. */
    val all: List<ProvidableCompositionLocal<*>> = listOf(
        LocalShareSheet,
        LocalFilePicker,
        LocalCameraCapture,
        LocalQrScanner,
        LocalDictation,
        LocalPermissionRequester,
        LocalOpenUrl,
        LocalSensitiveClipboard,
        LocalAppBuildValues,
    )
}
