/*
 * Adapted from OkHttp 4.12.0 (https://github.com/square/okhttp),
 * okhttp/src/main/kotlin/okhttp3/HttpUrl.kt (Copyright (C) 2015 Square, Inc.) and
 * okhttp/src/main/kotlin/okhttp3/FormBody.kt (Copyright (C) 2014 Square, Inc.; licence in
 * third_party/okhttp/LICENSE.txt), and from
 * google-http-java-client 1.42.3 (https://github.com/googleapis/google-http-java-client),
 * google-http-client/src/main/java/com/google/api/client/util/escape/PercentEscaper.java
 * (Copyright (c) 2010 Google Inc.; licence in third_party/google-http-client-java/LICENSE).
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Modified for CoPlanly (MON-24): the encode sets and the encoding loop the two clients used,
 * rewritten in common Kotlin.
 */
package com.coparently.app.data.http

/**
 * The percent-encodings the app's two HTTP clients wrote before MON-24, each pinned to the library
 * that wrote it, so a request built on Ktor carries the same bytes: a Bakaláři sign-in form as
 * OkHttp 4.12's `FormBody` encoded it, a path segment as its `HttpUrl` did, and a Google Calendar
 * query as google-http-client did.
 */
internal object PercentEncoding {
    private const val HEX = "0123456789ABCDEF"
    private const val ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BYTE_MASK = 0xFF
    private const val LOW_NIBBLE = 0xF
    private const val BITS_PER_HEX_DIGIT = 4
    private const val FIRST_PRINTABLE = ' '
    private const val DELETE = '\u007f'

    /** What OkHttp 4.12's `FormBody` left as it was; everything else is encoded, a space as `%20`. */
    private const val FORM_SAFE = "$ALPHANUMERIC*-._"

    /** google-http-client's `CharEscapers.escapeUriQuery` safe set. */
    private const val GOOGLE_QUERY_SAFE = "$ALPHANUMERIC-_.!~*'()@:\$,;/?"

    /** google-http-client's `CharEscapers.escapeUriPath` safe set, for a calendar id in the path. */
    private const val GOOGLE_PATH_SAFE = "$ALPHANUMERIC-_.!~*'()@:\$&=+,;"

    /** What OkHttp dropped from a path it read: tabs and line breaks. */
    private const val LINE_CHARACTERS = "\t\n\u000c\r"

    /** OkHttp's `QUERY_ENCODE_SET`: what a query read from an address percent-encodes. */
    private const val QUERY_ENCODE_SET = " \"'<>#"

    /** OkHttp's `PATH_SEGMENT_ENCODE_SET`: what a path segment percent-encodes. */
    private const val PATH_SEGMENT_ENCODE_SET = " \"<>^`{}|/\\?#"

    /** [value] as a field of an `application/x-www-form-urlencoded` body, as OkHttp wrote it. */
    fun formField(value: String): String = encode(value) { it in FORM_SAFE }

    /** [value] as a Google API query parameter value. */
    fun googleQuery(value: String): String = encode(value) { it in GOOGLE_QUERY_SAFE }

    /** [value] as one Google API path segment. */
    fun googlePath(value: String): String = encode(value) { it in GOOGLE_PATH_SAFE }

    /**
     * [text] as one path segment, as OkHttp's `canonicalize` wrote it with the path segment set:
     * control characters, non-ASCII and the set's characters percent-encoded as UTF-8, and a `%`
     * encoded too unless [alreadyEncoded] (a path read from an address, whose escapes stay). Tabs
     * and line breaks are dropped from text that was already encoded.
     */
    fun pathSegment(text: String, alreadyEncoded: Boolean): String {
        val dropped = if (alreadyEncoded) text.filterNot { it in LINE_CHARACTERS } else text
        return encode(dropped) { c ->
            c >= FIRST_PRINTABLE && c < DELETE && c !in PATH_SEGMENT_ENCODE_SET && (c != '%' || alreadyEncoded)
        }
    }

    /**
     * [text] as the query of an address read from text (a redirect's `Location`), as OkHttp's
     * `canonicalize` kept it: escapes and `+` as they are, tabs and line breaks dropped, and
     * control characters, non-ASCII and the query set's characters percent-encoded as UTF-8.
     */
    fun query(text: String): String = encode(text.filterNot { it in LINE_CHARACTERS }) { c ->
        c >= FIRST_PRINTABLE && c < DELETE && c !in QUERY_ENCODE_SET
    }

    /** [value] with every character [keep] refuses written as its UTF-8 bytes, each `%XX`. */
    private fun encode(value: String, keep: (Char) -> Boolean): String = buildString {
        var i = 0
        while (i < value.length) {
            val unit = value.unitAt(i)
            if (unit.length == 1 && keep(unit[0])) append(unit[0]) else appendEncoded(unit)
            i += unit.length
        }
    }

    private fun StringBuilder.appendEncoded(text: String) {
        for (byte in text.encodeToByteArray()) {
            val b = byte.toInt() and BYTE_MASK
            append('%').append(HEX[b shr BITS_PER_HEX_DIGIT]).append(HEX[b and LOW_NIBBLE])
        }
    }
}
