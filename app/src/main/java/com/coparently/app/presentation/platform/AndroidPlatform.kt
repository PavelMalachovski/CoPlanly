package com.coparently.app.presentation.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.coparently.app.BuildConfig
import com.coparently.app.data.dictation.OnDeviceSpeechDictation
import com.coparently.app.domain.chat.MessageReport
import com.coparently.app.presentation.chat.rememberDictationLanguageTag
import com.coparently.app.presentation.common.copySensitive
import com.coparently.app.presentation.common.hasMicrophonePermission
import com.coparently.app.presentation.common.hasNotificationPermission
import com.coparently.app.presentation.common.newSharedFileCaptureUri
import com.coparently.app.presentation.common.openSharedFile
import com.coparently.app.presentation.pairing.QRScannerActivity
import com.coparently.app.presentation.settings.AndroidAppLanguages
import java.io.File

/**
 * Provides every platform capability (`:shared-ui`'s `presentation/platform/`) to [content], from
 * the Android code the screens called before they moved (MON-24 phase 2, Task 11).
 *
 * `MainActivity` and `QRScannerActivity` wrap their content in it, and so does the screenshot
 * frame, so a screenshot renders with the values the app runs with. The services are built from
 * the composition's own `Context`, the Activity, which a share sheet, a picker or a dialler needs.
 */
@Composable
fun ProvideAndroidPlatform(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val services = remember(context) { androidPlatformServices(context) }
    ProvidePlatform(services, content)
}

/** The Android capabilities, each over [context] (an Activity in the app). */
fun androidPlatformServices(context: Context): PlatformServices = PlatformServices(
    shareSheet = AndroidShareSheet(context),
    filePicker = AndroidFilePicker,
    cameraCapture = AndroidCameraCapture(context),
    qrScanner = AndroidQrScanner(context),
    dictation = AndroidDictation(context),
    permissions = AndroidPermissions(context),
    openUrl = AndroidOpenUrl(context),
    clipboard = AndroidSensitiveClipboard(context),
    buildValues = androidBuildValues(),
    images = CoilImages,
    fileDetails = AndroidFileDetails(context),
    qrCodes = AndroidQrCodes(context),
    appLanguages = AndroidAppLanguages(context),
)

/** The build's values, from `BuildConfig`; a blank address is null, which hides its row. */
fun androidBuildValues(): AppBuildValues = AppBuildValues(
    versionName = BuildConfig.VERSION_NAME,
    privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL.takeIf { it.isNotBlank() },
    termsUrl = BuildConfig.TERMS_URL.takeIf { it.isNotBlank() },
    supportEmail = BuildConfig.SUPPORT_EMAIL.takeIf { it.isNotBlank() },
    pairingWebLink = BuildConfig.PAIRING_WEB_LINK,
)

/** `ACTION_SEND` in `Intent.createChooser`, as the invitation, feed and export screens built it. */
private class AndroidShareSheet(private val context: Context) : ShareSheet {

    override val available: Boolean = true

