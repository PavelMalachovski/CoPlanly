package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.presentation.settings.AppLanguage
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile

/*
 * The iPhone's platform capabilities (MON-24 phase 2, Task 11): what the iPhone app's view
 * controller will hand `ProvidePlatform` (phase 3). Until each capability is built there, it is a
 * stand-in that says it is not available, so the shared screens draw no affordance for it (design
 * item 8): QR scanning and dictation until phase 4, the rest until phase 3. A launch that
 * comes anyway does nothing and reports nothing it did not do. Images (`IosImages`) and payment
 * codes (`IosQrCodes`) are the iPhone's own since part C, and since its Task 11 the share sheet
 * (`IosShareSheet`), the pickers (`IosFilePicker`) and the camera (`IosCameraCapture`).
 */

/**
 * The capabilities the iPhone has today, with [buildValues] from the iPhone app's own build (its
 * version, and the hosted addresses it ships with).
 */
fun iosPlatformServices(buildValues: AppBuildValues): PlatformServices = PlatformServices(
    shareSheet = IosShareSheet,
    filePicker = IosFilePicker,
    cameraCapture = IosCameraCapture,
    qrScanner = NoQrScanner,
    dictation = NoDictation,
    permissions = NoPermissions,
    openUrl = NoOpenUrl,
    clipboard = NoClipboard,
    buildValues = buildValues,
    images = IosImages.fromKoin(),
    fileDetails = IosFileDetails,
    qrCodes = IosQrCodes(shareSheet = IosShareSheet),
    appLanguages = NoAppLanguages,
)

/**
 * Everything the shell provides, for the iPhone app's screens to be drawn in: [iosPlatformServices]
 * and the two capabilities `MainActivity` provides on its own on Android, because they need its
 * activity — the credential sheets ([LocalCredentialSignIn]) and Google Calendar's sign-in
 * ([LocalGoogleCalendarSignIn]). Their locals throw outside a provider, so Settings and the sign-in
 * screen would fail before their `available` checks. The credential sheets are [credentialSignIn],
 * the app's own (`IosCredentialSignIn`, Task 9: Google's sheet where the app carries Google's
 * configuration, unavailable otherwise, so the sign-in then offers e-mail alone); Google Calendar's
 * is a stand-in that says it is not available, so Settings draws no Google Calendar row (the 11f
 * review's I1). The shell must also switch the family before it arms any link (CLAUDE.md item 15):
 * `AppLaunch` carries no family, so the order is the shell's.
 */
@Composable
fun ProvideIosShell(
    buildValues: AppBuildValues,
    credentialSignIn: CredentialSignIn,
    content: @Composable () -> Unit
) {
    val services = remember(buildValues) { iosPlatformServices(buildValues) }
    ProvidePlatform(services) {
        CompositionLocalProvider(
            LocalCredentialSignIn provides credentialSignIn,
            LocalGoogleCalendarSignIn provides NoGoogleCalendarSignIn,
            content = content
        )
    }
}

/** No Google sign-in until phase 3: nothing to open. */
internal object NoGoogleCalendarSignIn : GoogleCalendarSignIn {
    override val available: Boolean = false

    override fun open(): GoogleSignInStart = GoogleSignInStart.UNAVAILABLE
}

/** A launcher with nothing behind it. */
private val NothingToLaunch = PlatformLauncher { }

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
 * A picked file's name and size, and sizes, as Foundation writes a file's (`NSByteCountFormatter`,
 * file style, the phone's locale). A picked item is the copy [IosFilePicker] kept, so its name is
 * the one the parent picked (`IosPickedFiles.displayNameOf`) and its size the copy's; a file whose
 * size cannot be read shows its name alone, as Android does when the resolver knows nothing.
 */
private object IosFileDetails : FileDetails {
    override val available: Boolean = true

    private val files = IosPickedFiles.forApp()

    override fun describe(contentUri: String): PickedFileDescription {
        val name = files.displayNameOf(contentUri) ?: contentUri.substringAfterLast('/')
        val bytes = runCatching { SystemFileSystem.metadataOrNull(Path(IosPickedFiles.pathOf(contentUri)))?.size }
            .getOrNull()
        return PickedFileDescription(name = name, size = bytes?.takeIf { it >= 0 }?.let(::formatSize).orEmpty())
    }

    override fun formatSize(bytes: Long): String =
        NSByteCountFormatter.stringFromByteCount(bytes, NSByteCountFormatterCountStyleFile)
}

/**
 * No language picker in the app: iOS Settings holds the per-app language, and it puts that choice
 * first in `NSLocale.preferredLanguages`. The app follows the owner's rule (`AppLanguageRule`: the
 * phone's first language when shipped, else English) through `readerLocale()`, which the date and
 * money seams read, so there is nothing to apply here. Phase 3 links to iOS Settings.
 */
private object NoAppLanguages : AppLanguages {
    override val available: Boolean = false

    override fun current(): AppLanguage = AppLanguage.SYSTEM

    override fun apply(language: AppLanguage) = Unit
}
