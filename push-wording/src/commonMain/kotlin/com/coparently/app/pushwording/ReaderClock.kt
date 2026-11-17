package com.coparently.app.pushwording

/**
 * Every date, time and amount a notification prints, in the reader's language, time zone and
 * 12/24-hour choice. On Android the text is what `java.time` and `currencyFormat` write today; on
 * the iPhone, Foundation's.
 */
interface ReaderClock {
    /** The deadline of a request, epoch [millis], as the "Answer by …" sentence prints it. */
    fun answerBy(millis: Long): String

    /** A day named inside a sentence, from its ISO date (`2026-11-17`); blank or unreadable gives "". */
    fun dayInSentence(isoDate: String?): String

    /** The day of the instant [millis] in the reader's zone. */
    fun dayOfMillis(millis: Long): String

    /** The days of a swap, from their ISO dates, as one list. */
    fun swapDays(isoDates: List<String>): String

    /** An event's start, the instant [millis], as a reminder prints it. */
    fun eventStart(millis: Long): String

    /** [amount] (plain decimal text) in [currency]; null when either cannot be read. */
    fun money(amount: String, currency: String): String?
}
