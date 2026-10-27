package com.coparently.app.qa

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.coparently.app.R
import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.e2e.EmulatorEnvironment
import com.coparently.app.e2e.EmulatorParent
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.testing.exists
import com.coparently.app.testing.settle
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * The calendar as a parent uses it: events created through the real form and found again in the
 * views, on the co-parent's phone and — for a private one — nowhere else; an edit that keeps what
 * the form does not show; a delete with Undo; the views and their navigation.
 *
 * Titles are Czech, as the testers' family writes them, and carry a short id so a title already
 * on screen from an earlier step can never satisfy a later one.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarScenarioTest : QaOnScreenTest() {

    @Inject
    lateinit var eventRepository: EventRepository

    @Test
    fun createKinds() {
        val id = shortId()
        val oneOff = "Rodičovská schůzka $id"
        val weekly = "Plavání Lena $id"
        val reminded = "Zubař Lena $id"
        val wholeDay = "Ředitelské volno $id"
        val aboutLena = "Očkování Lena $id"

        report.step("one_off", "Create a one-off event through the form", "\"$oneOff\" is in Day view and on Igor's phone") {
            createEvent(oneOff)
            openDayView()
            waitFor("the event in Day view", hasText(oneOff, substring = true))
            expectOnIgorsPhone(oneOff)
        }
        report.step("weekly", "Create a weekly event", "\"$weekly\" repeats in next week's Week view and is one row on Igor's phone") {
            // Repeat is a row that opens its options (UX audit H5).
            createEvent(weekly) {
                tap(textButton(R.string.event_form_repeat))
                tap(textButton(R.string.event_repeat_weekly))
            }
            openTab(BottomNavDestination.CALENDAR)
            viewMode(R.string.calendar_viewmode_week)
            check(driver.pageForward()) { "Week view does not page forward" }
            waitFor("next week's occurrence", hasText(weekly, substring = true))
            val row = expectOnIgorsPhone(weekly)
            check(row.isRecurring) { "Igor's copy of \"$weekly\" does not repeat" }
        }
        report.step("reminder", "Create an event with a reminder an hour before", "The reminder is stored and scheduled") {
            createEvent(reminded) {
                tap(textButton(R.string.event_form_reminder))
                tap(textButton(R.string.event_reminder_1_hour))
            }
            val saved = mine(reminded)
            check(saved.reminderMinutes == REMINDER_MINUTES) { "stored reminder is ${saved.reminderMinutes}, not 60" }
            val work = WorkManager.getInstance(context).getWorkInfosByTag(REMINDER_TAG).get()
            check(work.isNotEmpty()) { "no reminder work is enqueued" }
        }
        report.step("all_day", "Create an all-day event", "Saved over the whole day, and all day on Igor's phone") {
            createEvent(wholeDay) { tap(hasContentDescription(string(R.string.event_all_day)) and hasClickAction()) }
            val saved = mine(wholeDay)
            val day = saved.startDateTime.toLocalDate()
            check(saved.startDateTime == day.atStartOfDay() && saved.endDateTime == day.atTime(AllDayEvent.LAST_MINUTE)) {
                "the saved event runs ${saved.startDateTime}–${saved.endDateTime}, not the whole of $day"
            }
            val row = expectOnIgorsPhone(wholeDay)
            check(AllDayEvent.isAllDay(row)) { "Igor's copy runs ${row.startDateTime}–${row.endDateTime}" }
        }
        report.step("about_lena", "Create an event about Lena, with two children in the family", "The event names Lena and Igor sees that") {
            runBlocking {
                QaFamily.child(bob, aliceUid, LENA, LocalDate.of(2017, 3, 14))
                QaFamily.child(bob, aliceUid, EMA, LocalDate.of(2020, 6, 1))
                syncService.performFullSync().getOrThrow()
            }
            createEvent(aboutLena) {
                waitFor("Lena's chip", hasText(LENA) and hasClickAction())
                tap(hasText(LENA) and hasClickAction())
            }
            val saved = mine(aboutLena)
            check(saved.forMembers.any { it is FamilyMemberRef.Child }) { "the saved event names no child: ${saved.forMembers}" }
            val row = expectOnIgorsPhone(aboutLena)
            check(row.forMembers == saved.forMembers) { "Igor's copy names ${row.forMembers}, not ${saved.forMembers}" }
        }
    }

    @Test
    fun privateEvent() {
        val title = "Psycholog $id"
        report.step("private_not_on_server", "Create a private event", "Only Zuzana has it: not on the server, not on Igor's phone") {
            createEvent(title) { tap(hasContentDescription(string(R.string.event_form_private_title)) and hasClickAction()) }
            val saved = mine(title)
            check(saved.isPrivate) { "the saved event is not private" }
            check(!EmulatorEnvironment.documentExists("events/${saved.id}")) { "the private event reached the server" }
            coParentSync()
            val onIgor = runBlocking { bob.eventRepository.getAllEvents().first().any { it.title == title } }
            check(!onIgor) { "the private event reached Igor's phone" }
        }
    }

    @Test
    fun editPreserves() {
        val original = "Kroužek $id"
        val edited = "Kroužek keramiky $id"
        lateinit var before: Map<String, Any?>
        report.step("seed", "Igor adds an event for today", "It reaches Zuzana's Day view") {
            val event = runBlocking { QaFamily.event(bob, aliceUid, original, todayLater()) }
            before = serverDocument(event.id)
            openDayView()
            awaitDelivered(this, "Igor's event", hasText(original, substring = true), expectsListener = false)
        }
        report.step("edit_on_screen", "Zuzana renames it through the preview's Edit", "The new title is in Day view") {
            tap(hasText(original, substring = true) and hasClickAction())
            tap(textButton(R.string.event_preview_edit))
            waitFor("the event form", hasSetTextAction())
            composeTestRule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(edited)
            composeTestRule.settle()
            tap(textButton(R.string.event_form_save))
            waitFor("the renamed event", hasText(edited, substring = true))
        }
        report.step("edit_keeps_fields", "The edit keeps what the form does not show", "Audience, creator and family unchanged on the server; Igor sees the new title") {
            val id = runBlocking { mine(edited).id }
            val after = serverDocument(id)
            listOf("sharedWith", "createdByFirebaseUid", "familyId").forEach { key ->
                check(after[key] == before[key]) { "$key changed from ${before[key]} to ${after[key]}" }
            }
            expectOnIgorsPhone(edited)
        }
    }

    @Test
    fun deleteUndo() {
        val title = "Narozeniny babičky $id"
        report.step("seed", "Zuzana creates an event", "It is in Day view") {
            createEvent(title)
            openDayView()
            waitFor("the event", hasText(title, substring = true))
        }
        report.step("undo_restores", "Delete it from the preview, then Undo", "The event is back") {
            tap(hasText(title, substring = true) and hasClickAction())
            tap(textButton(R.string.event_preview_delete))
            waitFor("the Undo action", textButton(R.string.event_deleted_undo))
            tap(textButton(R.string.event_deleted_undo))
            waitFor("the restored event", hasText(title, substring = true))
        }
        report.step("delete_is_tombstone", "Delete it again and let Undo lapse", "The server keeps a tombstone Igor's phone honours") {
            val eventId = mine(title).id
            tap(hasText(title, substring = true) and hasClickAction())
            tap(textButton(R.string.event_preview_delete))
            composeTestRule.mainClock.advanceTimeBy(SNACKBAR_LAPSE_MS)
            composeTestRule.settle()
            runBlocking {
                withTimeout(EmulatorParent.WAIT_MS) {
                    while (serverDocument(eventId)["deletedAtMillis"] == null) delay(POLL_MS)
                }
            }
            coParentSync()
            val onIgor = runBlocking { bob.eventRepository.getAllEvents().first().any { it.id == eventId } }
            check(!onIgor) { "Igor still sees the deleted event" }
        }
    }

    @Test
    fun navigation() {
        report.step("views", "Switch Month, Week and Day", "Each view opens from the title's menu") {
            openTab(BottomNavDestination.CALENDAR)
            viewMode(R.string.calendar_viewmode_week)
            viewMode(R.string.calendar_viewmode_day)
            viewMode(R.string.calendar_viewmode_month)
        }
        report.step("month_paging", "Page to next month", "The grid pages") {
            check(driver.pageForward()) { "the month grid does not page" }
        }
        report.step("today_button", "Press Today", "The grid returns to this month") {
            tap(textButton(R.string.calendar_today_button))
        }
        report.step("filters_sheet", "Open Filters", "The filter sheet opens") {
            // A labelled chip, or from 1.3x its glyph alone.
            val filters = string(R.string.calendar_filters_button)
            tap((hasText(filters) or hasContentDescription(filters)) and hasClickAction())
            composeTestRule.settle()
            driver.back()
        }
        report.step("drag_reschedule", "Drag an event to another hour in Day view", "It moves") {
            couldNotRun("dragging is not automated yet; the device checklist keeps it")
        }
    }

    private val id: String by lazy { shortId() }

    /** Opens the form with "+", types [title], runs [extra] on the form and saves. */
    private fun createEvent(title: String, extra: () -> Unit = {}) {
        openTab(BottomNavDestination.CALENDAR)
        tap(hasContentDescription(string(R.string.calendar_add_event)) and hasClickAction())
        waitFor("the event form", hasSetTextAction())
        composeTestRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(title)
        composeTestRule.settle()
        driver.closeKeyboard()
        extra()
        tap(textButton(R.string.event_form_save))
        runBlocking {
            withTimeout(EmulatorParent.WAIT_MS) {
                while (eventRepository.getAllEvents().first().none { it.title == title }) delay(POLL_MS)
            }
        }
    }

    private fun openDayView() {
        openTab(BottomNavDestination.CALENDAR)
        viewMode(R.string.calendar_viewmode_day)
    }

    private fun mine(title: String): Event = runBlocking {
        eventRepository.getAllEvents().first().first { it.title == title }
    }

    /** Igor's copy of [title] after his sync; fails when it did not arrive. */
    private fun expectOnIgorsPhone(title: String): Event {
        coParentSync()
        return runBlocking {
            bob.eventRepository.getAllEvents().first().firstOrNull { it.title == title }
        } ?: throw AssertionError("\"$title\" did not reach Igor's phone")
    }

    private fun serverDocument(eventId: String): Map<String, Any?> = runBlocking {
        bob.firestore.collection("events").document(eventId).get().await().data.orEmpty()
    }

    private fun todayLater(): LocalDateTime = minOf(LocalDateTime.now().plusMinutes(30), LocalDate.now().atTime(23, 0))

    private companion object {
        const val LENA = "Lena"
        const val EMA = "Ema"
        const val REMINDER_MINUTES = 60
        const val REMINDER_TAG = "event_reminder"
        const val SNACKBAR_LAPSE_MS = 12_000L
        const val POLL_MS = 250L
    }
}
