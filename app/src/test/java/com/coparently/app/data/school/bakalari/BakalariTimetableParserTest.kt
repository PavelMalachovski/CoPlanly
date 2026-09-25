package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.SchoolTestSupport.fixture
import com.coparently.app.domain.school.SchoolDayType
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/3/timetable/actual` read into school hours (MON-8).
 *
 * `bezny.json` and `prazdniny.json` are Bakaláři's own samples, copied verbatim from
 * `bakalari-api/bakalari-api-v3` (`moduly/rozvrh_priklady/`). `zmeny.json` is built for these
 * tests around the `Change` objects that repository documents in `moduly/timetable.md`, copied
 * verbatim: `Added`, `Canceled` ("Obecná absence třídy"), `Removed`, `Substitution` and
 * `RoomChanged`, plus a string `HourId`, a `Holiday` day that has lessons, an unknown field and a
 * day from another week.
 */
class BakalariTimetableParserTest {

    private val normalWeek = LocalDate.of(2020, 3, 2)
    private val changesWeek = LocalDate.of(2020, 2, 24)

    @Test
    fun `a removed lesson in the middle of the day changes neither arrival nor pickup`() {
        val week = BakalariTimetableParser.parse(fixture("bezny.json"), normalWeek)

        val friday = week.days.single { it.date == LocalDate.of(2020, 3, 6) }
        assertEquals(LocalTime.of(8, 0), friday.firstLessonStart)
        assertEquals(LocalTime.of(14, 20), friday.lastLessonEnd)
        assertEquals(SchoolDayType.WORK_DAY, friday.type)
    }

    @Test
    fun `a normal week has five school days, each from its first to its last lesson`() {
        val week = BakalariTimetableParser.parse(fixture("bezny.json"), normalWeek)

        assertEquals(normalWeek, week.monday)
        assertEquals((2..6).map { LocalDate.of(2020, 3, it) }, week.days.map { it.date })
        assertTrue(week.days.all { it.hasLessons })
        // Tuesday runs to hour 11, 15:20-16:05.
        assertEquals(LocalTime.of(16, 5), week.days[1].lastLessonEnd)
    }

    @Test
    fun `a string HourId joins the hours table, and an added lesson counts`() {
        val monday = parseChanges().days.single { it.date == changesWeek }

        assertEquals(LocalTime.of(8, 0), monday.firstLessonStart)
        assertEquals(LocalTime.of(11, 40), monday.lastLessonEnd)
    }

    @Test
    fun `cancelled lessons are not school hours`() {
        val tuesday = parseChanges().days.single { it.date == changesWeek.plusDays(1) }

        assertEquals(LocalTime.of(10, 55), tuesday.firstLessonStart)
        assertEquals(LocalTime.of(12, 35), tuesday.lastLessonEnd)
    }

    @Test
    fun `a day whose every lesson is cancelled or removed has no school, but is not a day without cells`() {
        val wednesday = parseChanges().days.single { it.date == changesWeek.plusDays(2) }

        assertFalse(wednesday.hasLessons)
        assertTrue(wednesday.hasAtoms)
    }

    @Test
    fun `lessons win over a DayType that says holiday`() {
        val thursday = parseChanges().days.single { it.date == changesWeek.plusDays(3) }

        assertEquals(SchoolDayType.HOLIDAY, thursday.type)
        assertEquals(LocalTime.of(8, 0), thursday.firstLessonStart)
        assertEquals(LocalTime.of(9, 40), thursday.lastLessonEnd)
    }

    @Test
    fun `Hours is the source of truth for times, not Change Time`() {
        // The substitution's Change says "7:45 - 8:30"; hour 3 is 8:00-8:45.
        val friday = parseChanges().days.single { it.date == changesWeek.plusDays(4) }

        assertEquals(LocalTime.of(8, 0), friday.firstLessonStart)
        assertEquals(LocalTime.of(14, 20), friday.lastLessonEnd)
    }

    @Test
    fun `a day outside the requested week is dropped`() {
        val week = parseChanges()

        assertEquals(5, week.days.size)
        assertTrue(week.days.none { it.date == LocalDate.of(2020, 3, 2) })
    }

    @Test
    fun `a week the server clamped to another one yields no days at all`() {
        // Asked for a week in September, the server answered with March's.
        val week = BakalariTimetableParser.parse(fixture("bezny.json"), LocalDate.of(2020, 9, 9))

        assertEquals(LocalDate.of(2020, 9, 7), week.monday)
        assertTrue(week.days.isEmpty())
    }

    @Test
    fun `a holiday week has no lessons and names its days`() {
        val week = BakalariTimetableParser.parse(fixture("prazdniny.json"), LocalDate.of(2020, 7, 8))

        assertEquals(5, week.days.size)
        assertTrue(week.days.none { it.hasAtoms || it.hasLessons })
        val monday = week.days.first()
        assertEquals(SchoolDayType.CELEBRATION, monday.type)
        assertEquals("Mistr Jan Hus", monday.description)
        assertNull(monday.firstLessonStart)
        assertTrue(week.days.drop(1).all { it.type == SchoolDayType.HOLIDAY && it.description.isEmpty() })
    }

    @Test
    fun `an unknown DayType reads as unknown rather than failing`() {
        val json = """{"Hours":[],"Days":[{"Atoms":[],"Date":"2020-03-02T00:00:00+01:00","DayType":"Quarantine"}]}"""

        val day = BakalariTimetableParser.parse(json, normalWeek).days.single()

        assertEquals(SchoolDayType.UNKNOWN, day.type)
        assertFalse(day.hasAtoms)
    }

    @Test
    fun `text that is not a JSON object is malformed`() {
        assertFailsWith<BakalariException.Malformed> {
            BakalariTimetableParser.parse("<html>Service unavailable</html>", normalWeek)
        }
    }

    private fun parseChanges() = BakalariTimetableParser.parse(fixture("zmeny.json"), changesWeek.plusDays(2))
}
