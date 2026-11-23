package com.coparently.app.domain.chat

import com.coparently.app.domain.activity.ActivityKind
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageType

/**
 * Whether [message] is drawn in the chat thread (November 2026, owner decision: the chat is the
 * parents' own words).
 *
 * The thread shows what a parent wrote — a message, the files it carries — and a sticker, which
 * stays a chat item. Every other activity card the app posts (an event changed, a request
 * answered, a settle-up, …) is kept in `messages` for the communication record the export prints,
 * and is no longer drawn: what it announces is on Home. A card whose kind this build does not know
 * is a card too, so a message typed [MessageType.ACTIVITY] with no readable payload is hidden.
 *
 * The one rule the thread, the unread counts and the conversation's ordering mark read: the Room
 * count in `MessageDao.observeUnreadCount` is its SQL twin and has to agree with it.
 */
fun isShownInThread(message: Message): Boolean {
    val activity = message.activity
    return if (activity != null) {
        activity.kind == ActivityKind.STICKER_SENT
    } else {
        message.messageType != MessageType.ACTIVITY
    }
}
