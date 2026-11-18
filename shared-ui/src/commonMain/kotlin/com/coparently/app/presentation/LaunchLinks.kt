package com.coparently.app.presentation

import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.chat.ChatUri
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.guests.GuestInviteUri
import com.coparently.app.domain.pairing.PairingUri

/**
 * A `coplanly://` link the app was opened with, as [LaunchLinks.parse] reads it: one of the three
 * custom-scheme hosts Android's manifest declares (`pair`, `guest`, `chat`), or `calendar`, the
 * link the iPhone's Today widget opens (iPhone phase 4 part D).
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

    /**
     * `coplanly://calendar`: the Calendar tab on today, as a tap on the tab opens it (design item
     * 5). What a tap on the iPhone's Today widget opens (`widgetURL`); Android's widget starts the
     * activity instead, and its manifest declares no such host.
     */
    data object Calendar : LaunchLink
}

/**
 * What tapping a push opens, as [LaunchLinks.forPush] reads it from the push's data: the family to
 * switch to first ([familyId], CLAUDE.md item 15), then the [link] or the [destination] to arm.
 * The same three things Android's `PushNotifier.tapIntent` puts on the intent `MainActivity` reads.
 *
 * @property familyId The family the push names, or null when it names none.
 * @property link A pairing link (pairing news) or a chat link (a message, a departed co-parent's
 *   notice), or null.
 * @property destination The screen every other type names ([PushDestination.forType]), or null.
 */
data class PushTap(val familyId: String?, val link: LaunchLink?, val destination: PushDestination?)

/**
 * Reads a `coplanly://` link into a [LaunchLink], for both apps: Android's `MainActivity` and the
 * iPhone's shell read the same three forms through it, with the domain's parsers ([PairingUri],
 * [GuestInviteUri], [ChatUri]) deciding the codes and the conversation id, and the iPhone's Today
 * widget's `coplanly://calendar` ([LaunchLink.Calendar]) beside them.
 *
 * Only the custom scheme: the pairing link's web form (`https://…/pair/…`) is answered with null
 * here, and `MainActivity` reads it itself, where an App Link can arrive.
 */
object LaunchLinks {

    /**
     * The link [url] is, or null when it is not one of the custom scheme's four hosts. The scheme
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
            scheme == CALENDAR_SCHEME && host == CALENDAR_HOST -> LaunchLink.Calendar
            else -> null
        }
    }

    /**
     * What tapping a push whose data is [data] opens on a phone where [signedInUid] is signed in
     * (iPhone phase 4 part C; Android's `PushNotifier.tapIntent`, read back by `MainActivity`), or
     * null when the push opens nothing: nobody is signed in, or the push names another addressee
     * ([PushPayload.TARGET_USER_ID]), the rule Android's `PushNotifier.receive` drops a push by
     * (CLAUDE.md item 23). A push that names no addressee is the signed-in account's, as there.
     *
     * Pairing news opens the pairing screen with nothing prefilled, a chat message and a departed
     * co-parent's notice their thread (the list when the thread is missing or malformed, as
     * [parse] reads `ChatUri.build`'s link), and every other type the screen
     * [PushDestination.forType] names, or nothing more than the app for a type this build does not
     * know. The family rides beside it, never inside the chat link, as on Android.
     *
     * The iPhone's local reminders (phase 4 part C) carry no type, and name their screen as Android's
     * reminders put it on the launcher intent: [PushDestination.EXTRA] with a key [PushDestination.fromKey]
     * reads — the calendar for an event's, Family → Child support for the payer's. Read only where the
     * type names no screen, and only ever picks one.
     */
    fun forPush(data: Map<String, String>, signedInUid: String?): PushTap? {
        val addressee = data[PushPayload.TARGET_USER_ID]?.takeIf { it.isNotBlank() }
        if (signedInUid == null || (addressee != null && addressee != signedInUid)) return null
        val type = data[PushPayload.TYPE]
        val link = when (type) {
            PushPayload.PAIRING_ACCEPTED, PushPayload.PAIRING_REMOVED -> LaunchLink.Pair("")
            PushPayload.CHAT_MESSAGE, PushPayload.COPARENT_ACCOUNT_DELETED ->
                parse(ChatUri.build(data[PushPayload.CONVERSATION_ID]))
            else -> null
        }
        return PushTap(
            familyId = data[PushPayload.FAMILY_ID]?.takeIf { it.isNotBlank() },
            link = link,
            destination = if (link == null) {
                PushDestination.forType(type) ?: PushDestination.fromKey(data[PushDestination.EXTRA])
            } else {
                null
            }
        )
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

    /** The Today widget's link, `coplanly://calendar`, compared exactly as the other hosts are. */
    private const val CALENDAR_SCHEME = "coplanly"
    private const val CALENDAR_HOST = "calendar"

    private const val SCHEME_SEPARATOR_LENGTH = 3
    private const val ESCAPE_LENGTH = 3
    private const val HEX = 16

    /** What ends an authority, as `Uri.parseAuthority` reads it (a backslash as a slash, per the URL spec). */
    private val AUTHORITY_ENDS = charArrayOf('/', '\\', '?', '#')

    private val FAMILY_ID_PATTERN = Regex("[?&]${PushPayload.FAMILY_ID}=([^&#]+)")
}
