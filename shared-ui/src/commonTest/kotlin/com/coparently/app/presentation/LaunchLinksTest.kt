package com.coparently.app.presentation

import com.coparently.app.data.remote.firebase.PushDestination
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
    fun calendarLinkOpensTheCalendar() {
        assertEquals(LaunchLink.Calendar, LaunchLinks.parse("coplanly://calendar"))
        // Exactly the host, as the other three are compared: another scheme or host is no link.
        assertNull(LaunchLinks.parse("https://calendar"))
        assertNull(LaunchLinks.parse("coplanly://calendars"))
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

    @Test
    fun `a port is not part of the host - as android Uri reads it`() {
        assertEquals(LaunchLink.Pair("4F7K2M"), LaunchLinks.parse("coplanly://pair:80?code=4F7K2M"))
        assertEquals(LaunchLink.Pair("4F7K2M"), LaunchLinks.parse("coplanly://pair:?code=4F7K2M"))
        assertEquals(
            LaunchLink.Chat(familyId = null, conversationId = null),
            LaunchLinks.parse("coplanly://user@chat:443")
        )
    }

    @Test
    fun `a colon not followed by digits alone stays in the host`() {
        assertNull(LaunchLinks.parse("coplanly://pair:x?code=4F7K2M"))
        assertNull(LaunchLinks.parse("coplanly://pair:8a?code=4F7K2M"))
    }

    @Test
    fun `the host is percent-decoded and ends at a backslash`() {
        assertEquals(LaunchLink.Pair("4F7K2M"), LaunchLinks.parse("coplanly://p%61ir?code=4F7K2M"))
        assertEquals(LaunchLink.Pair(""), LaunchLinks.parse("coplanly://pair\\x"))
    }

    @Test
    fun `a push for another uid opens nothing`() {
        val data = mapOf("type" to "expense_added", "targetUserId" to "bob", "familyId" to "alice__bob")

        assertNull(LaunchLinks.forPush(data, signedInUid = "carol"))
    }

    @Test
    fun `a push opens nothing while nobody is signed in`() {
        assertNull(LaunchLinks.forPush(mapOf("type" to "expense_added", "targetUserId" to "bob"), signedInUid = null))
    }

    @Test
    fun `a push with no addressee is the signed-in account's`() {
        assertEquals(
            PushTap(familyId = null, link = null, destination = PushDestination.EXPENSES),
            LaunchLinks.forPush(mapOf("type" to "expense_added"), signedInUid = "bob")
        )
    }

    @Test
    fun `a push names its family beside the screen it opens`() {
        val data = mapOf("type" to "request_reminder", "targetUserId" to "bob", "familyId" to "alice__bob")

        assertEquals(
            PushTap(familyId = "alice__bob", link = null, destination = PushDestination.CHANGE_REQUESTS),
            LaunchLinks.forPush(data, signedInUid = "bob")
        )
    }

    @Test
    fun `a chat push opens its thread and keeps the family out of the link`() {
        val data = mapOf(
            "type" to "chat_message",
            "targetUserId" to "bob",
            "familyId" to "alice__bob",
            "conversationId" to "alice__bob",
        )

        assertEquals(
            PushTap(
                familyId = "alice__bob",
                link = LaunchLink.Chat(familyId = null, conversationId = "alice__bob"),
                destination = null,
            ),
            LaunchLinks.forPush(data, signedInUid = "bob")
        )
    }

    @Test
    fun `a chat card opens Home and keeps its family`() {
        val data = mapOf(
            "type" to "chat_message",
            "familyId" to "alice__bob",
            "conversationId" to "alice__bob",
            "activityKind" to "DAY_SWAP_WITHDRAWN",
        )

        assertEquals(
            PushTap(familyId = "alice__bob", link = null, destination = PushDestination.HOME),
            LaunchLinks.forPush(data, signedInUid = "bob")
        )
    }

    @Test
    fun `a sticker's chat push opens its thread`() {
        val data = mapOf("type" to "chat_message", "conversationId" to "alice__bob", "activityKind" to "STICKER_SENT")

        assertEquals(LaunchLink.Chat(null, "alice__bob"), LaunchLinks.forPush(data, "bob")?.link)
        assertNull(LaunchLinks.forPush(data, "bob")?.destination)
    }

    @Test
    fun `a departed co-parent's notice opens its thread and one with no thread the list`() {
        val notice = mapOf("type" to "coparent_account_deleted", "conversationId" to "alice__bob")
        val bare = mapOf("type" to "chat_message")

        assertEquals(LaunchLink.Chat(null, "alice__bob"), LaunchLinks.forPush(notice, "bob")?.link)
        assertEquals(LaunchLink.Chat(null, null), LaunchLinks.forPush(bare, "bob")?.link)
    }

    @Test
    fun `pairing news opens pairing with nothing prefilled`() {
        for (type in listOf("pairing_accepted", "pairing_removed")) {
            assertEquals(
                PushTap(familyId = null, link = LaunchLink.Pair(""), destination = null),
                LaunchLinks.forPush(mapOf("type" to type), "bob"),
                type
            )
        }
    }

    @Test
    fun `a type this build does not know opens the app alone`() {
        assertEquals(
            PushTap(familyId = "alice__bob", link = null, destination = null),
            LaunchLinks.forPush(mapOf("type" to "from_the_future", "familyId" to "alice__bob"), "bob")
        )
    }

    @Test
    fun `a local reminder opens the screen it names on its family`() {
        val support = mapOf(
            "targetUserId" to "bob",
            "coplanly.destination" to "support",
            "familyId" to "alice__bob",
        )
        val event = mapOf("targetUserId" to "bob", "coplanly.destination" to "calendar")

        assertEquals(
            PushTap(familyId = "alice__bob", link = null, destination = PushDestination.SUPPORT),
            LaunchLinks.forPush(support, "bob")
        )
        assertEquals(PushDestination.CALENDAR, LaunchLinks.forPush(event, "bob")?.destination)
        assertNull(LaunchLinks.forPush(event, "carol"))
    }

    @Test
    fun `a screen named beside a type that names one is not read`() {
        val data = mapOf("type" to "expense_added", "coplanly.destination" to "support")

        assertEquals(PushDestination.EXPENSES, LaunchLinks.forPush(data, "bob")?.destination)
    }
}
