package com.coparently.app.data.remote.google

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * An event's times read as google-http-client 1.42.3's `DateTime.parseRfc3339` read them before
 * MON-24. Each expected value is that method's `getValue()` for the same text, printed on the JVM
 * and pasted; a `null` is text it refused with a `NumberFormatException`, which failed the page.
 * The same comparison was run over 300,000 generated texts with no difference.
 */
class GoogleDateTimeTest {

    @Test
    fun `a time is read as google-http-client read it`() {
        for ((text, expected) in VECTORS) {
            if (expected == null) {
                assertFailsWith<IllegalArgumentException>("for [$text]") { GoogleDateTime.epochMillis(text) }
            } else {
                assertEquals(expected, GoogleDateTime.epochMillis(text), "for [$text]")
            }
        }
    }

    private companion object {
        val VECTORS = listOf(
            "2026-05-04T16:00:00+02:00" to 1777903200000L,
            "2026-05-04T14:00:00.000Z" to 1777903200000L,
            "2026-05-04t16:00:00z" to 1777910400000L,
            // No offset is UTC; a date alone is midnight UTC.
            "2026-05-04T16:00:00" to 1777910400000L,
            "2026-05-04" to 1777852800000L,
            // A fraction is cut to the millisecond.
            "2026-05-04T16:00:00.123456789+02:00" to 1777903200123L,
            "2026-05-04T16:00:00-00:30" to 1777912200000L,
            // GregorianCalendar was lenient: fields out of range roll over.
            "2026-02-30" to 1772409600000L,
            "2026-13-45T25:61:61Z" to 1802656921000L,
            "2026-00-00T00:00:00Z" to 1764460800000L,
            "2026-05-04Z" to null,
            "2026-05-04T16:00:00+0200" to null,
            "2026-05-04T16:00" to null,
            "garbage" to null,
            "" to null,
        )
    }
}