    override fun shareText(text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /**
     * The grant is on the intent *and* its `ClipData`, because some targets read the URI from the
     * clip and the chooser only forwards the grant it can see there (the export's `recordShareIntent`).
     */
    override fun shareFile(contentUri: String, mimeType: String, chooserTitle: String) {
        val uri = Uri.parse(contentUri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}

/** The photo picker (images only) and the system document picker. */
private object AndroidFilePicker : FilePicker {

    override val available: Boolean = true

    @Composable
    override fun rememberImagePicker(onPicked: (contentUri: String) -> Unit): PlatformLauncher {
        val currentOnPicked by rememberUpdatedState(onPicked)
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { currentOnPicked(it.toString()) }
        }
        return remember(picker) {
            PlatformLauncher {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    @Composable
    override fun rememberDocumentPicker(
        contentTypes: List<String>,
        onPicked: (contentUri: String) -> Unit
    ): PlatformLauncher {
        val currentOnPicked by rememberUpdatedState(onPicked)
        val currentTypes by rememberUpdatedState(contentTypes)
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { currentOnPicked(it.toString()) }
        }
        return remember(picker) { PlatformLauncher { picker.launch(currentTypes.toTypedArray()) } }
    }
}

/**
 * `TakePicture` into this app's `FileProvider`, behind the CAMERA permission the manifest declares,
 * as the expense and document screens did it. [available] is the phone having a camera at all.
 */
private class AndroidCameraCapture(private val context: Context) : CameraCapture {

    override val available: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    @Composable
    override fun rememberCapture(target: CaptureTarget, onResult: (CaptureResult) -> Unit): PlatformLauncher {
        val currentOnResult by rememberUpdatedState(onResult)
        // Saveable: the camera app often takes this activity down while it runs, and the photo's
        // address has to be here when it comes back.
        var pending by rememberSaveable { mutableStateOf<String?>(null) }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val uri = pending
            currentOnResult(if (taken && uri != null) CaptureResult.Taken(uri) else CaptureResult.Cancelled)
        }
        val takePhoto = {
            val uri = captureUri(context, target)
            pending = uri.toString()
            try {
                camera.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No camera app", e)
                currentOnResult(CaptureResult.NoCameraApp)
            }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) takePhoto() else currentOnResult(CaptureResult.PermissionDenied)
        }
        // Keyed on the target too, so a caller that switches it photographs into the new place.
        return remember(camera, permission, target) {
            PlatformLauncher {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) takePhoto() else permission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private companion object {
        const val TAG = "AndroidCameraCapture"
    }
}

/**
 * A fresh `content://` target for [target]: a receipt under `cache/receipts/` (as
 * `AddExpenseScreen` names it), a vault document under `cache/shared_files/capture/`.
 */
private fun captureUri(context: Context, target: CaptureTarget): Uri = when (target) {
    CaptureTarget.SHARED_FILE -> newSharedFileCaptureUri(context)
    CaptureTarget.RECEIPT -> {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(directory, "receipt_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

/** [QRScannerActivity], whose result carries a validated code in [QRScannerActivity.EXTRA_CODE]. */
private class AndroidQrScanner(private val context: Context) : QrScanner {

    /** The scanner screen explains a refused or missing camera itself, so it is always offered. */
    override val available: Boolean = true

    @Composable
    override fun rememberScanner(onScanned: (code: String) -> Unit): PlatformLauncher {
        val currentOnScanned by rememberUpdatedState(onScanned)
        val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra(QRScannerActivity.EXTRA_CODE)?.let { currentOnScanned(it) }
            }
        }
        return remember(scanner) {
            PlatformLauncher { scanner.launch(Intent(context, QRScannerActivity::class.java)) }
        }
    }
}

/** On-device recognition only: [OnDeviceSpeechDictation]'s own rule decides whether there is any. */
private class AndroidDictation(private val context: Context) : Dictation {

    override val available: Boolean
        get() = OnDeviceSpeechDictation(context).isAvailable()

    @Composable
    override fun rememberLanguageTag(): String = rememberDictationLanguageTag()
}

/** `RequestPermission`, as the notification and microphone requesters asked. */
private class AndroidPermissions(private val context: Context) : PermissionRequester {

    override val available: Boolean = true

    override fun isGranted(permission: PlatformPermission): Boolean = when (permission) {
        PlatformPermission.NOTIFICATIONS -> hasNotificationPermission(context)
        PlatformPermission.MICROPHONE -> hasMicrophonePermission(context)
        PlatformPermission.CAMERA ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    @Composable
    override fun rememberRequest(permission: PlatformPermission, onDenied: () -> Unit): PermissionRequest {
        val currentOnDenied by rememberUpdatedState(onDenied)
        val pending = remember { PendingAction() }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pending.action
            pending.action = null
            if (granted) action?.invoke() else currentOnDenied()
        }
        return remember(launcher, permission) {
            PermissionRequest { action ->
                if (isGranted(permission)) {
                    action()
                } else {
                    pending.action = action
                    launcher.launch(manifestName(permission))
                }
            }
        }
    }

    /** The action waiting on the system dialog's answer. */
    private class PendingAction {
        var action: (() -> Unit)? = null
    }

    private fun manifestName(permission: PlatformPermission): String = when (permission) {
        PlatformPermission.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
        PlatformPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        PlatformPermission.CAMERA -> Manifest.permission.CAMERA
    }
}

/** Other apps, each launch caught when no app takes it. */
private class AndroidOpenUrl(private val context: Context) : OpenUrl {

    override val available: Boolean = true

    override fun open(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "a link")

    override fun openFile(path: String, contentType: String): Boolean =
        openSharedFile(context, File(path), contentType)

    override fun dial(number: String): Boolean =
        start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)), "tel: intent")

    /**
     * The address, subject and body ride in the URI (RFC 6068) and again as extras, because mail
     * apps differ in which of the two they read (the chat's report).
     */
    override fun composeEmail(address: String, subject: String, body: String): Boolean =
        start(
            Intent(Intent.ACTION_SENDTO, Uri.parse(MessageReport.mailtoUri(address, subject, body)))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body),
            "a mail draft"
        )

    override fun openAppSettings(): Boolean =
        start(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ),
            "the app's settings"
        )

