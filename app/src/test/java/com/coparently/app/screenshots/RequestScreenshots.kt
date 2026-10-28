package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.domain.events.EventAcceptance
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalRole
import com.coparently.app.domain.support.SupportProposal
import com.coparently.app.domain.support.SupportTerms
import com.coparently.app.presentation.calendar.MiniMonthMark
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import com.coparently.app.presentation.changerequests.AwaitingEventActions
import com.coparently.app.presentation.changerequests.AwaitingEventCard
import com.coparently.app.presentation.changerequests.PlanRequestRow
import com.coparently.app.presentation.changerequests.ProfessionalRequestRow
import com.coparently.app.presentation.changerequests.RequestHistoryGroup
import com.coparently.app.presentation.changerequests.SupportRequestRow
import com.coparently.app.presentation.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal
import java.time.YearMonth

/**
 * What a parent answers a request with (phase 2): the mini month, the card of an event waiting
 * for an answer and the inbox's history. Text-heavy — names, dates and German at 2.0× — so the
 * full [ScreenshotVariants.TEXT_HEAVY] set.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class RequestScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    /** May 2026 with the fixtures' custody, their events' dots and a marked day eight days ahead. */
    @Test
    fun miniMonth() = snap("requests_mini_month") {
        MiniMonth(
            model = MiniMonthModel.build(
                month = ScreenshotFixtures.MONTH,
                custodyFor = ScreenshotFixtures::custodyFor,
                events = ScreenshotFixtures.monthEvents.values.flatten(),
                marks = mapOf(ScreenshotFixtures.TODAY.plusDays(MARKED_DAY_OFFSET) to MiniMonthMark.NEW_EVENT)
            ),
            parentNames = ScreenshotFixtures.parentNames,
            today = ScreenshotFixtures.TODAY
        )
    }

    /**
     * The card the parent asked sees: Pavel added an event to Olya's days, with three lines of his
     * own words — enough to reach the six-line ellipsis at 2.0× — and its month below.
     */
    @Test
    fun awaitingEvent() = snap("requests_awaiting_event") {
        val event = ScreenshotFixtures.event(
            id = "pending-1",
            title = "Dentist check-up",
            start = ScreenshotFixtures.TODAY.plusDays(OLYAS_DAY_OFFSET).atTime(16, 0),
            owner = "mom",
            durationMinutes = 90
        ).copy(
            description = "Mia's check-up at the clinic on Vinohradská.\n" +
                "Could you take her? I have a work meeting until five.\n" +
                "Her insurance card is in the blue folder by the door.",
            createdByFirebaseUid = ScreenshotFixtures.CO_PARENT_UID,
            acceptance = EventAcceptance.PENDING
        )
        AwaitingEventCard(
            event = event,
            miniMonth = MiniMonthModel.forAwaitingEvent(event, monthEvents, ScreenshotFixtures::custodyFor),
            parentNames = ScreenshotFixtures.parentNames,
            actions = AwaitingEventActions(onAccept = {}, onDecline = {})
        )
    }

    /** The creator's view of the same kind of card: no words of their own, no buttons, waiting. */
    @Test
    fun awaitingEventPlain() = snap("requests_awaiting_event_plain") {
        val event = ScreenshotFixtures.event(
            id = "pending-2",
            title = "Swimming gala",
            start = ScreenshotFixtures.TODAY.plusDays(MARKED_DAY_OFFSET).atTime(17, 0),
            owner = "dad"
        ).copy(
            createdByFirebaseUid = ScreenshotFixtures.MY_UID,
            acceptance = EventAcceptance.PENDING
        )
        AwaitingEventCard(
            event = event,
            miniMonth = MiniMonthModel.forAwaitingEvent(event, monthEvents, ScreenshotFixtures::custodyFor),
            parentNames = ScreenshotFixtures.parentNames,
            actions = null
        )
    }

    /** The history: every subject and every outcome, and an answer of Olya's still queued. */
    @Test
    fun history() = snap("requests_history") {
        RequestHistoryGroup(
            entries = historyEntries,
            parentNames = ScreenshotFixtures.parentNames,
            myUid = ScreenshotFixtures.MY_UID
        )
    }

    /**
     * Child support, a professional's access and the plan in the inbox (board SW-7): Pavel's three
     * as Olya reads them, then her own support proposal and consent, each with Withdraw.
     */
    @Test
    fun agreementRows() = snap("requests_agreements") {
        val terms = SupportTerms(BigDecimal("3500"), "CZK", 15, ScreenshotFixtures.CO_PARENT_UID, YearMonth.of(2026, 6))
        val parents = listOf(ScreenshotFixtures.MY_UID, ScreenshotFixtures.CO_PARENT_UID)
        fun grant(consentBy: String) = ProfessionalGrant(
            id = "family__pro",
            familyId = "family",
            familyParents = parents,
            proUid = "pro",
            role = ProfessionalRole.MEDIATOR,
            name = "Dana Nováková",
            invitedBy = consentBy,
            grantedAtMillis = 1L,
            expiresAtMillis = Long.MAX_VALUE,
            consents = mapOf(consentBy to 1L)
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            val names = ScreenshotFixtures.parentNames
            SupportRequestRow(SupportProposal(terms, ScreenshotFixtures.CO_PARENT_UID, 1L), false, names, {}, {})
            ProfessionalRequestRow(grant(ScreenshotFixtures.CO_PARENT_UID), false, names, {}, {})
            PlanRequestRow(PLAN_QUESTIONS, names.labelForUid(ScreenshotFixtures.CO_PARENT_UID)) {}
            SupportRequestRow(SupportProposal(terms, ScreenshotFixtures.MY_UID, 1L), true, names, {}, {})
            ProfessionalRequestRow(grant(ScreenshotFixtures.MY_UID), true, names, {}, {})
        }
    }

    companion object {
        /** How many plan questions Pavel's answers leave waiting. */
        private const val PLAN_QUESTIONS = 3

        /** How far ahead of the fixtures' today the marked day sits — one of Pavel's days. */
        private const val MARKED_DAY_OFFSET = 8L

        /** A day of Olya's week, for the event Pavel adds to her days. */
        private const val OLYAS_DAY_OFFSET = 14L

        private val monthEvents = ScreenshotFixtures.monthEvents.values.flatten()

        private fun at(day: Int, hour: Int, minute: Int = 0) = ScreenshotFixtures.MONTH.atDay(day).atTime(hour, minute)

        /** Newest first, as the inbox lists them. */
        private val historyEntries = listOf(
            HistoryEntry(
                key = "cr_queued",
                subject = RequestSubject.NewTime("e1", "Football", at(16, 17, 30)),
                outcome = RequestOutcome.DECLINED,
                askedBy = ScreenshotFixtures.CO_PARENT_UID,
                decidedBy = ScreenshotFixtures.MY_UID,
                decidedAt = at(12, 20, 15),
                queued = true
            ),
            HistoryEntry(
                key = "custody_1",
                subject = RequestSubject.CustodySchedule,
                outcome = RequestOutcome.ACCEPTED,
                askedBy = ScreenshotFixtures.CO_PARENT_UID,
                decidedBy = ScreenshotFixtures.MY_UID,
                decidedAt = at(11, 9, 0)
            ),
            HistoryEntry(
                key = "swap_g1",
                subject = RequestSubject.DaySwap(ScreenshotFixtures.MONTH.atDay(8), 3),
                outcome = RequestOutcome.DECLINED,
                askedBy = ScreenshotFixtures.MY_UID,
                decidedBy = ScreenshotFixtures.CO_PARENT_UID,
                decidedAt = at(6, 18, 45)
            ),
            HistoryEntry(
                key = "ev_e2",
                subject = RequestSubject.NewEvent("e2", "Parents' evening", at(5, 18, 0)),
                outcome = RequestOutcome.ACCEPTED,
                askedBy = ScreenshotFixtures.MY_UID,
                decidedBy = ScreenshotFixtures.CO_PARENT_UID,
                decidedAt = at(3, 8, 10)
            ),
            HistoryEntry(
                key = "cr_withdrawn_mine",
                subject = RequestSubject.NewTime("e3", "Dentist", at(4, 15, 0)),
                outcome = RequestOutcome.WITHDRAWN,
                askedBy = ScreenshotFixtures.MY_UID,
                decidedBy = ScreenshotFixtures.MY_UID,
                decidedAt = at(2, 11, 0)
            ),
            HistoryEntry(
                key = "cr_withdrawn_theirs",
                subject = RequestSubject.NewTime("e4", "Vet", at(2, 10, 0)),
                outcome = RequestOutcome.WITHDRAWN,
                askedBy = ScreenshotFixtures.CO_PARENT_UID,
                decidedBy = ScreenshotFixtures.CO_PARENT_UID,
                decidedAt = at(1, 19, 30)
            )
        )

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
