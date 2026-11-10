package com.coparently.app.domain.time

import com.coparently.app.domain.custody.CustodyTimestamp
import com.coparently.app.domain.events.EventTimestamp
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The wire time cases that sit at the extremes of the JVM's year range (±999,999,999 and the years
 * `Long` milliseconds reach).
 *
 * They are JVM-only on purpose: kotlinx-datetime documents a much narrower range (roughly ±100,000
 * years) on Kotlin/Native, so on the iOS simulator these values are out of range and would fail for
 * a reason that has nothing to do with the wire format. The app writes nothing near these years;
 * the cases only pin that the JVM implementation still behaves as the `java.time` code it replaced
 * did. Every other wire time case runs on both platforms in `commonTest`.
 */
class WireTimeJvmRangeTest {

    @Test
    fun `the extreme epoch milliseconds are written as ISO_LOCAL_DATE_TIME writes them`() {
        assertEquals("+292278994-08-17T07:12:55.807", EventTimestamp.toWire(Long.MAX_VALUE))
        assertEquals("-292275055-05-16T16:47:04.192", EventTimestamp.toWire(Long.MIN_VALUE))
    }

    @Test
    fun `a time too far from 1970 to be milliseconds fails as toEpochMilli failed`() {
        // Without an offset the old reader fell through to the offset parse, which refused the text;
        // with one, the offset parse succeeded and `toEpochMilli` threw.
        assertFailsWith<IllegalArgumentException> { EventTimestamp.fromWire("+999999999-12-31T23:59:59") }
        assertFailsWith<ArithmeticException> { EventTimestamp.fromWire("+999999999-12-31T23:59:59Z") }
        assertEquals(CustodyTimestamp.UNDATED, CustodyTimestamp.fromWire("+999999999-12-31T23:59:59"))
        assertFailsWith<ArithmeticException> {
            EventTimestamp.ofWallClock(LocalDateTime(999_999_999, 12, 31, 23, 59), TimeZone.UTC)
        }
    }
}
