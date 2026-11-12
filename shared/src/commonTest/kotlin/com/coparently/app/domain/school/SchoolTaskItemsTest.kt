package com.coparently.app.domain.school

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a school task asks of the calendar: an all-day event on its day, inside the window. */
class SchoolTaskItemsTest {

    private val today = LocalDate(2026, 11, 12)

    private val target = SchoolImportTarget(
        baseUrl = "https://zsx.edupage.org",
        username = "mama",
        childId = "anna",
        familyId = "alice__bob",
        ownerUid = "alice",
        ownerSlot = "mom",
        system = SchoolSystem.EDUPAGE,
        studentKey = "-1234"
    )

    @Test
    fun `a task becomes an all-day event on its date`() {
        val date = today.plus(3, DateTimeUnit.DAY)
        val items = SchoolImportItems.all(input(listOf(SchoolTask("991", date, " Maths page 4 ", " Do it "))))
        val item = items.single()
        assertEquals(SchoolImportKind.TASK, item.kind)
        assertEquals(SchoolImportIds.of(target, SchoolImportKind.TASK, "991"), item.id)
        assertEquals("Maths page 4", item.fields.title)
        assertEquals("Do it", item.fields.description)
        assertEquals(date.atTime(0, 0), item.fields.start)
        assertEquals(date.atTime(23, 59), item.fields.end)
        assertEquals(date, item.lastDate)
    }

    @Test
    fun `a blank description is none`() {
        val task = SchoolTask("1", today, "Read", "  ")
        assertNull(SchoolImportItems.tasks(input(listOf(task))).single().fields.description)
    }

    @Test
    fun `a task on today is imported but one before today is not`() {
        val tasks = listOf(
            SchoolTask("a", today, "Today", null),
            SchoolTask("b", today.plus(-1, DateTimeUnit.DAY), "Yesterday", null)
        )
        assertEquals(listOf("Today"), SchoolImportItems.tasks(input(tasks)).map { it.fields.title })
    }

    @Test
    fun `a task after the event window is not imported`() {
        val last = today.plus(SchoolImportPlanner.EVENT_WINDOW_DAYS - 1, DateTimeUnit.DAY)
        val beyond = today.plus(SchoolImportPlanner.EVENT_WINDOW_DAYS, DateTimeUnit.DAY)
        val tasks = listOf(SchoolTask("in", last, "In", null), SchoolTask("out", beyond, "Out", null))
        assertEquals(listOf("In"), SchoolImportItems.tasks(input(tasks)).map { it.fields.title })
    }

    @Test
    fun `a task title is cut to MAX_TITLE_LENGTH`() {
        val long = "x".repeat(SchoolImportPlanner.MAX_TITLE_LENGTH + 50)
        val item = SchoolImportItems.tasks(input(listOf(SchoolTask("1", today, long, null)))).single()
        assertEquals(SchoolImportPlanner.MAX_TITLE_LENGTH, item.fields.title.length)
    }

    @Test
    fun `a task with a blank title is not imported`() {
        assertTrue(SchoolImportItems.tasks(input(listOf(SchoolTask("1", today, "  ", null)))).isEmpty())
    }

    private fun input(tasks: List<SchoolTask>) = SchoolImportInput(
        target = target,
        today = today,
        now = today.atTime(6, 30),
        student = SchoolStudent(userUid = "u", fullName = "Anna", classId = ""),
        events = emptyList(),
        weeks = emptyList(),
        drawnHolidays = emptySet(),
        existing = emptyMap(),
        ledger = emptyMap(),
        wording = object : SchoolImportWording {
            override val schoolHours = "Anna at school"
            override fun dayOff(description: String, type: SchoolDayType) = "Anna: no school"
        },
        tasks = tasks
    )
}
