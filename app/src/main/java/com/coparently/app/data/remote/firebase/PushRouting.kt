package com.coparently.app.data.remote.firebase

import androidx.annotation.StringRes
import com.coparently.app.R

/**
 * The screen a push opens when tapped (docs/AUDIT-2026-10-design.md D-13).
 *
 * Most types used to open the launcher and leave the parent to find what the notification was
 * about. The two with links of their own keep them: pairing opens `coplanly://pair` and a chat
 * message its thread. Every other type names one of these, and [PushNotifier] puts it on the
 * launcher intent as [EXTRA]. `MainActivity` reads it through [fromKey], so a value it does not
 * know opens the app as before rather than anywhere else. The activity is exported, and nothing
 * here may do more than pick a screen.
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
    PROFESSIONALS("professionals");

    /** The intent extra, and reading it back. */
    companion object {
        /** The intent extra that carries [key]. */
        const val EXTRA = "coplanly.destination"

        /** The destination named by [key], or null for a value this build does not know. */
        fun fromKey(key: String?): PushDestination? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The notification channel a push posts to, one per kind of news (D-13), so a parent can silence
 * money without silencing a handover. Android lets a person mute a channel, never a single type.
 *
 * @property id The channel id. Stable: the system keeps each channel's settings under it.
 * @property nameRes What system Settings calls the channel.
 * @property descriptionRes What system Settings says the channel carries.
 */
enum class PushChannel(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int
) {
    CHAT("coplanly_chat", R.string.push_channel_chat_name, R.string.push_channel_chat_description),
    SCHEDULE(
        "coplanly_schedule",
        R.string.push_channel_schedule_name,
        R.string.push_channel_schedule_description
    ),
    MONEY("coplanly_money", R.string.push_channel_money_name, R.string.push_channel_money_description),
    FAMILY("coplanly_family", R.string.push_channel_family_name, R.string.push_channel_family_description);

    /** The one channel every push used to post to. */
    companion object {
        /** Deleted when the four channels are created, so Settings lists no dead channel. */
        const val LEGACY_ID = "coparently_notifications"
    }
}

/**
 * Where each push type lands and which channel it posts to.
 *
 * `when` over the type constants, with no `else`, would not compile over strings, so
 * `PushRoutingTest` holds the table complete instead: every type in [PushPayload] has a channel,
 * and every type except pairing and chat, which have links of their own, has a destination.
 */
object PushRouting {

    /** The screen [type] opens, or null for pairing, chat and a type this build does not know. */
    fun destinationOf(type: String?): PushDestination? = when (type) {
        PushPayload.EVENT_CREATED, PushPayload.EVENT_UPDATED, PushPayload.EVENT_DELETED ->
            PushDestination.CALENDAR
        PushPayload.CHILD_INFO_UPDATED -> PushDestination.CHILD_INFO
        PushPayload.CHANGE_REQUEST_CREATED,
        PushPayload.CHANGE_REQUEST_ACCEPTED,
        PushPayload.CHANGE_REQUEST_DECLINED,
        PushPayload.CHANGE_REQUEST_CANCELLED -> PushDestination.CHANGE_REQUESTS
        // Asks are answered on Home, where the pop-up and the inline card wait for this parent;
        // outcomes are read on the grid they changed.
        PushPayload.CUSTODY_PROPOSAL_PROPOSED,
        PushPayload.DAY_SWAP_OFFERED,
        PushPayload.DAY_SWAP_GROUP_OFFERED,
        PushPayload.RECORDS_SHARED -> PushDestination.HOME
        PushPayload.CUSTODY_PROPOSAL_ACCEPTED,
        PushPayload.CUSTODY_PROPOSAL_DECLINED,
        PushPayload.DAY_SWAP_ACCEPTED,
        PushPayload.DAY_SWAP_DECLINED,
        PushPayload.DAY_SWAP_GROUP_ACCEPTED,
        PushPayload.DAY_SWAP_GROUP_DECLINED -> PushDestination.CALENDAR
        PushPayload.SPLIT_RATIO_PROPOSED,
        PushPayload.SPLIT_RATIO_ACCEPTED,
        PushPayload.SPLIT_RATIO_DECLINED,
        PushPayload.SPLIT_RATIO_AGREED -> PushDestination.EXPENSES
        PushPayload.PROFESSIONAL_ACCESS_REQUESTED -> PushDestination.PROFESSIONALS
        else -> null
    }

    /** The channel [type] posts to; the family channel for a type this build does not know. */
    fun channelOf(type: String?): PushChannel = when (type) {
        PushPayload.CHAT_MESSAGE -> PushChannel.CHAT
        PushPayload.EVENT_CREATED,
        PushPayload.EVENT_UPDATED,
        PushPayload.EVENT_DELETED,
        PushPayload.CHANGE_REQUEST_CREATED,
        PushPayload.CHANGE_REQUEST_ACCEPTED,
        PushPayload.CHANGE_REQUEST_DECLINED,
        PushPayload.CHANGE_REQUEST_CANCELLED,
        PushPayload.CUSTODY_PROPOSAL_PROPOSED,
        PushPayload.CUSTODY_PROPOSAL_ACCEPTED,
        PushPayload.CUSTODY_PROPOSAL_DECLINED,
        PushPayload.DAY_SWAP_OFFERED,
        PushPayload.DAY_SWAP_ACCEPTED,
        PushPayload.DAY_SWAP_DECLINED,
        PushPayload.DAY_SWAP_GROUP_OFFERED,
        PushPayload.DAY_SWAP_GROUP_ACCEPTED,
        PushPayload.DAY_SWAP_GROUP_DECLINED -> PushChannel.SCHEDULE
        PushPayload.SPLIT_RATIO_PROPOSED,
        PushPayload.SPLIT_RATIO_ACCEPTED,
        PushPayload.SPLIT_RATIO_DECLINED,
        PushPayload.SPLIT_RATIO_AGREED -> PushChannel.MONEY
        else -> PushChannel.FAMILY
    }
}
