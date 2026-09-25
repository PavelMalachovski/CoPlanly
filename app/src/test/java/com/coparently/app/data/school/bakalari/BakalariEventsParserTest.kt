package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.SchoolTestSupport.fixture
import com.coparently.app.domain.school.SchoolEventAudience
import com.coparently.app.domain.school.SchoolStudent
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/3/events` and `/api/3/user` read, and the events a child's calendar takes (MON-8).
 *
 * `events.json` and `user.json` are the samples in `bakalari-api/bakalari-api-v3`'s
 * `moduly/events.md` and `moduly/user.md`, copied verbatim (the user sample without the comment
 * JSON cannot hold). `events_audience.json` is built for these tests in the same shape: one event
 * per audience the owner's rule names.
 */
class BakalariEventsParserTest {

    private val student = SchoolStudent(userUid = "1234/moje_id", fullName = "Příjmení Jméno, X.A", classId = "XL")

    @Test
    fun `the documented sample reads with both its time blocks`() {
        val event = BakalariEventsParser.parse(fixture("events.json")).single()

        assertEquals("TBVQN", event.id)
        assertEquals("Název události", event.title)
        assertNull(event.description, "a blank description with no note is nothing")
        assertEquals(2, event.times.size)
        val (wholeDay, morning) = event.times
        assertTrue(wholeDay.wholeDay)
        assertEquals(LocalDateTime.of(2019, 9, 2, 0, 0), wholeDay.start)
        assertEquals(LocalDateTime.of(2019, 9, 2, 23, 59), wholeDay.end)
        assertEquals("2019-09-02T00:00:00+02:00", wholeDay.startKey)
        assertFalse(morning.wholeDay)
        assertEquals(LocalDateTime.of(2019, 9, 3, 8, 0), morning.start)
        assertEquals(listOf("XS"), event.classIds)
        assertEquals("GQ1DS", event.students.single().id)
    }

    @Test
    fun `the child's own class is in`() {
        assertTrue(isForChild("OWNCL"))
    }

    @Test
    fun `an event naming the child is in, matched by name when the id says nothing`() {
        // The pupil's class is another one; the name has a doubled space.
        assertTrue(isForChild("PUPIL"))
    }

    @Test
    fun `an event naming the child is in, matched by the id in UserUID`() {
        val byId = student.copy(userUid = "9999/GQ1DS", fullName = "Someone Else, X.A", classId = "")
        val event = events().single { it.id == "PUPIL" }

        assertTrue(SchoolEventAudience.isForChild(event, byId))
    }

    @Test
    fun `a school-wide event is in`() {
        assertTrue(isForChild("WHOLE"))
    }

    @Test
    fun `another class's event is skipped`() {
        assertFalse(isForChild("OTHER"))
    }

    @Test
    fun `an event for a set of classes is not taken for a school-wide one`() {
        assertFalse(isForChild("CSETS"))
    }

    @Test
    fun `an event with no id is dropped, and a note stands in for a blank description`() {
        val events = events()

        assertEquals(6, events.size)
        assertEquals("Přinést kalkulačku", events.single { it.id == "PUPIL" }.description)
    }

    @Test
    fun `a parent's login names the child, the class and the rights`() {
        val account = BakalariAccount.parse(fixture("user.json"))

        assertEquals("Příjmení Jméno, X.A", account.displayName)
        assertEquals("Příjmení Jméno", account.student.name)
        assertEquals("XL", account.student.classId)
        assertEquals("1234/moje_id", account.student.userUid)
        assertEquals("X.A", account.className)
        assertEquals("škola", account.schoolName)
        assertTrue(account.canReadTimetable)
        assertTrue(account.canReadEvents)
    }

    @Test
    fun `a login without the modules cannot read either`() {
        val account = BakalariAccount.parse("""{"UserUID":"1/x","FullName":"A B, 1.A","EnabledModules":[]}""")

        assertFalse(account.canReadTimetable)
        assertFalse(account.canReadEvents)
        assertEquals("", account.student.classId)
    }

    private fun events() = BakalariEventsParser.parse(fixture("events_audience.json"))

    private fun isForChild(id: String) = SchoolEventAudience.isForChild(events().single { it.id == id }, student)
}
