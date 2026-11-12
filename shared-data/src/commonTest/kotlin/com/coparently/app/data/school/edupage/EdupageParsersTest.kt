package com.coparently.app.data.school.edupage

import com.coparently.app.domain.school.SchoolDayType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdupageParsersTest {

    // Inferred from the edupage-api 0.13.3 protocol description; not yet recorded live.
    private val parentHome = """
        {"userid":"Rodic1","dp":{"year":2025},
         "dbi":{"classes":{"7":{"id":"7","short":"5.A"}},
                "students":{"11":{"id":"11","firstname":"Eva","lastname":"Nováková","classid":"7"},
                            "12":{"id":"12","firstname":"Jan","lastname":"Novák","classid":"7"}}}}
    """.trimIndent()

    private val studentHome = """
        {"userid":"Student11","dp":{"year":2025},
         "dbi":{"classes":{"7":{"id":"7","short":"5.A"}},
                "students":{"11":{"firstname":"Eva","lastname":"Nováková","classid":"7"}}}}
    """.trimIndent()

    private val child = EdupageChild("11", "Eva Nováková", "5.A")

    private fun home(text: String) = Json.parseToJsonElement(text) as JsonObject

    private fun item(
        typ: String,
        meno: String,
        cas: String = "2025-10-20 00:00:00",
        text: String = "Text",
        data: String = "{}",
        removed: String = "0"
    ) = """{"timelineid":"t-$typ-$meno","typ":"$typ","text":"$text","cas_udalosti":"$cas",
        "user_meno":"$meno","removed":"$removed","data":${JsonPrimitive(data)}}"""

    private fun timeline(vararg items: String) = """{"timelineItems":[${items.joinToString(",")}]}"""

    private fun lesson(date: String, start: String, end: String, removed: Boolean = false) =
        """{"type":"lesson","date":"$date","starttime":"$start","endtime":"$end","removed":$removed}"""

    private fun timetable(vararg items: String) = """{"r":{"ttitems":[${items.joinToString(",")}]}}"""

    @Test
    fun `a parent account lists its children with their classes`() {
        val account = EdupageAccountParser.parse(home(parentHome))
        assertTrue(account.isParent)
        assertEquals(2025, account.year)
        assertEquals(listOf(child, EdupageChild("12", "Jan Novák", "5.A")), account.children)
    }

    @Test
    fun `a student account is its own single child`() {
        val account = EdupageAccountParser.parse(home(studentHome))
        assertTrue(!account.isParent)
        assertEquals(listOf(child), account.children)
    }

    @Test
    fun `a school day runs from the first lesson to the last`() {
        val json = timetable(
            lesson("2025-10-20", "10:00", "10:45"),
            lesson("2025-10-20", "08:00", "08:45"),
            lesson("2025-10-20", "12:45", "13:30")
        )
        val day = EdupageTimetableParser.weeks(json, LocalDate(2025, 10, 20), LocalDate(2025, 10, 20))
            .single().days.single()
        assertEquals(LocalTime(8, 0), day.firstLessonStart)
        assertEquals(LocalTime(13, 30), day.lastLessonEnd)
        assertEquals(SchoolDayType.WORK_DAY, day.type)
    }

    @Test
    fun `a removed lesson does not count`() {
        val json = timetable(
            lesson("2025-10-20", "08:00", "08:45"),
            lesson("2025-10-20", "13:00", "14:00", removed = true)
        )
        val day = EdupageTimetableParser.weeks(json, LocalDate(2025, 10, 20), LocalDate(2025, 10, 20))
            .single().days.single()
        assertEquals(LocalTime(8, 45), day.lastLessonEnd)
    }

    @Test
    fun `a day with no lessons gives no hours and no day off`() {
        val day = EdupageTimetableParser.weeks(timetable(), LocalDate(2025, 10, 21), LocalDate(2025, 10, 21))
            .single().days.single()
        assertEquals(SchoolDayType.WORK_DAY, day.type)
        assertNull(day.firstLessonStart)
        assertNull(day.lastLessonEnd)
    }

    @Test
    fun `a timetable error is a server failure`() {
        assertFailsWith<EdupageException.Server> {
            val day = LocalDate(2025, 10, 20)
            EdupageTimetableParser.weeks("""{"r":{"error":"bad"}}""", day, day)
        }
    }

    @Test
    fun `a trip for the child's class is an event`() {
        val result = EdupageTimelineParser.parse(
            timeline(item("trip", "5.A", cas = "2025-10-22 08:30:00", text = "Zoo")),
            child
        )
        val event = result.events.single()
        assertEquals("Zoo", event.title)
        val time = event.times.single()
        assertTrue(!time.wholeDay)
        assertNull(time.end)
        assertEquals("2025-10-22 08:30:00", time.startKey)
        assertTrue(event.classIds.isEmpty() && event.students.isEmpty())
    }

    @Test
    fun `an event for another class is dropped`() {
        assertTrue(EdupageTimelineParser.parse(timeline(item("trip", "6.B")), child).events.isEmpty())
    }

    @Test
    fun `an event for another pupil is dropped`() {
        assertTrue(EdupageTimelineParser.parse(timeline(item("event", "Jan Novák")), child).events.isEmpty())
        assertEquals(1, EdupageTimelineParser.parse(timeline(item("event", "eva  nováková")), child).events.size)
    }

    @Test
    fun `a whole-school event is kept`() {
        val result = EdupageTimelineParser.parse(
            timeline(item("schoolevent", "Celá škola"), item("culture", "*")),
            child
        )
        assertEquals(2, result.events.size)
        assertTrue(result.events.all { it.times.single().wholeDay })
    }

    @Test
    fun `a test with a date is a task titled by subject`() {
        val result = EdupageTimelineParser.parse(
            timeline(item("bexam", "5.A", text = "Písomka", data = """{"predmet":"Matematika"}""")),
            child
        )
        assertEquals("Matematika: Písomka", result.tasks.single().title)
        assertEquals(LocalDate(2025, 10, 20), result.tasks.single().date)
    }

    @Test
    fun `a grade and a message are ignored`() {
        val result = EdupageTimelineParser.parse(timeline(item("student_absent", "*"), item("sprava", "*")), child)
        assertTrue(result.events.isEmpty() && result.tasks.isEmpty() && result.daysOff.isEmpty())
    }

    @Test
    fun `a free day is a day off`() {
        val day = EdupageTimelineParser.parse(timeline(item("freeday", "*", text = "Voľno")), child)
            .daysOff.single()
        assertEquals(SchoolDayType.HOLIDAY, day.type)
        assertEquals("Voľno", day.description)
        assertEquals(LocalDate(2025, 10, 20), day.date)
    }

    @Test
    fun `a removed item is ignored`() {
        val result = EdupageTimelineParser.parse(timeline(item("trip", "5.A", removed = "1")), child)
        assertTrue(result.events.isEmpty())
    }
}
