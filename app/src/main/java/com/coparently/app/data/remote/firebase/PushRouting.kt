package com.coparently.app.data.remote.firebase

import androidx.annotation.StringRes
import com.coparently.app.R

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
 * and every type except pairing, chat and a co-parent's deleted account, which have links of
 * their own, has a destination.
 */
object PushRouting {

    /**
     * The screen [type] opens, or null for pairing, chat, a departed co-parent's notice (which
     * opens its thread through the chat link, like a message) and a type this build does not know.
     * The table is [PushDestination.forType], in `:shared-data`'s common code since iPhone phase 4
     * part C, so the iPhone's tap opens the same screen.
     */
    fun destinationOf(type: String?): PushDestination? = PushDestination.forType(type)

    /**
     * The channel [type] posts to; the family channel for news about the family itself — pairing,
     * a professional's request, a co-parent's deleted account — and for a type this build does not
     * know.
     */
    fun channelOf(type: String?): PushChannel = when (type) {
        PushPayload.CHAT_MESSAGE -> PushChannel.CHAT
        PushPayload.EVENT_CREATED,
        PushPayload.EVENT_UPDATED,
        PushPayload.EVENT_DELETED,
        PushPayload.EVENT_IMPORTANT,
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
        PushPayload.DAY_SWAP_GROUP_DECLINED,
        PushPayload.REQUEST_LAST_CHANCE,
        PushPayload.REQUEST_DEADLINE_PASSED,
        PushPayload.REQUEST_NO_ANSWER,
        PushPayload.REQUEST_REMINDER -> PushChannel.SCHEDULE
        PushPayload.SPLIT_RATIO_PROPOSED,
        PushPayload.SPLIT_RATIO_ACCEPTED,
        PushPayload.SPLIT_RATIO_DECLINED,
        PushPayload.SPLIT_RATIO_AGREED,
        PushPayload.EXPENSE_ADDED -> PushChannel.MONEY
        else -> PushChannel.FAMILY
    }
}
