package com.coparently.app.domain.school

import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Event
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The school import's decisions (MON-8), all owner rules: what is created, what is updated and
 * when, what is never brought back, and the one thing ever deleted.
 */
class SchoolImportPlannerTest {

    /** A Monday. */
    private val today = LocalDate.of(2026, 10, 5)
    private val now = today.atTime(6, 30)

    private val target = SchoolImportTarget(
        baseUrl = "https://skola.bakalari.cz",
        username = "novak",
        childId = "anna",
        familyId = "alice__bob",
        ownerUid = "alice",
        ownerSlot = "mom"
    )
    private val student = SchoolStudent(userUid = "1234/x", fullName = "Nováková Anna, 5.A", classId = "5A")

    private val wording = object : SchoolImportWording {
        override val schoolHours = "Anna at school"
        override fun dayOff(description: String, type: SchoolDayType) =
            if (description.isNotBlank()) "Anna: $description" else "Anna: no school"
    }

    @Test
    fun `a school event is created as an ordinary shared event of the connection's family and child`() {
        val plan = plan(events = listOf(trip()))

        val event = plan.creates.single()
        val expectedId = SchoolImportPlanner.idOf(target, SchoolImportKind.EVENT, "TRIP|2026-10-07T08:00:00+02:00")
        assertEquals(expectedId, event.id)
        assertTrue(event.id.startsWith("school-"))
        assertEquals("school-".length + 32, event.id.length)
        assertEquals("Trip to the zoo", event.title)
        assertEquals(LocalDateTime.of(2026, 10, 7, 8, 0), event.startDateTime)
        assertEquals(LocalDateTime.of(2026, 10, 7, 13, 0), event.endDateTime)
        assertEquals("school", event.eventType)
        assertEquals("mom", event.parentOwner)
        assertFalse(event.isPrivate)
        assertEquals(listOf<FamilyMemberRef>(FamilyMemberRef.Child("anna")), event.forMembers)
        assertEquals("alice__bob", event.familyId)
        assertEquals("alice", event.createdByFirebaseUid)
        assertTrue(event.sharedWith.isEmpty(), "the audience is the create path's to compute")
        assertEquals(SchoolImportKind.EVENT, plan.ledger.getValue(event.id).kind)
    }

    @Test
    fun `ids are deterministic and differ by account, kind and key`() {
        val one = SchoolImportPlanner.idOf(target, SchoolImportKind.HOURS, "2026-10-05")

        assertEquals(one, SchoolImportPlanner.idOf(target, SchoolImportKind.HOURS, "2026-10-05"))
        val otherAccount = target.copy(username = "other")
        assertFalse(one == SchoolImportPlanner.idOf(otherAccount, SchoolImportKind.HOURS, "2026-10-05"))
        assertFalse(one == SchoolImportPlanner.idOf(target, SchoolImportKind.DAY_OFF, "2026-10-05"))
        assertFalse(one.contains('/'))
    }

    @Test
    fun `each time block is its own event, and a whole day is an all-day event on its dates`() {
        val event = SchoolEvent(
            id = "OPEN",
            title = "Open day",
            description = null,
            times = listOf(
                wholeDay("2026-10-12", "2026-10-12"),
                SchoolEventTime(false, at("2026-10-13T08:00"), at("2026-10-13T10:45"), "2026-10-13T08:00:00+02:00"),
                wholeDay("2026-10-19", "2026-10-21")
            ),
            classIds = emptyList(),
            students = emptyList()
        )

        val creates = plan(events = listOf(event)).creates.sortedBy { it.startDateTime }

        assertEquals(3, creates.map { it.id }.distinct().size)
        assertEquals(LocalDate.of(2026, 10, 12).atStartOfDay(), creates[0].startDateTime)
        assertNull(creates[0].endDateTime, "one all-day date has no end")
        assertEquals(LocalDateTime.of(2026, 10, 13, 10, 45), creates[1].endDateTime)
        assertEquals(LocalDate.of(2026, 10, 21).atStartOfDay(), creates[2].endDateTime)
    }

