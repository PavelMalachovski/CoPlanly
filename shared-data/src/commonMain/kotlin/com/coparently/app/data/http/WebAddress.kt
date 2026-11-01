package com.coparently.app.data.http

/**
 * An `http://` or `https://` address, read and written the way OkHttp's `HttpUrl` read and wrote
 * it before the school import moved to Ktor (MON-24).
 *
 * The school import keys everything on a school's address: the connection, and the id of every
 * event it imports, hash the address `BakalariUrls.normalize` writes. A parent who connects the
 * same school on an older build and on this one must get the same text, so this reads an address
 * exactly as OkHttp 4.12 did, for every address a parent can type or the directory can send:
 * leading and trailing whitespace dropped, the scheme in any case, any number of slashes after it,
 * the user name and password skipped, the host percent-decoded, lowercased and, label by label,
 * written in Punycode when it is not ASCII ("škola.cz" is `xn--kola-f6a.cz`), the port checked,
 * `.` and `..` segments resolved, and each path segment percent-encoded with OkHttp's own set.
 * `WebAddressVectorsTest` holds it to OkHttp 4.12's output, on the JVM and the iPhone simulator.
 * Not to the OkHttp the app now carries: OkHttp 5 maps hosts by a newer IDNA standard.
 *
 * A redirect's `Location` is resolved against the address it answered ([resolve]) as OkHttp's
 * `HttpUrl.resolve` did, query included, because the school client follows redirects as OkHttp
 * did.
 *
 * What OkHttp did that this does not, none of which a school's address uses: an IPv6 literal host
 * is refused rather than read; IDNA 2003's mapping of a host label (Unicode normalisation, the
 * characters it drops or refuses) is not applied beyond lowercasing and `ß`, so a host typed with
 * combining accents is encoded as typed; the fragment and the user information are dropped; and a
 * base address's query is not carried into the addresses [withPathSegments] and [withPathSegment]
 * build from it (a school's base address has none).
 *
 * @property isHttps Whether the scheme is `https`.
 * @property host The host as OkHttp canonicalised it: lowercase ASCII.
 * @property port The port, the scheme's default when none was written.
 * @property encodedPathSegments The path's segments, percent-encoded; `[""]` for `/`, and a trailing
 *   `""` for a path that ends in a slash.
 * @property encodedQuery The query after `?`, percent-encoded as OkHttp kept it, or null for none.
 */
