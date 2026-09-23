package com.coparently.app.presentation.common

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.ui.platform.UriHandler
import com.coparently.app.BuildConfig

/**
 * The hosted privacy policy, and the one way the app opens it (REL-4).
 *
 * Play's User Data policy wants the policy reachable from inside an app that handles health
 * data, so it is linked from Settings → Account and from the telemetry consent screen. Both
 * links read [url] and **render nothing while it is null**: the URL comes from
 * `BuildConfig.PRIVACY_POLICY_URL`, which stays blank until the policy is actually hosted, and a
 * row that opens a dead page is the affordance-promising-nothing that design rule #8 forbids.
 */
object PrivacyPolicyLink {

    private const val TAG = "PrivacyPolicyLink"

    /** The published policy, or null while none is hosted yet. */
    val url: String? = BuildConfig.PRIVACY_POLICY_URL.takeIf { it.isNotBlank() }

    /**
     * Opens the policy in the browser.
     *
     * A device with no browser at all is rare but real (a kiosk, a work profile), and Compose's
     * handler reports it by throwing — `IllegalArgumentException` wrapping the
     * `ActivityNotFoundException` on current versions, the bare exception on older ones. Either
     * way the tap does nothing and the reason is logged, rather than the app crashing on a link.
     *
     * @param uriHandler From `LocalUriHandler.current`, so the launch uses the Activity context.
     */
    fun open(uriHandler: UriHandler) {
        val target = url ?: return
        try {
            uriHandler.openUri(target)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No app can open the privacy policy", e)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can open the privacy policy", e)
        }
    }
}
