package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
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
            pairingWebLink = false
        ),
    )
}
