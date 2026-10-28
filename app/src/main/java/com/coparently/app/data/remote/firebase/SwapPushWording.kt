package com.coparently.app.data.remote.firebase

import java.time.LocalDate

/**
 * Which sentence a day-swap offer's push gets on the receiving phone (day swap design, 28 October
 * 2026), decided from the offer's two date fields: the co-parent offers days, asks for days, or
 * proposes days each way. The receiving phone's own strings word it.
 */
internal sealed interface SwapPushWording {
    /** The sender offers [toYou] to the reader. */
    data class Gives(val toYou: List<LocalDate>) : SwapPushWording

    /** The sender asks for the reader's [toSender]. */
    data class Asks(val toSender: List<LocalDate>) : SwapPushWording

    /** Days each way: [toYou] to the reader, [toSender] to the sender. */
    data class Both(val toYou: List<LocalDate>, val toSender: List<LocalDate>) : SwapPushWording
}

/**
 * The wording an offer's push gets, or null when it gets the sentence it always did: not an offer,
 * or an older sender's push with no readable date in either field. An unreadable date is dropped
 * rather than shown, like an unparseable count.
 */
internal fun swapPushWording(type: String?, data: Map<String, String>): SwapPushWording? {
    if (type != PushPayload.DAY_SWAP_OFFERED && type != PushPayload.DAY_SWAP_GROUP_OFFERED) return null
    val toYou = datesIn(data[PushPayload.DATES_TO_YOU])
    val toSender = datesIn(data[PushPayload.DATES_TO_SENDER])
    return when {
        toYou.isNotEmpty() && toSender.isNotEmpty() -> SwapPushWording.Both(toYou, toSender)
        toYou.isNotEmpty() -> SwapPushWording.Gives(toYou)
        toSender.isNotEmpty() -> SwapPushWording.Asks(toSender)
        else -> null
    }
}

/** The readable ISO dates of a comma-separated field, soonest first. */
private fun datesIn(field: String?): List<LocalDate> =
    field.orEmpty().split(',').mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }.sorted()
