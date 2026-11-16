package com.coparently.app.presentation.platform

import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.platform.logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGInterpolationNone
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue

/**
 * The iPhone's QR codes (MON-24 phase 3, part C): what ZXing and the file provider are on Android
 * (`AndroidQrCodes`), for the settle-up card's payment code (CLAUDE.md item 45).
 *
 * The matrix is Core Image's `CIQRCodeGenerator` at error correction M, as all three payment
 * standards ask, read back one cell per module with the generator's quiet zone cut off, so it is
 * the shape Android's (no margin) is. The text goes in as UTF-8. The shared picture is a PNG of the
 * matrix with a four-module quiet zone, about 800 pixels square as on Android, written to
 * `payment_codes/` under the app's cache directory (as Android's, so sign-out wipes it) and handed
 * to [shareSheet] by its absolute path (the form the iPhone's picked files take). Nothing is sent
 * anywhere: a bank's app reads the picture the parent shares.
 */
internal class IosQrCodes(private val shareSheet: ShareSheet) : QrCodes {

    override val available: Boolean = true

    override fun encode(content: String): QrMatrix? =
        runCatching { CoreImageQr.matrixOf(content) }
            .onFailure { logger.w(TAG, "The payment code could not be drawn", it) }
            .getOrNull()

    override fun shareImage(matrix: QrMatrix, chooserTitle: String) {
        if (!shareSheet.available) return
        runCatching {
            val png = QrPicture.png(matrix) ?: return
            val directory = Path(IosPickedFiles.appCache(), SHARE_DIRECTORY)
            SystemFileSystem.createDirectories(directory)
            val file = Path(directory, SHARE_FILE)
            SystemFileSystem.sink(file).buffered().use { it.write(png) }
            shareSheet.shareFile(file.toString(), "image/png", chooserTitle)
        }.onFailure { logger.w(TAG, "The payment code could not be shared", it) }
    }

    private companion object {
        const val TAG = "SettleUpPayment"
        const val SHARE_DIRECTORY = "payment_codes"
        const val SHARE_FILE = "settle-up.png"
    }
}

/** Core Image's QR generator, read back into a [QrMatrix]. */
internal object CoreImageQr {

    /** [content] as a QR matrix at correction level M, one cell per module, no quiet zone; null if none. */
    fun matrixOf(content: String): QrMatrix? = generated(content)?.let(::modulesOf)

    /** The generator's image of [content]: one pixel per module, with its own quiet zone. */
    @OptIn(BetaInteropApi::class)
    private fun generated(content: String): CIImage? {
        val data = NSString.create(string = content).dataUsingEncoding(NSUTF8StringEncoding)
        val filter = CIFilter.filterWithName("CIQRCodeGenerator")
        if (data == null || filter == null) return null
        filter.setValue(data, forKey = "inputMessage")
        filter.setValue("M", forKey = "inputCorrectionLevel")
        return filter.outputImage
    }

    /** [image]'s pixels as modules, read through an 8-bit grey bitmap, cut to the symbol. */
    @Suppress("ReturnCount") // one guard per thing Core Graphics may not give back
    @OptIn(ExperimentalForeignApi::class)
    private fun modulesOf(image: CIImage): QrMatrix? {
        val extent = image.extent
        val width = extent.useContents { size.width.toInt() }
        val height = extent.useContents { size.height.toInt() }
        if (width <= 0 || height <= 0) return null
        val cgImage = CIContext.contextWithOptions(null).createCGImage(image, fromRect = extent) ?: return null
        val gray = ByteArray(width * height)
        try {
            gray.usePinned { pinned ->
                val space = CGColorSpaceCreateDeviceGray()
                val context = CGBitmapContextCreate(
                    pinned.addressOf(0),
                    width.toULong(),
                    height.toULong(),
                    BITS_PER_COMPONENT,
                    width.toULong(),
                    space,
                    CGImageAlphaInfo.kCGImageAlphaNone.value
                )
                CGColorSpaceRelease(space)
                context ?: return null
                CGContextSetInterpolationQuality(context, kCGInterpolationNone)
                CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
                CGContextRelease(context)
            }
        } finally {
            CGImageRelease(cgImage)
        }
        // Row 0 of a bitmap context's memory is the image's top row.
        return QrModules.cropped(width, height) { x, y -> (gray[y * width + x].toInt() and BYTE_MASK) < DARK_BELOW }
    }

    private const val BITS_PER_COMPONENT = 8uL
    private const val BYTE_MASK = 0xFF
    private const val DARK_BELOW = 128
}

/** The modules of a rendered code, cut to the symbol itself. */
internal object QrModules {

    /**
     * The [width] by [height] pixels [isDark] describes, cut to the box around their dark modules —
     * the symbol without whatever quiet zone the generator drew. A symbol's finder patterns reach
     * its top-left, top-right and bottom-left corners, so that box is exactly the symbol. Null when
     * nothing is dark.
     */
    fun cropped(width: Int, height: Int, isDark: (x: Int, y: Int) -> Boolean): QrMatrix? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!isDark(x, y)) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null
        val side = right - left + 1
        val tall = bottom - top + 1
        val cells = BooleanArray(side * tall) { index -> isDark(left + index % side, top + index / side) }
        return QrMatrix(side, tall, cells)
    }
}

/** A [QrMatrix] as the shared picture: black on white with a quiet zone, as Android draws it. */
internal object QrPicture {

    /** The PNG bytes of [matrix], or null when Skia could not encode it. */
    fun png(matrix: QrMatrix): ByteArray? {
        val modules = matrix.width + 2 * QUIET_MODULES
        val scale = maxOf(1, SHARE_PIXELS / modules)
        val side = modules * scale
        val surface = Surface.makeRasterN32Premul(side, side)
        val white = Paint().apply {
            color = WHITE
            isAntiAlias = false
        }
        val black = Paint().apply {
            color = BLACK
            isAntiAlias = false
        }
        try {
            val canvas = surface.canvas
            canvas.drawRect(Rect.makeWH(side.toFloat(), side.toFloat()), white)
            drawModules(canvas, matrix, scale, black)
            return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
        } finally {
            white.close()
            black.close()
            surface.close()
        }
    }

    /** Each dark module of [matrix] as a [scale]-pixel square inside the quiet zone. */
    private fun drawModules(canvas: Canvas, matrix: QrMatrix, scale: Int, paint: Paint) {
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (!matrix[x, y]) continue
                val left = ((x + QUIET_MODULES) * scale).toFloat()
                val top = ((y + QUIET_MODULES) * scale).toFloat()
                canvas.drawRect(Rect.makeXYWH(left, top, scale.toFloat(), scale.toFloat()), paint)
            }
        }
    }

    private const val SHARE_PIXELS = 800
    private const val QUIET_MODULES = 4
    private const val WHITE = -0x1 // 0xFFFFFFFF
    private const val BLACK = -0x1000000 // 0xFF000000
}
