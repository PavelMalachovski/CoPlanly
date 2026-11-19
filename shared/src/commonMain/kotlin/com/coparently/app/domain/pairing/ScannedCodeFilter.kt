package com.coparently.app.domain.pairing

/**
 * What one scan of the QR scanner hands over: the first payload that holds an invitation code
 * ([PairingUri.extractCode]: a bare code or a pairing link in either form), once.
 *
 * A camera reads the same code many times a second, and reads every other QR in view too (a
 * restaurant's menu, a parking sign), so the scanner feeds each payload through this and acts on a
 * non-null answer alone. After the first code every payload answers null, so a code is handed over
 * once per scan, as Android's scanner claims its one delivery. One filter per scan; it is not
 * thread-safe, so feed it from one queue.
 */
class ScannedCodeFilter {

    private var handedOver = false

    /**
     * The code in [payload] the first time a valid one is seen; null for a payload that holds none
     * and for every call after a code was handed over.
     */
    fun accept(payload: String): String? {
        if (handedOver) return null
        val code = PairingUri.extractCode(payload) ?: return null
        handedOver = true
        return code
    }
}
