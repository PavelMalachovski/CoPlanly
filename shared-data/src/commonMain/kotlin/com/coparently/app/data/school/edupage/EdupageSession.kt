package com.coparently.app.data.school.edupage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A signed-in EduPage session: what every later request needs, and nothing that signs in again.
 *
 * @property host The school's EduPage host, such as `zsx.edupage.org`: the host the sign-in ended
 *   on, which is the school's even when the sign-in started at `login1.edupage.org`.
 * @property cookies The session cookies by name (`PHPSESSID` among them), sent with each request.
 * @property gsecHash The page's `ASC.gsechash`, which EduPage's JSON calls carry as `__gsh`; empty
 *   when the page had none.
 * @property userhome The `userhome(…)` object of the page: the account, its children and the
 *   school year (`dp.year`).
 */
data class EdupageSession(
    val host: String,
    val cookies: Map<String, String>,
    val gsecHash: String,
    val userhome: JsonObject
) {
    /** The host and the cookies' names only: a cookie's value or the hash would sign anybody in. */
    override fun toString(): String =
        "EdupageSession(host=$host, cookies=${cookies.keys}, gsecHash=<hidden>, userhome=${userhome.size} keys)"
}

/**
 * The cookies one EduPage exchange collects, by name, in the order they arrived: a minimal jar for
 * the `*.edupage.org` hosts a sign-in moves between. Every cookie is sent to every host the client
 * follows, which stays inside that family.
 */
internal class EdupageCookieJar(initial: Map<String, String>) {
    private val cookies = LinkedHashMap(initial)

    /** The cookies as they stand. */
    fun snapshot(): Map<String, String> = cookies.toMap()

    /** The `Cookie` header that carries them, or null while there is none. */
    fun header(): String? =
        cookies.takeIf { it.isNotEmpty() }?.entries?.joinToString("; ") { (name, value) -> "$name=$value" }

    /**
     * Takes in the `Set-Cookie` headers of one answer. A value may hold several cookies joined by
     * commas, as NSURLSession joins repeated headers; an `Expires` date's comma is told apart. A
     * cookie set to nothing, to `deleted` (PHP's way of ending a session) or with `Max-Age` of zero
     * or less is removed.
     */
    fun absorb(setCookies: List<String>) {
        setCookies.flatMap(::splitJoined).forEach(::absorbOne)
    }

    private fun absorbOne(setCookie: String) {
        val parts = setCookie.split(';')
        val pair = parts.first()
        val eq = pair.indexOf('=')
        val name = if (eq > 0) pair.substring(0, eq).trim() else ""
        if (name.isEmpty()) return
        val value = pair.substring(eq + 1).trim()
        val expired = parts.drop(1).any { attribute ->
            val (key, setting) = attribute.trim().split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            key.equals("max-age", ignoreCase = true) && (setting.trim().toLongOrNull() ?: 1L) <= 0L
        }
        if (value.isEmpty() || value == "deleted" || expired) cookies.remove(name) else cookies[name] = value
    }

    private fun splitJoined(value: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var comma = value.indexOf(',')
        while (comma >= 0) {
            if (startsCookie(value, comma + 1)) {
                parts += value.substring(start, comma)
                start = comma + 1
            }
            comma = value.indexOf(',', comma + 1)
        }
        parts += value.substring(start)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Whether a `name=value` pair starts at [from], rather than the rest of an `Expires` date. */
    private fun startsCookie(value: String, from: Int): Boolean {
        val end = value.indexOf(';', from).let { if (it < 0) value.length else it }
        val pair = value.substring(from, end).trimStart()
        val eq = pair.indexOf('=')
        return eq > 0 && pair.substring(0, eq).none { it == ' ' || it == ',' }
    }
}

/** What the client reads out of EduPage's HTML pages. */
internal object EdupagePages {
    private const val CSRF_MARK = "\"csrftoken\":\""
    private const val GSEC_MARK = "ASC.gsechash=\""
    private const val USERHOME_MARK = "userhome("

    /** The sign-in page's `"csrftoken":"…"`, or null when the page has none. */
    fun csrfToken(page: String): String? = quotedAfter(page, CSRF_MARK)?.takeIf { it.isNotEmpty() }

    /** The page's `ASC.gsechash="…"`, or an empty text when it has none. */
    fun gsecHash(page: String): String = quotedAfter(page, GSEC_MARK).orEmpty()

    /**
     * The object of the page's `userhome(…);` call, with tabs and line breaks removed, or null when
     * the page has no such call (a sign-in page).
     *
     * @throws EdupageException.NotEdupage when the call is there and its object cannot be read.
     */
    fun userhome(page: String): JsonObject? {
        val call = page.indexOf(USERHOME_MARK)
        if (call < 0) return null
        val text = objectAt(page, call + USERHOME_MARK.length)
            ?.filterNot { it == '\t' || it == '\n' || it == '\r' }
        val parsed = text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        return parsed ?: throw EdupageException.NotEdupage("Unreadable user data")
    }

    private fun quotedAfter(page: String, mark: String): String? {
        val start = page.indexOf(mark)
        if (start < 0) return null
        val from = start + mark.length
        val end = page.indexOf('"', from)
        return if (end < 0) null else page.substring(from, end)
    }

    /**
     * The JSON object that opens at the first `{` at or after [from] (only whitespace before it), up
     * to its matching `}`: braces inside strings and escaped quotes are skipped. Null when there is
     * no object there or it never closes.
     */
    private fun objectAt(page: String, from: Int): String? {
        var open = from
        while (open < page.length && page[open].isWhitespace()) open++
        if (open >= page.length || page[open] != '{') return null
        var depth = 0
        var inString = false
        var i = open
        while (i < page.length) {
            val c = page[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> if (--depth == 0) return page.substring(open, i + 1)
            }
            i++
        }
        return null
    }
}
