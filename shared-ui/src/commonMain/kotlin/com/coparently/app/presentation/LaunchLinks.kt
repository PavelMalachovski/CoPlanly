package com.coparently.app.presentation

import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.chat.ChatUri
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.guests.GuestInviteUri
import com.coparently.app.domain.pairing.PairingUri

/**
 * A `coplanly://` link the app was opened with, as [LaunchLinks.parse] reads it: one of the three
 * custom-scheme hosts Android's manifest declares (`pair`, `guest`, `chat`).
 */
sealed interface LaunchLink {
    /**
     * A `coplanly://pair` link. [code] is the invite code the link carries, upper-cased, or empty for
     * a link with none (the one a pairing push opens): the pairing screen still opens, with nothing
     * prefilled (`MainActivity.readLaunchLink`).
     */
    data class Pair(val code: String) : LaunchLink

    /** A `coplanly://guest` link carrying [code]. A guest link with no valid code is no link. */
    data class Guest(val code: String) : LaunchLink

    /**
     * A `coplanly://chat` link: the thread [conversationId] names, or the Chat tab's list when it
     * names none ([ChatUri.extractConversationId]). [familyId] is the family the link names in a
     * `familyId` query parameter, which the shell switches to before it arms the link (CLAUDE.md
     * item 15), or null. Android takes the family from the push's intent extra instead and never
     * reads this one.
     */
    data class Chat(val familyId: String?, val conversationId: String?) : LaunchLink
}

/**
 * Reads a `coplanly://` link into a [LaunchLink], for both apps: Android's `MainActivity` and the
 * iPhone's shell read the same three forms through it, with the domain's parsers ([PairingUri],
 * [GuestInviteUri], [ChatUri]) deciding the codes and the conversation id.
 *
 * Only the custom scheme: the pairing link's web form (`https://…/pair/…`) is answered with null
 * here, and `MainActivity` reads it itself, where an App Link can arrive.
 */
object LaunchLinks {

    /**
     * The link [url] is, or null when it is not one of the custom scheme's three hosts. The scheme
     * and host are compared exactly, as Android's intent filters and `Uri` compare them.
     */
    fun parse(url: String): LaunchLink? {
        val (scheme, host) = schemeAndHost(url) ?: return null
        return when {
            // No path: the web form's host alone never matches, so this is the custom scheme's.
            PairingUri.isPairingUri(scheme, host) ->
                LaunchLink.Pair(PairingUri.extractCode(url).orEmpty())
            GuestInviteUri.isGuestUri(scheme, host) ->
                GuestInviteUri.extractCode(url)?.let { LaunchLink.Guest(it) }
            ChatUri.isChatUri(scheme, host) ->
                LaunchLink.Chat(familyId = familyIdOf(url), conversationId = ChatUri.extractConversationId(url))
            else -> null
        }
    }

    /**
     * The scheme and host of a hierarchical URL (`scheme://[userinfo@]host[:port]…`), as
     * `android.net.Uri` reads them, or null when [url] has no `://`.
     */
    private fun schemeAndHost(url: String): kotlin.Pair<String, String>? {
        val separator = url.indexOf("://")
        if (separator <= 0) return null
        val scheme = url.substring(0, separator)
        if (scheme.any { it in "/?#" }) return null
        val rest = url.substring(separator + SCHEME_SEPARATOR_LENGTH)
        val end = rest.indexOfAny(charArrayOf('/', '?', '#')).takeIf { it >= 0 } ?: rest.length
        val authority = rest.substring(0, end)
        val hostAndPort = authority.substringAfterLast('@')
        val host = hostAndPort.substringBeforeLast(':')
        return scheme to host
    }

    /** The family a chat link names, when it names one in the shape a family id has. */
    private fun familyIdOf(url: String): String? =
        FAMILY_ID_PATTERN.find(url)?.groupValues?.get(1)?.takeIf { FamilyKey.membersOf(it) != null }

    private const val SCHEME_SEPARATOR_LENGTH = 3

    private val FAMILY_ID_PATTERN = Regex("[?&]${PushPayload.FAMILY_ID}=([^&#]+)")
}