    @Test
    fun `another class's event is not imported`() {
        val plan = plan(events = listOf(trip().copy(classIds = listOf("5B"))))

        assertTrue(plan.creates.isEmpty())
    }

    @Test
    fun `only the next ninety days are imported`() {
        val past = trip().copy(id = "PAST", times = listOf(timed("2026-10-02T08:00", "2026-10-02T09:00")))
        val far = trip().copy(id = "FAR", times = listOf(timed("2027-01-04T08:00", "2027-01-04T09:00")))

        assertTrue(plan(events = listOf(past, far)).creates.isEmpty())
    }

    @Test
    fun `an unchanged event is not updated`() {
        val first = plan(events = listOf(trip()))
        val created = first.creates.single()

        val second = plan(events = listOf(trip()), existing = live(created), ledger = first.ledger)

        assertTrue(second.creates.isEmpty())
        assertTrue(second.updates.isEmpty())
    }

    @Test
    fun `a change at school updates the imported fields and keeps everything else`() {
        val first = plan(events = listOf(trip()))
        val stored = first.creates.single().copy(
            sharedWith = listOf("alice", "bob"),
            reminderMinutes = 30,
            isImportant = true,
            syncedToFirestore = true
        )

        val moved = trip().copy(
            title = "Trip to the zoo (moved)",
            times = listOf(timed("2026-10-07T08:00", "2026-10-07T15:00"))
        )
        val update = plan(events = listOf(moved), existing = live(stored), ledger = first.ledger).updates.single()

        assertEquals("Trip to the zoo (moved)", update.title)
        assertEquals(LocalDateTime.of(2026, 10, 7, 15, 0), update.endDateTime)
        assertEquals(listOf("alice", "bob"), update.sharedWith)
        assertEquals(30, update.reminderMinutes)
        assertTrue(update.isImportant)
        assertEquals(stored.id, update.id)
        assertEquals(stored.createdAt, update.createdAt)
    }

    @Test
    fun `a title the parent corrected survives an update in which the school changed nothing`() {
        val first = plan(events = listOf(trip()))
        val edited = first.creates.single().copy(title = "Zoo — pack lunch")

        val second = plan(events = listOf(trip()), existing = live(edited), ledger = first.ledger)

        assertTrue(second.updates.isEmpty())
    }

    @Test
    fun `a pending deletion is never brought back`() {
        val id = SchoolImportPlanner.idOf(target, SchoolImportKind.EVENT, "TRIP|2026-10-07T08:00:00+02:00")

        val plan = plan(events = listOf(trip()), existing = mapOf(id to ExistingImport.Deleted))

        assertTrue(plan.creates.isEmpty())
        assertTrue(plan.updates.isEmpty())
    }

    @Test
    fun `an event deleted and delivered is not re-created from the connection's record`() {
        val first = plan(events = listOf(trip()))

        // Room no longer holds it: the tombstone reached the server and the row went.
        val second = plan(events = listOf(trip()), existing = emptyMap(), ledger = first.ledger)

        assertTrue(second.creates.isEmpty())
    }

    @Test
    fun `nothing is deleted because it is missing from a response`() {
        val first = plan(events = listOf(trip()), weeks = listOf(week(today, schoolDay(today))))
        val existing = first.creates.associate { it.id to ExistingImport.Live(it) }

        // The summer purge: no events and no timetable came back.
        val second = plan(events = emptyList(), weeks = emptyList(), existing = existing, ledger = first.ledger)

        assertTrue(second.deletes.isEmpty())
        assertTrue(second.creates.isEmpty())
        assertEquals(first.ledger.keys, second.ledger.keys, "the record keeps what is still ahead")
    }

