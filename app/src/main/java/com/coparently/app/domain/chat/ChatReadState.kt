package com.coparently.app.domain.chat

import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus

/**
 * Derives everything the chat UI needs from the conversation's two per-user marks:
 * how many messages are unread, and how far each of my own messages has got.
 *
 * Nothing here is stored. One `lastReadAt` write when the thread opens and one
 * `lastDeliveredAt` write when a batch arrives are enough to render both.
 *
 * Every comparison here is epoch millis against epoch millis, in no timezone at all: a mark is
 * written by one parent's device and read on the other's, so an instant is the only thing the
 * two can agree on. See [Message.sentAtMillis].
 */
object ChatReadState {

    /**
     * Number of messages the other parent sent after [lastReadAtMillis].
     *
     * A message whose timestamp equals the mark is read — the mark is written *after*
     * the messages it covers. My own messages never count.
     *
     * @param lastReadAtMillis My mark, or null when I have never opened the thread.
     */
    fun unreadCount(messages: List<Message>, myUid: String, lastReadAtMillis: Long?): Int {
        val mark = lastReadAtMillis ?: Long.MIN_VALUE
        return messages.count { it.senderId != myUid && it.sentAtMillis > mark }
    }

    /**
     * Whether a thread holds something newer than my read mark, answered from the conversation
     * document alone — for a family that is not on screen, whose messages this device does not
     * mirror (M-8), so [unreadCount] has nothing to count.
     *
     * A yes/no, never a number: the document says *that* the thread moved after my mark, not by
     * how many messages. And it does not say who moved it — `lastMessageAt` is bumped by the
     * sender, me included. That is safe in practice because the open thread re-asserts my read
     * mark on every change to its messages, my own sends included, so a message I sent is
     * covered by my mark within the same moment; a mark that never reached the server (offline
     * at send) is the one way my own message can raise the signal, and it clears on the next
     * open.
     *
     * Strictly newer, like [unreadCount]: a mark equal to the newest message covers it.
     *
     * @param lastMessageAtMillis The conversation's `lastMessageAt`, or null when nothing has
     *   been sent in it yet — which is never unread.
     * @param lastReadAtMillis My mark, or null when I have never opened the thread.
     */
    fun hasUnread(lastMessageAtMillis: Long?, lastReadAtMillis: Long?): Boolean {
        val newest = lastMessageAtMillis ?: return false
        return newest > (lastReadAtMillis ?: Long.MIN_VALUE)
    }

    /**
     * The status to render for [message], promoting a successfully sent message to
     * [MessageSendStatus.DELIVERED] or [MessageSendStatus.READ] according to the other
     * parent's marks.
     *
     * Only a [MessageSendStatus.SENT] message is ever promoted: something still sending,
     * or failed, has not reached the server, so no mark can say anything about it.
     */
    fun statusFor(
        message: Message,
        otherUid: String,
        lastReadAt: Map<String, Long>,
        lastDeliveredAt: Map<String, Long>
    ): MessageSendStatus {
        if (message.status != MessageSendStatus.SENT) return message.status
        val sentAt = message.sentAtMillis
        return when {
            (lastReadAt[otherUid] ?: Long.MIN_VALUE) >= sentAt -> MessageSendStatus.READ
            (lastDeliveredAt[otherUid] ?: Long.MIN_VALUE) >= sentAt -> MessageSendStatus.DELIVERED
            else -> MessageSendStatus.SENT
        }
    }

    /**
     * [marks] with [uid]'s mark moved forward to [atMillis] — never backwards.
     *
     * A mark answers "read/delivered up to here", so lowering it would claim a message that
     * has already been accounted for is new again.
     */
    fun advancedMark(marks: Map<String, Long>, uid: String, atMillis: Long): Map<String, Long> =
        marks + (uid to maxOf(marks[uid] ?: Long.MIN_VALUE, atMillis))

    /**
     * Per-uid maximum of a local and a remote mark map.
     *
     * The larger value is always the newer truth, which makes merging safe in both
     * directions: a remote copy that predates the marks, or that has not yet caught up with
     * a write this device just made, cannot pull read state backwards — and neither can a
     * stale local copy hold back what the other parent has since recorded.
     *
     * @param local This device's marks, or null when it holds no row yet.
     * @param remote The marks on the remote document.
     */
    fun mergeMarks(local: Map<String, Long>?, remote: Map<String, Long>): Map<String, Long> {
        val merged = (local ?: emptyMap()).toMutableMap()
        remote.forEach { (uid, mark) ->
            merged[uid] = maxOf(merged[uid] ?: Long.MIN_VALUE, mark)
        }
        return merged
    }
}
