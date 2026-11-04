package com.coparently.app.domain.serialization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Prints a JSON tree exactly as Gson's `JsonWriter` printed the same values (MON-24), so that what
 * [StoredJson] writes is byte for byte what the app wrote before it.
 *
 * kotlinx's own printer differs from Gson's in three places that reach stored bytes: it does not
 * escape `<`, `>`, `&`, `=` and `'`, nor U+2028 and U+2029, and on Kotlin/Native a `Double` is
 * printed by the platform rather than as the JVM's `Double.toString`. Everything else — compact
 * output, the short escapes, `\u00XX` in lowercase hex for the other control characters, every
 * other character left as it is — is the same, and is written here the way Gson writes it rather
 * than relying on that.
 */
internal object GsonShapedText {

    /**
     * [element] as text.
     *
     * @param htmlSafe Gson's default for `toJson`; off only for a tree printed with
     *   `JsonElement.toString()`.
     * @param serializeNulls Whether an object member whose value is null is written. Gson leaves it
     *   out unless asked; a null inside an array is always written.
     */
    fun print(element: JsonElement, htmlSafe: Boolean, serializeNulls: Boolean): String =
        buildString { value(element, htmlSafe, serializeNulls) }

    private fun StringBuilder.value(element: JsonElement, htmlSafe: Boolean, serializeNulls: Boolean) {
        when (element) {
            JsonNull -> append("null")
            is JsonPrimitive ->
                if (element.isString) string(element.content, htmlSafe) else append(literal(element.content))
            is JsonObject -> {
                append('{')
                var first = true
                for ((name, member) in element) {
                    if (member === JsonNull && !serializeNulls) continue
                    if (!first) append(',')
                    first = false
                    string(name, htmlSafe)
                    append(':')
                    value(member, htmlSafe, serializeNulls)
                }
                append('}')
            }
            is JsonArray -> {
                append('[')
                element.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    value(item, htmlSafe, serializeNulls)
                }
                append(']')
            }
        }
    }

    /** `true`, `false`, a whole number as it is, a floating-point one in the JVM's layout. */
    private fun literal(content: String): String =
        if (content.any { it == '.' || it == 'e' || it == 'E' }) JvmNumberLayout.of(content) else content

    /** Gson's `JsonWriter.string`, character for character. */
    private fun StringBuilder.string(text: String, htmlSafe: Boolean) {
        append('"')
        for (c in text) {
            val replacement = when {
                c.code < ESCAPED.size -> ESCAPED[c.code] ?: if (htmlSafe) HTML_ESCAPED[c] else null
                c == '\u2028' -> "\\u2028"
                c == '\u2029' -> "\\u2029"
                else -> null
            }
            if (replacement == null) append(c) else append(replacement)
        }
        append('"')
    }

    private const val ASCII = 128
    private const val FIRST_PRINTABLE = 0x20
    private const val HEX = 16
    private const val HEX_DIGITS = 4

    /** Gson's `REPLACEMENT_CHARS`: the control characters, the quote and the backslash. */
    private val ESCAPED: Array<String?> = arrayOfNulls<String>(ASCII).also { table ->
        for (code in 0 until FIRST_PRINTABLE) table[code] = "\\u" + code.toString(HEX).padStart(HEX_DIGITS, '0')
        table['"'.code] = "\\\""
        table['\\'.code] = "\\\\"
        table['\t'.code] = "\\t"
        table['\b'.code] = "\\b"
        table['\n'.code] = "\\n"
        table['\r'.code] = "\\r"
        table['\u000C'.code] = "\\f"
    }

    /** What Gson's `HTML_SAFE_REPLACEMENT_CHARS` adds. */
    private val HTML_ESCAPED: Map<Char, String> = mapOf(
        '<' to "\\u003c",
        '>' to "\\u003e",
        '&' to "\\u0026",
        '=' to "\\u003d",
        '\'' to "\\u0027"
    )
}

/**
 * A floating-point number's text laid out as the JVM's `Double.toString` lays it out, with its
 * digits unchanged (MON-24).
 *
 * The JVM writes `1.0`, `0.001`, `1234567.0`, `1.0E7`, `1.0E-4` and `-0.0`: a plain decimal with at
 * least one digit after the point when 10⁻³ ≤ |x| < 10⁷, otherwise one digit, a point, at least one
 * more digit, `E` and the exponent. Another platform may print the same digits as `1e+10` or
 * `1.0e-05`; this only moves the point and rewrites the exponent, so on the JVM it returns its
 * input unchanged — `StoredJsonVectorsTest` holds the layouts and `GsonParityTest` the identity.
 */
internal object JvmNumberLayout {

    private const val PLAIN_FROM = -3
    private const val PLAIN_UNTIL = 7

    /** [text] in the JVM's layout; text that is not a finite decimal number is returned as it is. */
    fun of(text: String): String {
        val negative = text.startsWith('-')
        val body = text.removePrefix("-").removePrefix("+")
        val exponentAt = body.indexOfFirst { it == 'e' || it == 'E' }
        val mantissa = if (exponentAt < 0) body else body.substring(0, exponentAt)
        val exponent = if (exponentAt < 0) 0 else body.substring(exponentAt + 1).removePrefix("+").toIntOrNull()
        val point = mantissa.indexOf('.')
        val whole = if (point < 0) mantissa else mantissa.substring(0, point)
        val fraction = if (point < 0) "" else mantissa.substring(point + 1)
        if (exponent == null || (whole + fraction).isEmpty() || !(whole + fraction).all { it in '0'..'9' }) return text

        val digits = (whole + fraction).trimStart('0')
        val sign = if (negative) "-" else ""
        if (digits.isEmpty()) return "${sign}0.0"
        // The decimal exponent of the first significant digit.
        val leadingZeros = (whole + fraction).length - digits.length
        val scale = whole.length - leadingZeros - 1 + exponent
        val significant = digits.trimEnd('0')
        val isPlain = scale in PLAIN_FROM until PLAIN_UNTIL
        return sign + if (isPlain) plain(significant, scale) else scientific(significant, scale)
    }

    private fun plain(digits: String, scale: Int): String = if (scale >= 0) {
        val whole = digits.take(scale + 1).padEnd(scale + 1, '0')
        val fraction = digits.drop(scale + 1).ifEmpty { "0" }
        "$whole.$fraction"
    } else {
        "0." + "0".repeat(-scale - 1) + digits
    }

    private fun scientific(digits: String, scale: Int): String =
        digits.first() + "." + digits.drop(1).ifEmpty { "0" } + "E" + scale
}
