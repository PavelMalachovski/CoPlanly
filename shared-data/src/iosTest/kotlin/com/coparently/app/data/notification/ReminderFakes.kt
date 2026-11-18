package com.coparently.app.data.notification

import com.coparently.app.pushwording.ReaderClock
import com.coparently.app.pushwording.ReminderWording
import com.coparently.app.pushwording.WordingStrings

/**
 * The system's notification center as the local reminders see it, in memory: a request is pending
 * until [nowMillis] reaches its instant, then shown (kept in [requests], no longer [pending]).
 */
internal class FakeNotificationCenter(var nowMillis: Long = 0) : LocalNotificationCenter {

    /** Every request added and not removed, shown ones included, by id. */
    val requests = LinkedHashMap<String, LocalReminder>()

    /** When true, [pending] throws, as a center that cannot be asked. */
    var failPending = false

    override suspend fun pending(): List<String> {
        check(!failPending) { "The notification center could not be asked" }
        return waiting().map { it.id }
    }

    override fun add(id: String, atMillis: Long, title: String, body: String, data: Map<String, String>) {
        requests[id] = LocalReminder(id, atMillis, title, body, data)
    }

    override fun remove(ids: List<String>) {
        ids.forEach { requests.remove(it) }
    }

    /** The requests still waiting to be shown. */
    fun waiting(): List<LocalReminder> = requests.values.filter { it.atMillis > nowMillis }
}

/** Strings that print their key and arguments, so a test sees which text was chosen with what. */
internal object EchoStrings : WordingStrings {
    override fun string(key: String, vararg args: Any): String = "$key(${args.joinToString("|")})"

    override fun plural(key: String, count: Int, vararg args: Any): String =
        "$key[$count](${args.joinToString("|")})"
}

/** A clock that prints what it was asked, for the reminders' three calls. */
internal object EchoClock : ReaderClock {
    override fun eventStart(millis: Long): String = "start:$millis"

    override fun mediumDate(millis: Long): String = "day:$millis"

    override fun amount(amount: String?, currency: String?): String = "$amount $currency"

    override fun answerBy(millis: Long): String = "by:$millis"

    override fun dayInSentence(isoDate: String?): String = isoDate.orEmpty()

    override fun dayOfMillis(millis: Long): String = "dayOf:$millis"

    override fun swapDays(isoDates: List<String>): String = isoDates.joinToString()

    override fun dayWithWeekday(isoDate: String): String = isoDate

    override fun monthOfDay(isoDate: String): String? = isoDate

    override fun monthOfMonth(isoMonth: String): String? = isoMonth

    override fun money(amount: String, currency: String): String? = "$amount $currency"
}

/** The reminders' wording over [EchoStrings] and [EchoClock]. */
internal fun echoWording(): ReminderWording = ReminderWording(EchoStrings, EchoClock)
