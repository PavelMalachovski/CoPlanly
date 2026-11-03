package com.coparently.app.domain.export

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RecordWindow] against literal instants `java.time` gave once (JDK 17, `LocalDate.atStartOfDay(zone)`
 * and `plusDays(1).atStartOfDay(zone)` in epoch milliseconds), so the iOS simulator holds its own
 * time zone code to them: on the JVM kotlinx-datetime calls `java.time` itself, and only these
 * literals can tell an iPhone's export from an Android phone's.
 *
 * The days are the ones a period's edge could fall on differently: a day before and a day whose
 * midnight is in a summer-time gap (Havana, Santiago, Beirut; the day starts at 01:00), Lord Howe's
 * half-hour changes, and Samoa's 30 December 2011, which never happened, so its window is empty.
 */
class RecordWindowVectorsTest {

    private class Vector(val zone: String, val day: LocalDate, val start: Long, val end: Long)

    @Test
    fun theWindowStartsAndEndsWhereJavaTimePlacedIt() {
        for (vector in VECTORS) {
            val zone = TimeZone.of(vector.zone)
            val label = "${vector.day} in ${vector.zone}"
            assertEquals(vector.start, RecordWindow.startMillis(vector.day, zone), "start of $label")
            assertEquals(vector.end, RecordWindow.endMillis(vector.day, zone), "end of $label")
        }
    }

    private companion object {
        val VECTORS = listOf(
            Vector("America/Havana", LocalDate(2016, 3, 12), 1457758800000L, 1457845200000L),
            Vector("America/Havana", LocalDate(2016, 3, 13), 1457845200000L, 1457928000000L),
            Vector("America/Havana", LocalDate(2017, 3, 11), 1489208400000L, 1489294800000L),
            Vector("America/Havana", LocalDate(2017, 3, 12), 1489294800000L, 1489377600000L),
            Vector("America/Santiago", LocalDate(2016, 8, 13), 1471060800000L, 1471147200000L),
            Vector("America/Santiago", LocalDate(2016, 8, 14), 1471147200000L, 1471230000000L),
            Vector("America/Santiago", LocalDate(2017, 8, 12), 1502510400000L, 1502596800000L),
            Vector("America/Santiago", LocalDate(2017, 8, 13), 1502596800000L, 1502679600000L),
            Vector("Asia/Beirut", LocalDate(2016, 3, 26), 1458943200000L, 1459029600000L),
            Vector("Asia/Beirut", LocalDate(2016, 3, 27), 1459029600000L, 1459112400000L),
            Vector("Asia/Beirut", LocalDate(2017, 3, 25), 1490392800000L, 1490479200000L),
            Vector("Asia/Beirut", LocalDate(2017, 3, 26), 1490479200000L, 1490562000000L),
            Vector("Australia/Lord_Howe", LocalDate(2016, 4, 3), 1459602000000L, 1459690200000L),
            Vector("Australia/Lord_Howe", LocalDate(2016, 10, 2), 1475328600000L, 1475413200000L),
            Vector("Pacific/Apia", LocalDate(2011, 12, 28), 1325066400000L, 1325152800000L),
            Vector("Pacific/Apia", LocalDate(2011, 12, 29), 1325152800000L, 1325239200000L),
            Vector("Pacific/Apia", LocalDate(2011, 12, 30), 1325239200000L, 1325239200000L),
            Vector("Pacific/Apia", LocalDate(2011, 12, 31), 1325239200000L, 1325325600000L)
        )
    }
}