    @Test
    fun `a school day becomes a school-hours event from the first lesson to the last`() {
        val event = plan(weeks = listOf(week(today, schoolDay(today)))).creates.single()

        assertEquals("Anna at school", event.title)
        assertEquals(today.atTime(8, 0), event.startDateTime)
        assertEquals(today.atTime(14, 20), event.endDateTime)
        assertEquals(SchoolImportPlanner.idOf(target, SchoolImportKind.HOURS, today.toString()), event.id)
    }

    @Test
    fun `a day that no longer has school loses its school-hours event through the delete path`() {
        val first = plan(weeks = listOf(week(today, schoolDay(today.plusDays(1)))))
        val hours = first.creates.single()

        val cancelled = SchoolDay(today.plusDays(1), SchoolDayType.DIRECTOR_DAY, "", false, null, null)
        val second = plan(weeks = listOf(week(today, cancelled)), existing = live(hours), ledger = first.ledger)

        assertEquals(listOf(hours), second.deletes)
        // And the day off itself is imported beside it.
        assertEquals("Anna: no school", second.creates.single().title)
    }

    @Test
    fun `a day the calendar already draws as a holiday or vacation is not imported again`() {
        val holiday = SchoolDay(today.plusDays(2), SchoolDayType.HOLIDAY, "", false, null, null)
        val celebration = SchoolDay(today.plusDays(3), SchoolDayType.CELEBRATION, "Den státnosti", false, null, null)

        val drawn = plan(
            weeks = listOf(week(today, holiday, celebration)),
            drawnHolidays = setOf(holiday.date, celebration.date)
        )
        val notDrawn = plan(weeks = listOf(week(today, celebration)))

        assertTrue(drawn.creates.isEmpty())
        assertEquals("Anna: Den státnosti", notDrawn.creates.single().title)
    }

    @Test
    fun `days off with one title are one event, across a weekend`() {
        val friday = today.plusDays(4)
        val nextMonday = today.plusDays(7)
        val days = listOf(
            SchoolDay(friday, SchoolDayType.HOLIDAY, "Podzimní prázdniny", false, null, null),
            SchoolDay(nextMonday, SchoolDayType.HOLIDAY, "Podzimní prázdniny", false, null, null),
            SchoolDay(nextMonday.plusDays(1), SchoolDayType.DIRECTOR_DAY, "", false, null, null)
        )

        val creates = plan(weeks = listOf(week(today, days[0]), week(nextMonday, days[1], days[2]))).creates
            .sortedBy { it.startDateTime }

        assertEquals(2, creates.size)
        assertEquals(friday.atStartOfDay(), creates[0].startDateTime)
        assertEquals(nextMonday.atStartOfDay(), creates[0].endDateTime)
        assertEquals(SchoolImportPlanner.idOf(target, SchoolImportKind.DAY_OFF, friday.toString()), creates[0].id)
        assertNull(creates[1].endDateTime)
    }

    @Test
    fun `a run whose first days fell behind the fetched weeks extends its event rather than duplicating it`() {
        val friday = today.plusDays(4)
        val nextMonday = today.plusDays(7)
        val first = plan(
            weeks = listOf(week(today, SchoolDay(friday, SchoolDayType.HOLIDAY, "", false, null, null)))
        )
        val fridayEvent = first.creates.single()

        // A week later only next week is fetched, and the break goes on for two more days.
        val later = plan(
            today = nextMonday,
            weeks = listOf(
                week(
                    nextMonday,
                    SchoolDay(nextMonday, SchoolDayType.HOLIDAY, "", false, null, null),
                    SchoolDay(nextMonday.plusDays(1), SchoolDayType.HOLIDAY, "", false, null, null)
                )
            ),
            existing = live(fridayEvent),
            ledger = first.ledger
        )

        assertTrue(later.creates.isEmpty())
        val extended = later.updates.single()
        assertEquals(fridayEvent.id, extended.id)
        assertEquals(friday.atStartOfDay(), extended.startDateTime)
        assertEquals(nextMonday.plusDays(1).atStartOfDay(), extended.endDateTime)
    }

