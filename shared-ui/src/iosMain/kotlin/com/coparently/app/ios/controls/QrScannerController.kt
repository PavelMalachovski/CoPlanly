@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.ios.controls

import com.coparently.app.data.platform.logger
import com.coparently.app.domain.pairing.ScannedCodeFilter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CALayer
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontTextStyleBody
import platform.UIKit.UILabel
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationController
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleDark
import platform.UIKit.UIViewController
import platform.UIKit.navigationController
import platform.UIKit.navigationItem
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.native.ref.WeakReference

/**
 * The full-screen QR scanner (MON-24 phase 4 part E1, Task 4): the back camera's picture over the
 * whole screen, a square guide in its middle, [hint] (`pairing_qr_hint`) under it, and a system
 * Cancel in a navigation bar. Present [inNavigation] through [presentWhenSettled].
 *
 * Every QR in view is read; each payload goes through a [ScannedCodeFilter], so a menu's or a
 * sign's code is ignored and a pairing code is handed over once. On a code the session stops, the
 * scanner is dismissed, and then [onScanned] is called with it. Cancel, a refused camera and a
 * camera that cannot be opened dismiss it and call [onCancelled]. It ends once, whichever way;
 * [close] takes it away without calling either (its screen left composition).
 *
 * The camera permission is read when the scanner has appeared: granted starts the session, not yet
 * answered asks first, and refused or restricted dismisses it at once. The session starts and stops
 * on a serial queue of its own, never on the main thread, where `startRunning` would block.
 */
