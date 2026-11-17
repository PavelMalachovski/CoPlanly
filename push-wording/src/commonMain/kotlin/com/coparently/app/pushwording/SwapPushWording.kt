package com.coparently.app.pushwording

/**
 * Which sentence a day-swap offer's push gets on the receiving phone (day swap design, 28 October
 * 2026), decided from the offer's two date fields: the co-parent offers days, asks for days, or
 * proposes days each way. The receiving phone's own strings word it. The days are ISO dates
 * (`2026-11-06`), each a real day, soonest first.
 */
internal sealed interface SwapPushWording {
    /** The sender offers [toYou] to the reader. */
    data class Gives(val toYou: List<String>) : SwapPushWording

    /** The sender asks for the reader's [toSender]. */
    data class Asks(val toSender: List<String>) : SwapPushWording

    /** Days each way: [toYou] to the reader, [toSender] to the sender. */
    data class Both(val toYou: List<String>, val toSender: List<String>) : SwapPushWording
}

/**
 * The wording an offer's push gets, or null when it gets the sentence it always did: not an offer,
 * or an older sender's push with no readable date in either field. An unreadable date is dropped
 * rather than shown, like an unparseable count.
 */
internal fun swapPushWording(type: String?, data: Map<String, String>): SwapPushWording? {
    if (type != PushFields.DAY_SWAP_OFFERED && type != PushFields.DAY_SWAP_GROUP_OFFERED) return null
    val toYou = datesIn(data[PushFields.DATES_TO_YOU])
    val toSender = datesIn(data[PushFields.DATES_TO_SENDER])
    return when {
        toYou.isNotEmpty() && toSender.isNotEmpty() -> SwapPushWording.Both(toYou, toSender)
        toYou.isNotEmpty() -> SwapPushWording.Gives(toYou)
        toSender.isNotEmpty() -> SwapPushWording.Asks(toSender)
        else -> null
    }
}

/**
 * The readable ISO dates of a comma-separated field, soonest first: [isIsoDate] keeps what
 * `java.time`'s `LocalDate.parse` read, and four-digit ISO dates sort as text exactly as the days
 * they name.
 */
private fun datesIn(field: String?): List<String> =
    field.orEmpty().split(',').map { it.trim() }.filter(::isIsoDate).sorted()

/** `yyyy-MM-dd`, four digits of year, the form a swap's days travel in. */
private val ISO_DATE = Regex("""([0-9]{4})-([0-9]{2})-([0-9]{2})""")

/**
 * Whether [text] is an ISO date naming a real day, as `LocalDate.parse` (strict, the ISO calendar)
 * reads one: month 1–12, a day the month has, the 29th of February in a leap year only.
 *
 * A year beyond four digits, which `LocalDate.parse` reads with a sign (`+10000-01-01`), is not
 * read: nothing the app writes carries one, and `firestore.rules` bounds the fields to its dates.
 */
internal fun isIsoDate(text: String): Boolean {
    val match = ISO_DATE.matchEntire(text) ?: return false
    val (yearText, monthText, dayText) = match.destructured
    val year = yearText.toInt()
    val month = monthText.toInt()
    val day = dayText.toInt()
    if (month !in 1..MONTHS) return false
    return day in 1..daysIn(year, month)
}

private const val MONTHS = 12
private const val FEBRUARY = 2
private const val LONG_MONTH = 31
private const val SHORT_MONTH = 30
private const val LEAP_FEBRUARY = 29
private const val FEBRUARY_DAYS = 28
private val SHORT_MONTHS = setOf(4, 6, 9, 11)

private fun daysIn(year: Int, month: Int): Int = when {
    month == FEBRUARY -> if (isLeap(year)) LEAP_FEBRUARY else FEBRUARY_DAYS
    month in SHORT_MONTHS -> SHORT_MONTH
    else -> LONG_MONTH
}

@Suppress("MagicNumber") // the Gregorian leap rule's own numbers
private fun isLeap(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
