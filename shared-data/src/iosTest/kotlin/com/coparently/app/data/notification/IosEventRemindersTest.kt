package com.coparently.app.data.notification

import com.coparently.app.domain.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iPhone's event reminders (iPhone phase 4 part C, design §7.4) over a fake notification center:
 * one per event at its next occurrence's instant, gone at the next re-arm once the event is, the
 * nearest 60 only (a child support reminder first), every one removed by `cancelAll`, and worded by
 * `ReminderWording` when armed.
 */
class IosEventRemindersTest {

    private val zone = TimeZone.UTC
    private val center = FakeNotificationCenter(nowMillis = millis(LocalDateTime(2026, 11, 17, 10, 0)))
    private var events: List<Event> = emptyList()
    private var signedIn: String? = "alice"
    private var supportPlan = SupportPlan(emptyList(), emptyList())
    private var supportForgotten = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val planner = EventReminderPlanner(
        events = { events },
        uid = { signedIn },
        wording = ::echoWording,
        zone = { zone },
    )

    private val book = IosLocalReminders(
        center = center,
        events = { now -> planner.plan(now) },
        support = { supportPlan },
        onCancelAll = { supportForgotten += 1 },
        scope = scope,
        now = { center.nowMillis },
    )

    @AfterTest
    fun stop() {
        scope.cancel()
    }

    @Test
    fun anEventReminderIsArmedAtItsInstant() = runBlocking {
        events = listOf(
            event("dentist", LocalDateTime(2026, 11, 18, 15, 0), reminderMinutes = 60, familyId = "alice__bob"),
            // Weekly from last Tuesday 09:00: today's occurrence reminded at 08:30, before now.
            event("swim", LocalDateTime(2026, 11, 10, 9, 0), reminderMinutes = 30, pattern = "weekly"),
        )

        book.rearm()

        val dentist = center.requests.getValue("event-dentist")
        assertEquals(millis(LocalDateTime(2026, 11, 18, 14, 0)), dentist.atMillis)
        assertEquals("calendar", dentist.data["coplanly.destination"])
        assertEquals("alice__bob", dentist.data["familyId"])
        assertEquals("alice", dentist.data["targetUserId"])
        assertEquals(millis(LocalDateTime(2026, 11, 24, 8, 30)), center.requests.getValue("event-swim").atMillis)
        assertEquals(setOf("event-dentist", "event-swim"), center.requests.keys)
    }

    @Test
    fun theReminderTextIsReminderWordingsInTheAppsLanguage() = runBlocking {
        val start = LocalDateTime(2026, 11, 18, 15, 0)
        events = listOf(event("dentist", start, reminderMinutes = 60))

        book.rearm()

        val armed = center.requests.getValue("event-dentist")
        val expected = echoWording().event("Title of dentist", millis(start))
        assertEquals(expected.title, armed.title)
        assertEquals(expected.body, armed.body)
        assertEquals("reminder_notification_text(start:${millis(start)})", armed.body)
    }

    @Test
    fun aDeletedEventsReminderIsCancelledOnSync() = runBlocking {
        val kept = event("kept", LocalDateTime(2026, 11, 20, 9, 0), reminderMinutes = 15)
        events = listOf(event("gone", LocalDateTime(2026, 11, 19, 9, 0), reminderMinutes = 15), kept)
        book.rearm()
        assertEquals(setOf("event-gone", "event-kept"), center.requests.keys)

        // The other phone deleted it: the sync removed the row, and its re-arm follows.
        events = listOf(kept)
        book.rearm()

        assertEquals(setOf("event-kept"), center.requests.keys)
    }

    @Test
    fun anEventWhoseReminderWasRemovedOrPassedIsNotArmed() = runBlocking {
        events = listOf(
            event("none", LocalDateTime(2026, 11, 20, 9, 0), reminderMinutes = null),
            event("past", LocalDateTime(2026, 11, 17, 10, 30), reminderMinutes = 60),
        )

        book.rearm()

        assertTrue(center.requests.isEmpty())
    }

    @Test
    fun onlyTheNearestSixtyAreScheduled() = runBlocking {
        events = (1..70).map { hour ->
            val start = LocalDateTime(2026, 11, 20 + hour / HOURS_A_DAY, hour % HOURS_A_DAY, 0)
            event("e$hour", start, reminderMinutes = 0)
        }.shuffled()

        book.rearm()

        assertEquals(60, center.requests.size)
        assertEquals((1..60).map { "event-e$it" }.toSet(), center.requests.keys)

        // A support reminder takes one slot first, and the farthest event leaves.
        supportPlan = SupportPlan(
            listOf(support("support-alice__bob-2026-12", LocalDateTime(2027, 1, 1, 9, 0))),
            emptyList(),
        )
        book.rearm()

        assertEquals(60, center.requests.size)
        assertTrue("support-alice__bob-2026-12" in center.requests)
        assertEquals((1..59).map { "event-e$it" }.toSet(), center.requests.keys - "support-alice__bob-2026-12")
    }

