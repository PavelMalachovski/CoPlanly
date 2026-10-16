package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByOffer
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestDeadlineId
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.domain.changerequests.TimedRequest
import com.coparently.app.presentation.changerequests.CardClock
import com.coparently.app.presentation.changerequests.CardClockBanner
import com.coparently.app.presentation.changerequests.CardClockChip
import com.coparently.app.presentation.changerequests.CardClockDetails
import com.coparently.app.presentation.changerequests.ClockRole
import com.coparently.app.presentation.changerequests.LocalCardClock
import com.coparently.app.presentation.changerequests.RequestHistoryGroup
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.theme.Spacing
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The deadline block of the four request forms (phase 3a, spec §6.1) in each of its three offers: the
 * choices, a choice the cap shortened, the one fixed time, and the sentence for a request too near to
 * have a deadline. Then each part of an inbox card's clock on its own (spec §6.2–§6.6): the answering
 * parent's line, the proposer's rows before and after delivery and once opened, the deadline passed
 * with its button, the last-chance banner, and a "No answer" row in History. Text-heavy — dates,
 * plurals and helper lines in five languages at 2.0× in German — so the full
 * [ScreenshotVariants.TEXT_HEAVY] set.
 *
 * Every offer is made, and every clock is seen, at Wednesday 13 May 2026, 10:07 in Prague, so the times
 * in the images do not move with the day the suite runs on.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class RequestClockScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    private var savedZone: TimeZone? = null

    /** The offers are worked out in Prague's calendar, which the block then reads in its device zone. */
    @Before
    fun pinZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(PRAGUE))
    }

    /** Leaves the default zone as it was found, for the next test class in the process. */
    @After
    fun restoreZone() {
        savedZone?.let(TimeZone::setDefault)
    }

    /** No cap: the three presets and "Pick a date…", three days selected. */
    @Test
    fun answerByChoices() = snap("requests_answer_by_choices") {
        Block(AnswerBy.offer(NOW, null, ZONE))
    }

    /** A cap three days ahead: "A week" is shortened to the latest time, so it reads "By <time>". */
    @Test
    fun answerByCompressed() = snap("requests_answer_by_compressed") {
        Block(AnswerBy.offer(NOW, NOW + TimeUnit.DAYS.toMillis(3), ZONE))
    }

    /** A cap twenty hours ahead: no choice, one deadline halfway there. */
    @Test
    fun answerByFixed() = snap("requests_answer_by_fixed") {
        Block(AnswerBy.offer(NOW, NOW + TimeUnit.HOURS.toMillis(20), ZONE))
    }

    /** A cap an hour ahead: no deadline, and the request goes out as a plain one. */
    @Test
    fun answerByTooSoon() = snap("requests_answer_by_too_soon") {
        Block(AnswerBy.offer(NOW, NOW + TimeUnit.HOURS.toMillis(1), ZONE))
    }

    /** The answering parent's card, open: "Answer by <time> · <left>". */
    @Test
    fun clockResponderLine() = snap("requests_clock_responder") {
        Clocked(ClockRole.RESPONDER, CLOCK) { CardClockDetails() }
    }

    /** The proposer's card before the other phone has received it: "Sent" only. */
    @Test
    fun clockProposerSent() = snap("requests_clock_sent") {
        Clocked(ClockRole.PROPOSER, CLOCK) { CardClockDetails() }
    }

    /** Delivered, not opened yet, no reminder planned. */
    @Test
    fun clockProposerDelivered() = snap("requests_clock_delivered") {
        Clocked(ClockRole.PROPOSER, CLOCK.copy(deliveredAtMillis = NOW - HOUR, remindersPlanned = 0)) {
            CardClockDetails()
        }
    }

    /** Opened, with one reminder of three shown and the next still ahead. */
    @Test
    fun clockProposerOpened() = snap("requests_clock_opened") {
        Clocked(ClockRole.PROPOSER, DELIVERED.copy(seenAtMillis = NOW - 20 * MINUTE)) { CardClockDetails() }
    }

    /** The deadline passed with no answer: the chip, the rows and the last-chance button. */
    @Test
    fun clockNoAnswer() = snap("requests_clock_no_answer") {
        val passed = DELIVERED.copy(answerByMillis = NOW - 2 * HOUR, remindersShown = 3)
        Clocked(ClockRole.PROPOSER, passed, onGiveLastChance = {}) {
            CardClockChip()
            CardClockDetails()
        }
    }

    /** The answering parent in their last chance: the banner. */
    @Test
    fun clockLastChanceBanner() = snap("requests_clock_last_chance") {
        val lastChance = DELIVERED.copy(answerByMillis = NOW - 2 * HOUR, lastChanceUntilMillis = NOW + 16 * HOUR)
        Clocked(ClockRole.RESPONDER, lastChance) { CardClockBanner() }
    }

    /** History's "No answer" row with its meta line. */
    @Test
    fun historyNoAnswer() = snap("requests_history_no_answer") {
        val closed = DELIVERED.copy(
            answerByMillis = NOW - 30 * HOUR,
            seenAtMillis = NOW - 50 * HOUR,
            remindersShown = 3,
            lastChanceUntilMillis = NOW - 4 * HOUR,
            outcome = RequestClockOutcome.NO_ANSWER
        )
        RequestHistoryGroup(
            entries = listOf(
                HistoryEntry(
                    key = "cr_r1",
                    subject = RequestSubject.NewTime("e1", "Football", LocalDateTime.of(2026, 5, 16, 17, 30)),
                    outcome = RequestOutcome.NO_ANSWER,
                    askedBy = ScreenshotFixtures.MY_UID,
                    decidedBy = ScreenshotFixtures.CO_PARENT_UID,
                    decidedAt = LocalDateTime.of(2026, 5, 13, 6, 0),
                    clock = closed
                )
            ),
            parentNames = ScreenshotFixtures.parentNames,
            myUid = ScreenshotFixtures.MY_UID
        )
    }

    /** [content] with [clock] seen at [NOW] in [LocalCardClock], read from [role]'s side. */
    @Composable
    private fun Clocked(
        role: ClockRole,
        clock: RequestDeadline,
        onGiveLastChance: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        // The fixtures' clocks are this parent's own; the answering parent reads the co-parent's.
        val fromThisSide = when (role) {
            ClockRole.PROPOSER -> clock
            ClockRole.RESPONDER -> clock.copy(
                proposerUid = ScreenshotFixtures.CO_PARENT_UID,
                responderUid = ScreenshotFixtures.MY_UID
            )
        }
        val cardClock = CardClock(
            timed = TimedRequest(fromThisSide, NOW),
            role = role,
            otherName = ScreenshotFixtures.parentNames.labelForUid(ScreenshotFixtures.CO_PARENT_UID),
            onSeen = null,
            onGiveLastChance = onGiveLastChance
        )
        CompositionLocalProvider(LocalCardClock provides cardClock) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) { content() }
        }
    }

    @Composable
    private fun Block(offer: AnswerByOffer) {
        AnswerByField(
            offer = offer,
            selected = AnswerByPick.DEFAULT,
            onSelect = {},
            coParentName = ScreenshotFixtures.parentNames.coParentLabel()
        )
    }

    companion object {
        private const val PRAGUE = "Europe/Prague"
        private val ZONE: ZoneId = ZoneId.of(PRAGUE)

        /** The fixtures' today at 10:07: the moment every offer is made for. */
        private val NOW: Long = ScreenshotFixtures.TODAY.atTime(10, 7).atZone(ZONE).toInstant().toEpochMilli()

        private val MINUTE: Long = TimeUnit.MINUTES.toMillis(1)
        private val HOUR: Long = TimeUnit.HOURS.toMillis(1)

        /** A change request this parent sent at 8:07 with a deadline three days later; not delivered yet. */
        private val CLOCK = RequestDeadline(
            id = RequestDeadlineId.of(RequestKind.CHANGE, ScreenshotFixtures.MY_UID, "r1", ""),
            kind = RequestKind.CHANGE,
            ref = "r1",
            familyId = "${ScreenshotFixtures.MY_UID}__${ScreenshotFixtures.CO_PARENT_UID}",
            proposerUid = ScreenshotFixtures.MY_UID,
            responderUid = ScreenshotFixtures.CO_PARENT_UID,
            answerByMillis = NOW + TimeUnit.DAYS.toMillis(3) - 2 * HOUR,
            createdAtMillis = NOW - 2 * HOUR
        )

        /** The same, delivered with three reminders planned, one of them shown. */
        private val DELIVERED = CLOCK.copy(
            deliveredAtMillis = NOW - HOUR,
            remindersPlanned = 3,
            reminderTimesMillis = listOf(NOW - 30 * MINUTE, NOW + 22 * HOUR, NOW + 46 * HOUR),
            remindersShown = 1
        )

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
