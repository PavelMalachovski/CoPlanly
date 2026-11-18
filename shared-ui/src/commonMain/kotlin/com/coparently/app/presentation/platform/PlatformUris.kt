package com.coparently.app.presentation.platform

/** The mail draft address a phone's mail app opens for [address], with [subject] and [body] filled in. */
fun mailtoUri(address: String, subject: String, body: String): String =
    "mailto:" + percentEncode(address, keep = "@+") +
        "?subject=" + percentEncode(subject) +
        "&body=" + percentEncode(body.replace("\r\n", "\n").replace("\n", "\r\n"))

/**
 * The dialler address for [number]: its digits, and a leading `+`. Null when no digit is left, so
 * nothing is dialled for a text that holds none.
 */
fun telUri(number: String): String? {
    val trimmed = number.trim()
    val digits = trimmed.filter { it in '0'..'9' }
    if (digits.isEmpty()) return null
    return "tel:" + (if (trimmed.startsWith("+")) "+" else "") + digits
}

/**
 * [text] as UTF-8 percent-encoded bytes, keeping the RFC 3986 unreserved characters and those of
 * [keep]. Hex digits are upper case.
 */
private fun percentEncode(text: String, keep: String = ""): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val value = byte.toInt() and BYTE_MASK
        val char = value.toChar()
        if (value < ASCII_LIMIT && (isUnreserved(char) || char in keep)) {
            append(char)
        } else {
            append('%')
            append(HEX[value shr NIBBLE_BITS])
            append(HEX[value and NIBBLE_MASK])
        }
    }
}

private fun isUnreserved(char: Char): Boolean =
    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '.' ||
        char == '_' || char == '~'

private const val HEX = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val ASCII_LIMIT = 0x80
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0F
