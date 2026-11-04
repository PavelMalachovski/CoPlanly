package com.coparently.app.presentation.platform

/**
 * A QR code drawn from text, and handed to another app as a picture: the settle-up card's payment
 * code (CLAUDE.md item 45), which a bank's app reads from an image because it cannot scan the
 * screen it runs on.
 *
 * On Android the matrix is ZXing's (`QRCodeWriter`, error correction M, no margin) and the picture a
 * PNG in the app's cache shared through its file provider, as `SettleUpPayment` made them before it
 * moved (MON-24 phase 2, Task 11). The screen draws [QrMatrix] itself, one cell per module.
 */
interface QrCodes : PlatformCapability {

    /** [content] as a QR matrix, one cell per module, or null when it cannot be encoded. */
    fun encode(content: String): QrMatrix?

    /**
     * Offers [matrix] as a black-on-white picture, with a quiet zone, to the app the parent picks.
     *
     * @param chooserTitle The share sheet's title
     */
    fun shareImage(matrix: QrMatrix, chooserTitle: String)
}

/**
 * A QR code's modules, [width] by [height], `true` for a dark one.
 *
 * @param cells Row by row, `y * width + x`
 */
class QrMatrix(val width: Int, val height: Int, private val cells: BooleanArray) {

    init {
        require(cells.size == width * height) { "a $width by $height matrix needs ${width * height} cells" }
    }

    /** Whether the module at column [x], row [y] is dark. */
    operator fun get(x: Int, y: Int): Boolean = cells[y * width + x]
}
