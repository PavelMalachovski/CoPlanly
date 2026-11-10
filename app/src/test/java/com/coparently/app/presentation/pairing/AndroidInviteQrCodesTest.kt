package com.coparently.app.presentation.pairing

import com.coparently.app.data.remote.firebase.QRCodeService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The invitation's QR carries the link the share sheet sends (CLAUDE.md item 37): the check
 * `PairingViewModelTest` made of `QRCodeService` until the ViewModel moved to `:shared-ui` (MON-24).
 */
class AndroidInviteQrCodesTest {

    @Test
    fun `the QR encodes the link pairingLinkFor picks`() {
        val qrCodeService = mockk<QRCodeService>(relaxed = true)

        AndroidInviteQrCodes(qrCodeService).forInvite("4F7K2M")

        verify(exactly = 1) { qrCodeService.generatePairingQRCode(content = pairingLinkFor("4F7K2M")) }
    }

    @Test
    fun `a QR that could not be drawn is none`() {
        val qrCodeService = mockk<QRCodeService> {
            every { generatePairingQRCode(any(), any(), any()) } returns null
        }

        assertNull(AndroidInviteQrCodes(qrCodeService).forInvite("4F7K2M"))
    }
}
