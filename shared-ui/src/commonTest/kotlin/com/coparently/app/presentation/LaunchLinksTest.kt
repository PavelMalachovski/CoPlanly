package com.coparently.app.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchLinksTest {

    @Test
    fun `a pairing link carries its code`() {
        assertEquals(LaunchLink.Pair("4F7K2M"), LaunchLinks.parse("coplanly://pair?code=4F7K2M"))
    }

    @Test
    fun `a pairing link's code is upper-cased`() {
        assertEquals(LaunchLink.Pair("4F7K2M"), LaunchLinks.parse("coplanly://pair?code=4f7k2m"))
    }

    @Test
    fun `a bare pairing link opens pairing with nothing prefilled`() {
        assertEquals(LaunchLink.Pair(""), LaunchLinks.parse("coplanly://pair"))
    }

    @Test
    fun `a pairing link with an invalid code opens pairing with nothing prefilled`() {
        assertEquals(LaunchLink.Pair(""), LaunchLinks.parse("coplanly://pair?code=ABC123"))
    }

    @Test
    fun `a guest link carries its code`() {
        assertEquals(LaunchLink.Guest("4F7K2M"), LaunchLinks.parse("coplanly://guest?code=4F7K2M"))
    }

    @Test
    fun `a guest link with no code is no link`() {
        assertNull(LaunchLinks.parse("coplanly://guest"))
    }

    @Test
    fun `a bare chat link opens the list`() {
        assertEquals(LaunchLink.Chat(familyId = null, conversationId = null), LaunchLinks.parse("coplanly://chat"))
    }

    @Test
    fun `a chat link names its thread`() {
        assertEquals(
            LaunchLink.Chat(familyId = null, conversationId = "alice__bob"),
            LaunchLinks.parse("coplanly://chat?conversationId=alice__bob")
        )
    }

    @Test
    fun `a chat link with a malformed thread opens the list`() {
        assertEquals(
            LaunchLink.Chat(familyId = null, conversationId = null),
            LaunchLinks.parse("coplanly://chat?conversationId=a/b")
        )
    }

    @Test
    fun `a chat link names its family`() {
        assertEquals(
            LaunchLink.Chat(familyId = "alice__carol", conversationId = "alice__carol"),
            LaunchLinks.parse("coplanly://chat?conversationId=alice__carol&familyId=alice__carol")
        )
    }

    @Test
    fun `a chat link's family must have a family id's shape`() {
        assertEquals(
            LaunchLink.Chat(familyId = null, conversationId = null),
            LaunchLinks.parse("coplanly://chat?familyId=single")
        )
    }

    @Test
    fun `the pairing link's web form is not read here`() {
        assertNull(LaunchLinks.parse("https://coparently-a39c9.web.app/pair/4F7K2M"))
    }

    @Test
    fun `an unknown host is no link`() {
        assertNull(LaunchLinks.parse("coplanly://settings"))
    }

    @Test
    fun `another scheme is no link`() {
        assertNull(LaunchLinks.parse("other://pair?code=4F7K2M"))
    }

    @Test
    fun `the host is compared exactly`() {
        assertNull(LaunchLinks.parse("coplanly://PAIR?code=4F7K2M"))
    }

    @Test
    fun `garbage is no link`() {
        assertNull(LaunchLinks.parse("not a link"))
        assertNull(LaunchLinks.parse(""))
        assertNull(LaunchLinks.parse("coplanly:pair?code=4F7K2M"))
        assertNull(LaunchLinks.parse("://pair"))
    }
}
