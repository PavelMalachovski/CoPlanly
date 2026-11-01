package com.coparently.app.data.money

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
     */
    fun fromText(text: String?): Long? = text?.toBigDecimalOrNull()?.let(::fromDecimal)

    /** [value] in minor units rounded half up, or null when it does not fit a Long. */
    fun fromDecimal(value: BigDecimal): Long? =
        runCatching { value.setScale(PaymentQr.MONEY_SCALE, RoundingMode.HALF_UP).unscaledValue().longValueExact() }
            .getOrNull()

    /** [value] in minor units, read as `BigDecimal.valueOf(value)` and rounded half up. */
    fun fromDouble(value: Double): Long? = fromDecimal(BigDecimal.valueOf(value))

    /** [units] as the `BigDecimal` a `NumberFormat` formats: 45000 is 450.00. */
    fun toDecimal(units: Long): BigDecimal = BigDecimal.valueOf(units, PaymentQr.MONEY_SCALE)
}
