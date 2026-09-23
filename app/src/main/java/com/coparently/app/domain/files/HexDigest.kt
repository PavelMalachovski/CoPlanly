package com.coparently.app.domain.files

/**
 * Lowercase hexadecimal, two digits per byte — the form every stored SHA-256 takes (MON-23).
 *
 * Written out rather than `"%02x".format(byte)`: that formats through the default locale, and a
 * digest has to read the same on every phone that ever compares it.
 */
fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt()
        append(HEX_DIGITS[(value shr NIBBLE_BITS) and NIBBLE_MASK])
        append(HEX_DIGITS[value and NIBBLE_MASK])
    }
}

private const val HEX_DIGITS = "0123456789abcdef"
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0F
