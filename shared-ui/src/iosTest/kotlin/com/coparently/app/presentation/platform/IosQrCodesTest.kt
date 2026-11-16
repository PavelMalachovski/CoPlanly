package com.coparently.app.presentation.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iPhone's payment code (MON-24 phase 3, part C, Task 9): Core Image's matrix comes back the
 * shape ZXing's does on Android — square, one cell per module, no quiet zone, finder patterns in
 * three corners — and the shared picture is a PNG.
 */
class IosQrCodesTest {

    private val qrCodes = IosQrCodes(shareSheet = UnavailableShareSheet)

    @Test
    fun `encodes QR Platba text to a square matrix with finder patterns`() {
        val matrix = assertNotNull(qrCodes.encode(QR_PLATBA))
        assertEquals(matrix.width, matrix.height)
        assertTrue(matrix.width >= MIN_MODULES, "${matrix.width} modules")
        // A QR symbol is 21 + 4 n modules wide.
        assertEquals(0, (matrix.width - 21) % 4)
        assertTrue(isFinderAt(matrix, 0, 0), "top left")
        assertTrue(isFinderAt(matrix, matrix.width - FINDER, 0), "top right")
        assertTrue(isFinderAt(matrix, 0, matrix.height - FINDER), "bottom left")
        // The fourth corner has none, so the code is neither flipped nor mirrored.
        assertFalse(isFinderAt(matrix, matrix.width - FINDER, matrix.height - FINDER), "bottom right")
    }

    @Test
    fun `the cut leaves no light border around the symbol`() {
        val matrix = assertNotNull(qrCodes.encode(QR_PLATBA))
        assertTrue(matrix[0, 0] && matrix[matrix.width - 1, 0] && matrix[0, matrix.height - 1])
    }

    @Test
    fun `text outside Latin 1 still encodes`() {
        assertNotNull(qrCodes.encode("SPD*1.0*ACC:CZ6508000000192000145399*AM:12.00*CC:CZK*MSG:Účet za září"))
    }

    @Test
    fun `a matrix with nothing dark is no code`() {
        assertNull(QrModules.cropped(3, 3) { _, _ -> false })
    }

    @Test
    fun `the shared picture is a PNG with a quiet zone`() {
        val matrix = assertNotNull(qrCodes.encode(QR_PLATBA))
        val png = assertNotNull(QrPicture.png(matrix))
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), png.take(4).map { it.toInt() and 0xFF })
        val drawn = assertNotNull(IosImageDecoder.decode(png, bound = null))
        assertEquals(drawn.width, drawn.height)
        assertEquals(0, drawn.width % (matrix.width + 8))
    }

    /** Whether the 7 by 7 modules from [left], [top] are a finder pattern: a ring, a gap, a core. */
    private fun isFinderAt(matrix: QrMatrix, left: Int, top: Int): Boolean =
        (0 until FINDER).all { y ->
            (0 until FINDER).all { x ->
                val ring = x == 0 || y == 0 || x == FINDER - 1 || y == FINDER - 1
                val core = x in 2..4 && y in 2..4
                matrix[left + x, top + y] == (ring || core)
            }
        }

    private object UnavailableShareSheet : ShareSheet {
        override val available: Boolean = false

        override fun shareText(text: String, chooserTitle: String) = Unit

        override fun shareFile(contentUri: String, mimeType: String, chooserTitle: String) = Unit
    }

    private companion object {
        const val QR_PLATBA = "SPD*1.0*ACC:CZ6508000000192000145399*AM:480.50*CC:CZK*MSG:VYUCTOVANI LISTOPAD 2026"
        const val MIN_MODULES = 25
        const val FINDER = 7
    }
}
