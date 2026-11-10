package com.coparently.app.data.http

/**
 * RFC 3492 Punycode, the encoding of a non-ASCII host label after `xn--` ("škola" is `kola-f6a`),
 * which `java.net.IDN` applied for OkHttp before MON-24 and which no common library offers.
 */
internal object Punycode {
    private const val BASE = 36
    private const val T_MIN = 1
    private const val T_MAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 0x80
    private const val DIGIT_LETTERS = 26
    private const val HIGH_SURROGATE_START = 0xD800
    private const val LOW_SURROGATE_START = 0xDC00
    private const val SUPPLEMENTARY_START = 0x10000
    private const val SURROGATE_BITS = 10

    /** [label]'s code points as Punycode, without the `xn--` prefix. */
    fun encode(label: String): String {
        val input = codePoints(label)
        val output = StringBuilder()
        input.filter { it < INITIAL_N }.forEach { output.append(it.toChar()) }
        val basicCount = output.length
        var handled = basicCount
        if (basicCount > 0) output.append('-')
        var n = INITIAL_N
        var delta = 0L
        var bias = INITIAL_BIAS
        while (handled < input.size) {
            val m = input.filter { it >= n }.min()
            delta += (m - n).toLong() * (handled + 1)
            n = m
            for (c in input) {
                if (c < n) delta++
                if (c == n) {
                    appendNumber(output, delta, bias)
                    bias = adapt(delta, handled + 1, handled == basicCount)
                    delta = 0
                    handled++
                }
            }
            delta++
            n++
        }
        return output.toString()
    }

    /** [value] as RFC 3492's generalized variable-length integer, with thresholds from [bias]. */
    private fun appendNumber(output: StringBuilder, value: Long, bias: Int) {
        var q = value
        var k = BASE
        while (true) {
            val t = (k - bias).coerceIn(T_MIN, T_MAX)
            if (q < t) break
            output.append(digit(t + ((q - t) % (BASE - t)).toInt()))
            q = (q - t) / (BASE - t)
            k += BASE
        }
        output.append(digit(q.toInt()))
    }

    private fun adapt(delta: Long, points: Int, first: Boolean): Int {
        var d = if (first) delta / DAMP else delta / 2
        d += d / points
        var k = 0
        while (d > ((BASE - T_MIN) * T_MAX) / 2) {
            d /= BASE - T_MIN
            k += BASE
        }
        return (k + (BASE - T_MIN + 1) * d / (d + SKEW)).toInt()
    }

    private fun digit(d: Int): Char = if (d < DIGIT_LETTERS) 'a' + d else '0' + (d - DIGIT_LETTERS)

    private fun codePoints(text: String): List<Int> {
        val points = ArrayList<Int>(text.length)
        var i = 0
        while (i < text.length) {
            val unit = text.unitAt(i)
            points += if (unit.length == 2) {
                ((unit[0].code - HIGH_SURROGATE_START) shl SURROGATE_BITS) +
                    (unit[1].code - LOW_SURROGATE_START) + SUPPLEMENTARY_START
            } else {
                unit[0].code
            }
            i += unit.length
        }
        return points
    }
}