    @Test
    fun cancelAllRemovesEveryEventAndSupportReminder() = runBlocking {
        events = listOf(event("dentist", LocalDateTime(2026, 11, 18, 15, 0), reminderMinutes = 60))
        supportPlan = SupportPlan(
            listOf(support("support-alice__bob-2026-11", LocalDateTime(2026, 11, 20, 9, 0))),
            emptyList(),
        )
        book.rearm()
        center.add("someone-else", millis(LocalDateTime(2026, 11, 30, 9, 0)), "t", "b", emptyMap())
        assertEquals(3, center.requests.size)

        // Through the scheduler, as sign-out, a switch and deletion call it: queued, then run.
        IosEventReminders(book).cancelAll()
        withTimeout(WAIT_MILLIS) {
            while (center.requests.size != 1) delay(POLL_MILLIS)
        }

        assertEquals(setOf("someone-else"), center.requests.keys)
        assertEquals(1, supportForgotten)
    }

    @Test
    fun nothingStaysArmedOnceNobodyIsSignedIn() = runBlocking {
        events = listOf(event("dentist", LocalDateTime(2026, 11, 18, 15, 0), reminderMinutes = 60))
        book.rearm()

        signedIn = null
        book.rearm()

        assertTrue(center.requests.isEmpty())
    }

    @Test
    fun aPrivateEventOpensTheCalendarOnNoFamily() = runBlocking {
        events = listOf(
            event("mine", LocalDateTime(2026, 11, 18, 15, 0), reminderMinutes = 60, familyId = "alice__bob")
                .copy(isPrivate = true),
        )

        book.rearm()

        assertNull(center.requests.getValue("event-mine").data["familyId"])
    }

    @Test
    fun aFailedEventPlanKeepsWhatIsArmed() = runBlocking {
        events = listOf(event("dentist", LocalDateTime(2026, 11, 18, 15, 0), reminderMinutes = 60))
        book.rearm()
        val failing = IosLocalReminders(
            center = center,
            events = { error("Room could not be read") },
            support = { supportPlan },
            onCancelAll = {},
            scope = scope,
            now = { center.nowMillis },
        )

        failing.rearm()

        assertEquals(setOf("event-dentist"), center.requests.keys)
    }

    @Test
    fun aNewAccountPlansNothingBeforeItsAccountCheck() = runBlocking {
        // Bob signed in where Alice signed out: Room still holds her events until the check wipes them.
        var lastChecked: String? = "alice"
        val gated = EventReminderPlanner(
            events = { events },
            uid = { checkedAccount("bob", lastChecked) },
            wording = ::echoWording,
            zone = { zone },
        )
        val gatedBook = IosLocalReminders(
            center = center,
            events = { now -> gated.plan(now) },
            support = { supportPlan },
            onCancelAll = {},
            scope = scope,
            now = { center.nowMillis },
        )
        events = listOf(event("alices", LocalDateTime(2026, 11, 18, 15, 0), reminderMinutes = 60))

        gatedBook.rearm()
        assertTrue(center.requests.isEmpty())

        // The check ran for Bob (and wiped her rows); his own events are armed from then on.
        lastChecked = "bob"
        events = listOf(event("bobs", LocalDateTime(2026, 11, 18, 15, 0), reminderMinutes = 60))
        gatedBook.rearm()
        assertEquals("bob", center.requests.getValue("event-bobs").data["targetUserId"])
    }

    @Test
    fun anAccountIsCheckedOnlyWhenTheCheckNamedIt() {
        assertEquals("bob", checkedAccount("bob", "bob"))
        assertNull(checkedAccount("bob", "alice"))
        assertNull(checkedAccount("bob", null))
        assertNull(checkedAccount(null, "bob"))
        assertNull(checkedAccount("", ""))
    }

    private fun support(id: String, at: LocalDateTime) = LocalReminder(id, millis(at), "t", "b", emptyMap())

    private fun millis(time: LocalDateTime): Long = time.toInstant(zone).toEpochMilliseconds()

    private fun event(
        id: String,
        start: LocalDateTime,
        reminderMinutes: Int?,
        pattern: String? = null,
        familyId: String? = null,
    ) = Event(
        id = id,
        title = "Title of $id",
        startDateTime = start,
        eventType = "other",
        parentOwner = "mom",
        isRecurring = pattern != null,
        recurrencePattern = pattern,
        createdAt = start,
        updatedAt = start,
        reminderMinutes = reminderMinutes,
        familyId = familyId,
    )

    private companion object {
        const val HOURS_A_DAY = 24
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
    }
}
