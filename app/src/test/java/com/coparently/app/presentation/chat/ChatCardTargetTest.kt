package com.coparently.app.presentation.chat

import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.activity.ActivityEntityType
import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a tap on a chat bubble leads ([ChatCardTarget.of]).
 *
 * The defect behind it: a card about an event shared the change-request route, so "New event: …"
 * opened the inbox, which found no request for that event and said the request was no longer
 * open. The event itself was never shown.
 */
class ChatCardTargetTest {

    private val message = Message(
        id = "m1",
        conversationId = "a_b",
        senderId = "b",
        senderName = "Bob",
        content = "New event: Dentist"
    )

    /** An announcement as `ActivityAnnouncer` posts it: the payload, and its type and id in `attachments`. */
    private fun card(kind: ActivityKind, entityType: ActivityEntityType, entityId: String = ID) =
        message.copy(
            messageType = MessageType.ACTIVITY,
            attachments = listOf(entityType.name, entityId),
            activity = ActivityAnnouncement(
                kind = kind,
                entityType = entityType,
                entityId = entityId,
                title = "Dentist"
            )
        )

    /** A kind with the entity it is announced about, and where its card leads. */
    private data class Expectation(val entityType: ActivityEntityType, val target: ChatCardTarget?)

    /**
     * Every kind of card the app announces. Written out rather than derived, so the test states
     * the rule instead of repeating the code's `when` — and so a new [ActivityKind] fails
     * `every kind of card has a decided destination` until somebody decides where it goes.
     */
    private val expectations: Map<ActivityKind, Expectation> = mapOf(
        ActivityKind.EVENT_CREATED to Expectation(ActivityEntityType.EVENT, ChatCardTarget.Event(ID)),
        ActivityKind.EVENT_UPDATED to Expectation(ActivityEntityType.EVENT, ChatCardTarget.Event(ID)),
        ActivityKind.EVENT_DELETED to Expectation(ActivityEntityType.EVENT, null),
        ActivityKind.EVENT_ACCEPTED to Expectation(ActivityEntityType.EVENT, ChatCardTarget.Event(ID)),
        ActivityKind.EVENT_DECLINED to Expectation(ActivityEntityType.EVENT, ChatCardTarget.Event(ID)),
        ActivityKind.CHANGE_REQUESTED to
            Expectation(ActivityEntityType.CHANGE_REQUEST, ChatCardTarget.ChangeRequest(ID)),
        ActivityKind.CHANGE_ACCEPTED to
            Expectation(ActivityEntityType.CHANGE_REQUEST, ChatCardTarget.ChangeRequest(ID)),
        ActivityKind.CHANGE_DECLINED to
            Expectation(ActivityEntityType.CHANGE_REQUEST, ChatCardTarget.ChangeRequest(ID)),
        ActivityKind.EXPENSE_ADDED to Expectation(ActivityEntityType.EXPENSE, null),
        ActivityKind.EXPENSE_UPDATED to Expectation(ActivityEntityType.EXPENSE, null),
        ActivityKind.EXPENSE_DELETED to Expectation(ActivityEntityType.EXPENSE, null),
        ActivityKind.DAY_SWAP_OFFERED to Expectation(ActivityEntityType.DAY_SWAP, ChatCardTarget.Inbox),
        ActivityKind.DAY_SWAP_ACCEPTED to Expectation(ActivityEntityType.DAY_SWAP, ChatCardTarget.Inbox),
        ActivityKind.DAY_SWAP_DECLINED to Expectation(ActivityEntityType.DAY_SWAP, ChatCardTarget.Inbox),
        ActivityKind.CUSTODY_PROPOSED to Expectation(ActivityEntityType.CUSTODY_PROPOSAL, null),
        ActivityKind.CUSTODY_ACCEPTED to Expectation(ActivityEntityType.CUSTODY_PROPOSAL, null),
        ActivityKind.CUSTODY_DECLINED to Expectation(ActivityEntityType.CUSTODY_PROPOSAL, null),
        // Announced about all four kinds of request, so one entity type speaks for each here and
        // `a request's last-chance or no-answer card goes where its own cards go` covers the rest.
        ActivityKind.REQUEST_LAST_CHANCE to Expectation(ActivityEntityType.DAY_SWAP, ChatCardTarget.Inbox),
        ActivityKind.REQUEST_UNANSWERED to Expectation(ActivityEntityType.DAY_SWAP, ChatCardTarget.Inbox),
        // A settle-up card shows its payment code in place; a tap leads nowhere.
        ActivityKind.SETTLE_UP_OWED to Expectation(ActivityEntityType.EXPENSE, null),
        ActivityKind.SETTLE_UP_OWING to Expectation(ActivityEntityType.EXPENSE, null),
        // An expense split proposal is a request: its cards open the inbox, where it is answered.
        ActivityKind.SPLIT_PROPOSED to Expectation(ActivityEntityType.SPLIT_PROPOSAL, ChatCardTarget.Inbox),
        ActivityKind.SPLIT_ACCEPTED to Expectation(ActivityEntityType.SPLIT_PROPOSAL, ChatCardTarget.Inbox),
        ActivityKind.SPLIT_DECLINED to Expectation(ActivityEntityType.SPLIT_PROPOSAL, ChatCardTarget.Inbox),
        ActivityKind.SPLIT_WITHDRAWN to Expectation(ActivityEntityType.SPLIT_PROPOSAL, ChatCardTarget.Inbox)
    )

