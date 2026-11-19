package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.coparently.app.data.platform.logger
import com.coparently.app.ios.controls.QrScannerController
import com.coparently.app.ios.controls.backCamera
import com.coparently.app.ios.controls.presentWhenSettled
import com.coparently.app.resources.Res
import com.coparently.app.resources.pairing_qr_hint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.UIKit.UIViewController

/**
 * The iPhone's QR scanner for a co-parent's invitation (MON-24 phase 4 part E1, Task 4): the
 * full-screen [QrScannerController], presented from the screen's own view controller once nothing
 * there is moving ([presentWhenSettled]), as Android's `QRScannerActivity` is started.
 *
 * [available] is whether the phone has a back camera (false on the simulator, where the pairing form
 * then draws no scan button). A scan hands [QrScanner.rememberScanner]'s `onScanned` a code
 * `PairingUri.extractCode` accepted, once, and the screen redeems it as it redeems a typed one
 * (CLAUDE.md item 37); Cancel or a refused camera calls nothing.
 */
internal object IosQrScanner : QrScanner {

    override val available: Boolean
        get() = backCamera() != null

    @Composable
    override fun rememberScanner(onScanned: (code: String) -> Unit): PlatformLauncher {
        val current by rememberUpdatedState(onScanned)
        val scope = rememberCoroutineScope()
        return rememberPresenterLauncher { onEnded ->
            ScannerPresentation(scope, onScanned = { current(it) }, onEnded = onEnded)
        }
    }
}

/**
 * One launch of the scanner: the hint read from the app's strings, then the scanner, and at most
 * one [onScanned]. A camera already refused (or restricted) presents nothing, so the scanner does
 * not flash up only to go; one not yet asked is asked by the scanner itself once it has appeared.
 * [close] (its screen left composition) takes the scanner away and answers nothing.
 */
private class ScannerPresentation(
    private val scope: CoroutineScope,
    private val onScanned: (String) -> Unit,
    private val onEnded: () -> Unit
) : Presentation {

    private var finished = false
    private var scanner: QrScannerController? = null

    override fun present(from: UIViewController?) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        if (status == AVAuthorizationStatusDenied || status == AVAuthorizationStatusRestricted) {
            logger.w(TAG, "The camera is not allowed; the scanner is not shown")
            return end()
        }
        scope.launch {
            val hint = getString(Res.string.pairing_qr_hint)
            if (finished) return@launch
            val controller = QrScannerController(
                hint = hint,
                onScanned = { code -> scanned(code) },
                onCancelled = ::end
            )
            scanner = controller
            presentWhenSettled(
                controller = controller.inNavigation(),
                from = from,
                isWanted = { !finished },
                onDropped = {
                    controller.dropped()
                    end()
                }
            )
        }
    }

    override fun close() {
        if (finished) return
        finished = true
        scanner?.close()
    }

    private fun scanned(code: String) {
        if (finished) return
        finished = true
        onScanned(code)
        onEnded()
    }

    private fun end() {
        if (finished) return
        finished = true
        onEnded()
    }

    private companion object {
        const val TAG = "IosQrScanner"
    }
}
