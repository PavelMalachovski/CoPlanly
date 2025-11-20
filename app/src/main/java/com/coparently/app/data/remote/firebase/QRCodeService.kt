package com.coparently.app.data.remote.firebase

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating QR codes using ZXing library.
 * Creates QR codes that can be shared for co-parent pairing invitations.
 */
@Singleton
class QRCodeService @Inject constructor() {

    /**
     * Generates a QR code bitmap from the provided text content.
     *
     * @param content The text content to encode in the QR code
     * @param width The width of the QR code bitmap in pixels
     * @param height The height of the QR code bitmap in pixels
     * @return Bitmap containing the QR code, or null if generation fails
     */
    fun generateQRCode(content: String, width: Int = 512, height: Int = 512): Bitmap? {
        return try {
            val qrCodeWriter = QRCodeWriter()

            // Set encoding hints for better error correction and character set
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1 // Minimal margin
            )

            // Encode the content into a BitMatrix
            val bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints)

            // Convert BitMatrix to Bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }

            bitmap
        } catch (e: WriterException) {
            // Log error and return null if QR code generation fails
            android.util.Log.e("QRCodeService", "Failed to generate QR code", e)
            null
        } catch (e: Exception) {
            // Catch any other unexpected exceptions
            android.util.Log.e("QRCodeService", "Unexpected error generating QR code", e)
            null
        }
    }

    /**
     * Generates a pairing invitation QR code with structured data.
     *
     * @param invitationId Unique ID for the invitation
     * @param inviterName Name of the person sending the invitation
     * @param inviterEmail Email of the person sending the invitation
     * @param width QR code width in pixels
     * @param height QR code height in pixels
     * @return Bitmap containing the pairing QR code, or null if generation fails
     */
    fun generatePairingQRCode(
        invitationId: String,
        inviterName: String,
        inviterEmail: String,
        width: Int = 512,
        height: Int = 512
    ): Bitmap? {
        // Create structured JSON content for the QR code
        val qrContent = """
            {
                "type": "coparent_invitation",
                "invitationId": "$invitationId",
                "inviterName": "$inviterName",
                "inviterEmail": "$inviterEmail",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        return generateQRCode(qrContent, width, height)
    }
}