    // ---- the defect -----------------------------------------------------------

    @Test
    fun `a new event card opens the event, not a change request`() {
        assertEquals(
            ChatCardTarget.Event(ID),
            ChatCardTarget.of(card(ActivityKind.EVENT_CREATED, ActivityEntityType.EVENT))
        )
    }

    @Test
    fun `a card about a removed event opens nothing`() {
        assertNull(ChatCardTarget.of(card(ActivityKind.EVENT_DELETED, ActivityEntityType.EVENT)))
    }

    // ---- what stays as it was -------------------------------------------------

    @Test
    fun `a change-request card opens the inbox on the request about its event`() {
        assertEquals(
            ChatCardTarget.ChangeRequest(ID),
            ChatCardTarget.of(card(ActivityKind.CHANGE_REQUESTED, ActivityEntityType.CHANGE_REQUEST))
        )
    }

    @Test
    fun `a day-swap card opens the inbox with nothing highlighted, never the date as an event id`() {
        val swap = card(ActivityKind.DAY_SWAP_OFFERED, ActivityEntityType.DAY_SWAP, entityId = "2026-10-14")

        assertEquals(ChatCardTarget.Inbox, ChatCardTarget.of(swap))
    }

    @Test
    fun `a request's last-chance or no-answer card goes where its own cards go`() {
        // The card carries its request's own entity type, so the tap needs no rule of its own: a
        // swap opens the inbox, a change request the inbox on its event, an event its preview, a
        // custody proposal nothing, and an expense split the inbox.
        val own = mapOf(
            ActivityEntityType.DAY_SWAP to ChatCardTarget.Inbox,
            ActivityEntityType.CHANGE_REQUEST to ChatCardTarget.ChangeRequest(ID),
            ActivityEntityType.EVENT to ChatCardTarget.Event(ID),
            ActivityEntityType.CUSTODY_PROPOSAL to null,
            ActivityEntityType.SPLIT_PROPOSAL to ChatCardTarget.Inbox
        )

        listOf(ActivityKind.REQUEST_LAST_CHANCE, ActivityKind.REQUEST_UNANSWERED).forEach { kind ->
            own.forEach { (type, target) ->
                assertEquals(target, ChatCardTarget.of(card(kind, type)), "$kind about $type")
            }
        }
    }

    @Test
    fun `an older change-request link opens the inbox on the event it carries`() {
        val link = message.copy(messageType = MessageType.EVENT_LINK, attachments = listOf("e7"))

        assertEquals(ChatCardTarget.ChangeRequest("e7"), ChatCardTarget.of(link))
    }

    @Test
    fun `a change-request link that carries no event opens nothing`() {
        assertNull(ChatCardTarget.of(message.copy(messageType = MessageType.EVENT_LINK)))
    }

    @Test
    fun `an ordinary message opens nothing`() {
        assertNull(ChatCardTarget.of(message))
    }

    @Test
    fun `an announcement whose payload could not be read opens nothing`() {
        // `ActivityAnnouncement.fromMap` dropped a payload this build does not understand; the
        // bubble shows the fallback text, and nothing about it says where it would lead.
        val unreadable = message.copy(messageType = MessageType.ACTIVITY, attachments = listOf("EVENT", ID))

        assertNull(ChatCardTarget.of(unreadable))
    }

    // ---- every kind -----------------------------------------------------------

    @Test
    fun `every kind of card has a decided destination`() {
        assertEquals(ActivityKind.entries.toSet(), expectations.keys)
        ActivityKind.entries.forEach { kind ->
            val expected = expectations.getValue(kind)
            assertEquals(expected.target, ChatCardTarget.of(card(kind, expected.entityType)), "$kind")
        }
    }

    @Test
    fun `every entity type is covered by some kind`() {
        assertEquals(ActivityEntityType.entries.toSet(), expectations.values.map { it.entityType }.toSet())
    }

    @Test
    fun `an expense split card opens the inbox, whatever its kind says`() {
        ActivityKind.entries.forEach { kind ->
            assertEquals(
                ChatCardTarget.Inbox,
                ChatCardTarget.of(card(kind, ActivityEntityType.SPLIT_PROPOSAL, entityId = "uA__uB")),
                "$kind"
            )
        }
    }

    @Test
    fun `an expense or custody card opens nothing, whatever its kind says`() {
        listOf(ActivityEntityType.EXPENSE, ActivityEntityType.CUSTODY_PROPOSAL).forEach { type ->
            ActivityKind.entries.forEach { kind ->
                assertNull(ChatCardTarget.of(card(kind, type)), "$type / $kind")
            }
        }
    }

    private companion object {
        const val ID = "e1"
    }
}
