package com.coparently.app.pushwording

import platform.Foundation.ISOCurrencyCodes
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.canonicalLocaleIdentifierFromString
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

/**
 * [ReaderClock] on the iPhone: Foundation's dates and money, written as `:shared-ui`'s
 * `ReaderDateText` and `ReaderMoneyText` write them for the screens (a skeleton is a date format
 * template, on the Gregorian calendar; a fixed `HH:mm` or `h:mm a` for a time; the currency style
 * over an `NSDecimalNumber`), in the language [languageTag] names (the app's resolved language,
 * `AppLanguageRule`, which the extension reads from the App Group), in [zone].
 *
 * The two platforms may differ where their locale data differ (design §11 call 2): Android's text
 * is the golden one. What they share — the words, the order of the arguments, the plural chosen —
 * `PushWordingIosTest` holds to Android's.
 *
 * @param languageTag The language the notification is worded in (`cs`, `en-150`, …).
 * @param is24Hour Whether times are written on a 24-hour clock; null for what the language expects.
 * @param zone The reader's zone, which decides the day and hour of an instant.
 */
class FoundationReaderClock(
    languageTag: String,
    is24Hour: Boolean? = null,
    private val zone: NSTimeZone = NSTimeZone.localTimeZone,
) : ReaderClock {

    private val locale = NSLocale(localeIdentifier = NSLocale.canonicalLocaleIdentifierFromString(languageTag))

    private val twentyFourHours: Boolean = is24Hour ?: languageUses24Hour(locale)

    override fun answerBy(millis: Long): String {
        val moment = instant(millis)
        return template(ANSWER_BY_DAY, zone).stringFromDate(moment) + ", " + time(moment)
    }

    override fun dayInSentence(isoDate: String?): String {
        val text = isoDate.orEmpty()
        return wallDate(text)?.let { template(DAY_IN_SENTENCE, utc).stringFromDate(it) } ?: text
    }

    override fun dayOfMillis(millis: Long): String = template(DAY_IN_SENTENCE, zone).stringFromDate(instant(millis))

    override fun swapDays(isoDates: List<String>): String = isoDates.joinToString(", ") { iso ->
        wallDate(iso)?.let { template(SWAP_DAY, utc).stringFromDate(it) } ?: iso
    }

    override fun eventStart(millis: Long): String = time(instant(millis))

    override fun money(amount: String, currency: String): String? {
        if (currency !in currencyCodes) return null
        if (DECIMAL.matchEntire(amount) == null) return null
        return currencyText(amount, currency)
    }

    override fun amount(amount: String?, currency: String?): String {
        val text = amount.orEmpty()
        val match = DECIMAL.matchEntire(text) ?: return text
        return if (currency == null) plainText(match) ?: text else currencyText(text, currency) ?: text
    }

    override fun dayWithWeekday(isoDate: String): String =
        wallDate(isoDate)?.let { template(DAY_WITH_WEEKDAY, utc).stringFromDate(it) } ?: isoDate

    override fun monthOfDay(isoDate: String): String? =
        wallDate(isoDate)?.let { pattern(MONTH_AND_YEAR, utc).stringFromDate(it) }

    override fun monthOfMonth(isoMonth: String): String? {
        val match = ISO_MONTH.matchEntire(isoMonth) ?: return null
        return wallDate("${match.value}-01")?.let { pattern(MONTH_AND_YEAR, utc).stringFromDate(it) }
    }

    override fun mediumDate(millis: Long): String {
        val formatter = NSDateFormatter().apply {
            locale = this@FoundationReaderClock.locale
            calendar = gregorian(zone)
            timeZone = zone
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterNoStyle
        }
        return formatter.stringFromDate(instant(millis))
    }

    private fun time(moment: NSDate): String =
        pattern(if (twentyFourHours) PATTERN_24_HOUR else PATTERN_12_HOUR, zone).stringFromDate(moment)

    /** A formatter for [skeleton] in the reader's locale (Foundation's own order for the fields), in [timeZone]. */
    private fun template(skeleton: String, timeZone: NSTimeZone): NSDateFormatter = NSDateFormatter().apply {
        locale = this@FoundationReaderClock.locale
        calendar = gregorian(timeZone)
        this.timeZone = timeZone
        setLocalizedDateFormatFromTemplate(skeleton)
    }

    /** A formatter for a fixed [format], with the reader's month, weekday and day-period words. */
    private fun pattern(format: String, timeZone: NSTimeZone): NSDateFormatter = NSDateFormatter().apply {
        locale = this@FoundationReaderClock.locale
        calendar = gregorian(timeZone)
        this.timeZone = timeZone
        dateFormat = format
    }

    private fun currencyText(amount: String, currency: String): String? {
        val formatter = NSNumberFormatter().apply {
            locale = this@FoundationReaderClock.locale
            numberStyle = NSNumberFormatterCurrencyStyle
            currencyCode = currency
        }
        val value = NSDecimalNumber(string = amount, locale = NSLocale(localeIdentifier = "en_US_POSIX"))
        return formatter.stringFromNumber(value)
    }

    private companion object {
        const val ANSWER_BY_DAY = "EEEMMMd"
        const val DAY_IN_SENTENCE = "MMMMd"
        const val DAY_WITH_WEEKDAY = "MMMMEEEEd"
        const val SWAP_DAY = "EEEdMMM"
        const val MONTH_AND_YEAR = "LLLL yyyy"
        const val PATTERN_24_HOUR = "HH:mm"
        const val PATTERN_12_HOUR = "h:mm a"
        const val MILLIS_PER_SECOND = 1000.0

        /** What `toBigDecimalOrNull` reads: a sign, digits with at most one point, an exponent. */
        val DECIMAL = Regex("""([+-]?)(\d+\.?\d*|\.\d+)([eE]([+-]?\d+))?""")

        /** The exponent's digits in [DECIMAL]. */
        const val EXPONENT_GROUP = 4

        val ISO_MONTH = Regex("""\d{4}-\d{2}""")

        val utc: NSTimeZone = NSTimeZone.timeZoneWithName("UTC")!!

        val currencyCodes: Set<String> by lazy { NSLocale.ISOCurrencyCodes.mapNotNull { it as? String }.toSet() }

        fun gregorian(timeZone: NSTimeZone): NSCalendar =
            NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { this.timeZone = timeZone }

        fun instant(millis: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(millis / MILLIS_PER_SECOND)

        /** The ISO date [iso] (a real day) as its midnight in UTC, or null. */
        fun wallDate(iso: String): NSDate? {
            if (!isIsoDate(iso)) return null
            val parts = iso.split('-').map { it.toLong() }
            val components = NSDateComponents().apply {
                year = parts[0]
                month = parts[1]
                day = parts[2]
            }
            return gregorian(utc).dateFromComponents(components)
        }

        /** Whether [locale] writes its hours 0–23: its preferred hour pattern is `H` or `k`. */
        fun languageUses24Hour(locale: NSLocale): Boolean {
            val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, locale) ?: return true
            return pattern.contains('H') || pattern.contains('k')
        }

        /**
         * What `BigDecimal(text).toPlainString()` writes, as `ReaderMoneyText` on the iPhone writes
         * it: the unscaled digits without leading zeros, the scale the text gave, zeros appended for
         * a negative scale, a minus sign only before a value that is not zero. Null for a scale that
         * does not fit an `Int`.
         */
        @Suppress("ReturnCount") // an exponent or a scale out of range, as BigDecimal refuses them
        fun plainText(match: MatchResult): String? {
            val exponentText = match.groupValues[EXPONENT_GROUP]
            val exponent = if (exponentText.isEmpty()) 0 else exponentText.toIntOrNull() ?: return null
            val sign = match.groupValues[1]
            val mantissa = match.groupValues[2]
            val point = mantissa.indexOf('.')
            val integer = if (point < 0) mantissa else mantissa.substring(0, point)
            val fraction = if (point < 0) "" else mantissa.substring(point + 1)
            val digits = (integer + fraction).trimStart('0').ifEmpty { "0" }
            val wideScale = fraction.length.toLong() - exponent
            if (wideScale !in Int.MIN_VALUE..Int.MAX_VALUE) return null
            val scale = wideScale.toInt()
            val body = when {
                scale <= 0 -> if (digits == "0") "0" else digits + "0".repeat(-scale)
                digits.length > scale -> digits.substring(0, digits.length - scale) + "." +
                    digits.substring(digits.length - scale)
                else -> "0." + "0".repeat(scale - digits.length) + digits
            }
            return if (sign == "-" && digits != "0") "-$body" else body
        }
    }
}
