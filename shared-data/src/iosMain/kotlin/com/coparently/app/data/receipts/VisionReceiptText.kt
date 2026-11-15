@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coparently.app.data.receipts

import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.platform.logger
import com.coparently.app.domain.receipts.ReceiptTextRecognizer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.ImageIO.kCGImagePropertyOrientationDown
import platform.ImageIO.kCGImagePropertyOrientationDownMirrored
import platform.ImageIO.kCGImagePropertyOrientationLeft
import platform.ImageIO.kCGImagePropertyOrientationLeftMirrored
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.ImageIO.kCGImagePropertyOrientationRightMirrored
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.ImageIO.kCGImagePropertyOrientationUpMirrored
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [ReceiptTextRecognizer] on the iPhone (MON-24 phase 3, part C, Task 12): Apple's Vision reads the
 * receipt's text on the phone, as ML Kit's bundled recognizer does on Android. **Nothing leaves the
 * phone** (CLAUDE.md, item 10): no request goes anywhere, the photo is read from the app's own cache
 * and nothing is kept.
 *
 * A `VNRecognizeTextRequest` at the accurate level, in the five languages the app speaks where this
 * phone's Vision knows them ([LANGUAGES]); language correction is off, because a receipt is digits
 * and abbreviations, not prose. The lines come back top to bottom ([readingOrder]), the order
 * `ReceiptParser` takes from ML Kit; words Vision split on one printed row are joined left to right,
 * so a total and its amount stay on one line.
 *
 * The image is the absolute path of a picked or captured photo, or its `file://` address
 * (`IosPickedFiles.pathOf`). The work runs on a background queue; a failure is an [IOException]
 * (the expense form shows its failed-scan message and keeps the photo), and cancelling the caller
 * cancels Vision's request.
 */
class VisionReceiptText : ReceiptTextRecognizer {

    /** Vision is on every iPhone this app runs on (iOS 17). */
    override val available: Boolean = true

    override suspend fun recognize(imageUri: String): List<String> {
        val path = IosPickedFiles.pathOf(imageUri)
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) throw IOException("No image to read")
        return suspendCancellableCoroutine { continuation ->
            val run = Recognition(path)
            continuation.invokeOnCancellation { run.cancel() }
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                try {
                    continuation.resume(run.lines())
                } catch (failure: IOException) {
                    continuation.resumeWithException(failure)
                } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                    // An exception out of this block would end the process, not the scan.
                    continuation.resumeWithException(IOException("The receipt could not be read", failure))
                }
            }
        }
    }

    /** One image's recognition, which [cancel] stops from another thread. */
    private class Recognition(private val path: String) {

        @Volatile
        private var current: VNRecognizeTextRequest? = null

        @Volatile
        private var cancelled = false

        fun cancel() {
            cancelled = true
            current?.cancel()
        }

        /** The lines in reading order; throws [IOException] when Vision could not read the image. */
        fun lines(): List<String> {
            val image = UIImage.imageWithContentsOfFile(path)?.takeIf { it.CGImage != null }
                ?: throw IOException("The image could not be opened")
            // A language list this phone's Vision refuses must not cost the scan: once more with
            // Vision's own languages.
            val observations = perform(image, withLanguages = true) ?: perform(image, withLanguages = false)
                ?: throw IOException("Vision could not read the image")
            return readingOrder(observations.mapNotNull { boxOf(it) })
        }

        private fun perform(image: UIImage, withLanguages: Boolean): List<VNRecognizedTextObservation>? {
            if (cancelled) throw IOException("Cancelled")
            val request = VNRecognizeTextRequest()
            request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
            request.usesLanguageCorrection = false
            if (withLanguages) {
                val supported = request.supportedRecognitionLanguagesAndReturnError(null)
                    ?.filterIsInstance<String>().orEmpty()
                val languages = LANGUAGES.filter { it in supported }
                if (languages.isNotEmpty()) request.recognitionLanguages = languages
            }
            // The simulator's Vision has no Neural Engine to run on (CI's tests).
            if (onSimulator) request.usesCPUOnly = true
            current = request
            // The handler retains the image's `CGImage` for as long as it reads it.
            val handler = VNImageRequestHandler(
                cGImage = image.CGImage,
                orientation = orientationOf(image.imageOrientation),
                options = emptyMap<Any?, Any>()
            )
            val refusal = memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                if (handler.performRequests(listOf(request), error = error.ptr)) null else error.value
            }
            current = null
            if (cancelled) throw IOException("Cancelled")
            if (refusal != null) {
                logger.w(TAG, "Vision refused the request (languages: $withLanguages): ${refusal.localizedDescription}")
                return null
            }
            return request.results?.filterIsInstance<VNRecognizedTextObservation>().orEmpty()
        }
    }

    /** Public for the tests. */
    companion object {
        /** The app's five languages, in Vision's names; those this phone's Vision lacks are left out. */
        val LANGUAGES = listOf("cs-CZ", "de-DE", "en-US", "ru-RU", "uk-UA")

        private const val TAG = "VisionReceiptText"

        /** Words whose heights overlap by this share of the shorter one sit on one printed row. */
        private const val SAME_ROW_OVERLAP = 0.5

        private val onSimulator: Boolean
            get() = NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null

        /**
         * The recognised words as lines, top to bottom; words on one printed row are joined left to
         * right with a space. Vision's boxes are normalised with the origin at the bottom left, so the
         * top of the page is the largest [TextBox.top].
         */
        fun readingOrder(boxes: List<TextBox>): List<String> {
            val rows = mutableListOf<MutableList<TextBox>>()
            for (box in boxes.filter { it.text.isNotBlank() }.sortedByDescending { it.top }) {
                val row = rows.lastOrNull()
                if (row != null && sameRow(row.first(), box)) row += box else rows += mutableListOf(box)
            }
            return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
        }

        private fun sameRow(a: TextBox, b: TextBox): Boolean {
            val overlap = minOf(a.top, b.top) - maxOf(a.bottom, b.bottom)
            val shorter = minOf(a.top - a.bottom, b.top - b.bottom)
            return shorter > 0 && overlap >= shorter * SAME_ROW_OVERLAP
        }

        private fun boxOf(observation: VNRecognizedTextObservation): TextBox? {
            val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string ?: return null
            return observation.boundingBox.useContents {
                TextBox(text, left = origin.x, bottom = origin.y, top = origin.y + size.height)
            }
        }

        /** The EXIF orientation Vision takes for a `UIImage`'s, so a photo held sideways reads upright. */
        private fun orientationOf(orientation: UIImageOrientation): UInt = when (orientation) {
            UIImageOrientation.UIImageOrientationUp -> kCGImagePropertyOrientationUp
            UIImageOrientation.UIImageOrientationDown -> kCGImagePropertyOrientationDown
            UIImageOrientation.UIImageOrientationLeft -> kCGImagePropertyOrientationLeft
            UIImageOrientation.UIImageOrientationRight -> kCGImagePropertyOrientationRight
            UIImageOrientation.UIImageOrientationUpMirrored -> kCGImagePropertyOrientationUpMirrored
            UIImageOrientation.UIImageOrientationDownMirrored -> kCGImagePropertyOrientationDownMirrored
            UIImageOrientation.UIImageOrientationLeftMirrored -> kCGImagePropertyOrientationLeftMirrored
            UIImageOrientation.UIImageOrientationRightMirrored -> kCGImagePropertyOrientationRightMirrored
        }
    }

    /**
     * One recognised run of text and its place, in Vision's normalised coordinates (0…1, origin at
     * the bottom left).
     */
    data class TextBox(val text: String, val left: Double, val bottom: Double, val top: Double)
}
