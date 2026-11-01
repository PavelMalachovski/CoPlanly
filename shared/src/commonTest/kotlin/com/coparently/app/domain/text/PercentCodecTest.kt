package com.coparently.app.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [PercentCodec] is `URLEncoder`/`URLDecoder` in UTF-8, as the install referrer and the message
 * report write and read it today. These vectors run on the JVM and on iOS, so the iPhone is held to
 * the JDK's answers (computed with JDK 21's `URLDecoder`) — except for a surrogate written as
 * UTF-8, where decoding follows the Unicode Standard's replacement of each maximal subpart (ruling
 * R11); `PercentCodecParityTest` holds the JVM side to the JDK itself.
 */
class PercentCodecTest {

    @Test
    fun encodesAsAFormBodyDoes() {
        assertEquals("a+b%2B%C4%8D*%7E%25%F0%9F%98%80", PercentCodec.encode("a b+č*~%😀"))
    }

    @Test
    fun leavesTheUnreservedMarksAlone() {
        assertEquals("AZaz09.-*_", PercentCodec.encode("AZaz09.-*_"))
    }

    @Test
    fun encodesCyrillic() {
        assertEquals("%D0%BB%D0%B5%D1%82%D0%BE", PercentCodec.encode("лето"))
    }

    @Test
    fun anUnpairedSurrogateIsAQuestionMark() {
        // String.getBytes(UTF_8), which URLEncoder calls, writes '?' for a surrogate with no partner.
        assertEquals("a%3Fb", PercentCodec.encode("a\uD83Db"))
        assertEquals("%3F", PercentCodec.encode("\uDE00"))
    }

    @Test
    fun decodesAPlusAsASpace() {
        assertEquals("a b", PercentCodec.decode("a+b"))
    }

    @Test
    fun decodesWhatItEncodes() {
        val text = "a b+č*~%😀 лето"
        assertEquals(text, PercentCodec.decode(PercentCodec.encode(text)))
    }

    @Test
    fun decodesLowercaseHex() {
        assertEquals("č", PercentCodec.decode("%c4%8d"))
    }

    @Test
    fun passesLiteralCharactersThrough() {
        assertEquals("pair=ABC~č", PercentCodec.decode("pair=ABC~č"))
    }

    @Test
    fun refusesWhatUrlDecoderRefuses() {
        assertNull(PercentCodec.decode("%ZZ"))
        assertNull(PercentCodec.decode("%4"))
        assertNull(PercentCodec.decode("abc%"))
        assertNull(PercentCodec.decode("%"))
        assertNull(PercentCodec.decode("%A"))
        // Integer.parseInt reads "-1" as -1, and URLDecoder refuses a negative byte.
        assertNull(PercentCodec.decode("%-1"))
        assertNull(PercentCodec.decode("%+"))
    }

    @Test
    fun readsAnEscapeAsIntegerParseIntDoes() {
        // A sign, Unicode decimal digits and fullwidth letters are hex digits to Integer.parseInt.
        assertEquals("\u000F", PercentCodec.decode("%+F"))
        assertEquals("\u0000", PercentCodec.decode("%-0"))
        assertEquals("\u0012", PercentCodec.decode("%\u0661\u0662"))
        assertEquals("\u0012", PercentCodec.decode("%\uFF11\uFF12"))
        assertEquals("\uFFFD", PercentCodec.decode("%\uFF21\uFF22"))
    }

    @Test
    fun replacesMalformedUtf8AsTheJdkDoes() {
        assertEquals("\uFFFD", PercentCodec.decode("%C4"))
        assertEquals("\uFFFD", PercentCodec.decode("%FF"))
        assertEquals("\uFFFD", PercentCodec.decode("%E2%82"))
        assertEquals("\uFFFD", PercentCodec.decode("%F0%9F%98"))
        // A surrogate written as UTF-8 is three maximal subparts, so three replacements: Unicode's
        // practice, where the JDK writes one (ruling R11 of MON-24 follows Unicode).
        assertEquals("\uFFFD\uFFFD\uFFFD", PercentCodec.decode("%ED%A0%80"))
        assertEquals("\uFFFD\uFFFDA", PercentCodec.decode("%ED%A0%41"))
        assertEquals("\uD7FF", PercentCodec.decode("%ED%9F%BF"))
        // An overlong NUL is two.
        assertEquals("\uFFFD\uFFFD", PercentCodec.decode("%C0%80"))
        assertEquals("\uFFFD\uFFFD\uFFFD", PercentCodec.decode("%E0%80%80"))
        assertEquals("\uFFFD\uFFFD\uFFFD\uFFFD", PercentCodec.decode("%F4%90%80%80"))
        assertEquals("\uFFFD\uFFFD\uFFFD\uFFFD", PercentCodec.decode("%F0%80%80%80"))
        assertEquals("a\uFFFDb\u010D", PercentCodec.decode("a%C4b%C4%8D"))
    }
}
