package com.coparently.app.domain.chat

import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus

/**
 * When the thread's "newest messages" button shows, and the count it carries (chat: newest message
 * always in view, November 2026).
 *
 * The thread is a reversed list whose item 0 is the newest message, so how far the reader is from
 * the newest is simply the first visible item's index. A message that arrives while the reader is
 * in history does not move the list; the button says how many arrived and brings them into view.
 * Kept out of the composable so the decision can be tested on every platform.
 */
object NewestButton {

    /** Items the reader may be away from the newest before the button shows: a few messages. */
    const val NEAR_NEWEST = 3

    /**
     * Whether the button shows for a reader whose first visible item is [firstVisibleIndex], item 0
     * being the newest message.
     */
    fun shows(firstVisibleIndex: Int): Boolean = firstVisibleIndex > NEAR_NEWEST

    /**
     * Whether [newest], arriving while the reader is up the list, brings the newest into view: only
     * a message the reader sent **from this phone**, which is still SENDING (or failed, ERROR) when
     * it first appears, since a send always shows what was sent. The reader's own message from
     * another phone arrives already sent and leaves them where they are reading, as a co-parent's
     * message does (without counting on the button, [unseen]).
     */
    fun followsOwnSend(newest: Message, currentUserId: String): Boolean =
        newest.senderId == currentUserId &&
            (newest.status == MessageSendStatus.SENDING || newest.status == MessageSendStatus.ERROR)

    /**
     * How many of the co-parent's messages arrived since the reader left the newest: those before
     * [newestIdWhenLeft] in [messagesNewestFirst] that [currentUserId] did not send. The reader's own
     * messages are never news to them (one sent from another phone included). Zero while the reader
     * is at the newest ([newestIdWhenLeft] null), when nothing arrived since, and when that message
     * is no longer in the window (another conversation on screen), where no count could be honest.
     *
     * @param newestIdWhenLeft The id of the newest message when the reader left it, or null.
     * @param messagesNewestFirst The messages the thread draws, newest first.
     * @param currentUserId The reader's uid.
     */
    fun unseen(newestIdWhenLeft: String?, messagesNewestFirst: List<Message>, currentUserId: String): Int {
        if (newestIdWhenLeft == null) return 0
        val left = messagesNewestFirst.indexOfFirst { it.id == newestIdWhenLeft }
        if (left <= 0) return 0
        return messagesNewestFirst.subList(0, left).count { it.senderId != currentUserId }
    }
}
