package com.coparently.app.presentation.pairing

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.coparently.app.data.platform.logger
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.presentation.platform.QrCodes
import com.coparently.app.presentation.platform.QrMatrix
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * The iPhone's invitation QR (iPhone phase 4 part E1): what Android's `AndroidInviteQrCodes` and
 * `QRCodeService` are. It draws the link the share sheet carries, [pairingLinkFor] with the build's
 * choice of web or app-only form from [buildValues], so the QR and the share text never disagree
 * (CLAUDE.md item 37). The matrix comes from [qrCodes]; the picture is [SIDE] pixels square, black
 * on white with a quiet zone of one module, as Android draws it.
 */
internal class IosInviteQrCodes(
    private val buildValues: AppBuildValues,
    private val qrCodes: QrCodes,
) : InviteQrCodes {

    override val available: Boolean = true

    override fun forInvite(code: String): ImageBitmap? {
        val matrix = qrCodes.encode(pairingLinkFor(code, buildValues.pairingWebLink)) ?: return null
        return runCatching { draw(matrix) }
            .onFailure { logger.w(TAG, "The invitation's QR could not be drawn", it) }
            .getOrNull()
    }

    /** [matrix] scaled to fill [SIDE] pixels with one module of white around it. */
    private fun draw(matrix: QrMatrix): ImageBitmap {
        val surface = Surface.makeRasterN32Premul(SIDE, SIDE)
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
            canvas.drawRect(Rect.makeWH(SIDE.toFloat(), SIDE.toFloat()), white)
            drawModules(canvas, matrix, black)
            return surface.makeImageSnapshot().toComposeImageBitmap()
        } finally {
            white.close()
            black.close()
            surface.close()
        }
    }

    /** Each dark module of [matrix] as a box of whole pixels, so neighbours leave no seam. */
    private fun drawModules(canvas: Canvas, matrix: QrMatrix, paint: Paint) {
        val cell = SIDE.toFloat() / (matrix.width + 2 * QUIET_MODULES)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) canvas.drawRect(boxOf(x, y, cell), paint)
            }
        }
    }

    /** The pixel box of the module at column [x], row [y], [cell] pixels wide with edges rounded down. */
    private fun boxOf(x: Int, y: Int, cell: Float): Rect = Rect.makeLTRB(
        ((x + QUIET_MODULES) * cell).toInt().toFloat(),
        ((y + QUIET_MODULES) * cell).toInt().toFloat(),
        ((x + 1 + QUIET_MODULES) * cell).toInt().toFloat(),
        ((y + 1 + QUIET_MODULES) * cell).toInt().toFloat(),
    )

    internal companion object {
        /** The picture's width and height in pixels, as Android's `QRCodeService` draws it. */
        const val SIDE = 512
        private const val QUIET_MODULES = 1
        private const val WHITE = -0x1 // 0xFFFFFFFF
        private const val BLACK = -0x1000000 // 0xFF000000
        private const val TAG = "IosInviteQrCodes"
    }
}
