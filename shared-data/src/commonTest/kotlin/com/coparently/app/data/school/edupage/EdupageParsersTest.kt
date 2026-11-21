package com.coparently.app.data.school.edupage

import com.coparently.app.domain.school.SchoolDayType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
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

    private class Fields(
        val cas: String = "2025-10-20 00:00:00",
        val text: String = "Text",
        val data: String = "{}",
        val removed: String = "0"
    )

    private fun item(typ: String, meno: String, fields: Fields = Fields()) = with(fields) {
        """{"timelineid":"t-$typ-$meno","typ":"$typ","text":"$text","cas_udalosti":"$cas",
        "user_meno":"$meno","removed":"$removed","data":${JsonPrimitive(data)}}"""
    }

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
    fun `a timetable answer that is not JSON is a server failure`() {
        val day = LocalDate(2025, 10, 20)
        assertFailsWith<EdupageException.Server> { EdupageTimetableParser.weeks("<html>Sign in</html>", day, day) }
    }

    @Test
    fun `a timetable answer without r or without ttitems is a server failure`() {
        val day = LocalDate(2025, 10, 20)
        assertFailsWith<EdupageException.Server> { EdupageTimetableParser.weeks("{}", day, day) }
        assertFailsWith<EdupageException.Server> { EdupageTimetableParser.weeks("""{"r":{}}""", day, day) }
        assertFailsWith<EdupageException.Server> { EdupageTimetableParser.weeks("[]", day, day) }
    }

    @Test
    fun `a timeline answer that is not JSON or has no items is a server failure`() {
        assertFailsWith<EdupageException.Server> { EdupageTimelineParser.parse("<html></html>", child) }
        assertFailsWith<EdupageException.Server> { EdupageTimelineParser.parse("""{"status":"ok"}""", child) }
    }

    @Test
    fun `an empty timeline is an empty result`() {
        val result = EdupageTimelineParser.parse(timeline(), child)
        assertTrue(result.events.isEmpty() && result.tasks.isEmpty() && result.daysOff.isEmpty())
    }

    @Test
    fun `a trip for the child's class is an event`() {
        val result = EdupageTimelineParser.parse(
            timeline(item("trip", "5.A", Fields(cas = "2025-10-22 08:30:00", text = "Zoo"))),
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
            timeline(item("bexam", "5.A", Fields(text = "Písomka", data = """{"predmet":"Matematika"}"""))),
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
        val day = EdupageTimelineParser.parse(timeline(item("freeday", "*", Fields(text = "Voľno"))), child)
            .daysOff.single()
        assertEquals(SchoolDayType.HOLIDAY, day.type)
        assertEquals("Voľno", day.description)
        assertEquals(LocalDate(2025, 10, 20), day.date)
    }

    @Test
    fun `a removed item is ignored`() {
        val result = EdupageTimelineParser.parse(timeline(item("trip", "5.A", Fields(removed = "1"))), child)
        assertTrue(result.events.isEmpty())
    }

    // Shapes recorded live from a Czech school (November 2026), names and ids replaced.
    private val liveParentHome = """
        {"userid":"Rodic-60","parentStudentids":["-80"],"dp":{"year":2026},
         "dbi":{"classes":{"-3":{"id":"-3","short":"2.B"}},
                "students":{"-80":{"id":"-80","firstname":"Eva","lastname":"Nováková","classid":"-3",
                                   "parent1id":"-60","parent2id":"-61","parent3id":""},
                            "-81":{"id":"-81","firstname":"Jan","lastname":"Dvořák","classid":"-3",
                                   "parent1id":"-62","parent2id":"","parent3id":""}}}}
    """.trimIndent()

    private val liveChild = EdupageChild("-80", "Eva Nováková", "2.B")

    @Test
    fun `a parent lists only the children parentStudentids names - not the whole class`() {
        val account = EdupageAccountParser.parse(home(liveParentHome))
        assertEquals(listOf(liveChild), account.children)
    }

    @Test
    fun `without parentStudentids a parent's children are those naming the parent`() {
        val withoutList = liveParentHome.replace("\"parentStudentids\":[\"-80\"],", "")
        assertEquals(listOf(liveChild), EdupageAccountParser.parse(home(withoutList)).children)
    }

    @Test
    fun `the class id of a pupil is read from dbi`() {
        assertEquals("-3", EdupageAccountParser.classIdOf(home(liveParentHome), "-80"))
        assertNull(EdupageAccountParser.classIdOf(home(liveParentHome), "-99"))
    }

    @Test
    fun `card items are lessons and events are not`() {
        val json = timetable(
            """{"type":"card","date":"2026-11-11","starttime":"08:00","endtime":"08:45","removed":null}""",
            """{"type":"event","date":"2026-11-11","starttime":"08:00","endtime":"12:35"}""",
            """{"type":"card","date":"2026-11-11","starttime":"10:55","endtime":"11:40"}""",
            """{"type":"card","date":"2026-11-11","starttime":"11:50","endtime":"12:35","removed":true}"""
        )
        val day = EdupageTimetableParser.weeks(json, LocalDate(2026, 11, 11), LocalDate(2026, 11, 11))
            .single().days.single()
        assertEquals(LocalTime(8, 0), day.firstLessonStart)
        assertEquals(LocalTime(11, 40), day.lastLessonEnd)
    }

    @Test
    fun `a refusal of the pupil table is told apart from other errors`() {
        val refused = """{"r":{"ttitems":[],"error":"Error2759154749 - Insuficient priviledges",
            "rights":{"students":false,"classes":true}}}"""
        assertTrue(EdupageTimetableParser.refusesPupilTable(refused))
        assertTrue(!EdupageTimetableParser.refusesPupilTable("""{"r":{"error":"bad"}}"""))
        assertTrue(!EdupageTimetableParser.refusesPupilTable(timetable()))
        assertTrue(!EdupageTimetableParser.refusesPupilTable("<html></html>"))
    }

    private companion object {
        const val NOV_11 = "2026-11-11 00:00:00"
        const val NOV_19 = "2026-11-19 00:00:00"
        const val NOV_20 = "2026-11-20 00:00:00"
        const val HW_HERS = """{"nazov":"PS str.76/3","predmetid":"-28"}"""
        const val PHOTO_TEXT = "Udalosť: Focení 1. - 4.ročník -  11.11.2026"
        const val PHOTO = """{"name":"Focení 1. - 4.ročník","allday":false,"period":"1","durationperiods":3}"""
    }

    private fun liveItem(typ: String, user: String, cas: String, data: String, text: String = "") =
        """{"timelineid":"t-$typ-$cas","typ":"$typ","user":"$user","user_meno":"2.B","text":"$text",
        "cas_udalosti":"$cas","removed":0,"data":${JsonPrimitive(data)}}"""

    private fun liveTimeline(vararg items: String) = """{"timelineItems":[${items.joinToString(",")}],
        "childGroups":{"-80":["*","Student-80","Trieda-3","CustPlan5"]},
        "dbi":{"periods":[{"id":"1","starttime":"08:00","endtime":"08:45"},
                          {"id":"2","starttime":"08:55","endtime":"09:40"},
                          {"id":"3","starttime":"10:00","endtime":"10:45"}],
               "subjects":{"-28":{"id":-28,"name":"Český jazyk","short":"ČJ"}}}}"""

    @Test
    fun `items are the child's by EduPage's group ids`() {
        val result = EdupageTimelineParser.parse(
            liveTimeline(
                liveItem("event", "yd2026@Trieda-3", NOV_19, """{"name":"Minigolf"}"""),
                liveItem("event", "yd2026@Trieda-4", NOV_19, """{"name":"Another class"}"""),
                liveItem("homework", "CustPlan5", NOV_20, HW_HERS, text = "Změněné: PS str.76/3"),
                liveItem("homework", "CustPlan9", NOV_20, """{"nazov":"Not hers","predmetid":"-28"}""")
            ),
            liveChild
        )
        assertEquals(listOf("Minigolf"), result.events.map { it.title })
        assertEquals(listOf("Český jazyk: PS str.76/3"), result.tasks.map { it.title })
        assertEquals(LocalDate(2026, 11, 20), result.tasks.single().date)
    }

    @Test
    fun `an event takes its name and its lesson periods`() {
        val event = EdupageTimelineParser.parse(
            liveTimeline(
                liveItem("event", "yd2026@Trieda-3", NOV_11, PHOTO, text = PHOTO_TEXT)
            ),
            liveChild
        ).events.single()
        assertEquals("Focení 1. - 4.ročník", event.title)
        val time = event.times.single()
        assertTrue(!time.wholeDay)
        assertEquals(LocalDate(2026, 11, 11).atTime(8, 0), time.start)
        assertEquals(LocalDate(2026, 11, 11).atTime(10, 45), time.end)
    }

    @Test
    fun `an event with no period stays whole-day and a cancelled one is dropped`() {
        val result = EdupageTimelineParser.parse(
            liveTimeline(
                liveItem("event", "Trieda-3", "2026-12-02 00:00:00", """{"name":"Jarmark","period":null}"""),
                liveItem("event", "Trieda-3", "2026-12-03 00:00:00", """{"name":"Zrušeno","cancelled":true}""")
            ),
            liveChild
        )
        assertEquals(listOf("Jarmark"), result.events.map { it.title })
        assertTrue(result.events.single().times.single().wholeDay)
    }

    @Test
    fun `an edited homework loses EduPage's change mark and keeps a colon of its own`() {
        val tasks = EdupageTimelineParser.parse(
            liveTimeline(
                liveItem("homework", "CustPlan5", NOV_11, """{"nazov":"Změněné: PS str.72/1,2","zmena":true}"""),
                liveItem("homework", "CustPlan5", NOV_11, """{"nazov":"Věta: Eva volá Alenu.","zmena":false}""")
            ),
            liveChild
        ).tasks
        assertEquals(listOf("PS str.72/1,2", "Věta: Eva volá Alenu."), tasks.map { it.title })
    }
}
