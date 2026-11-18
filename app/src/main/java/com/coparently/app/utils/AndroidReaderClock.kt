package com.coparently.app.utils

import android.content.Context
import android.text.format.DateFormat
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.pushwording.AndroidWordingStrings
import com.coparently.app.pushwording.PushWording
import com.coparently.app.pushwording.ReaderClock
import com.coparently.app.pushwording.ReminderWording
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * [ReaderClock] on Android (iPhone phase 4 part C): the `java.time` and `NumberFormat` calls the
 * notifications made before their wording moved to `:push-wording` — [answerByText], [localizedDate],
 * [isoDateText], [swapDateList], [shortTime] and [currencyFormat], the same functions the screens
 * call — in [context]'s language, the device's zone and [context]'s 12/24-hour clock
 * ([DateFormat.is24HourFormat]: the device's setting, else what that language expects).
 *
 * It lives in the app, not in the module, because those functions are the screens' too (in
 * `:shared-ui`'s `androidMain` and here), and the module may depend on no other module of ours: one
 * copy of each rule, so a notification and a screen never write a date two ways.
 * `PushWordingGoldenTest` holds the text to what the old code wrote, byte for byte.
 *
 * @param context The context the notification is worded in (`inAppLanguage()` in production).
 */
class AndroidReaderClock(private val context: Context) : ReaderClock {

    private val locale: Locale get() = context.resources.configuration.locales[0]

    private val is24Hour: Boolean get() = DateFormat.is24HourFormat(context)

    override fun answerBy(millis: Long): String = answerByText(millis, locale = locale, is24Hour = is24Hour)

    override fun dayInSentence(isoDate: String?): String = isoDateText(isoDate, DAY_IN_SENTENCE, locale)

    override fun dayOfMillis(millis: Long): String =
        dayOf(millis).format(localizedDate(DAY_IN_SENTENCE, locale))

    override fun swapDays(isoDates: List<String>): String = swapDateList(isoDates.map { LocalDate.parse(it) }, locale)

    override fun eventStart(millis: Long): String = moment(millis).format(shortTime(locale, is24Hour))

    override fun money(amount: String, currency: String): String? {
        val code = runCatching { Currency.getInstance(currency) }.getOrNull() ?: return null
        val value = amount.toBigDecimalOrNull() ?: return null
        return NumberFormat.getCurrencyInstance(locale).apply { this.currency = code }.format(value)
    }

    override fun amount(amount: String?, currency: String?): String {
        val value = amount?.toBigDecimalOrNull() ?: return amount.orEmpty()
        val code = currency ?: return value.toPlainString()
        return currencyFormat(code, locale).format(value)
    }

    override fun dayWithWeekday(isoDate: String): String = isoDateText(isoDate, DAY_WITH_WEEKDAY, locale)

    override fun monthOfDay(isoDate: String): String? =
        runCatching { LocalDate.parse(isoDate) }.getOrNull()?.format(monthAndYear())

    override fun monthOfMonth(isoMonth: String): String? =
        runCatching { YearMonth.parse(isoMonth) }.getOrNull()?.format(monthAndYear())

    override fun mediumDate(millis: Long): String =
        dayOf(millis).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    private fun moment(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    private fun dayOf(millis: Long): LocalDate = moment(millis).toLocalDate()

    /** "October 2026": a month on its own, in its stand-alone form. */
    private fun monthAndYear(): DateTimeFormatter = DateTimeFormatter.ofPattern(MONTH_AND_YEAR, locale)

    private companion object {
        const val MONTH_AND_YEAR = "LLLL yyyy"
    }
}

/** Every push's wording in [this] context's language, on its clock (`PushNotifier`'s). */
fun Context.pushWording(): PushWording = PushWording(AndroidWordingStrings(this), AndroidReaderClock(this))

/** The reminders' wording in [this] context's language, on its clock. */
fun Context.reminderWording(): ReminderWording = ReminderWording(AndroidWordingStrings(this), AndroidReaderClock(this))
