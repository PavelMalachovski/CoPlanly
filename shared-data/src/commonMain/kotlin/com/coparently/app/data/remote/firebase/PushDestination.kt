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
 * that opens the screen moved there; the app's `PushRouting` decides which type names which.
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
    }
}
