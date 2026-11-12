package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.coparently.app.presentation.settings.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `PlatformServices` provides exactly the locals `PlatformLocals.all` lists, once each, so the
 * guard over the shell's provider (the app's `AndroidPlatformTest`) reads every one.
 */
class PlatformServicesTest {

    @Test
    fun `every local is provided once - and only those`() {
        val provided = services().providedValues().map { it.compositionLocal }

        assertEquals(PlatformLocals.all, provided)
        assertEquals(provided.size, provided.toSet().size)
    }

    private fun services() = PlatformServices(
        shareSheet = object : ShareSheet {
            override val available = false
            override fun shareText(text: String, chooserTitle: String) = Unit
            override fun shareFile(contentUri: String, mimeType: String, chooserTitle: String) = Unit
        },
        filePicker = object : FilePicker {
            override val available = false

            @Composable
            override fun rememberImagePicker(onPicked: (contentUri: String) -> Unit) = PlatformLauncher { }

            @Composable
            override fun rememberDocumentPicker(contentTypes: List<String>, onPicked: (contentUri: String) -> Unit) =
                PlatformLauncher { }
        },
        cameraCapture = object : CameraCapture {
            override val available = false

            @Composable
            override fun rememberCapture(target: CaptureTarget, onResult: (CaptureResult) -> Unit) =
                PlatformLauncher { }
        },
        qrScanner = object : QrScanner {
            override val available = false

            @Composable
            override fun rememberScanner(onScanned: (code: String) -> Unit) = PlatformLauncher { }
        },
        dictation = object : Dictation {
            override val available = false

            @Composable
            override fun rememberLanguageTag() = ""
        },
        permissions = object : PermissionRequester {
            override val available = false
            override fun isGranted(permission: PlatformPermission) = false

            @Composable
            override fun rememberRequest(permission: PlatformPermission, onDenied: () -> Unit) =
                PermissionRequest { }
        },
        openUrl = object : OpenUrl {
            override val available = false
            override fun open(url: String) = false
            override fun openFile(path: String, contentType: String) = false
            override fun dial(number: String) = false
            override fun composeEmail(address: String, subject: String, body: String) = false
            override fun openAppSettings() = false
        },
        clipboard = object : SensitiveClipboard {
            override val available = false
            override fun copySensitive(label: String, text: String) = Unit
        },
        buildValues = AppBuildValues(
            versionName = "1",
            privacyPolicyUrl = null,
            termsUrl = null,
            supportEmail = null,
            pairingWebLink = false,
            edupageImport = false
        ),
        images = object : PlatformImages {
            override val available = false

            @Composable
            override fun Image(
                model: Any?,
                contentDescription: String?,
                modifier: Modifier,
                contentScale: ContentScale,
                fullResolution: Boolean,
                placeholder: (@Composable () -> Unit)?,
            ) = Unit
        },
        fileDetails = object : FileDetails {
            override val available = false
            override fun describe(contentUri: String) = PickedFileDescription("", "")
            override fun formatSize(bytes: Long) = ""
        },
        qrCodes = object : QrCodes {
            override val available = false
            override fun encode(content: String): QrMatrix? = null
            override fun shareImage(matrix: QrMatrix, chooserTitle: String) = Unit
        },
        appLanguages = object : AppLanguages {
            override val available = false
            override fun current() = AppLanguage.SYSTEM
            override fun apply(language: AppLanguage) = Unit
        },
    )
}