    @Test
    fun `a school day without cells or with an undefined type is not a day off`() {
        val empty = SchoolDay(today, SchoolDayType.WORK_DAY, "", false, null, null)
        val undefined = SchoolDay(today.plusDays(1), SchoolDayType.UNDEFINED, "", false, null, null)
        val allCancelled = SchoolDay(today.plusDays(2), SchoolDayType.WORK_DAY, "", true, null, null)

        assertTrue(plan(weeks = listOf(week(today, empty, undefined, allCancelled))).creates.isEmpty())
    }

    @Test
    fun `days before today are neither created nor deleted`() {
        val yesterday = today.minusDays(1)
        val sunday = SchoolDay(yesterday, SchoolDayType.HOLIDAY, "", false, null, null)

        val plan = plan(weeks = listOf(week(yesterday, sunday)))

        assertTrue(plan.creates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun `the ids to look up include what the connection made before`() {
        val ids = SchoolImportPlanner.idsToLookUp(
            input(ledger = mapOf("school-old" to SchoolImportRecord(SchoolImportKind.EVENT, today.plusDays(3), "f")))
        )

        assertTrue("school-old" in ids)
    }

    @Test
    fun `the record forgets what is behind today`() {
        val ledger = mapOf(
            "school-past" to SchoolImportRecord(SchoolImportKind.EVENT, today.minusDays(1), "f"),
            "school-today" to SchoolImportRecord(SchoolImportKind.HOURS, today, "f")
        )

        assertEquals(setOf("school-today"), plan(ledger = ledger).ledger.keys)
    }

    // --- fixtures -------------------------------------------------------------------------------

    private fun trip() = SchoolEvent(
        id = "TRIP",
        title = "Trip to the zoo",
        description = "Meet at 7:45",
        times = listOf(timed("2026-10-07T08:00", "2026-10-07T13:00")),
        classIds = listOf("5A"),
        students = emptyList()
    )

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun timed(start: String, end: String) = SchoolEventTime(false, at(start), at(end), "$start:00+02:00")

    private fun wholeDay(first: String, last: String) =
        SchoolEventTime(true, at("${first}T00:00"), at("${last}T23:59"), "${first}T00:00:00+02:00")

    private fun schoolDay(date: LocalDate) =
        SchoolDay(date, SchoolDayType.WORK_DAY, "", true, LocalTime.of(8, 0), LocalTime.of(14, 20))

    private fun week(anyDay: LocalDate, vararg days: SchoolDay) = SchoolWeek(SchoolWeek.mondayOf(anyDay), days.toList())

    private fun live(event: Event) = mapOf(event.id to ExistingImport.Live(event))

    @Suppress("LongParameterList")
    private fun input(
        today: LocalDate = this.today,
        events: List<SchoolEvent> = emptyList(),
        weeks: List<SchoolWeek> = emptyList(),
        drawnHolidays: Set<LocalDate> = emptySet(),
        existing: Map<String, ExistingImport> = emptyMap(),
        ledger: Map<String, SchoolImportRecord> = emptyMap()
    ) = SchoolImportInput(
        target = target,
        today = today,
        now = now,
        student = student,
        events = events,
        weeks = weeks,
        drawnHolidays = drawnHolidays,
        existing = existing,
        ledger = ledger,
        wording = wording
    )

    @Suppress("LongParameterList")
    private fun plan(
        today: LocalDate = this.today,
        events: List<SchoolEvent> = emptyList(),
        weeks: List<SchoolWeek> = emptyList(),
        drawnHolidays: Set<LocalDate> = emptySet(),
        existing: Map<String, ExistingImport> = emptyMap(),
        ledger: Map<String, SchoolImportRecord> = emptyMap()
    ) = SchoolImportPlanner.plan(input(today, events, weeks, drawnHolidays, existing, ledger))
}
