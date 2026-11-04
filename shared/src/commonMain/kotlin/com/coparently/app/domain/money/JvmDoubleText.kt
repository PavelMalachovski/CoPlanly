package com.coparently.app.domain.money

/**
 * A double as an Android phone's `Double.toString` writes it — "1250.5", "42.0", "1.0E7", "1.0E-4" —
 * on every platform (MON-24 phase 2, Task 8f).
 *
 * An expense's chat card carries its amount this way ("1250.5 CZK", `ActivityAnnouncement.amount`),
 * and an older build and the export print that text as it stands; Kotlin/Native may write the same
 * digits as `1e+07`.
 *
 * **On Android [of] is `value.toString()` itself** ([platformDoubleText]), so the card's text is
 * what the app wrote before, by construction, whatever the runtime's `Double.toString` does. That
 * matters: ART's, like the JDK's before 19, is the older `FloatingDecimal`, which writes a few
 * short-digit values in [0.001, 0.01) with a superfluous trailing zero (JDK-4511638: 2.0E-3 as
 * "0.0020"), where JDK 19 and later write "0.002".
 *
 * **On the iPhone [of] lays the platform's digits out as the JVM does**, through `JvmNumberLayout`:
 * a plain decimal for 10⁻³ ≤ |x| < 10⁷, otherwise `d.dddE±n`, with no trailing zero but the one
 * after the point. That is JDK 19's layout, so for those few values in [0.001, 0.01) an iPhone writes
 * "0.002" where an Android phone writes "0.0020". No stored or wire text depends on it: the card's
 * amount is read only as text, an expense's amount has two decimals at most, and no `StoredJson`
 * model holds a floating-point field.
 */
object JvmDoubleText {

    /** [value] in an Android phone's layout; see the class KDoc for the iPhone's. */
    fun of(value: Double): String = platformDoubleText(value)
}

/** `Double.toString` on Android; the JVM's layout of the platform's digits elsewhere. */
internal expect fun platformDoubleText(value: Double): String
