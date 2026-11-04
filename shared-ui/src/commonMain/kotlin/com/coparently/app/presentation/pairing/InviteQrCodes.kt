package com.coparently.app.presentation.pairing

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Draws an invitation's link as a QR code (MON-24 phase 2, Task 10): what [PairingViewModel] asks
 * of ZXing, which stays in the app.
 *
 * Takes the invitation's code, not a link, because which link an invitation is drawn as — the web
 * form or the app-only one — is the build's choice (`BuildConfig.PAIRING_WEB_LINK`), and the share
 * sheet and the QR must carry the same one (CLAUDE.md item 37). The app's `AndroidInviteQrCodes`
 * answers it with `pairingLinkFor`, the function the share sheet calls, and `QRCodeService`; the
 * iPhone's stand-in draws none yet.
 */
fun interface InviteQrCodes {

    /**
     * Whether this phone draws QR codes at all. The pairing screen draws the QR frame only when it
     * does (design item 8): ZXing on Android always does; the iPhone's stand-in does not yet, and an
     * empty frame would wait for a code that never comes (MON-24 phase 2, slice 11f).
     */
    val available: Boolean get() = true

    /** The QR code of [code]'s invitation link, or null when it could not be drawn. */
    fun forInvite(code: String): ImageBitmap?
}
