package com.coparently.app.domain.chat

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

    /** Items the reader may be away from the newest before the button shows: about a screen. */
    const val NEAR_NEWEST = 3

    /**
     * Whether the button shows for a reader whose first visible item is [firstVisibleIndex], item 0
     * being the newest message.
     */
    fun shows(firstVisibleIndex: Int): Boolean = firstVisibleIndex > NEAR_NEWEST

    /**
     * How many messages arrived since the reader left the newest: those before [newestIdWhenLeft]
     * in [messagesNewestFirst]. Zero while the reader is at the newest ([newestIdWhenLeft] null),
     * when nothing arrived since, and when that message is no longer in the window (another
     * conversation on screen), where no count could be honest.
     *
     * @param newestIdWhenLeft The id of the newest message when the reader left it, or null.
     * @param messagesNewestFirst The ids of the messages the thread draws, newest first.
     */
    fun unseen(newestIdWhenLeft: String?, messagesNewestFirst: List<String>): Int {
        if (newestIdWhenLeft == null) return 0
        return messagesNewestFirst.indexOf(newestIdWhenLeft).coerceAtLeast(0)
    }
}
