package com.coparently.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.chat.ChatReadState
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.model.Conversation
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Chat between two phones in **two time zones twenty-five hours apart** — the acceptance scenario
 * CLAUDE.md's known issue says was deferred on real devices (CQ-18).
 *
 * Alice's half runs with the default zone set to `Pacific/Kiritimati` (UTC+14) and Bob's with
 * `Pacific/Pago_Pago` (UTC−11), switched around every step each of them takes. So a message is
 * sent on one side of the date line and read on the other, and every derived answer — the unread
 * count, the badge's Room count, the ticks — is computed on the phone whose zone it would be
 * computed in. That is what the epoch-millis move (item 13) was for: had a mark or a send time
 * been a naive local date-time, Bob would have seen Alice's message as sent tomorrow and read
 * as unread forever.
 *
 * What this cannot cover, and the device checklist keeps: the push that tells Bob's phone to look,
 * which needs real FCM, and anything drawn on screen.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentChatTest : TwoParentTest() {

    @Test
    fun aMessageCrossesTheDateLineAsUnreadThenDeliveredThenRead() = runBlocking<Unit> {
        val conversationId = ConversationKey.of(alice.uid, bob.uid)
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = alice.uid,
            senderName = alice.name,
            content = "Pickup moved to 5 pm"
        )

        inZone(ALICE_ZONE) { alice.messageRepository.sendMessage(message) }

        inZone(BOB_ZONE) {
            // Through the app's own listener — `messages` where conversationId ==, by timestamp.
            val received = withTimeout(EmulatorParent.WAIT_MS) {
                bob.messageRepository.observeMessages(conversationId).first { list ->
                    list.any { it.id == message.id }
                }
            }
            assertEquals(message.sentAtMillis, received.single { it.id == message.id }.sentAtMillis)
            assertEquals(1, ChatReadState.unreadCount(received, bob.uid, lastReadAtMillis = null))
            assertEquals(1, bob.messageRepository.observeUnreadCount(conversationId, bob.uid).first())

            bob.messageRepository.markDelivered(conversationId, bob.uid)
        }

        inZone(ALICE_ZONE) {
            val conversation = awaitConversation(alice, conversationId) {
                (it.lastDeliveredAt[bob.uid] ?: Long.MIN_VALUE) >= message.sentAtMillis
            }
            assertEquals(MessageSendStatus.DELIVERED, statusOnAlicesPhone(message, conversation))
        }

        inZone(BOB_ZONE) {
            bob.messageRepository.markRead(conversationId, bob.uid)
            assertEquals(0, bob.messageRepository.observeUnreadCount(conversationId, bob.uid).first())
        }

        inZone(ALICE_ZONE) {
            val conversation = awaitConversation(alice, conversationId) {
                (it.lastReadAt[bob.uid] ?: Long.MIN_VALUE) >= message.sentAtMillis
            }
            assertEquals(MessageSendStatus.READ, statusOnAlicesPhone(message, conversation))
        }
    }

    /** Waits for the conversation, as mirrored into [parent]'s Room, to satisfy [condition]. */
    private suspend fun awaitConversation(
        parent: EmulatorParent,
        conversationId: String,
        condition: (Conversation) -> Boolean
    ): Conversation = withTimeout(EmulatorParent.WAIT_MS) {
        parent.messageRepository.observeConversation(conversationId)
            .first { it != null && condition(it) }!!
    }

    /** The tick Alice's thread would draw under her own copy of [message]. */
    private suspend fun statusOnAlicesPhone(message: Message, conversation: Conversation): MessageSendStatus {
        val stored = alice.messageRepository.observeMessages(message.conversationId).first()
            .single { it.id == message.id }
        return ChatReadState.statusFor(
            message = stored,
            otherUid = bob.uid,
            lastReadAt = conversation.lastReadAt,
            lastDeliveredAt = conversation.lastDeliveredAt
        )
    }

    private companion object {
        /** UTC+14, the first place a new day starts. */
        const val ALICE_ZONE = "Pacific/Kiritimati"

        /** UTC−11, among the last. */
        const val BOB_ZONE = "Pacific/Pago_Pago"
    }
}
