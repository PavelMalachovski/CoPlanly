package com.coparently.app.data.remote.firebase

/**
 * The screen a push opens when tapped (docs/AUDIT-2026-10-design.md D-13).
 *
 * Most types used to open the launcher and leave the parent to find what the notification was
 * about. The ones with links of their own keep them: pairing opens `coplanly://pair`, and a chat
 * message and a departed co-parent's notice open their thread. Every other type names one of
 * these, and the app's `PushNotifier` puts it on the launcher intent as [EXTRA]. `MainActivity`
 * reads it through [fromKey], so a value it does not know opens the app as before rather than
 * anywhere else. The activity is exported, and nothing here may do more than pick a screen.
 *
 * In `:shared-data`'s common code since MON-24 phase 2 (Task 12), because the navigation graph
 * that opens the screen moved there; which type names which is [forType], since iPhone phase 4
 * part C (the app's `PushRouting` reads it).
 *
 * @property key The value carried in the intent. Stable: a notification already in the tray
 *   holds it.
 */
enum class PushDestination(val key: String) {
    /** Where an ask waiting on this parent is answered: a proposal, an offered swap. */
    HOME("home"),

    /** An event, or the outcome of a swap or a proposal, on the grid. */
    CALENDAR("calendar"),

    /** The inbox of change requests, where one is answered or its outcome read. */
    CHANGE_REQUESTS("change_requests"),

    /** The split ratio's banners live on the Expenses tab. */
    EXPENSES("expenses"),

    /** The children's records. */
    CHILD_INFO("child_info"),

    /** The professionals screen, where a request for access is consented to. */
    PROFESSIONALS("professionals"),

    /** The family friends screen, where either parent sees who reads the calendar and ends it. */
    FRIENDS("friends"),

    /**
     * The family's child support screen: a local reminder's tap (25 October 2026). No push type
     * leads here — the agreement's news arrives as chat cards.
     */
    SUPPORT("support");

    /** The intent extra, and reading it back. */
    companion object {
        /** The intent extra that carries [key]. */
        const val EXTRA = "coplanly.destination"

        /** The destination named by [key], or null for a value this build does not know. */
        fun fromKey(key: String?): PushDestination? = entries.firstOrNull { it.key == key }

        /**
         * The screen a push of [type] opens when tapped, or null for pairing and chat, a departed
         * co-parent's notice (which opens its thread through the chat link, like a message) and a
         * type this build does not know. One table for both apps (iPhone phase 4 part C): Android's
         * `PushRouting.destinationOf` puts it on the launcher intent, and the iPhone's tap
         * (`IosApp.openPush`) arms it for the shell. `PushRoutingTest` holds it complete.
         */
        fun forType(type: String?): PushDestination? = when (type) {
            PushPayload.EVENT_CREATED,
            PushPayload.EVENT_UPDATED,
            PushPayload.EVENT_DELETED,
            PushPayload.EVENT_IMPORTANT -> CALENDAR
            PushPayload.CHILD_INFO_UPDATED -> CHILD_INFO
            PushPayload.CHANGE_REQUEST_CREATED,
            PushPayload.CHANGE_REQUEST_ACCEPTED,
            PushPayload.CHANGE_REQUEST_DECLINED,
            PushPayload.CHANGE_REQUEST_CANCELLED,
            // A last chance is answered in the Requests inbox, like the request it gives another go.
            PushPayload.REQUEST_LAST_CHANCE,
            // The server's clock pushes (MON-25) say a request closed or lapsed; the inbox shows it.
            PushPayload.REQUEST_DEADLINE_PASSED,
            PushPayload.REQUEST_NO_ANSWER,
            // The server's reminder (phase 4 part C) is answered where the request waits.
            PushPayload.REQUEST_REMINDER -> CHANGE_REQUESTS
            // Asks are answered on Home, where the pop-up and the inline card wait for this parent;
            // outcomes are read on the grid they changed.
            PushPayload.CUSTODY_PROPOSAL_PROPOSED,
            PushPayload.DAY_SWAP_OFFERED,
            PushPayload.DAY_SWAP_GROUP_OFFERED,
            PushPayload.RECORDS_SHARED,
            // A sticker is drawn on Home, where its card waits to be seen.
            PushPayload.STICKER_RECEIVED -> HOME
            PushPayload.CUSTODY_PROPOSAL_ACCEPTED,
            PushPayload.CUSTODY_PROPOSAL_DECLINED,
            PushPayload.DAY_SWAP_ACCEPTED,
            PushPayload.DAY_SWAP_DECLINED,
            PushPayload.DAY_SWAP_GROUP_ACCEPTED,
            PushPayload.DAY_SWAP_GROUP_DECLINED -> CALENDAR
            PushPayload.SPLIT_RATIO_PROPOSED,
            PushPayload.SPLIT_RATIO_ACCEPTED,
            PushPayload.SPLIT_RATIO_DECLINED,
            PushPayload.SPLIT_RATIO_AGREED,
            PushPayload.EXPENSE_ADDED -> EXPENSES
            PushPayload.PROFESSIONAL_ACCESS_REQUESTED -> PROFESSIONALS
            PushPayload.CALENDAR_FRIEND_ACCEPTED -> FRIENDS
            else -> null
        }
    }
}
