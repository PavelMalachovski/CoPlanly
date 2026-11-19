package com.coparently.app.presentation.pairing

import androidx.compose.ui.graphics.ImageBitmap
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.presentation.platform.IosQrCodes
import com.coparently.app.presentation.platform.IosShareSheet
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The invitation's QR on the iPhone (phase 4 part E1, Task 3): drawn 512 pixels square, and read
 * back by Core Image's own QR detector it says the link the share sheet carries, in the web form
 * and in the app-only one (CLAUDE.md item 37).
 */
class IosInviteQrCodesTest {

    @Test
    fun `the web form of the link reads back from a 512 pixel picture`() = readsBack(web = true)

    @Test
    fun `the app only form of the link reads back from a 512 pixel picture`() = readsBack(web = false)

    private fun readsBack(web: Boolean) {
        val qr = IosInviteQrCodes(values(web), IosQrCodes(IosShareSheet))
        val bitmap = assertNotNull(qr.forInvite(CODE))
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
        assertEquals(pairingLinkFor(CODE, web), decoded(bitmap))
    }

    private fun values(web: Boolean) = AppBuildValues(
        versionName = "",
        privacyPolicyUrl = null,
        termsUrl = null,
        supportEmail = null,
        pairingWebLink = web,
        edupageImport = false,
        appleSignIn = false,
    )

    /** The message a QR detector finds in [bitmap], read through its pixels as an RGB `CGImage`. */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun decoded(bitmap: ImageBitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.readPixels(argb)
        val rgba = ByteArray(width * height * BYTES_PER_PIXEL)
        for (i in argb.indices) {
            val p = argb[i]
            rgba[i * BYTES_PER_PIXEL] = (p shr RED_SHIFT).toByte()
            rgba[i * BYTES_PER_PIXEL + 1] = (p shr GREEN_SHIFT).toByte()
            rgba[i * BYTES_PER_PIXEL + 2] = p.toByte()
            rgba[i * BYTES_PER_PIXEL + 3] = -1
        }
        val cgImage = rgba.usePinned { pinned ->
            val space = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                BITS_PER_COMPONENT,
                (width * BYTES_PER_PIXEL).toULong(),
                space,
                CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value
            )
            CGColorSpaceRelease(space)
            val image = CGBitmapContextCreateImage(context)
            CGContextRelease(context)
            image
        }
        try {
            val detector = CIDetector.detectorOfType(
                CIDetectorTypeQRCode,
                context = null,
                options = mapOf<Any?, Any?>(CIDetectorAccuracy to CIDetectorAccuracyHigh)
            )
            val features = detector?.featuresInImage(CIImage(cGImage = cgImage)) ?: return null
            return features.filterIsInstance<CIQRCodeFeature>().firstOrNull()?.messageString
        } finally {
            CGImageRelease(cgImage)
        }
    }

    private companion object {
        const val CODE = "ABC234"
        const val BYTES_PER_PIXEL = 4
        const val BITS_PER_COMPONENT = 8uL
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
    }
}
