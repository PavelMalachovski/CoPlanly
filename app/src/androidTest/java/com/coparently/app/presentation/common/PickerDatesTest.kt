package com.coparently.app.presentation.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Pins [PickerDates], the one conversion every date picker in the app goes through, on a real
 * device runtime with the default time zone set east and west of Greenwich.
 *
 * The September 2026 audit (`docs/AUDIT-2026-09.md` §1 item 3) found the pickers converting
 * `DatePickerState`'s UTC-midnight millis through `ZoneId.systemDefault()`: east of Greenwich the
 * previous day was highlighted, west of it the previous day was saved. A JVM test in UTC cannot
 * see that bug at all, which is why this runs on the emulator with [TimeZone.setDefault] — the
 * same default `ZoneId.systemDefault()` reads.
 */
@RunWith(AndroidJUnit4::class)
class PickerDatesTest {

    private lateinit var savedZone: TimeZone

    @Before
    fun saveZone() {
        savedZone = TimeZone.getDefault()
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(savedZone)
    }

    @Test
    fun aDayIsItsUtcMidnight_andReadsBackAsTheSameDay_inEveryZone() {
        for (zone in EAST + WEST) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (date in DATES) {
                val millis = PickerDates.toPickerMillis(date)
                // What DatePickerState itself reports for a tapped day: that day's UTC midnight.
                assertEquals("$zone $date", date.toEpochDay() * DAY_MILLIS, millis)
                assertEquals("$zone $date", date, PickerDates.fromPickerMillis(millis))
            }
        }
    }

    /**
     * Proves the zones above are the ones that expose the defect, so the test above is not
     * passing by accident: reading picker millis back through the system zone — the audited bug —
     * gives the previous day west of Greenwich, and a system-zone midnight handed to the picker
     * lands on the previous UTC day east of it.
     */
    @Test
    fun theTestZonesWouldCatchASystemZoneConversion() {
        for (zone in WEST) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (date in DATES) {
                val readInSystemZone = Instant.ofEpochMilli(PickerDates.toPickerMillis(date))
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                assertEquals("$zone $date", date.minusDays(1), readInSystemZone)
            }
        }
        for (zone in EAST) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            for (date in DATES) {
                val systemMidnight = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                assertNotEquals("$zone $date", PickerDates.toPickerMillis(date), systemMidnight)
                assertEquals("$zone $date", date.minusDays(1), PickerDates.fromPickerMillis(systemMidnight))
            }
        }
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L

        /** UTC+1/+2 (Central Europe, where every parent so far lives) and UTC+14. */
        val EAST = listOf("Europe/Prague", "Pacific/Kiritimati")

        /** UTC−8/−7 and UTC−11. */
        val WEST = listOf("America/Los_Angeles", "Pacific/Pago_Pago")

        /** Year ends, a leap day, and both sides' DST switch days, where a midnight can move. */
        val DATES = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 3, 8),
            LocalDate.of(2026, 3, 29),
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2010, 6, 15)
        )
    }
}
