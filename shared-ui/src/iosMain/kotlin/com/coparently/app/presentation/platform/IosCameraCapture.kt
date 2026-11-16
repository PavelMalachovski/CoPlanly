@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.platform.logger
import com.coparently.app.ios.controls.presentWhenSettled
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The iPhone's camera, for one photo (MON-24 phase 3, part C, Task 11): `UIImagePickerController`
 * with the camera as its source, presented from the screen's own view controller once nothing there
 * is moving ([presentWhenSettled]).
 *
 * [available] is whether the phone has a camera the picker can use (`isSourceTypeAvailable`), false
 * on the simulator, where a screen offers the picker alone; a launch there answers
 * [CaptureResult.NoCameraApp]. The camera permission is asked first while it has not been answered,
 * and a refusal (now or before, or a restriction) answers [CaptureResult.PermissionDenied]. A photo
 * is saved as JPEG at quality 0.9 in `receipts/` ([CaptureTarget.RECEIPT]) or
 * `shared_files/capture/` ([CaptureTarget.SHARED_FILE]) under the app's cache directory, as on
 * Android (and wiped with the account's files at sign-out, `IosAccountTraces`), and answered as
 * [CaptureResult.Taken] with its absolute path. Every launch answers exactly once: Cancel, a photo
 * that could not be saved, or a presentation UIKit dropped answer [CaptureResult.Cancelled].
 */
internal object IosCameraCapture : CameraCapture {

    override val available: Boolean
        get() = UIImagePickerController.isSourceTypeAvailable(CAMERA)

    @Composable
    override fun rememberCapture(target: CaptureTarget, onResult: (CaptureResult) -> Unit): PlatformLauncher {
        val current by rememberUpdatedState(onResult)
        val place by rememberUpdatedState(target)
        return rememberPresenterLauncher { onEnded ->
            CameraPresentation(place, onResult = { current(it) }, onEnded = onEnded)
        }
    }

    /** The camera source type. */
    val CAMERA = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
}

/**
 * One launch of the camera: the permission, then the picker, and one [onResult] whatever happens.
 * [close] (its screen left composition) takes the picker away and answers nothing.
 */
private class CameraPresentation(
    private val target: CaptureTarget,
    private val onResult: (CaptureResult) -> Unit,
    private val onEnded: () -> Unit
) : Presentation {

    private var finished = false
    private var picker: UIImagePickerController? = null
    private val delegate = Delegate()

    override fun present(from: UIViewController?) {
        if (!UIImagePickerController.isSourceTypeAvailable(IosCameraCapture.CAMERA)) {
            return answer(CaptureResult.NoCameraApp)
        }
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> showPicker(from)
            AVAuthorizationStatusNotDetermined ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (finished) return@dispatch_async
                        if (granted) showPicker(from) else answer(CaptureResult.PermissionDenied)
                    }
                }
            // Denied, or restricted by the phone's own settings.
            else -> answer(CaptureResult.PermissionDenied)
        }
    }

    private fun showPicker(from: UIViewController?) {
        val camera = UIImagePickerController().apply {
            sourceType = IosCameraCapture.CAMERA
            allowsEditing = false
            delegate = this@CameraPresentation.delegate
        }
        picker = camera
        presentWhenSettled(camera, from, isWanted = { !finished }, onDropped = { answer(CaptureResult.Cancelled) })
    }

    override fun close() {
        if (finished) return
        finished = true
        picker?.let(::takeAway)
    }

    /** A photo was taken: saved, then answered. */
    fun taken(picker: UIImagePickerController, info: Map<Any?, *>) {
        if (finished) return
        takeAway(picker)
        val image = info[UIImagePickerControllerOriginalImage] as? UIImage
        val saved = image?.let(::save)
        answer(saved?.let { CaptureResult.Taken(it) } ?: CaptureResult.Cancelled)
    }

    /** Cancel. */
    fun cancelled(picker: UIImagePickerController) {
        if (finished) return
        takeAway(picker)
        answer(CaptureResult.Cancelled)
    }

    /** [result], once, then the launcher is free again. */
    private fun answer(result: CaptureResult) {
        if (finished) return
        finished = true
        onResult(result)
        onEnded()
    }

    /** [image] as a JPEG in [target]'s folder; its path, or null when it could not be written. */
    private fun save(image: UIImage): String? {
        val data = UIImageJPEGRepresentation(image, JPEG_QUALITY) ?: return null
        val caches = IosPickedFiles.appCache()
        val (folder, name) = when (target) {
            CaptureTarget.RECEIPT ->
                "$caches/receipts" to "receipt_${(NSDate().timeIntervalSince1970 * MILLIS).toLong()}.jpg"
            CaptureTarget.SHARED_FILE ->
                "$caches/shared_files/capture" to "photo-${NSUUID().UUIDString.lowercase()}.jpg"
        }
        val created = NSFileManager.defaultManager.createDirectoryAtPath(
            folder,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        val path = "$folder/$name"
        if (!created || !data.writeToFile(path, atomically = true)) {
            logger.w(CAMERA_TAG, "The photo could not be saved")
            return null
        }
        return path
    }

    /** The picker's delegate, which UIKit does not retain. */
    private inner class Delegate :
        NSObject(),
        UIImagePickerControllerDelegateProtocol,
        UINavigationControllerDelegateProtocol {

        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>
        ) {
            taken(picker, didFinishPickingMediaWithInfo)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            cancelled(picker)
        }
    }
}

private const val CAMERA_TAG = "IosCameraCapture"

/** The JPEG quality a captured photo is saved at, as Android's camera app saves one. */
private const val JPEG_QUALITY = 0.9

private const val MILLIS = 1000.0
