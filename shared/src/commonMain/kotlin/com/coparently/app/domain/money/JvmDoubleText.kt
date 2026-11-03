package com.coparently.app.domain.money

import com.coparently.app.domain.serialization.JvmNumberLayout

/**
 * A double as the JVM's `Double.toString` writes it — "1250.5", "42.0", "1.0E7", "1.0E-4" — on
 * every platform: `"$value"` on Android (MON-24 phase 2, Task 8f).
 *
 * An expense's chat card carries its amount this way ("1250.5 CZK", `ActivityAnnouncement.amount`),
 * and an older build and the export print that text as it stands; another platform may write the
 * same digits as `1e+07`. Only the layout moves, never a digit: on the JVM [of] returns
 * `value.toString()` unchanged, which `DecimalTextParityTest` holds.
 */
object JvmDoubleText {

    /** [value] in the JVM's layout. */
    fun of(value: Double): String = JvmNumberLayout.of(value.toString())
}