internal class QrScannerController(
    private val hint: String,
    private val onScanned: (code: String) -> Unit,
    private val onCancelled: () -> Unit
) : UIViewController(nibName = null, bundle = null) {

    /** Whether the scanner has ended, by any of its ways. */
    private var finished = false

    private var began = false
    private val filter = ScannedCodeFilter()
    private val session = AVCaptureSession()
    private val sessionQueue = dispatch_queue_create(SESSION_QUEUE, null)
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }
    private val guideLayer = CALayer().apply {
        borderColor = UIColor.whiteColor.CGColor
        borderWidth = GUIDE_BORDER_PT
        cornerRadius = GUIDE_CORNER_PT
    }

    /** Cancel's target and the metadata output's delegate, which UIKit and AVFoundation do not retain. */
    private val actions = Actions(WeakReference(this))

    /** The metadata output while the session reads; dropped with its delegate when the scanner ends. */
    private var output: AVCaptureMetadataOutput? = null

    /**
     * A navigation controller holding the scanner, full screen, with Cancel on the left of its bar:
     * what is presented. Not kept here, since the navigation controller keeps this one, and a cycle
     * through Objective-C objects is never collected; the scanner reaches it as `navigationController`.
     */
    fun inNavigation(): UINavigationController {
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel,
            target = actions,
            action = NSSelectorFromString("cancel")
        )
        return UINavigationController(rootViewController = this).also { navigation ->
            navigation.modalPresentationStyle = UIModalPresentationFullScreen
            // The bar sits over the camera's picture, so it is drawn in the dark style whatever the theme.
            navigation.overrideUserInterfaceStyle = UIUserInterfaceStyleDark
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        view.layer.addSublayer(previewLayer)
        view.layer.addSublayer(guideLayer)
        val label = UILabel().apply {
            text = hint
            textColor = UIColor.whiteColor
            textAlignment = NSTextAlignmentCenter
            numberOfLines = 0
            font = UIFont.preferredFontForTextStyle(UIFontTextStyleBody)
            adjustsFontForContentSizeCategory = true
            layer.shadowColor = UIColor.blackColor.CGColor
            layer.shadowOpacity = HINT_SHADOW_OPACITY
            layer.shadowRadius = HINT_SHADOW_RADIUS_PT
            layer.shadowOffset = CGSizeMake(0.0, 0.0)
            translatesAutoresizingMaskIntoConstraints = false
        }
        view.addSubview(label)
        val guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activateConstraints(
            listOf(
                label.leadingAnchor.constraintEqualToAnchor(guide.leadingAnchor, constant = HINT_MARGIN_PT),
                label.trailingAnchor.constraintEqualToAnchor(guide.trailingAnchor, constant = -HINT_MARGIN_PT),
                label.bottomAnchor.constraintEqualToAnchor(guide.bottomAnchor, constant = -HINT_BOTTOM_PT)
            )
        )
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val bounds = view.bounds
        previewLayer.setFrame(bounds)
        guideLayer.setFrame(
            bounds.useContents {
                val side = minOf(size.width, size.height) * GUIDE_SHARE
                CGRectMake((size.width - side) / 2, (size.height - side) / 2, side, side)
            }
        )
    }

    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        if (began || finished) return
        began = true
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> startSession()
            AVAuthorizationStatusNotDetermined ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) {
                            startSession()
                        } else {
                            logger.w(SCANNER_TAG, "The camera was refused; the scanner closes")
                            cancel()
                        }
                    }
                }
            // Denied, or restricted by the phone's own settings.
            else -> {
                logger.w(SCANNER_TAG, "The camera is not allowed; the scanner closes")
                cancel()
            }
        }
    }

    /** Opens the back camera, reads QR codes from it on the main queue, and starts the session. */
    private fun startSession() {
        if (finished) return
        val device = backCamera()
        val input = device?.let {
            AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) as? AVCaptureDeviceInput
        }
        val metadata = AVCaptureMetadataOutput()
        if (input == null || !session.canAddInput(input) || !session.canAddOutput(metadata)) {
            logger.w(SCANNER_TAG, "The camera could not be opened; the scanner closes")
            return cancel()
        }
        session.addInput(input)
        session.addOutput(metadata)
        metadata.setMetadataObjectsDelegate(actions, queue = dispatch_get_main_queue())
        // The types can be set only once the output is on the session.
        metadata.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        output = metadata
        val running = session
        dispatch_async(sessionQueue) { running.startRunning() }
    }

    /** A payload the camera read, on the main queue. */
    internal fun read(payload: String) {
        if (finished) return
        val code = filter.accept(payload) ?: return
        end { onScanned(code) }
    }

    /** Cancel, a refused camera or one that could not be opened. */
    internal fun cancel() {
        if (finished) return
        end(onCancelled)
    }

    /** Takes the scanner away without calling back. */
    fun close() {
        if (finished) return
        finished = true
        stopSession()
        takeAway(then = null)
    }

    /** The scanner was never shown (UIKit dropped it): stops what may run and calls nothing. */
    fun dropped() {
        finished = true
        stopSession()
    }

    private fun end(then: () -> Unit) {
        finished = true
        stopSession()
        takeAway(then)
    }

    private fun stopSession() {
        output?.setMetadataObjectsDelegate(null, queue = null)
        output = null
        val running = session
        dispatch_async(sessionQueue) { running.stopRunning() }
    }

    /** Dismisses the navigation controller, then runs [then]; at once when it is not presented. */
    private fun takeAway(then: (() -> Unit)?) {
        val navigation = navigationController
        if (navigation?.presentingViewController != null && !navigation.beingDismissed) {
            navigation.dismissViewControllerAnimated(true) { then?.invoke() }
        } else {
            then?.invoke()
        }
    }

    /** Cancel's target and the metadata output's delegate. */
    private class Actions(
        private val owner: WeakReference<QrScannerController>
    ) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

        /** Cancel's target-action. */
        @ObjCAction
        fun cancel() {
            owner.get()?.cancel()
        }

        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: AVCaptureConnection
        ) {
            val scanner = owner.get() ?: return
            didOutputMetadataObjects
                .mapNotNull { (it as? AVMetadataMachineReadableCodeObject)?.stringValue }
                .forEach { payload -> scanner.read(payload) }
        }
    }
}

/**
 * The phone's back wide-angle camera, the one a QR is held up to; null on a phone without one (and on
 * the simulator).
 */
internal fun backCamera(): AVCaptureDevice? = AVCaptureDevice.defaultDeviceWithDeviceType(
    AVCaptureDeviceTypeBuiltInWideAngleCamera,
    mediaType = AVMediaTypeVideo,
    position = AVCaptureDevicePositionBack
)

/*
 * The scanner's constants are top-level: Kotlin/Native allows no fields in the companion of a
 * subclass of an Objective-C class.
 */

private const val SCANNER_TAG = "QrScannerController"

/** The serial queue the session starts and stops on. */
private const val SESSION_QUEUE = "app.coplanly.qrscanner.session"

/** The guide's side as a share of the screen's shorter edge. */
private const val GUIDE_SHARE = 0.65

private const val GUIDE_BORDER_PT = 3.0
private const val GUIDE_CORNER_PT = 16.0
private const val HINT_MARGIN_PT = 24.0
private const val HINT_BOTTOM_PT = 32.0
private const val HINT_SHADOW_OPACITY = 0.8f
private const val HINT_SHADOW_RADIUS_PT = 4.0
