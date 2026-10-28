package com.coparently.app.data.repository

import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DaySwap
import com.coparently.app.domain.custody.DaySwapGroup
import com.coparently.app.domain.custody.SwapSides
import java.time.LocalDate

/**
 * The parent making a day-swap offer, as the offering phone knows them (day swap design, 28
 * October 2026): the repository holds neither slots nor names, and the push needs both.
 *
 * @property slot Their own parent slot, which decides which way each day goes.
 * @property name Their display name, for the push's sentence on the other phone.
 */
data class SwapOfferer(val slot: String, val name: String) {
    /** The days of [map] named by [dates], split by which way each goes. */
    internal fun sidesOf(map: Map<String, DayOverride>?, dates: List<String>): SwapOffer {
        val swaps = dates.mapNotNull { iso ->
            val day = runCatching { LocalDate.parse(iso) }.getOrNull()
            val entry = map?.get(iso)
            if (day != null && entry != null) DaySwap(day, entry) else null
        }
        return SwapOffer(SwapSides.ofOffer(DaySwapGroup(swaps), slot), name)
    }
}

/** An offer's days each way and its sender's name, for [swapPush]. */
internal data class SwapOffer(val sides: SwapSides, val name: String)

/**
 * The push about a swap of [date]: its type and its date, and for a run of [dayCount] days the
 * count — what the receiving device writes its sentence from, in the reader's language (SEC-3).
 * Null for a withdrawal, which has no push type of its own (adding one takes four places agreeing,
 * CLAUDE.md item 15): its chat card is pushed like any message.
 *
 * An offer also names its days each way, from the addressee's side (day swap design, 28 October
 * 2026): [PushPayload.DATES_TO_YOU] for the offerer's days that would go to them,
 * [PushPayload.DATES_TO_SENDER] for theirs that would go to the offerer, each only when it holds a
 * day. Fields, not types, so an older build ignores them and words the sentence it always did.
 *
 * @param sides Which way each offered day goes, from the offerer's side; null when the caller does
 *   not know (an answer, or an older call site), and then no dates are added.
 * @param actorName Who performed the write, cut to the rule's bound; blank adds nothing.
 */
internal fun swapPush(
    date: String,
    status: DayOverrideStatus,
    dayCount: Int,
    sides: SwapSides? = null,
    actorName: String? = null
): Map<String, String>? {
    val action = when (status) {
        DayOverrideStatus.PENDING -> "offered"
        DayOverrideStatus.ACCEPTED -> "accepted"
        DayOverrideStatus.DECLINED -> "declined"
        DayOverrideStatus.CANCELLED -> return null
    }
    // A run of days gets its own type carrying the count, rather than a date the reader
    // would have to expand into a range. The count is a string like every other payload
    // value: `notification_queue` documents hold strings, and the receiving device parses
    // it — a type it cannot parse composes nothing at all, which is the SEC-3 rule.
    val push = if (dayCount > 1) {
        mutableMapOf(
            PushPayload.TYPE to "day_swap_group_$action",
            PushPayload.DATE to date,
            PushPayload.DAY_COUNT to dayCount.toString()
        )
    } else {
        mutableMapOf(
            PushPayload.TYPE to "day_swap_$action",
            PushPayload.DATE to date
        )
    }
    if (status == DayOverrideStatus.PENDING && sides != null) {
        if (sides.toOther.isNotEmpty()) push[PushPayload.DATES_TO_YOU] = sides.toOther.joinToString(",")
        if (sides.toOfferer.isNotEmpty()) push[PushPayload.DATES_TO_SENDER] = sides.toOfferer.joinToString(",")
    }
    actorName?.takeIf { it.isNotBlank() }?.let { push[PushPayload.ACTOR] = it.take(ACTOR_MAX_LENGTH) }
    return push
}

/** The longest actor name `firestore.rules` admits on a queued push. */
private const val ACTOR_MAX_LENGTH = 100
