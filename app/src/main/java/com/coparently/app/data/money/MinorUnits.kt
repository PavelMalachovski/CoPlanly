package com.coparently.app.data.money

import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.payment.PaymentQr
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Between the minor units the payment types hold (`Payment`, `SupportTerms`, `PaymentRequest`:
 * 45000 is 450.00, MON-24) and the `BigDecimal` the app's screens format and its readers parse.
 *
 * The readers round half up to two decimals, because that is what every amount they read was
 * turned into before it was written or shown: the amounts used to stay `BigDecimal` until
 * `PaymentQr.amountText` wrote them with `setScale(2, HALF_UP)`. The money documents' rules allow
 * two decimals only, so a stored amount never needs it; a settle-up card's text is not bound by a
 * rule, and an amount typed with more places is refused before it gets here.
 */
object MinorUnits {

    /**
     * [text] as `toBigDecimalOrNull()` reads it, in minor units rounded half up — "450.00" is
     * 45000, "12.345" is 1235 — or null when it is not a number or does not fit a Long.
     *
     * [DecimalText.parseHalfUp], which the money documents in `:shared-data` read their amounts
     * with (MON-24 phase 2, Task 8e), so a screen and a document never read one text two ways;
     * `DecimalTextParityTest` holds it to the `BigDecimal` reading this used to make.
     */
    fun fromText(text: String?): Long? = text?.let { DecimalText.parseHalfUp(it, PaymentQr.MONEY_SCALE) }

    /**
     * [value] in minor units rounded half up, or null when it does not fit a Long.
     *
     * `BigDecimal.longValueExact` (API 1), never `BigInteger.longValueExact`, which is API 31: on
     * API 26–30 it throws `NoSuchMethodError`. Only the overflow is caught, so an error is never
     * read as "no amount".
     */
    fun fromDecimal(value: BigDecimal): Long? = try {
        value.setScale(PaymentQr.MONEY_SCALE, RoundingMode.HALF_UP)
            .movePointRight(PaymentQr.MONEY_SCALE)
            .longValueExact()
    } catch (overflow: ArithmeticException) {
        null
    }

    /** [value] in minor units, read as `BigDecimal.valueOf(value)` and rounded half up. */
    fun fromDouble(value: Double): Long? = fromDecimal(BigDecimal.valueOf(value))

    /** [units] as the `BigDecimal` a `NumberFormat` formats: 45000 is 450.00. */
    fun toDecimal(units: Long): BigDecimal = BigDecimal.valueOf(units, PaymentQr.MONEY_SCALE)
}
