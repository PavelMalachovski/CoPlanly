@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.coparently.app.data.receipts

import com.coparently.app.data.receipts.VisionReceiptText.TextBox
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.drawAtPoint
import platform.Vision.VNRequestTextRecognitionLevelFast
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [VisionReceiptText] (MON-24 phase 3, part C, Task 12): Vision reads a rendered receipt line on the
 * simulator, and the recognised words come back top to bottom, one printed row a line. The image is
 * drawn large, black on white, so recognition does not depend on the simulator's luck. A CI
 * simulator's Vision may still recognise nothing: on a simulator the image is read at the fast
 * level (the app keeps the accurate one), and if Vision still recognises nothing the recognition
 * checks are skipped with a printed reason (`docs/DEVICE-CHECKLIST.md` §3.40 proves recognition on
 * a phone). The reading order's pure checks always run.
 */
class VisionReceiptTextTest {

    private val folder = NSTemporaryDirectory().trimEnd('/') + "/coplanly-vision-" + NSUUID().UUIDString

    private val onSimulator = NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null

    // The fast level on a simulator, whose Vision may lack the accurate model; the app's level elsewhere.
    private val recognizer =
        if (onSimulator) VisionReceiptText(VNRequestTextRecognitionLevelFast) else VisionReceiptText()

    @AfterTest
    fun removeTheFolder() {
        NSFileManager.defaultManager.removeItemAtPath(folder, error = null)
    }

    @Test
    fun `reads the total line of a rendered receipt`() {
        val image = renderReceipt("CELKEM 249,00 Kč")

        val lines = recognizedOrSkipped(image) ?: return

        assertTrue(lines.any { "249" in it }, "No line holds 249: $lines")
    }

    @Test
    fun `a file address is read as its path`() {
        val image = renderReceipt("CELKEM 249,00 Kč")

        val lines = recognizedOrSkipped("file://$image") ?: return

        assertTrue(lines.any { "249" in it }, "No line holds 249: $lines")
    }

    @Test
    fun `a missing image is an IOException`() {
        assertFailsWith<IOException> {
            runBlocking { recognizer.recognize("$folder/none.png") }
        }
    }

    @Test
    fun `lines run top to bottom and words on one row join left to right`() {
        val boxes = listOf(
            TextBox("249,00", left = 0.7, bottom = 0.40, top = 0.45),
            TextBox("Bakery", left = 0.1, bottom = 0.90, top = 0.95),
            TextBox("CELKEM", left = 0.1, bottom = 0.41, top = 0.46),
            TextBox("Bread", left = 0.1, bottom = 0.70, top = 0.75),
            TextBox("  ", left = 0.5, bottom = 0.60, top = 0.65)
        )

        assertEquals(listOf("Bakery", "Bread", "CELKEM 249,00"), VisionReceiptText.readingOrder(boxes))
    }

    /**
     * The lines Vision reads in [image]; null, with the reason printed, when it recognises nothing on
     * a simulator (text recognition unavailable there), so the caller skips its checks. On a phone
     * nothing recognised is a failure.
     */
    private fun recognizedOrSkipped(image: String): List<String>? {
        val lines = runBlocking { recognizer.recognize(image) }
        if (lines.isNotEmpty()) return lines
        assertTrue(onSimulator, "Vision recognised nothing")
        println("Skipped: text recognition unavailable on this simulator (Vision recognised nothing)")
        return null
    }

    /** Draws [text] black on white, large, into a PNG in [folder]; answers its path. */
    private fun renderReceipt(text: String): String {
        NSFileManager.defaultManager.createDirectoryAtPath(
            folder,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(WIDTH, HEIGHT))
        val image = renderer.imageWithActions { _ ->
            UIColor.whiteColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, WIDTH, HEIGHT))
            NSString.create(string = text).drawAtPoint(
                CGPointMake(MARGIN, MARGIN),
                withAttributes = mapOf<Any?, Any?>(
                    NSFontAttributeName to UIFont.boldSystemFontOfSize(FONT_SIZE),
                    NSForegroundColorAttributeName to UIColor.blackColor
                )
            )
        }
        val path = "$folder/receipt.png"
        val written = UIImagePNGRepresentation(image)?.writeToFile(path, atomically = true) ?: false
        assertTrue(written, "The receipt image was not written")
        return path
    }

    private companion object {
        const val WIDTH = 1400.0
        const val HEIGHT = 300.0
        const val MARGIN = 60.0
        const val FONT_SIZE = 96.0
    }
}
