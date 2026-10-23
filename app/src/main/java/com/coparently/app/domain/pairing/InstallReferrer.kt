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

    /** The value of Play's `referrer` parameter for [code], percent-encoded for a URL. */
    fun encode(code: String): String = URLEncoder.encode("$PAIR_KEY=$code", Charsets.UTF_8.name())
}

/**
 * Reads the pairing code the install referrer carries, at most once per install.
 *
 * Once means once: a later launch, a sign-out or a second account on the same phone never gets
 * the code again, because the invitation was meant for whoever signed in first after installing.
 */
interface InstallReferrerSource {

    /** The code, the first time this is asked on this install; null after that, or when none. */
    suspend fun pairingCodeOnce(): String?
}
