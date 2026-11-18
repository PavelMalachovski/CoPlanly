package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.remote.firebase.NotificationPermission
import com.coparently.app.data.remote.firebase.UserNotificationsPermission
import com.coparently.app.presentation.settings.AppLanguage
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile

/*
 * The iPhone's platform capabilities (MON-24 phase 2, Task 11): what the iPhone app's view
 * controller will hand `ProvidePlatform` (phase 3). Until each capability is built there, it is a
 * stand-in that says it is not available, so the shared screens draw no affordance for it (design
 * item 8): dictation and the language picker. Images (`IosImages`) and payment codes (`IosQrCodes`)
 * are the iPhone's own since part C, and since its Task 11 the share sheet (`IosShareSheet`), the
 * pickers (`IosFilePicker`) and the camera (`IosCameraCapture`), since phase 4 part C the
 * notification permission (`IosPermissions`), and since part E1 links, files, the dialler and mail
 * (`IosOpenUrl`), the clipboard (`IosClipboard`) and the QR scanner (`IosQrScanner`).
 */

/**
 * The capabilities the iPhone has today, with [buildValues] from the iPhone app's own build (its
 * version, and the hosted addresses it ships with).
 */
fun iosPlatformServices(buildValues: AppBuildValues): PlatformServices = PlatformServices(
    shareSheet = IosShareSheet,
    filePicker = IosFilePicker,
    cameraCapture = IosCameraCapture,
    qrScanner = IosQrScanner,
    dictation = NoDictation,
    permissions = IosPermissions(UserNotificationsPermission),
    openUrl = IosOpenUrl,
    clipboard = IosClipboard,
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
 * is [googleCalendarSignIn] (`IosGoogleCalendarSignIn`, phase 4's Task 7), which the shell builds
 * because the answer goes to its `SyncViewModel`, and which is unavailable without the web client
 * id, so Settings then draws no Google Calendar row. The shell must also switch the family before it
 * arms any link (CLAUDE.md item 15): `AppLaunch` carries no family, so the order is the shell's.
 */
@Composable
fun ProvideIosShell(
    buildValues: AppBuildValues,
    credentialSignIn: CredentialSignIn,
    googleCalendarSignIn: GoogleCalendarSignIn,
    content: @Composable () -> Unit
) {
    val services = remember(buildValues) { iosPlatformServices(buildValues) }
    ProvidePlatform(services) {
        CompositionLocalProvider(
            LocalCredentialSignIn provides credentialSignIn,
            LocalGoogleCalendarSignIn provides googleCalendarSignIn,
            content = content
        )
    }
}

/** No dictation until phase 4, beside `NoSpeechDictation` in `platformUiModule`. */
private object NoDictation : Dictation {
    override val available: Boolean = false

    @Composable
    override fun rememberLanguageTag(): String = ""
}

/**
 * The runtime permissions on the iPhone (phase 4 part C, spec §7.6): notifications are asked of
 * `UNUserNotificationCenter` for alerts, sounds and badges ([NotificationPermission.request]), where
 * Android asks for `POST_NOTIFICATIONS`, and only from the screens that need them (UX overhaul item
 * 7). The answer is the one the push registration reads too ([UserNotificationsPermission]), and
 * `IosApp` reads it on start and on every return to the foreground, so a change made in the phone's
 * Settings shows.
 *
 * The microphone is part E's and the camera `IosCameraCapture`'s own, so neither is asked here: as
 * before, neither reads granted and a request for one takes its "denied" path at once.
 */
internal class IosPermissions(private val notifications: NotificationPermission) : PermissionRequester {
    override val available: Boolean = true

    override fun isGranted(permission: PlatformPermission): Boolean =
        permission == PlatformPermission.NOTIFICATIONS && notifications.granted

    @Composable
    override fun rememberRequest(permission: PlatformPermission, onDenied: () -> Unit): PermissionRequest {
        val scope = rememberCoroutineScope()
        val denied by rememberUpdatedState(onDenied)
        return remember(permission, scope) {
            PermissionRequest { action ->
                if (permission != PlatformPermission.NOTIFICATIONS) {
                    denied()
                } else {
                    scope.launch { if (notifications.request()) action() else denied() }
                }
            }
        }
    }
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
