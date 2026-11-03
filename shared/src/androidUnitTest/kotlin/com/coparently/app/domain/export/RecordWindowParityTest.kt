package com.coparently.app.domain.export

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * [RecordWindow] against the `java.time` calls the export made before its model moved to
 * kotlinx-datetime (MON-24): `from.atStartOfDay(zone)` and `to.plusDays(1).atStartOfDay(zone)`, in
 * epoch milliseconds, every day of 2010–2030 in zones whose day can start in a gap or move by half
 * an hour, and Samoa, which skipped 30 December 2011.
 *
 * A smoke test on the JVM only: there kotlinx-datetime's `atStartOfDayIn` is `java.time`'s
 * `atStartOfDay` itself, so this cannot tell the two apart. What holds the iPhone's own time zone
 * code to `java.time` is `RecordWindowVectorsTest` in commonTest, on literal instants.
 */
class RecordWindowParityTest {

    @Test
    fun `the period starts and ends at the instants java time placed them`() {
        for (id in ZONES) {
            val javaZone = ZoneId.of(id)
            val zone = TimeZone.of(id)
            var day = LocalDate.of(2010, 1, 1)
            while (day.isBefore(LocalDate.of(2031, 1, 1))) {
                val date = day.toKotlinLocalDate()
                assertEquals(
                    day.atStartOfDay(javaZone).toInstant().toEpochMilli(),
                    RecordWindow.startMillis(date, zone),
                    "start of $day in $id"
                )
                assertEquals(
                    day.plusDays(1).atStartOfDay(javaZone).toInstant().toEpochMilli(),
                    RecordWindow.endMillis(date, zone),
                    "end of $day in $id"
                )
                day = day.plusDays(1)
            }
        }
    }

    private companion object {
        val ZONES = listOf(
            "Europe/Prague",
            "America/Havana",
            "America/Santiago",
            "America/Sao_Paulo",
            "Asia/Beirut",
            "Asia/Tehran",
            "Africa/Cairo",
            "Australia/Lord_Howe",
            "Pacific/Apia",
            "Pacific/Kiritimati",
            "UTC"
        )
    }
}
