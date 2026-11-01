/*
 * Adapted from OkHttp 4.12.0 (https://github.com/square/okhttp),
 * okhttp/src/main/kotlin/okhttp3/internal/hostnames.kt
 * (Copyright (C) 2012 The Android Open Source Project) and
 * okhttp/src/main/kotlin/okhttp3/HttpUrl.kt (Copyright (C) 2015 Square, Inc.).
 *
 * Licensed under the Apache License, Version 2.0; the licence is in
 * third_party/okhttp/LICENSE.txt.
 *
 * Modified for CoPlanly (MON-24): `toCanonicalHost` and `percentDecode` rewritten in common
 * Kotlin, with the `java.net.IDN.toASCII` step they called written from RFC 3490 and RFC 3491
 * (no JDK code) and Punycode from RFC 3492 (`Punycode.kt`).
 */
package com.coparently.app.data.http

/**
 * A host as OkHttp 4.12's `toCanonicalHost` wrote it over `java.net.IDN.toASCII` (MON-24), for
 * [WebAddress]: percent-decoded, an empty label refused except a final one (the root's dot),
 * lowercased, `ß` written `ss` as IDNA 2003 maps it, a non-ASCII label in Punycode after `xn--`,
 * and refused when a label is longer than 63 or the result holds a character a host may not.
 * IPv6 literals and the rest of IDNA 2003's mapping are not read; see [WebAddress].
 */
internal object HostNames {
    private const val MAX_LABEL_LENGTH = 63
    private const val ASCII_LIMIT = 0x80
    private const val FIRST_PRINTABLE = ' '
    private const val DELETE = '\u007f'
    private const val ACE_PREFIX = "xn--"
    private const val REPLACEMENT_CHARACTER = '�'
    private const val DECIMAL_DIGITS = 10
    private const val ESCAPE_LENGTH = 3
    private const val BITS_PER_HEX_DIGIT = 4

    /** What OkHttp refused in a host once it was ASCII. */
    private const val INVALID_HOST_CHARACTERS = " #%/:?@[\\]"

    /** The separators `java.net.IDN` splits labels on. */
    private val LABEL_SEPARATORS = charArrayOf('.', '。', '．', '｡')

    /** [encoded] (the host as written, percent-escapes and all) canonicalised, or null when OkHttp refused it. */
    fun canonical(encoded: String): String? {
        val host = percentDecode(encoded)
        val labels = host.split(*LABEL_SEPARATORS)
        return when {
            // An IPv6 literal, and a byte that was not UTF-8 (decoded to U+FFFD, which IDNA refuses).
            ':' in host || REPLACEMENT_CHARACTER in host -> null
            labels.size == 2 && labels.all { it.isEmpty() } -> "."
            else ->
                labels
                    .mapIndexed { i, label -> asciiLabel(label, emptyAllowed = i == labels.lastIndex && i > 0) }
                    .takeUnless { null in it }
                    ?.joinToString(".")
                    ?.lowercase()
                    ?.takeUnless { text -> text.any(::isInvalidInHost) }
        }
    }

    private fun isInvalidInHost(c: Char): Boolean = c < FIRST_PRINTABLE || c >= DELETE || c in INVALID_HOST_CHARACTERS

    private fun asciiLabel(label: String, emptyAllowed: Boolean): String? {
        val ascii = when {
            label.isEmpty() -> label.takeIf { emptyAllowed }
            label.all { it.code < ASCII_LIMIT } -> label
            else -> {
                val mapped = label.lowercase().replace("ß", "ss")
                if (mapped.all { it.code < ASCII_LIMIT }) mapped else ACE_PREFIX + Punycode.encode(mapped)
            }
        }
        return ascii?.takeIf { it.length <= MAX_LABEL_LENGTH }
    }

    /** [text] with each `%XX` decoded, as UTF-8; a `%` not followed by two hex digits stays. */
    private fun percentDecode(text: String): String {
        if ('%' !in text) return text
        val bytes = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            if (isEscape(text, i)) {
                bytes += ((text[i + 1].hexValue() shl BITS_PER_HEX_DIGIT) + text[i + 2].hexValue()).toByte()
                i += ESCAPE_LENGTH
            } else {
                val unit = text.unitAt(i)
                unit.encodeToByteArray().forEach { bytes += it }
                i += unit.length
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun isEscape(text: String, i: Int): Boolean =
        text[i] == '%' && i + 2 < text.length && text[i + 1].hexValue() >= 0 && text[i + 2].hexValue() >= 0

    /** An ASCII hex digit's value, or -1: only `0-9`, `a-f` and `A-F`, as OkHttp read them. */
    private fun Char.hexValue(): Int = when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + DECIMAL_DIGITS
        in 'A'..'F' -> this - 'A' + DECIMAL_DIGITS
        else -> -1
    }
}

/** The character at [i], or the surrogate pair starting there, as text. */
internal fun String.unitAt(i: Int): String {
    val pair = this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()
    return if (pair) substring(i, i + 2) else this[i].toString()
}