internal class WebAddress private constructor(
    val isHttps: Boolean,
    val host: String,
    val port: Int,
    val encodedPathSegments: List<String>,
    val encodedQuery: String? = null,
) {
    /**
     * This address with [path]'s `/`-separated segments added, as `HttpUrl.Builder.addPathSegments`
     * added them: a trailing empty segment is replaced, and each segment is percent-encoded.
     */
    fun withPathSegments(path: String): String = copyWith { segments ->
        val parts = path.split('/', '\\')
        parts.forEachIndexed { i, part -> push(segments, part, addTrailingSlash = i < parts.lastIndex) }
    }

    /** This address with [segment] added as one segment, a `/` in it encoded, as `addPathSegment` did. */
    fun withPathSegment(segment: String): String = copyWith { segments -> push(segments, segment, false) }

    /**
     * [link] (a redirect's `Location`) resolved against this address as OkHttp's `HttpUrl.resolve`
     * resolved it, or null when it is not an http(s) address.
     */
    fun resolve(link: String): WebAddress? = parse(this, link)

    /** Whether a request to [other] goes to the same scheme, host and port, as OkHttp judged it. */
    fun sameOrigin(other: WebAddress): Boolean = isHttps == other.isHttps && host == other.host && port == other.port

    /** `scheme://host[:port]/path[?query]`, the port left out when it is the scheme's default. */
    override fun toString(): String = render(encodedPathSegments) + encodedQuery?.let { "?$it" }.orEmpty()

    private fun copyWith(edit: (MutableList<String>) -> Unit): String {
        val segments = encodedPathSegments.toMutableList()
        edit(segments)
        return render(segments)
    }

    private fun render(segments: List<String>): String = buildString {
        append(if (isHttps) "https://" else "http://")
        append(host)
        if (port != defaultPort(isHttps)) append(':').append(port)
        segments.forEach { append('/').append(it) }
    }

    companion object {
        private const val HTTP_PORT = 80
        private const val HTTPS_PORT = 443
        private const val MAX_PORT = 65_535

        /** [input] as an http(s) address, or null when OkHttp's `toHttpUrlOrNull` returned null. */
        fun parse(input: String): WebAddress? = parse(null, input)

        /** OkHttp's `HttpUrl.Builder.parse`: [input] on its own, or relative to [base]. */
        private fun parse(base: WebAddress?, input: String): WebAddress? {
            val start = input.indexOfFirst { !it.isAsciiWhitespace() }
            val limit = input.indexOfLast { !it.isAsciiWhitespace() } + 1
            if (start < 0) return base?.let { relative(it, input, limit, limit) }
            val explicit = if (hasScheme(input, start, limit)) schemeOf(input, start) else null
            if (explicit == null && (base == null || hasScheme(input, start, limit))) return null
            val isHttps = explicit ?: checkNotNull(base).isHttps
            val pos = if (explicit == null) start else start + if (isHttps) "https:".length else "http:".length
            val slashes = (pos until limit).takeWhile { input[it] == '/' || input[it] == '\\' }.size
            return if (slashes >= 2 || base == null || base.isHttps != isHttps) {
                afterScheme(input, isHttps, pos + slashes, limit)
            } else {
                relative(base, input, pos, limit)
            }
        }

        /**
         * The authority and path after the scheme and its slashes: the user information is
         * skipped up to the last `@` before the path, then the host and the port, then the path.
         */
        private fun afterScheme(input: String, isHttps: Boolean, start: Int, limit: Int): WebAddress? {
            val authorityEnd = input.delimiterOffset("/\\?#", start, limit)
            val lastAt = if (authorityEnd > start) input.lastIndexOf('@', authorityEnd - 1) else -1
            val hostStart = if (lastAt >= start) lastAt + 1 else start
            val portColon = portColonOffset(input, hostStart, authorityEnd)
            val host = HostNames.canonical(input.substring(hostStart, portColon))
            val port = portOf(input, portColon + 1, authorityEnd, isHttps)
            if (host == null || port == null) return null
            return withPathAndQuery(WebAddress(isHttps, host, port, listOf("")), input, authorityEnd, limit)
        }

        /** A relative link: [base]'s authority, its path and, for a link with no path or query, its query. */
        private fun relative(base: WebAddress, input: String, pos: Int, limit: Int): WebAddress {
            val keepsQuery = pos == limit || input[pos] == '#'
            val start = WebAddress(base.isHttps, base.host, base.port, base.encodedPathSegments)
            return withPathAndQuery(start, input, pos, limit, if (keepsQuery) base.encodedQuery else null)
        }

        /** [address] with the path at [pos] resolved onto its own, then the query that follows. */
        private fun withPathAndQuery(
            address: WebAddress,
            input: String,
            pos: Int,
            limit: Int,
            keptQuery: String? = null
        ): WebAddress {
            val pathEnd = input.delimiterOffset("?#", pos, limit)
            val segments = address.encodedPathSegments.toMutableList()
            resolvePath(input, pos, pathEnd, segments)
            val query = if (pathEnd < limit && input[pathEnd] == '?') {
                PercentEncoding.query(input.substring(pathEnd + 1, input.delimiterOffset("#", pathEnd, limit)))
            } else {
                keptQuery
            }
            return WebAddress(address.isHttps, address.host, address.port, segments, query)
        }

        /** The port written in [from, to), or the scheme's when none is; null when it is not one. */
        private fun portOf(input: String, from: Int, to: Int, isHttps: Boolean): Int? =
            if (from < to) {
                input.substring(from, to).toIntOrNull()?.takeIf { it in 1..MAX_PORT }
            } else {
                defaultPort(isHttps)
            }

        private fun defaultPort(isHttps: Boolean): Int = if (isHttps) HTTPS_PORT else HTTP_PORT

        /**
         * OkHttp's `resolvePath` into [segments]: nothing for an empty path, a path from the root for
         * one that starts with a slash, and otherwise in place of the last segment.
         */
        private fun resolvePath(input: String, start: Int, limit: Int, segments: MutableList<String>) {
            if (start == limit) return
            var i = start
            if (input[start] == '/' || input[start] == '\\') {
                segments.clear()
                segments += ""
                i++
            } else {
                segments[segments.lastIndex] = ""
            }
            while (i < limit) {
                val end = input.delimiterOffset("/\\", i, limit)
                val hasTrailingSlash = end < limit
                val segment = PercentEncoding.pathSegment(input.substring(i, end), alreadyEncoded = true)
                push(segments, segment, hasTrailingSlash, encoded = true)
                i = if (hasTrailingSlash) end + 1 else end
            }
        }

        /** OkHttp's `push`: a `.` is skipped, a `..` pops, an empty last segment is replaced. */
        private fun push(
            segments: MutableList<String>,
            segment: String,
            addTrailingSlash: Boolean,
            encoded: Boolean = false
        ) {
            val canonical = if (encoded) segment else PercentEncoding.pathSegment(segment, alreadyEncoded = false)
            when {
                canonical == "." || canonical.equals("%2e", ignoreCase = true) -> Unit
                canonical.isDotDot() -> pop(segments)
                else -> {
                    if (segments.last().isEmpty()) segments[segments.lastIndex] = canonical else segments += canonical
                    if (addTrailingSlash) segments += ""
                }
            }
        }

        private fun pop(segments: MutableList<String>) {
            val removed = segments.removeAt(segments.lastIndex)
            if (removed.isEmpty() && segments.isNotEmpty()) segments[segments.lastIndex] = "" else segments += ""
        }
    }
}

