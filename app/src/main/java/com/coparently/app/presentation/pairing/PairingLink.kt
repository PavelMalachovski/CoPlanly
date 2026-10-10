package com.coparently.app.presentation.pairing

import com.coparently.app.BuildConfig
import com.coparently.app.domain.pairing.PairingUri

/**
 * The link an invitation is shared and drawn as: the web form once `web/pair/` is hosted
 * ([BuildConfig.PAIRING_WEB_LINK]), the app-only form until then. One place, so the share sheet
 * and the QR can never carry different links for the same code.
 */
internal fun pairingLinkFor(code: String, web: Boolean = BuildConfig.PAIRING_WEB_LINK): String =
    if (web) PairingUri.buildWeb(code) else PairingUri.build(code)
