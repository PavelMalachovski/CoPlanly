package com.coparently.app.ios.controls

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.timeZoneWithName
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UISheetPresentationControllerDetentIdentifierLarge
import platform.UIKit.UISheetPresentationControllerDetentIdentifierMedium
import platform.UIKit.sheetPresentationController
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iPhone's date and time pickers (MON-24 phase 3, part C, Task 3): a day crosses into UIKit and
 * back as the day the parent saw, in the zones furthest east (UTC+14) and west (UTC−11) of
 * Greenwich and on the European DST days — the iPhone's half of what `PickerDates` guarantees on
 * Android. And a picker sheet ends exactly once, whichever way it ends.
 */
class PickerDatesIosTest {

    private val originalZone = NSTimeZone.defaultTimeZone

    @AfterTest
    fun restoreZone() {
        NSTimeZone.defaultTimeZone = originalZone
    }

    @Test
    fun `a day reads back as itself east and west of Greenwich`() {
        for (zone in ZONES) {
            NSTimeZone.defaultTimeZone = checkNotNull(NSTimeZone.timeZoneWithName(zone)) { zone }
            for (day in DAYS) {
                assertEquals(day, localDateOf(nsDateFor(day)), "$day in $zone")
            }
        }
    }

    @Test
    fun `a time reads back as itself east and west of Greenwich`() {
        for (zone in ZONES) {
            NSTimeZone.defaultTimeZone = checkNotNull(NSTimeZone.timeZoneWithName(zone)) { zone }
            for (time in listOf(LocalTime(0, 0), LocalTime(9, 30), LocalTime(23, 59))) {
                assertEquals(time, localTimeOf(nsDateFor(time)), "$time in $zone")
            }
        }
    }

    @Test
    fun `a picker shows the day it was given`() {
        NSTimeZone.defaultTimeZone = checkNotNull(NSTimeZone.timeZoneWithName(ZONES.first()))
        val day = LocalDate(2026, 11, 1)
        val sheet = dateSheet(day, "Done", "Cancel", onConfirm = {}, onDismiss = {})

        assertEquals(day, localDateOf(sheet.picker.date))
    }

    @Test
    fun `done reports the day then ends - once`() {
        val events = mutableListOf<String>()
        val sheet = dateSheet(
            LocalDate(2026, 3, 29),
            "Done",
            "Cancel",
            onConfirm = { events += "confirm $it" },
            onDismiss = { events += "dismiss" }
        )

        sheet.confirm()
        sheet.dismissed()
        sheet.cancel()

        assertEquals(listOf("confirm 2026-03-29", "dismiss"), events)
        assertTrue(sheet.finished)
    }

    @Test
    fun `a swipe down ends without confirming - once`() {
        val events = mutableListOf<String>()
        val sheet = timeSheet(
            LocalTime(9, 0),
            "Done",
            "Cancel",
            onConfirm = { events += "confirm" },
            onDismiss = { events += "dismiss" }
        )

        sheet.dismissed()
        sheet.dismissed()
        sheet.confirm()

        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun `leaving composition takes the sheet away without calling back`() {
        val events = mutableListOf<String>()
        val sheet = dateSheet(
            LocalDate(2026, 10, 25),
            "Done",
            "Cancel",
            onConfirm = { events += "confirm" },
            onDismiss = { events += "dismiss" }
        )

        sheet.close()
        sheet.dismissed()

        assertEquals(emptyList(), events)
        assertTrue(sheet.finished)
    }

    @Test
    fun `a compact pill shows a day set from outside without calling back`() {
        val picked = mutableListOf<LocalDate>()
        val pill = CompactDatePill { picked += it }
        val today = LocalDate(2026, 11, 15)

        updateCompactDate(pill, value = null, today = today, label = LABEL)
        assertFalse(pill.placeholder.hidden)
        assertEquals(today, localDateOf(pill.picker.date))

        updateCompactDate(pill, value = LocalDate(2026, 11, 20), today = today, label = LABEL)
        assertTrue(pill.placeholder.hidden)
        assertEquals(LocalDate(2026, 11, 20), localDateOf(pill.picker.date))

        assertEquals(emptyList(), picked)
    }

    @Test
    fun `the inline calendar opens full height and the time wheels at medium height`() {
        // A medium sheet is shorter than the inline calendar, and UIKit then lays it out again and
        // again until the app stops answering (iOS 27, November 2026).
        val date = dateSheet(LocalDate(2026, 11, 1), "Done", "Cancel", onConfirm = {}, onDismiss = {})
        val time = timeSheet(LocalTime(9, 0), "Done", "Cancel", onConfirm = {}, onDismiss = {})

        assertEquals(listOf(UISheetPresentationControllerDetentIdentifierLarge), detentsOf(date))
        assertEquals(listOf(UISheetPresentationControllerDetentIdentifierMedium), detentsOf(time))
    }

    private fun detentsOf(sheet: PickerSheet): List<String?> =
        checkNotNull(sheet.navigation.sheetPresentationController).detents
            .map { (it as UISheetPresentationControllerDetent).identifier }

    private companion object {
        const val LABEL = "Starts"
        val ZONES = listOf("Pacific/Kiritimati", "Pacific/Pago_Pago", "Europe/Prague")
        val DAYS = listOf(LocalDate(2026, 11, 1), LocalDate(2026, 3, 29), LocalDate(2026, 10, 25))
    }
}
