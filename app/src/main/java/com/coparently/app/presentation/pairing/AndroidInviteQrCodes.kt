package com.coparently.app.presentation.pairing

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.coparently.app.data.remote.firebase.QRCodeService

/**
 * The Android [InviteQrCodes] (MON-24 phase 2, Task 10): ZXing's drawing, through [QRCodeService],
 * of the link [pairingLinkFor] picks — the function the share sheet calls, so the QR and the shared
 * text carry the same link (CLAUDE.md item 37). The bitmap is wrapped, not copied.
 */
class AndroidInviteQrCodes(private val qrCodeService: QRCodeService) : InviteQrCodes {

    override fun forInvite(code: String): ImageBitmap? =
        qrCodeService.generatePairingQRCode(content = pairingLinkFor(code))?.asImageBitmap()
}
