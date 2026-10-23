package com.coparently.app.presentation.pairing

import com.coparently.app.BuildConfig
import com.coparently.app.domain.pairing.InstallReferrer
import com.coparently.app.domain.pairing.PairingUri

/**
 * The link an invitation is shared and drawn as: the web form once `web/pair/` is hosted
 * ([BuildConfig.PAIRING_WEB_LINK]), the app-only form until then. One place, so the share sheet
 * and the QR can never carry different links for the same code.
 */
internal fun pairingLinkFor(code: String, web: Boolean = BuildConfig.PAIRING_WEB_LINK): String =
    if (web) PairingUri.buildWeb(code) else PairingUri.build(code)

/**
 * Where a recipient without the app installed goes to get it — in the co-parent, professional and
 * family-friend invitations alike.
 *
 * **This listing does not exist yet** (REL-6 in `docs/BACKLOG.md` is the Play Console work), so
 * the link answers "item not found" until the app is published. Shipped anyway, on the owner's
 * decision: the deep link alone strands exactly the person an invitation is for — someone who
 * does not have the app — and the code in the same message still works by hand. When the listing
 * goes live nothing here needs to change, which is why the URL is built from the applicationId
 * rather than pasted.
 */
internal const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=app.coplanly"

/**
 * The Play link a co-parent invitation sends: the listing with [code] in Play's `referrer`
 * parameter, so the invited parent who installs from it finds the code waiting after sign-in
 * instead of typing it ([InstallReferrer]). Without a code, the plain listing.
 */
internal fun playStoreUrlFor(code: String?): String =
    if (code.isNullOrBlank()) PLAY_STORE_URL else "$PLAY_STORE_URL&referrer=${InstallReferrer.encode(code)}"
