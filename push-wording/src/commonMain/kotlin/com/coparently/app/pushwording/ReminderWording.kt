package com.coparently.app.pushwording

/**
 * The wording of the notifications a phone posts on its own, at a time it decides (iPhone phase 4
 * part C, design §11 call 3): an event's reminder, the child support reminder and a request's
 * reminder. Android's `DueReminders`, `SupportReminderWorker` and `RequestNotices` word through it,
 * and the iPhone's local notifications do too, so both phones say the same thing.
 *
 * A name that is missing or blank reads "Your co-parent" in the reader's language, never a gap.
 *
 * @param strings The strings, already in the reader's language.
 * @param clock Dates, times and amounts as the reader writes them.
 */
class ReminderWording(private val strings: WordingStrings, private val clock: ReaderClock) {

    /**
     * An event's reminder: the event's [title] over "Starts at <time>", the start, epoch
     * [startMillis], on the reader's clock.
     */
    fun event(title: String, startMillis: Long): PushText = eventAt(title, clock.eventStart(startMillis))

    /**
     * An event's reminder with its start already written ([startTime]): Android words the time when
     * it arms the alarm and carries the text to the delivery, so an alarm armed by an older build is
     * worded as it always was.
     */
    fun eventAt(title: String, startTime: String): PushText =
        PushText(title, strings.string("reminder_notification_text", startTime))

    /**
     * The payer's reminder of this month's child support: the due day, epoch [dueMillis] (the start
     * of that day in the reader's zone), in the medium date style, and [amount] (plain decimal text)
     * in [currency], to be paid to [payeeName].
     */
    fun support(dueMillis: Long, amount: String, currency: String, payeeName: String?): PushText =
        PushText(
            strings.string("support_reminder_title", clock.mediumDate(dueMillis)),
            strings.string("support_reminder_body", clock.amount(amount, currency), nameOr(payeeName))
        )

    /**
     * The answering parent's reminder that [proposerName] waits for an answer until epoch
     * [answerByMillis].
     */
    fun request(proposerName: String?, answerByMillis: Long): PushText =
        PushText(
            strings.string("request_notice_reminder_title"),
            strings.string("request_notice_reminder_body", nameOr(proposerName), clock.answerBy(answerByMillis))
        )

    private fun nameOr(name: String?): String =
        name?.takeIf { it.isNotBlank() } ?: strings.string("push_actor_fallback")
}
