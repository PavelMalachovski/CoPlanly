package com.coparently.app.presentation.pairing

import com.coparently.app.BuildConfig

/**
 * [pairingLinkFor] in the form this build shares ([BuildConfig.PAIRING_WEB_LINK]), for the app's own
 * callers: `AndroidInviteQrCodes`, which draws the QR the pairing screen shows, and the tests. The
 * shared screen reads the same flag through `AppBuildValues.pairingWebLink`, so the QR and the share
 * sheet carry the same link (CLAUDE.md item 37).
 */
internal fun pairingLinkFor(code: String): String = pairingLinkFor(code, web = BuildConfig.PAIRING_WEB_LINK)
