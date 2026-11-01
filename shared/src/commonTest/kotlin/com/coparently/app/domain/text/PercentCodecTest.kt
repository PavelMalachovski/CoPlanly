package com.coparently.app.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [PercentCodec] is `URLEncoder`/`URLDecoder` in UTF-8, as the install referrer and the message
 * report write it today. These vectors run on the JVM and on iOS; `PercentCodecParityTest` holds
 * the JVM side to the JDK itself.
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
    fun refusesAMalformedEscape() {
        assertNull(PercentCodec.decode("%ZZ"))
        assertNull(PercentCodec.decode("%4"))
        assertNull(PercentCodec.decode("abc%"))
        assertNull(PercentCodec.decode("%+F"))
    }

    @Test
    fun refusesMalformedUtf8() {
        assertNull(PercentCodec.decode("%C4"))
        assertNull(PercentCodec.decode("%FF"))
        // A lone surrogate written as UTF-8 is not UTF-8.
        assertNull(PercentCodec.decode("%ED%A0%80"))
    }
}
