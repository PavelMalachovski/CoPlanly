package com.coparently.app.screenshots

import androidx.compose.runtime.Composable
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.domain.expenses.ExpenseBalance
import com.coparently.app.domain.home.WeekDay
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.presentation.calendar.components.DayAgendaCard
import com.coparently.app.presentation.home.ActivityItem
import com.coparently.app.presentation.home.ActivityKind
import com.coparently.app.presentation.home.ChildWithParent
import com.coparently.app.presentation.home.CurrencyAmount
import com.coparently.app.presentation.home.HandoverCard
import com.coparently.app.presentation.home.MoneyCard
import com.coparently.app.presentation.home.MonthSpend
import com.coparently.app.presentation.home.OpenRequestsCard
import com.coparently.app.presentation.home.RequestsRowState
import com.coparently.app.presentation.home.UpdatesCard
import com.coparently.app.presentation.home.WeekTiles
import com.coparently.app.presentation.payments.SettleUpPanelActions
import com.coparently.app.presentation.payments.SettleUpPanelState
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's cards (redesign, November 2026, variant A): the Updates card, the open requests, the
 * week's seven tiles, the handover card and the Money card in its five settle-up states, beside
 * the "today" agenda card (with the co-parent's contact window, MON-6b). All carry translated
 * sentences or parent colours, so they take the full [ScreenshotVariants.TEXT_HEAVY] set, 2.0×
 * German included.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = RobolectricTestApplication::class)
class HomeScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    /** The next handover, to the reader ("you", design item 23). */
    @Test
    fun handoverCard() = snap("home_handover_card") {
        HandoverCard(
            info = HandoverInfo(
                date = ScreenshotFixtures.TODAY.plusDays(HANDOVER_IN_DAYS).toKotlinLocalDate(),
                daysUntil = HANDOVER_IN_DAYS,
                fromParent = "dad",
                toParent = "mom"
            ),
            parentNames = ScreenshotFixtures.parentNames
        )
    }

    /** FAM-4: a day the children are with different parents names each child with theirs. */
    @Test
    fun handoverCardChildrenApart() = snap("home_handover_card_children_apart") {
        HandoverCard(
            info = HandoverInfo(
                date = ScreenshotFixtures.TODAY.plusDays(HANDOVER_IN_DAYS).toKotlinLocalDate(),
                daysUntil = HANDOVER_IN_DAYS,
                fromParent = "mom",
                toParent = "dad"
            ),
            parentNames = ScreenshotFixtures.parentNames,
            childrenToday = listOf(ChildWithParent("Ema", "mom"), ChildWithParent("Tomáš", "dad"))
        )
    }

    @Test
    fun todayCard() = snap("home_today_card") {
        DayAgendaCard(
            date = ScreenshotFixtures.TODAY.toKotlinLocalDate(),
            events = ScreenshotFixtures.todayEvents,
            custody = ScreenshotFixtures.custodyFor(ScreenshotFixtures.TODAY),
            parentNames = ScreenshotFixtures.parentNames,
            onEventClick = {},
            contactWindows = ScreenshotFixtures.contactWindowsFor(ScreenshotFixtures.TODAY)
        )
    }

    /** This week's seven tiles in each day's custody wash, today ringed, dots on days with events. */
    @Test
    fun weekTiles() = snap("home_week_tiles") {
        val events = ScreenshotFixtures.monthEvents.values.flatten()
        WeekTiles(
            days = (0L until WEEK_DAYS).map { offset ->
                val date = ScreenshotFixtures.TODAY.plusDays(offset)
                WeekDay(
                    date = date.toKotlinLocalDate(),
                    dayParent = ScreenshotFixtures.custodyFor(date),
                    hasEvents = events.any { it.startDateTime.date.toJavaLocalDate() == date }
                )
            },
            parentNames = ScreenshotFixtures.parentNames,
            onOpenDay = {}
        )
    }

    /** The co-parent's feed: an accepted swap, a declined new time, an event added and one changed. */
    @Test
    fun updatesCard() = snap("home_updates_card") {
        val at = ScreenshotFixtures.TODAY.atTime(UPDATE_HOUR, 0).toKotlinLocalDateTime()
        val events = ScreenshotFixtures.monthEvents.values.flatten()
        UpdatesCard(
            items = listOf(
                answerItem(
                    key = "swap",
                    subject = RequestSubject.DaySwap(
                        firstDate = ScreenshotFixtures.TODAY.plusDays(SWAP_IN_DAYS).toKotlinLocalDate(),
                        dayCount = 2
                    ),
                    outcome = RequestOutcome.ACCEPTED,
                    at = at
                ),
                answerItem(
                    key = "time",
                    subject = RequestSubject.NewTime(events[0].id, events[0].title, events[0].startDateTime),
                    outcome = RequestOutcome.DECLINED,
                    at = at
                ),
                ActivityItem("ev1", ActivityKind.EVENT_CREATED, events[1].title, at, events[1].id, false),
                ActivityItem("ev2", ActivityKind.EVENT_UPDATED, events[2].title, at, events[2].id, false)
            ),
            coParentName = "Pavel",
            onOpenChangeRequests = {},
            onOpenRequestHistory = {},
            onOpenEvent = {}
        )
    }

    /** The empty feed says so inside the card, naming the co-parent. */
    @Test
    fun updatesCardEmpty() = snap("home_updates_card_empty") {
        UpdatesCard(
            items = emptyList(),
            coParentName = "Pavel",
            onOpenChangeRequests = {},
            onOpenRequestHistory = {},
            onOpenEvent = {}
        )
    }

    /** What waits on each parent, with Review. */
    @Test
    fun openRequestsCard() = snap("home_open_requests_card") {
        OpenRequestsCard(
            state = RequestsRowState(waitingOnMe = 2, waitingOnThem = 1),
            coParentName = "Pavel",
            onOpenChangeRequests = {}
        )
    }

    /** The payee, before anything is recorded: "Ask Pavel to settle up" (board MoneySettleUp, 1). */
    @Test
    fun moneyCardAskToSettle() = snap("home_money_card_ask") {
        moneyCard(
            net = CZK_OWED_TO_ME,
            panel = SettleUpPanelState.AskToSettle(MONTH, "CZK", CZK_OWED_MINOR, CZK_OWED_TO_ME)
        )
    }

    /** The payer: the payment code for Pavel's account, the IBAN, Share and "I paid" (2). */
    @Test
    fun moneyCardPay() = snap("home_money_card_pay") {
        moneyCard(net = -CZK_OWED_TO_ME, panel = SettleUpPanelState.Pay(MONTH, "CZK", CZK_OWED_MINOR))
    }

    /** The payer after "I paid": waiting for Pavel, the code again on request (3). */
    @Test
    fun moneyCardWaitingForConfirm() = snap("home_money_card_waiting") {
        moneyCard(
            net = -CZK_OWED_TO_ME,
            panel = SettleUpPanelState.WaitingForConfirm(MONTH, "CZK", CZK_OWED_MINOR, PAID_AT_MILLIS)
        )
    }

    /** The payee, once Pavel says he paid: "Money arrived" (4). */
    @Test
    fun moneyCardConfirm() = snap("home_money_card_confirm") {
        moneyCard(
            net = CZK_OWED_TO_ME,
            panel = SettleUpPanelState.Confirm(MONTH, "CZK", CZK_OWED_MINOR, PAID_AT_MILLIS, paymentByCoParent)
        )
    }

    /** Both phones once the money arrived: settled, and the balance reads square (5). */
    @Test
    fun moneyCardSettled() = snap("home_money_card_settled") {
        moneyCard(net = CZK_OWED_TO_ME, panel = SettleUpPanelState.Settled(MONTH, "CZK", CZK_OWED_MINOR))
    }

    @Composable
    private fun moneyCard(net: Double, panel: SettleUpPanelState) {
        MoneyCard(
            month = MONTH,
            spend = MonthSpend(byCurrency = listOf(CurrencyAmount(currency = "CZK", amount = CZK_SPENT))),
            balances = listOf(
                CurrencyBalance(
                    currency = "CZK",
                    balance = ExpenseBalance(
                        momPaid = CZK_MOM_PAID,
                        dadPaid = CZK_SPENT - CZK_MOM_PAID,
                        total = CZK_SPENT,
                        netForCurrentUser = net,
                        splitKnown = true
                    )
                )
            ),
            panels = listOf(panel),
            coParentName = CO_PARENT_NAME,
            payeeIban = CO_PARENT_IBAN,
            actionsFor = { SettleUpPanelActions(onAskToSettle = {}, onMarkPaid = {}, onConfirm = {}) },
            onOpenExpenses = {}
        )
    }

    private val paymentByCoParent = Payment(
        id = "fam__expenses__2026-05__CZK",
        familyId = "fam",
        kind = PaymentKind.EXPENSES,
        month = MONTH,
        currency = "CZK",
        amountMinor = CZK_OWED_MINOR,
        payerUid = ScreenshotFixtures.CO_PARENT_UID,
        payeeUid = ScreenshotFixtures.MY_UID,
        paidAtMillis = PAID_AT_MILLIS
    )

    private fun answerItem(key: String, subject: RequestSubject, outcome: RequestOutcome, at: LocalDateTime) =
        ActivityItem(
            id = "ans_$key",
            kind = ActivityKind.ANSWER,
            title = "",
            timestamp = at,
            eventId = "",
            isChangeRequest = false,
            answer = HistoryEntry(
                key = key,
                subject = subject,
                outcome = outcome,
                askedBy = ScreenshotFixtures.MY_UID,
                decidedBy = ScreenshotFixtures.CO_PARENT_UID,
                decidedAt = at
            )
        )

    companion object {
        private const val HANDOVER_IN_DAYS = 5L
        private const val WEEK_DAYS = 7L
        private const val SWAP_IN_DAYS = 9L
        private const val UPDATE_HOUR = 9
        private const val CZK_SPENT = 4_250.0
        private const val CZK_MOM_PAID = 3_000.0
        private const val CZK_OWED_TO_ME = 875.0
        private const val CZK_OWED_MINOR = 87_500L
        private const val CO_PARENT_NAME = "Pavel"

        /** A valid Czech IBAN (the well-known example account), so the card draws QR Platba. */
        private const val CO_PARENT_IBAN = "CZ6508000000192000145399"

        /** 22 May 2026, 12:00 UTC: the day the payer said they paid, in every zone CI runs in. */
        private const val PAID_AT_MILLIS = 1_779_451_200_000L

        /** The fixtures' month, May 2026, as the money card takes it. */
        private val MONTH = YearMonth(2026, 5)

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
