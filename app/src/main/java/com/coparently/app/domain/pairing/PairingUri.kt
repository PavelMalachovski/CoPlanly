package com.coparently.app.domain.pairing

/**
 * The pairing link, in its two forms, used by the share sheet, the QR image and the deep link.
 * All of them redeem the same invitation, so parsing lives here once instead of in the scanner
 * and the navigation graph separately.
 *
 * - `coplanly://pair?code=…`, the custom scheme: opens the app wherever it is installed, but
 *   messengers do not make it clickable, so in a WhatsApp message it was text to copy.
 * - `https://coparently-a39c9.web.app/pair/…`, the web form: clickable everywhere. It opens the
 *   app directly once `web/.well-known/assetlinks.json` names the app's signing key (an App
 *   Link), and until then opens `web/pair/`, whose button hands the code to the app through the
 *   custom scheme — or, without the app, says how to get it.
 *
 * Both forms are always *read*; which one is *shared* is the build's choice
 * (`BuildConfig.PAIRING_WEB_LINK`), because the web form is a dead link until the page is hosted.
 */
object PairingUri {

    /** Custom scheme of the app-only form. */
    const val SCHEME = "coplanly"

    /** Host segment of the app-only form. */
    const val HOST = "pair"

    /**
     * Host of the web form: the project's Firebase Hosting site, which serves `web/`. The
     * manifest's App Link filter names the same host.
     */
    const val WEB_HOST = "coparently-a39c9.web.app"

    /** Path prefix of the web form; the code is the segment after it. */
    const val WEB_PATH_PREFIX = "/pair/"

    /** Builds the app-only link for [code]. */
    fun build(code: String): String = "$SCHEME://$HOST?code=$code"

    /** Builds the web link for [code]. */
    fun buildWeb(code: String): String = "https://$WEB_HOST$WEB_PATH_PREFIX$code"

    /**
     * Whether [scheme], [host] and [path] (as read off an incoming `Intent`'s `Uri`, e.g.
     * `intent.data?.scheme`) identify a pairing link in either form. Takes plain strings rather
     * than `android.net.Uri` so this check has no Android dependency and can be unit-tested
     * directly. [path] matters only for the web form, whose host serves other pages too.
     */
    fun isPairingUri(scheme: String?, host: String?, path: String? = null): Boolean =
        (scheme == SCHEME && host == HOST) ||
            (scheme == "https" && host == WEB_HOST && path?.startsWith(WEB_PATH_PREFIX) == true)

    /**
     * Extracts a valid invite code from [input], which may be a bare code, a pairing link in
     * either form, or free text containing one (a pasted share message).
     *
     * @return the upper-cased code, or null when [input] holds none.
     */
    fun extractCode(input: String): String? {
        val normalized = input.trim().uppercase()
        val fromUri = CODE_PARAM.find(normalized)?.groupValues?.get(1)
            ?: WEB_PATH.find(normalized)?.groupValues?.get(1)
        val candidate = fromUri ?: normalized
        return candidate.takeIf { InviteCodeGenerator.isValid(it) }
    }

    private val CODE_PARAM = Regex("CODE=([A-Z0-9]{${InviteCodeGenerator.LENGTH}})")

    /** The web form's code segment, upper-cased like the input it is matched against. */
    private val WEB_PATH = Regex(
        Regex.escape((WEB_HOST + WEB_PATH_PREFIX).uppercase()) +
            "([A-Z0-9]{${InviteCodeGenerator.LENGTH}})(?![A-Z0-9])"
    )
}
