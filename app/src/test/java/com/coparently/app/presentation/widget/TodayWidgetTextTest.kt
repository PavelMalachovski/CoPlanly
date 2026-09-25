package com.coparently.app.presentation.widget

import android.app.Application
import android.content.Context
import android.provider.Settings
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.model.Event
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.theme.ParentColorChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * What the widget says, with the app's real string resources.
 *
 * Robolectric rather than a mocked `Context`, because the point is the resources: that each line
 * is worded with the app's own string for it, that a parent is a name and never a slot, that a
 * plural is chosen by the reader's language, and that nothing the rows leave out goes unsaid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodayWidgetTextTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** The reader's clock is the device setting (release audit R-9); 24-hour unless a test says. */
    @Before
    fun twentyFourHourClock() {
        clock("24")
    }

    private fun clock(hours: String) {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, hours)
    }

    private val today: LocalDate = LocalDate.of(2026, 9, 2)
    private val alex = NamedParent("uid-alex", "mom", "Alex", colorCode = ParentColorChoice.PURPLE.storedCode)
    private val sam = NamedParent("uid-sam", "dad", "Sam", colorCode = ParentColorChoice.ORANGE.storedCode)
    private val parents = Parents(me = alex, coParent = sam, isPaired = true, loaded = true)

    private val day = TodayWidgetModel(
        signedIn = true,
        today = today,
        dayParent = "mom",
        contactWindows = listOf(ContactWindow(2, LocalTime.of(15, 0), LocalTime.of(19, 0), "dad")),
        handover = HandoverInfo(date = today.plusDays(5), daysUntil = 5, fromParent = "mom", toParent = "dad"),
        events = listOf(
            event("Dentist", today.atTime(9, 0), today.atTime(10, 0), owner = "mom"),
            event("Football", today.atTime(17, 30), null, owner = "dad")
        )
    )

    @Test
    fun `the tall widget names both parents and lists the day`() {
        val lines = TodayWidgetText.lines(context, day, parents, maxEvents = 4)

        assertTrue(lines.title, lines.title.startsWith("Today · "))
        assertEquals(WidgetLine("Today with Alex", ParentColorChoice.PURPLE), lines.custody)
        assertEquals(listOf(WidgetLine("15:00–19:00 · contact with Sam", ParentColorChoice.ORANGE)), lines.windows)
        assertEquals(WidgetLine("Handover to Sam in 5 days", ParentColorChoice.ORANGE), lines.handover)
        assertEquals(
            listOf(
                WidgetEventLine("09:00–10:00", "Dentist", ParentColorChoice.PURPLE),
                WidgetEventLine("17:30", "Football", ParentColorChoice.ORANGE)
            ),
            lines.events
        )
        assertNull(lines.footer)
    }

    @Test
    fun `a twelve-hour clock writes the times as the reader reads them`() {
        clock("12")

        val lines = TodayWidgetText.lines(context, day, parents, maxEvents = 4)

        assertEquals("3:00 PM–7:00 PM · contact with Sam", lines.windows.single().text)
        assertEquals("9:00 AM–10:00 AM", lines.events.first().time)
    }

    @Test
    fun `rows that do not fit are counted, never dropped silently`() {
        val busy = day.copy(events = (8..12).map { event("Event $it", today.atTime(it, 0), null, owner = "mom") })

        val tall = TodayWidgetText.lines(context, busy, parents, maxEvents = 4)
        val compact = TodayWidgetText.lines(context, busy, parents, maxEvents = 0)

        assertEquals(4, tall.events.size)
        assertEquals("+1 more", tall.footer)
        assertTrue(compact.events.isEmpty())
        assertEquals("5 events today", compact.footer)
    }

    @Test
    fun `an empty day says so, as the today card does`() {
        val lines = TodayWidgetText.lines(context, day.copy(events = emptyList()), parents, maxEvents = 4)

        assertEquals("Nothing scheduled.", lines.footer)
    }

    @Test
    fun `tomorrow's handover is worded as the hero words it`() {
        val lines = TodayWidgetText.lines(
            context,
            day.copy(handover = HandoverInfo(today.plusDays(1), 1, "mom", "dad")),
            parents,
            maxEvents = 4
        )

        assertEquals("Handover to Sam tomorrow", lines.handover?.text)
    }

    @Test
    fun `with no names known a parent is a fallback label, never a slot`() {
        val lines = TodayWidgetText.lines(context, day, parents = null, maxEvents = 4)

        assertEquals("Today with Parent", lines.custody?.text)
        assertTrue(listOfNotNull(lines.custody, lines.handover).none { "mom" in it.text || "dad" in it.text })
    }

    @Test
    fun `signed out, the widget asks for a sign-in and names nobody`() {
        val lines = TodayWidgetText.lines(context, TodayWidgetModel.signedOut(today), parents, maxEvents = 4)

        assertEquals("Sign in to CoPlanly to see your day.", lines.footer)
        assertNull(lines.custody)
        assertNull(lines.handover)
        assertTrue(lines.events.isEmpty())
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the reader's language picks the words and the plural`() {
        val lines = TodayWidgetText.lines(context, day, parents, maxEvents = 0)

        assertTrue(lines.title, lines.title.startsWith("Сегодня · "))
        assertEquals("Через 5 дней передача, дальше с: Sam", lines.handover?.text)
        assertEquals("2 события сегодня", lines.footer)
    }

    private fun event(title: String, start: LocalDateTime, end: LocalDateTime?, owner: String) = Event(
        id = title,
        title = title,
        startDateTime = start,
        endDateTime = end,
        eventType = "general",
        parentOwner = owner,
        createdAt = start,
        updatedAt = start
    )
}
