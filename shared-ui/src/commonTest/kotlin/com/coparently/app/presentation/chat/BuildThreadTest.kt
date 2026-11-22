package com.coparently.app.presentation.chat

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildThreadTest {

    private fun message(id: String, sentAt: Long, activity: ActivityAnnouncement? = null) = Message(
        id = id,
        conversationId = "c1",
        senderId = "them",
        senderName = "Bob",
        content = id,
        sentAtMillis = sentAt,
        messageType = if (activity == null) MessageType.TEXT else MessageType.ACTIVITY,
        activity = activity
    )

    private fun card(kind: ActivityKind) = ActivityAnnouncement(kind, ActivityEntityType.EVENT, "e1", "Dentist")

    private fun bubbles(entries: List<ThreadEntry>) =
        entries.filterIsInstance<ThreadEntry.Bubble>().map { it.message.id }

    @Test
    fun `a card other than a sticker is left out of the thread`() {
        val entries = buildThread(
            listOf(
                message("m1", NOON),
                message("c1", NOON + MINUTE, card(ActivityKind.EVENT_DECLINED)),
                message("s1", NOON + 2 * MINUTE, card(ActivityKind.STICKER_SENT))
            )
        )

        assertEquals(listOf("m1", "s1"), bubbles(entries))
    }

    @Test
    fun `a day that held only cards gets no separator`() {
        val entries = buildThread(
            listOf(
                message("m1", NOON),
                message("c1", NOON + THREE_DAYS, card(ActivityKind.EXPENSE_ADDED)),
                message("m2", NOON + 2 * THREE_DAYS)
            )
        )

        assertEquals(2, entries.count { it is ThreadEntry.DayHeader })
        assertEquals(listOf("m1", "m2"), bubbles(entries))
    }

    @Test
    fun `a hidden card does not split the group around it`() {
        val entries = buildThread(
            listOf(
                message("m1", NOON),
                message("c1", NOON + MINUTE, card(ActivityKind.DAY_SWAP_ACCEPTED)),
                message("m2", NOON + 2 * MINUTE)
            )
        )
        val (first, second) = entries.filterIsInstance<ThreadEntry.Bubble>()

        assertTrue(first.startsGroup && !first.endsGroup)
        assertTrue(!second.startsGroup && second.endsGroup)
    }

    @Test
    fun `a window of cards alone draws nothing`() {
        val entries = buildThread(listOf(message("c1", NOON, card(ActivityKind.SETTLE_UP_OWED))))

        assertTrue(entries.isEmpty())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val THREE_DAYS = 3 * 24 * 60 * MINUTE

        /** 12:00 UTC on 2 November 2026: the same calendar day in every zone from UTC−11 to UTC+11. */
        const val NOON = 1_793_620_800_000L
    }
}
