package com.coparently.app.qa

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.R
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.PatternSubmission
import com.coparently.app.data.requests.RequestClockSteps
import com.coparently.app.data.requests.StepResult
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestStart
import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.CustodyKey
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.custody.DayOverrideTransition
import com.coparently.app.domain.model.CustodyModel
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.e2e.EmulatorEnvironment
import com.coparently.app.e2e.EmulatorParent
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.utils.unbreakableDate
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Day swaps between the two parents, with a deadline — Zuzana's "a month without an answer".
 * Both screens: the one that proposes (Zuzana) and the one that answers (Igor, `…AsIgor`); then
 * the request nobody answers, from its deadline to "No answer" in History on both phones, and the
 * one last chance the proposer may give.
 *
 * A deadline on screen is never shorter than three days, so the expiry runs on a short clock
 * started through the data layer, and the clock's steps are run directly, as WorkManager would run
 * them (`TwoParentRequestDeadlinesTest` does the same). No step waits for a reminder: reminders are
 * kept out of the night on the runner's clock, and the run may be at night.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SwapScenarioTest : QaOnScreenTest() {

    @Inject
    lateinit var custodyRepository: CustodyModelRepository

    @Inject
    lateinit var requestDeadlineRepository: RequestDeadlineRepository

    @Inject
    lateinit var requestClockSteps: RequestClockSteps

    @Test
    fun answerOnScreenAsIgor() {
        lateinit var agreed: CustodyModel
        val first = LocalDate.now().plusDays(FIRST_OFFSET)
        val second = LocalDate.now().plusDays(SECOND_OFFSET)
        report.step("pattern", "$coParentName sets week on, week off", "Igor's phone holds the pattern") {
            agreed = runBlocking { coParentSetsFirstPattern() }
        }
        report.step("home_popup", "$coParentName offers a day with a three-day deadline", "Igor's Home asks him about that day") {
            runBlocking { coParentOffers(agreed, first, AnswerByPick.DEFAULT) }
            awaitDelivered(this, "the swap pop-up", swapDialogFor(first), expectsListener = true)
        }
        report.step("decline_on_screen", "Igor declines", "$coParentName's phone records the decline") {
            tap(dialogButton(R.string.day_swap_decline))
            runBlocking { awaitCoParentOverride(first) { it.status == DayOverrideStatus.DECLINED && it.decidedBy == aliceUid } }
        }
        report.step("accept_on_screen", "$coParentName offers another day; Igor accepts", "$coParentName's phone records the agreement") {
            runBlocking { coParentOffers(agreed, second, AnswerByPick.DEFAULT) }
            awaitDelivered(this, "the second swap pop-up", swapDialogFor(second), expectsListener = true)
            tap(dialogButton(R.string.day_swap_accept))
            runBlocking { awaitCoParentOverride(second) { it.isAccepted && it.decidedBy == aliceUid } }
        }
        report.step("history", "Open Requests from Home", "History lists both answers") {
            openRequests()
            waitFor("the agreed day", hasText(string(R.string.day_swap_inbox_accepted), substring = true))
            waitFor("the turned-down day", hasText(string(R.string.day_swap_inbox_declined), substring = true))
        }
    }

    @Test
    fun proposeOnScreen() {
        val today = LocalDate.now()
        val familyId by lazy { CustodyKey.of(aliceUid, bob.uid) }
        lateinit var groupId: String
        report.step("pattern", "Igor sets week on, week off", "Zuzana's phone holds the pattern") {
            runBlocking { coParentSetsFirstPattern() }
        }
        report.step("deadline_picker", "Swap today from Day view with a three-day deadline", "The sheet takes the deadline and the offer is sent") {
            openTab(BottomNavDestination.CALENDAR)
            viewMode(R.string.calendar_viewmode_day)
            tap(textButton(R.string.day_swap_sheet_title))
            waitFor("the swap sheet", textButton(R.string.day_swap_offer))
            tap(hasText(string(R.string.answer_by_three_days)) and hasClickAction())
            tap(textButton(R.string.day_swap_offer))
            groupId = runBlocking { awaitCoParentOverride(today) { it.status == DayOverrideStatus.PENDING }.groupId }
                ?: error("the offer carries no group id")
        }
        report.step("coparent_sees_deadline", "What reaches Igor", "Igor's phone holds the request and its deadline; his push names it") {
            val clock = runBlocking {
                withTimeout(EmulatorParent.WAIT_MS) {
                    var found = bob.requestDeadlineRepository.get(RequestDeadlineId.of(RequestKind.SWAP, aliceUid, groupId, familyId))
                    while (found == null) {
                        delay(POLL_MS)
                        found = bob.requestDeadlineRepository.get(RequestDeadlineId.of(RequestKind.SWAP, aliceUid, groupId, familyId))
                    }
                    found
                }
            }
            val push = runBlocking { EmulatorEnvironment.awaitQueuedPush(bob.uid, "day_swap_offered") }
            check(push["answerBy"] == clock.answerByMillis.toString()) {
                "the push says answer by ${push["answerBy"]}, the clock ${clock.answerByMillis}"
            }
            actual = "deadline ${clock.answerByMillis} on Igor's phone and in his push"
        }
        report.step("grid_follows", "Igor accepts", "Zuzana's phone holds the agreed day") {
            runBlocking {
                val date = today.toString()
                val now = LocalDateTime.now().toString()
                bob.custodyRepository.applyDayOverridesForDates(
                    dates = listOf(date),
                    transform = { current, each ->
                        if (DayOverrideTransition.awaitsAnswerFrom(current, each, bob.uid)) {
                            DayOverrideTransition.decideGroup(current, listOf(each), bob.uid, now, true)
                        } else {
                            Result.success(current)
                        }
                    }
                ).getOrThrow()
                withTimeout(EmulatorParent.WAIT_MS) {
                    custodyRepository.observeDayOverrides().first { it[date]?.isAccepted == true }
                }
            }
            openTab(BottomNavDestination.CALENDAR)
            viewMode(R.string.calendar_viewmode_month)
        }
    }

    @Test
    fun noAnswer() {
        val day = LocalDate.now().plusDays(FIRST_OFFSET)
        lateinit var clockId: String
        report.step("offer_short_clock", "Zuzana offers a day; its deadline is a minute away", "The request waits on Igor") {
            clockId = runBlocking { offerWithShortClock(day) }
        }
        report.step("deadline_passes", "The deadline passes with no answer", "Zuzana's Requests offer her one last chance") {
            runBlocking { waitPast(checkNotNull(requestDeadlineRepository.get(clockId)).answerByMillis) }
            openRequests()
            waitFor("the last-chance button", lastChanceButton)
        }
        report.step("no_answer_history_proposer", "The request ends without an answer", "History says No answer on Zuzana's phone") {
            runBlocking { awaitStep { requestClockSteps.end(clockId) } }
            driver.back()
            openRequests()
            waitFor("No answer in History", hasText(string(R.string.request_clock_no_answer), substring = true))
        }
        report.step("no_answer_coparent", "What Igor's phone reads", "The request is closed as No answer for Igor too", independent = true) {
            val clock = runBlocking { bob.requestDeadlineRepository.get(clockId) }
            check(clock?.outcome == RequestClockOutcome.NO_ANSWER) { "Igor reads the request as ${clock?.outcome}" }
        }
    }

    @Test
    fun lastChance() {
        val day = LocalDate.now().plusDays(SECOND_OFFSET)
        lateinit var clockId: String
        report.step("offer_short_clock", "Zuzana offers a day; its deadline is a minute away", "The request waits on Igor") {
            clockId = runBlocking { offerWithShortClock(day) }
            runBlocking { waitPast(checkNotNull(requestDeadlineRepository.get(clockId)).answerByMillis) }
        }
        report.step("give_last_chance", "Zuzana gives one last chance from Requests", "Igor gets a last-chance push with the new deadline") {
            openRequests()
            waitFor("the last-chance button", lastChanceButton)
            tap(lastChanceButton)
            val push = runBlocking { EmulatorEnvironment.awaitQueuedPush(bob.uid, "request_last_chance") }
            val clock = runBlocking { requestDeadlineRepository.get(clockId) }
            check(push["answerBy"] == clock?.lastChanceUntilMillis?.toString()) {
                "the push says ${push["answerBy"]}, the clock ${clock?.lastChanceUntilMillis}"
            }
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private val lastChanceButton: SemanticsMatcher
        get() = hasText(string(R.string.request_clock_give_last_chance).substringBefore("—").trim(), substring = true) and
            hasClickAction()

    private fun openRequests() {
        openTab(BottomNavDestination.HOME)
        tap(hasText(string(R.string.home_requests_title)) and hasClickAction())
    }

    private suspend fun slots(): Pair<String, String> {
        val mine = checkNotNull(bob.userRepository.getRemoteUserProfile(aliceUid)).role
        val theirs = checkNotNull(bob.userRepository.getRemoteUserProfile(bob.uid)).role
        check(mine != theirs) { "the pair still shares one slot" }
        return mine to theirs
    }

    /** The co-parent's first pattern, week on, week off from this week, the first week on screen's. */
    private suspend fun coParentSetsFirstPattern(): CustodyModel {
        val (mine, _) = slots()
        val submission = bob.custodyRepository.createWeekOnWeekOff(LocalDate.now(), momFirst = mine == ContactWindow.SLOT_ONE)
        check(submission == PatternSubmission.ACTIVATED) { "the first pattern was $submission" }
        val model = checkNotNull(bob.custodyRepository.getActiveModelSync())
        withTimeout(EmulatorParent.WAIT_MS) {
            custodyRepository.getActiveModel().first { it?.startDate == model.startDate }
        }
        return model
    }

    private suspend fun coParentOffers(agreed: CustodyModel, date: LocalDate, answerBy: AnswerByPick?) {
        val iso = date.toString()
        val toSlot = if (agreed.getCustodyFor(date) == ContactWindow.SLOT_ONE) ContactWindow.SLOT_TWO else ContactWindow.SLOT_ONE
        val groupId = UUID.randomUUID().toString()
        val at = LocalDateTime.now().toString()
        bob.custodyRepository.applyDayOverrides(
            date = iso,
            transform = { current -> DayOverrideTransition.offer(current, iso, toSlot, bob.uid, at, groupId = groupId) },
            answerBy = answerBy
        ).getOrThrow()
    }

    /** Zuzana offers [day] through her app's repository and starts a clock a minute long. */
    private suspend fun offerWithShortClock(day: LocalDate): String {
        val agreed = coParentSetsFirstPattern()
        val iso = day.toString()
        val toSlot = if (agreed.getCustodyFor(day) == ContactWindow.SLOT_ONE) ContactWindow.SLOT_TWO else ContactWindow.SLOT_ONE
        val groupId = UUID.randomUUID().toString()
        val at = LocalDateTime.now().toString()
        custodyRepository.applyDayOverrides(
            date = iso,
            transform = { current -> DayOverrideTransition.offer(current, iso, toSlot, aliceUid, at, groupId = groupId) }
        ).getOrThrow()
        val familyId = CustodyKey.of(aliceUid, bob.uid)
        val clock = requestDeadlineRepository.start(
            RequestStart(
                kind = RequestKind.SWAP,
                ref = groupId,
                familyId = familyId,
                responderUid = bob.uid,
                answerByMillis = System.currentTimeMillis() + SHORT_DEADLINE_MS,
                capMillis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                firstDate = day,
                dayCount = 1
            )
        ) ?: error("the rules refused the clock")
        return clock.id
    }

    private suspend fun awaitCoParentOverride(date: LocalDate, condition: (DayOverride) -> Boolean): DayOverride =
        withTimeout(EmulatorParent.WAIT_MS) {
            bob.custodyRepository.observeDayOverrides()
                .first { overrides -> overrides[date.toString()]?.let(condition) == true }
                .getValue(date.toString())
        }

    private suspend fun waitPast(millis: Long) {
        val wait = millis + PAST_MARGIN_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
    }

    private suspend fun awaitStep(block: suspend () -> StepResult): StepResult = withTimeout(EmulatorParent.WAIT_MS) {
        var result = block()
        while (result == StepResult.RETRY) {
            delay(POLL_MS)
            result = block()
        }
        result
    }

    /** Home's swap pop-up for [date] offered by the co-parent, worded as `AwaitingDialogs` words it. */
    private fun swapDialogFor(date: LocalDate): SemanticsMatcher = hasAnyAncestor(isDialog()) and hasText(
        string(
            R.string.home_dialog_swap_message,
            coParentName,
            unbreakableDate(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.forLanguageTag(variant.language))))
        )
    )

    private companion object {
        const val FIRST_OFFSET = 2L
        const val SECOND_OFFSET = 9L
        const val SHORT_DEADLINE_MS = 45_000L
        const val PAST_MARGIN_MS = 3_000L
        const val POLL_MS = 250L
    }
}
