package com.coparently.app.presentation.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/**
 * ZXing's QR matrix, and the code shared as a PNG from the app's cache through its file provider —
 * the settle-up card's payment code as `SettleUpPayment` made and shared it before it moved to
 * `:shared-ui` (MON-24 phase 2, Task 11), unchanged.
 */
internal class AndroidQrCodes(private val context: Context) : QrCodes {

    override val available: Boolean = true

    /** One cell per module, error correction M as all three payment standards ask, no margin. */
    override fun encode(content: String): QrMatrix? = runCatching {
        val bits = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        )
        val cells = BooleanArray(bits.width * bits.height) { index -> bits[index % bits.width, index / bits.width] }
        QrMatrix(bits.width, bits.height, cells)
    }.onFailure { Log.w(TAG, "The payment code could not be drawn", it) }.getOrNull()

    /** Writes the code to the cache as a PNG and opens the share sheet on it. */
    override fun shareImage(matrix: QrMatrix, chooserTitle: String) {
        runCatching {
            val scale = SHARE_PIXELS / (matrix.width + 2 * SHARE_QUIET_MODULES)
            val side = (matrix.width + 2 * SHARE_QUIET_MODULES) * scale
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.RGB_565)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (!matrix[x, y]) continue
                    val left = (x + SHARE_QUIET_MODULES) * scale
                    val top = (y + SHARE_QUIET_MODULES) * scale
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) bitmap.setPixel(left + dx, top + dy, android.graphics.Color.BLACK)
                    }
                }
            }
            val dir = File(context.cacheDir, "payment_codes").apply { mkdirs() }
            val file = File(dir, "settle-up.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
        }.onFailure { Log.w(TAG, "The payment code could not be shared", it) }
    }

    private companion object {
        const val TAG = "SettleUpPayment"
        const val SHARE_PIXELS = 800
        const val SHARE_QUIET_MODULES = 4
        const val PNG_QUALITY = 100
    }
}
