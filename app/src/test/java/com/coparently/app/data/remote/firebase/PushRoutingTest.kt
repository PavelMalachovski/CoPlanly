package com.coparently.app.data.remote.firebase

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where each push lands and which channel it posts to (docs/AUDIT-2026-10-design.md D-13).
 *
 * The table is a `when` over strings, which the compiler cannot hold complete, so this does:
 * every type the app words opens a screen of its own, and every kind of news has its channel.
 */
class PushRoutingTest {

    private val serverTypes = setOf(
        PushPayload.CHAT_MESSAGE,
        PushPayload.PAIRING_ACCEPTED,
        PushPayload.PAIRING_REMOVED,
        PushPayload.PROFESSIONAL_ACCESS_REQUESTED
    )

    @Test
    fun `every worded type opens a screen, except the two with links of their own`() {
        for (type in PushNotifier.PUSH_TEXT.keys) {
            if (type == PushPayload.PAIRING_ACCEPTED || type == PushPayload.PAIRING_REMOVED) {
                assertNull(PushRouting.destinationOf(type), "$type keeps its pairing link")
            } else {
                assertNotNull(PushRouting.destinationOf(type), "$type opens a screen, not the launcher")
            }
        }
        assertNull(PushRouting.destinationOf(PushPayload.CHAT_MESSAGE), "a chat message keeps its thread link")
    }

    @Test
    fun `an ask opens where it is answered, and its outcome where it shows`() {
        assertEquals(PushDestination.HOME, PushRouting.destinationOf(PushPayload.DAY_SWAP_OFFERED))
        assertEquals(PushDestination.HOME, PushRouting.destinationOf(PushPayload.CUSTODY_PROPOSAL_PROPOSED))
        assertEquals(PushDestination.CALENDAR, PushRouting.destinationOf(PushPayload.DAY_SWAP_ACCEPTED))
        assertEquals(PushDestination.CHANGE_REQUESTS, PushRouting.destinationOf(PushPayload.CHANGE_REQUEST_CREATED))
        assertEquals(PushDestination.EXPENSES, PushRouting.destinationOf(PushPayload.SPLIT_RATIO_PROPOSED))
        assertEquals(PushDestination.CALENDAR, PushRouting.destinationOf(PushPayload.EVENT_CREATED))
        assertEquals(
            PushDestination.PROFESSIONALS,
            PushRouting.destinationOf(PushPayload.PROFESSIONAL_ACCESS_REQUESTED)
        )
    }

    @Test
    fun `money, schedule and chat each have their own channel, so one can be muted alone`() {
        assertEquals(PushChannel.CHAT, PushRouting.channelOf(PushPayload.CHAT_MESSAGE))
        for (type in PushPayload.CLIENT_TYPES) {
            val expected = when {
                type.startsWith("split_ratio_") -> PushChannel.MONEY
                type == PushPayload.CHILD_INFO_UPDATED || type == PushPayload.RECORDS_SHARED -> PushChannel.FAMILY
                else -> PushChannel.SCHEDULE
            }
            assertEquals(expected, PushRouting.channelOf(type), type)
        }
        for (type in serverTypes - PushPayload.CHAT_MESSAGE) {
            assertEquals(PushChannel.FAMILY, PushRouting.channelOf(type), type)
        }
    }

    @Test
    fun `a destination survives the intent, and an unknown key opens nothing`() {
        for (destination in PushDestination.entries) {
            assertEquals(destination, PushDestination.fromKey(destination.key))
        }
        assertNull(PushDestination.fromKey("settings"))
        assertNull(PushDestination.fromKey(null))
        assertTrue(PushChannel.entries.map { it.id }.none { it == PushChannel.LEGACY_ID })
    }
}
