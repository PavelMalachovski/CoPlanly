package com.coparently.app.domain.model

/**
 * Who is behind a pairing code, as the server answers before anything is linked
 * (`previewPairingInvitation` in `functions/index.js`).
 *
 * @property inviterName The inviter's first name, or null when there is none to show — an
 *   account with no name, or one whose stored name is an address the server will not repeat.
 */
data class InvitePreview(val inviterName: String?)