    private fun start(intent: Intent, what: String): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app for $what", e)
        false
    }

    private companion object {
        const val TAG = "AndroidOpenUrl"
    }
}

/** [copySensitive], the sensitive-flagged clip. */
private class AndroidSensitiveClipboard(private val context: Context) : SensitiveClipboard {

    override val available: Boolean = true

    override fun copySensitive(label: String, text: String) = copySensitive(context, label, text)
}

/**
 * Coil 2, as the screens drew with it: the app's `ImageLoaderFactory` (`CoPlanlyApplication`) and
 * its `RecordPhotoFetcher` answer every model, so a record photo is fetched as the signed-in parent.
 */
private object CoilImages : PlatformImages {

    override val available: Boolean = true

    @Composable
    override fun Image(
        model: Any?,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        fullResolution: Boolean,
        placeholder: (@Composable () -> Unit)?,
    ) {
        // The zoomable viewer's (`ZoomableImage`): the photo at its own size, so zooming in shows
        // detail rather than an upscaled thumbnail. Otherwise the model as it is, sized to its place.
        // A local file's kotlinx-io `Path` (a chat attachment's thumbnail) is the `File` the screen
        // handed Coil before it moved.
        val data: Any? = if (model is kotlinx.io.files.Path) File(model.toString()) else model
        val request: Any? = if (fullResolution) {
            ImageRequest.Builder(LocalContext.current)
                .data(data)
                .size(Size.ORIGINAL)
                .crossfade(true)
                .build()
        } else {
            data
        }
        if (placeholder != null) {
            // The account picture's shape (`AccountAvatar`): the initial while loading and on failure.
            SubcomposeAsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                loading = { placeholder() },
                error = { placeholder() },
                modifier = modifier
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        }
    }
}

/**
 * The `ContentResolver`'s display name and size, and `Formatter.formatShortFileSize` in the
 * Activity's language, as the chat's attachment confirmation and chip wrote them.
 */
private class AndroidFileDetails(private val context: Context) : FileDetails {

    override val available: Boolean = true

    override fun describe(contentUri: String): PickedFileDescription {
        val parsed = Uri.parse(contentUri)
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return runCatching {
            context.contentResolver.query(parsed, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val size = if (cursor.isNull(1)) "" else Formatter.formatShortFileSize(context, cursor.getLong(1))
                PickedFileDescription(cursor.getString(0).orEmpty(), size)
            }
        }.getOrNull() ?: PickedFileDescription(parsed.lastPathSegment.orEmpty(), "")
    }

    override fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)
}
