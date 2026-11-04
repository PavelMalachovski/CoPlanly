package com.coparently.app.domain.chat

/**
 * A conversation kept for this parent after their co-parent deleted their account (GDPR review,
 * September 2026).
 *
 * `deleteAccount` (`functions/index.js`, `retainOrDeleteConversations`) no longer deletes a thread
 * whose other participant still has an account. It marks the conversation document with the three
 * fields named below and leaves the messages and files in place for 30 days, so the parent who
 * remains can read the thread and export it; a daily sweep deletes it afterwards. Only the server
 * writes the marks — `firestore.rules` refuses them from a client, and refuses any new message in a
 * thread that carries [DEPARTED_UID].
 *
 * The marks are read from the Firestore listener and held in memory, never in Room: the thread is
 * going away, and nothing about it needs to outlive the process.
 *
 * @property conversationId The thread, `ConversationKey.of(me, departedUid)`.
 * @property departedUid The account that was deleted.
 * @property departedName Their display name as it was when they left; blank when they had none.
 * @property retainedUntilMillis When the thread is deleted, epoch millis.
 */
data class DepartedThread(
    val conversationId: String,
    val departedUid: String,
    val departedName: String,
    val retainedUntilMillis: Long
) {
    /** The wire names, and reading one document. */
    companion object {
        /** Epoch millis after which the server deletes the thread. */
        const val RETAINED_UNTIL = "retainedUntilMillis"

        /** The uid whose account was deleted. */
        const val DEPARTED_UID = "departedUid"

        /** The departed parent's display name, copied before their profile was deleted. */
        const val DEPARTED_NAME = "departedName"

        /**
         * The kept thread a conversation document describes, as [myUid] sees it at [nowMillis] —
         * or null when it is an ordinary thread, one [myUid] is not in, one that names [myUid] as
         * the departed parent, or one whose deadline has passed (the sweep simply has not run yet).
         *
         * @param id The document id.
         * @param data The document's fields.
         */
        fun fromDocument(id: String, data: Map<String, Any?>, myUid: String, nowMillis: Long): DepartedThread? {
            val participants = (data["participants"] as? List<*>).orEmpty()
            val departed = (data[DEPARTED_UID] as? String)?.takeIf { it.isNotBlank() && it != myUid }
            val until = (data[RETAINED_UNTIL] as? Number)?.toLong()?.takeIf { it > nowMillis }
            val mine = myUid.isNotBlank() && myUid in participants
            return if (!mine || departed == null || until == null) {
                null
            } else {
                DepartedThread(
                    conversationId = id,
                    departedUid = departed,
                    departedName = (data[DEPARTED_NAME] as? String).orEmpty(),
                    retainedUntilMillis = until
                )
            }
        }
    }
}