private fun Char.isAsciiWhitespace(): Boolean =
    this == '\t' || this == '\n' || this == '\u000c' || this == '\r' || this == ' '

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isSchemeCharacter(): Boolean =
    isAsciiLetter() || this in '0'..'9' || this == '+' || this == '-' || this == '.'

private fun String.isDotDot(): Boolean =
    this == ".." || equals("%2e.", ignoreCase = true) || equals(".%2e", ignoreCase = true) ||
        equals("%2e%2e", ignoreCase = true)

private fun String.delimiterOffset(delimiters: String, from: Int, limit: Int): Int =
    (from until limit).firstOrNull { this[it] in delimiters } ?: limit

/** The ':' before a port, skipping a bracketed IPv6 literal, or [limit]. */
private fun portColonOffset(input: String, pos: Int, limit: Int): Int {
    var i = pos
    while (i < limit) {
        when (input[i]) {
            '[' -> while (++i < limit) if (input[i] == ']') break
            ':' -> return i
        }
        i++
    }
    return limit
}

/** Whether [input] starts at [pos] with `https:` (true), `http:` (false), or neither (null). */
private fun schemeOf(input: String, pos: Int): Boolean? = when {
    input.startsWith("https:", ignoreCase = true, startIndex = pos) -> true
    input.startsWith("http:", ignoreCase = true, startIndex = pos) -> false
    else -> null
}

/** Whether [input] has a scheme at [pos]: a letter, then letters, digits, `+`, `-` or `.`, then `:`. */
private fun hasScheme(input: String, pos: Int, limit: Int): Boolean {
    if (limit - pos < 2 || !input[pos].isAsciiLetter()) return false
    val end = (pos + 1 until limit).firstOrNull { !input[it].isSchemeCharacter() } ?: return false
    return input[end] == ':'
}
