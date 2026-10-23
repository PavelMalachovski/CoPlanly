package com.coparently.app.domain.pairing

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The pairing code an invitation's Google Play link carries through the install.
 *
 * The invited parent usually has no app yet, so the `coplanly://` link in the invitation does
 * nothing for them and the code used to be lost on the way through the store: they had to find
 * the message again and type it. The Play link therefore names the code in Play's own `referrer`
 * parameter (`…details?id=app.coplanly&referrer=pair%3D4F7K2M`), and Play hands the referrer to
 * the installed app once (see [InstallReferrerSource]).
 *
 * The code read here is only a prefill: it goes through the preview and the parent's own yes
 * like a typed one (CLAUDE.md item 37), so a forged referrer can at most open the invitation
 * screen with somebody's name on it.
 */
object InstallReferrer {

    /** The referrer key the invitation's Play link stores the code under. */
    const val PAIR_KEY = "pair"

    /**
     * The pairing code in [referrer], or null when it holds none.
     *
     * Tolerates Play's own parameters around ours (`utm_source=google-play&…`) and a value that
     * comes back still percent-encoded. Only a well-formed code counts, by the same rule as a
     * typed one ([InviteCodeGenerator.isValid]).
     */
    fun pairingCode(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        val decoded = runCatching { URLDecoder.decode(referrer, Charsets.UTF_8.name()) }
            .getOrDefault(referrer)
        return decoded.split('&')
            .map { it.substringBefore('=') to it.substringAfter('=', "") }
            .firstOrNull { (key, _) -> key.trim() == PAIR_KEY }
            ?.second
            ?.trim()
            ?.uppercase()
            ?.takeIf { InviteCodeGenerator.isValid(it) }
    }

    /**
     * Whether [consumed] — the code the pairing screen just received — is the referrer's own
     * [fromReferrer], so the referrer is used up. A deep link the parent tapped may have taken the
     * slot instead, and then the referrer's code is still owed to them on a later launch.
     */
    fun isHandOff(consumed: String?, fromReferrer: String?): Boolean =
        fromReferrer != null && consumed == fromReferrer

    /** The value of Play's `referrer` parameter for [code], percent-encoded for a URL. */
    fun encode(code: String): String = URLEncoder.encode("$PAIR_KEY=$code", Charsets.UTF_8.name())
}

/**
 * Reads the pairing code the install referrer carries, until it has reached the pairing screen.
 *
 * Play serves the same referrer for months, so the code is offered on every launch until
 * [markHandedOff] — not merely until it was read: a rotation, a language change or a process death
 * before sign-in would otherwise lose it, and the invited parent would type it after all. Once
 * handed off it is never offered again, so a later sign-out or a second account on the phone does
 * not reopen somebody else's invitation.
 */
interface InstallReferrerSource {

    /** The code, while it has not been handed off on this install; null when none or Play is unsure. */
    suspend fun pendingPairingCode(): String?

    /** Records that the code reached a signed-in account's pairing screen; it is not offered again. */
    fun markHandedOff()
}
