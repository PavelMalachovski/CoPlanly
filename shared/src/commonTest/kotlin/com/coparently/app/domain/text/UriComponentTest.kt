package com.coparently.app.domain.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [UriComponent] is `android.net.Uri.encode`, as the navigation routes wrote it before they moved to
 * common code. These vectors run on the JVM and on iOS; `UriComponentParityTest`, in the app's
 * Robolectric tests, holds the encoder to `Uri.encode` itself over a corpus and random text.
 */
class UriComponentTest {

    @Test
    fun leavesTheUnreservedCharactersAlone() {
        assertEquals("AZaz09_-!.~'()*", UriComponent.encode("AZaz09_-!.~'()*"))
    }

    @Test
    fun encodesASpaceAsPercentTwenty() {
        assertEquals("a%20b", UriComponent.encode("a b"))
    }

    @Test
    fun encodesTheRouteSeparators() {
        assertEquals("a%2Fb%3Fc%3Dd%26e%23f%25g%2Bh", UriComponent.encode("a/b?c=d&e#f%g+h"))
    }

    @Test
    fun encodesUtf8WithUpperCaseHex() {
        assertEquals("%C4%8D%D0%BB%D0%B5%D1%82%D0%BE", UriComponent.encode("čлето"))
        assertEquals("%F0%9F%98%80", UriComponent.encode("😀"))
    }

    @Test
    fun anUnpairedSurrogateIsAQuestionMark() {
        assertEquals("a%3Fb", UriComponent.encode("a\uD83Db"))
        assertEquals("%3F", UriComponent.encode("\uDE00"))
        assertEquals("%3F%3F", UriComponent.encode("\uDE00\uD83D"))
    }

    @Test
    fun aConversationIdIsUnchanged() {
        assertEquals("alice__bob", UriComponent.encode("alice__bob"))
    }

    @Test
    fun theEmptyTextIsEmpty() {
        assertEquals("", UriComponent.encode(""))
    }
}
