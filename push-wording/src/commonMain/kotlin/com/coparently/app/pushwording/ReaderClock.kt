package com.coparently.app.pushwording

/**
 * Every date, time and amount a notification prints, in the reader's language, time zone and
 * 12/24-hour choice. On Android the text is what `java.time` and `currencyFormat` write today (the
 * app's `AndroidReaderClock`, which calls the same functions the screens and the old notification
 * code call); on the iPhone, Foundation's (`FoundationReaderClock`).
 *
 * An ISO date that cannot be read is answered as it came, never dropped: a payload from another
 * build must still say something rather than nothing.
 */
interface ReaderClock {
    /** The deadline of a request, epoch [millis], as the "Answer by …" sentence prints it. */
    fun answerBy(millis: Long): String

    /**
     * A day named inside a sentence ("3 октября"), from its ISO date (`2026-11-17`); null gives "",
     * and text that is not a date is answered as it came.
     */
    fun dayInSentence(isoDate: String?): String

    /** The day of the instant [millis] in the reader's zone, as [dayInSentence] writes a day. */
    fun dayOfMillis(millis: Long): String

    /** The days of a swap, from their ISO dates (each one readable, in order), as one list. */
    fun swapDays(isoDates: List<String>): String

    /** An event's start, the instant [millis], on the reader's clock, as a reminder prints it. */
    fun eventStart(millis: Long): String

    /**
     * [amount] (plain decimal text) in [currency], as a new expense's push prints it; null when the
     * amount is not a number or the currency is not an ISO 4217 code.
     */
    fun money(amount: String, currency: String): String?

    /**
     * A chat card's or a reminder's amount: [amount] in [currency]'s conventions; the plain decimal
     * with no currency; and [amount] as it came (or "") when it is not a number. An unknown currency
     * code keeps the language's own currency rather than failing, as the thread's cards do.
     */
    fun amount(amount: String?, currency: String?): String

    /**
     * A day standing on its own, with its weekday ("Saturday, October 3"), from its ISO date; as it
     * came when unreadable.
     */
    fun dayWithWeekday(isoDate: String): String

    /** The month of an ISO date (`2026-10-01`) as a month and year ("October 2026"); null when unreadable. */
    fun monthOfDay(isoDate: String): String?

    /** An ISO month (`2026-10`) as a month and year ("October 2026"); null when unreadable. */
    fun monthOfMonth(isoMonth: String): String?

    /** The day of the instant [millis] in the reader's zone, in the language's medium date style. */
    fun mediumDate(millis: Long): String
}
