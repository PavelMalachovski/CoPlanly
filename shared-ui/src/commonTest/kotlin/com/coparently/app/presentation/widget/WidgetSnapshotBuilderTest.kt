package com.coparently.app.presentation.widget

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.Parents
import com.coparently.app.pushwording.WordingStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The seven-day snapshot the iPhone's widget extension draws (iPhone phase 4 part D).
 *
 * It must say what the Android widget says, so these pin that each day is [TodayWidgetModel.of] worded
 * by [TodayWidgetText], that its colours are the Android widget's tones, and that it holds lines
 * only: the co-parent's private event never reaches it.
 */
class WidgetSnapshotBuilderTest {

    // Week on, week off from Monday 31 August 2026: slot 1 ("mom", Alice) to 6 September.
    private val pattern = CustodyModel(
        id = "m1",
        modelType = CustodyModelType.WEEK_ON_WEEK_OFF,
        patternDays = 14,
        momDayIndices = (0..6).toSet(),
        startDate = LocalDate(2026, 8, 31)
    )
    private val today = LocalDate(2026, 9, 2)
    private val parents = Parents(
        me = NamedParent(uid = "uid-mom", slot = "mom", name = "Alice"),
        coParent = NamedParent(uid = "uid-dad", slot = "dad", name = "Bob"),
        isPaired = true,
        loaded = true
    )

    @Test
    fun sevenConsecutiveDaysFromToday() {
        val snapshot = build(today)

        assertTrue(snapshot.signedIn)
        assertEquals(
            listOf(
                "2026-09-02",
                "2026-09-03",
                "2026-09-04",
                "2026-09-05",
                "2026-09-06",
                "2026-09-07",
                "2026-09-08"
            ),
            snapshot.days.map { it.date }
        )
        assertEquals("widget_today_title(2026-09-02)", snapshot.days.first().compact.title)
    }

    @Test
    fun daysAreConsecutiveLocalDatesAcrossDst() {
        val snapshot = build(LocalDate(2026, 10, 24))

        assertEquals(
            listOf(
                "2026-10-24",
                "2026-10-25",
                "2026-10-26",
                "2026-10-27",
                "2026-10-28",
                "2026-10-29",
                "2026-10-30"
            ),
            snapshot.days.map { it.date }
        )
    }

    @Test
    fun signedOutGivesOneSignedOutDay() {
        val snapshot = build(today, userId = null, events = listOf(event("dentist", at(today, 9, 0))))

        assertFalse(snapshot.signedIn)
        assertEquals(1, snapshot.days.size)
        val day = snapshot.days.single()
        assertEquals("2026-09-02", day.date)
        listOf(day.compact, day.compactLarge, day.tall).forEach { lines ->
            assertEquals("widget_today_signed_out()", lines.footer)
            assertEquals(null, lines.custody)
            assertTrue(lines.events.isEmpty())
            assertTrue(lines.windows.isEmpty())
        }
        assertFalse(snapshot.toString().contains("dentist"))
    }

    @Test
    fun coParentPrivateEventNeverAppears() {
        val events = listOf(
            event("visible", at(today, 9, 0)),
            event("secret", at(today, 12, 0))
                .copy(isPrivate = true, createdByFirebaseUid = "uid-dad", parentOwner = "dad")
        )

        val snapshot = build(today, events = events)

        val tall = snapshot.days.first().tall
        assertEquals(listOf("visible"), tall.events.map { it.title })
        assertFalse(snapshot.toString().contains("secret"))
    }

    @Test
    fun coloursAreTheWidgetsTones() {
        val events = listOf(event("football", at(today, 16, 0)).copy(parentOwner = "dad"))

        val day = build(today, events = events).days.first()

        // The default palette: Alice (slot 1) pink, Bob (slot 2) blue.
        val custody = assertNotNull(day.tall.custody)
        assertEquals("home_handover_current_you()", custody.text)
        assertEquals(WidgetColour(light = "#A0124F", dark = "#FF99BB"), custody.colour)
        assertEquals(WidgetColour(light = "#2979FF", dark = "#448AFF"), day.tall.events.single().mark)
        assertEquals(WidgetColour(light = "#1550C0", dark = "#9EC2FF"), day.tall.handover?.colour)
    }

    @Test
    fun compactLargeIsCompactAtLargeText() {
        val window = ContactWindow(dayIndex = 2, LocalTime(15, 0), LocalTime(19, 0), parent = "dad")
        val model = pattern.copy(contactWindows = listOf(window))
        val events = listOf(event("dentist", at(today, 9, 0)))

        val day = build(today, events = events, model = model).days.first()

        val compact = TodayWidgetText.lines(
            strings = FakeStrings,
            clock = FakeClock,
            model = TodayWidgetModel.of(today, "uid-mom", events, model, emptyMap()),
            parents = parents,
            maxEvents = 0
        )
        assertEquals(compact.toSnapshotLines(), day.compact)
        assertEquals(compact.compactAtLargeText().toSnapshotLines(), day.compactLarge)
        assertTrue(day.compactLarge.windows.isEmpty())
        assertEquals(null, day.compactLarge.footer)
    }

    @Test
    fun lastEntryIsTheStaleLine() {
        assertEquals("widget_today_stale()", build(today).staleLine)
        assertEquals("widget_today_stale()", build(today, userId = null).staleLine)
    }

    private fun build(
        day: LocalDate,
        userId: String? = "uid-mom",
        events: List<Event> = emptyList(),
        model: CustodyModel? = pattern
    ): WidgetSnapshot = WidgetSnapshotBuilder.build(
        WidgetSnapshotInput(
            today = day,
            writtenAtMillis = 1_000L,
            language = "en",
            userId = userId,
            eventsOn = { on -> events.filter { it.startDateTime.date == on } },
            model = model,
            overrides = emptyMap(),
            parents = parents
        ),
        FakeStrings,
        FakeClock
    )

    private fun at(day: LocalDate, hour: Int, minute: Int) =
        LocalDateTime(day.year, day.month.number, day.day, hour, minute)

    private fun event(id: String, start: LocalDateTime) = Event(
        id = id,
        title = id,
        startDateTime = start,
        eventType = "general",
        parentOwner = "mom",
        createdAt = start,
        updatedAt = start
    )

    /** Every string as "key(args)", so a test reads which key was asked for and with what. */
    private object FakeStrings : WordingStrings {
        override fun string(key: String, vararg args: Any): String = "$key(${args.joinToString(",")})"

        override fun plural(key: String, count: Int, vararg args: Any): String = "$key(${args.joinToString(",")})"
    }

    /** ISO text for both, so a line's date and times are plain to read. */
    private object FakeClock : WidgetClock {
        override fun titleDate(date: LocalDate): String = date.toString()

        override fun time(time: LocalTime): String = time.toString()
    }
}
