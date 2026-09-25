package com.coparently.app.presentation.common

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.ui.platform.UriHandler
import com.coparently.app.BuildConfig

/**
 * The hosted terms of service, and the one way the app opens them.
 *
 * Linked from the sign-in screen, where an account is created, because terms a consumer never
 * had a chance to read before the contract was made do not bind them (§ 1751 and § 1820 of the
 * Czech Civil Code; `docs/legal/LEGAL-REVIEW-2026-09.md` L-12). Like [PrivacyPolicyLink], every
 * link reads [url] and **renders nothing while it is null**: the URL comes from
 * `BuildConfig.TERMS_URL`, blank until `web/terms/` is hosted, and a link to a page that does not
 * resolve is the affordance-promising-nothing design rule #8 forbids.
 */
object TermsOfServiceLink {

    private const val TAG = "TermsOfServiceLink"

    /** The published terms, or null while none are hosted yet. */
    val url: String? = BuildConfig.TERMS_URL.takeIf { it.isNotBlank() }

    /**
     * Opens the terms in the browser. A device with no browser logs the reason and does nothing,
     * as [PrivacyPolicyLink.open] does.
     *
     * @param uriHandler From `LocalUriHandler.current`, so the launch uses the Activity context.
     */
    fun open(uriHandler: UriHandler) {
        val target = url ?: return
        try {
            uriHandler.openUri(target)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No app can open the terms of service", e)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can open the terms of service", e)
        }
    }
}
