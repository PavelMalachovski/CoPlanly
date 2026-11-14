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
     * The scheme and host of a hierarchical URL (`scheme://[userinfo@]host[:port]…`), read as
     * `android.net.Uri` reads them (`parseAuthority`, `parseHost`, `findPortSeparator`), or null
     * when [url] has no `://`: the authority ends at a slash, a backslash, `?` or `#`; the user
     * info is cut at the last `@`; a port is the digits after the last `:` and nothing else
     * (`pair:80` is host `pair`, while `pair:x` is host `pair:x`, which matches nothing); and the
     * host is percent-decoded. `MainActivity` compared `Uri`'s own host before this reader existed, so it
     * must read every link alike.
     */
    private fun schemeAndHost(url: String): kotlin.Pair<String, String>? {
        val separator = url.indexOf("://")
        if (separator <= 0) return null
        val scheme = url.substring(0, separator)
        if (scheme.any { it in "/?#" }) return null
        val rest = url.substring(separator + SCHEME_SEPARATOR_LENGTH)
        val end = rest.indexOfAny(AUTHORITY_ENDS).takeIf { it >= 0 } ?: rest.length
        val authority = rest.substring(0, end)
        val hostStart = authority.lastIndexOf('@') + 1
        // The scan for a port stops at the first non-digit, so it never reaches back past the '@'.
        val hostEnd = portSeparatorOf(authority) ?: authority.length
        return scheme to percentDecoded(authority.substring(hostStart, hostEnd))
    }

    /**
     * Where the port starts, as `Uri.findPortSeparator`: scanning back from the end, the first `:`
     * reached over ASCII digits alone, or null as soon as anything else comes first.
     */
    private fun portSeparatorOf(authority: String): Int? {
        val lastNonDigit = authority.indexOfLast { it !in '0'..'9' }
        return lastNonDigit.takeIf { it >= 0 && authority[it] == ':' }
    }

    /**
     * [text] with each `%XX` escape decoded as UTF-8, as `Uri.decode` decodes a host; a malformed
     * escape is kept as written (`Uri` writes U+FFFD there instead, and neither matches a host).
     */
    private fun percentDecoded(text: String): String {
        if ('%' !in text) return text
        val decoded = StringBuilder(text.length)
        val bytes = ArrayList<Byte>()
        fun flush() {
            if (bytes.isNotEmpty()) decoded.append(bytes.toByteArray().decodeToString())
            bytes.clear()
        }
        var i = 0
        while (i < text.length) {
            val isEscape = text[i] == '%' && i + 2 <= text.lastIndex && isHex(text[i + 1]) && isHex(text[i + 2])
            if (isEscape) {
                bytes += text.substring(i + 1, i + ESCAPE_LENGTH).toInt(HEX).toByte()
                i += ESCAPE_LENGTH
            } else {
                flush()
                decoded.append(text[i])
                i++
            }
        }
        flush()
        return decoded.toString()
    }

    private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** The family a chat link names, when it names one in the shape a family id has. */
    private fun familyIdOf(url: String): String? =
        FAMILY_ID_PATTERN.find(url)?.groupValues?.get(1)?.takeIf { FamilyKey.membersOf(it) != null }

    private const val SCHEME_SEPARATOR_LENGTH = 3
    private const val ESCAPE_LENGTH = 3
    private const val HEX = 16

    /** What ends an authority, as `Uri.parseAuthority` reads it (a backslash as a slash, per the URL spec). */
    private val AUTHORITY_ENDS = charArrayOf('/', '\\', '?', '#')

    private val FAMILY_ID_PATTERN = Regex("[?&]${PushPayload.FAMILY_ID}=([^&#]+)")
}
