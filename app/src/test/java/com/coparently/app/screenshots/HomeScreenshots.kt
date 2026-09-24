package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Column
import com.coparently.app.domain.custody.HandoverInfo
import com.coparently.app.domain.expenses.CurrencyBalance
import com.coparently.app.domain.expenses.ExpenseBalance
import com.coparently.app.domain.home.WeekEntry
import com.coparently.app.presentation.calendar.components.DayAgendaCard
import com.coparently.app.presentation.home.ChildWithParent
import com.coparently.app.presentation.home.CurrencyAmount
import com.coparently.app.presentation.home.HandoverHero
import com.coparently.app.presentation.home.MonthSpend
import com.coparently.app.presentation.home.StatTiles
import com.coparently.app.presentation.home.TimelineRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's cards: the handover hero, the "today" agenda card (with the co-parent's contact window,
 * MON-6b), the seven-day timeline and the stat tiles. All four carry translated sentences and
 * parent colours, so they take the full [ScreenshotVariants.TEXT_HEAVY] set.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class HomeScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun handoverHero() = snap("home_handover_hero") {
        HandoverHero(
            info = HandoverInfo(
                date = ScreenshotFixtures.TODAY.plusDays(HANDOVER_IN_DAYS),
                daysUntil = HANDOVER_IN_DAYS,
                fromParent = "mom",
                toParent = "dad"
            ),
            parentNames = ScreenshotFixtures.parentNames,
            onConfirm = {}
        )
    }

    /** FAM-4: a day the children are with different parents names each child with theirs. */
    @Test
    fun handoverHeroChildrenApart() = snap("home_handover_hero_children_apart") {
        HandoverHero(
            info = HandoverInfo(
                date = ScreenshotFixtures.TODAY.plusDays(HANDOVER_IN_DAYS),
                daysUntil = HANDOVER_IN_DAYS,
                fromParent = "mom",
                toParent = "dad"
            ),
            parentNames = ScreenshotFixtures.parentNames,
            onConfirm = {},
            childrenToday = listOf(ChildWithParent("Ema", "mom"), ChildWithParent("Tomáš", "dad"))
        )
    }

    @Test
    fun todayCard() = snap("home_today_card") {
        DayAgendaCard(
            date = ScreenshotFixtures.TODAY,
            events = ScreenshotFixtures.todayEvents,
            custody = ScreenshotFixtures.custodyFor(ScreenshotFixtures.TODAY),
            parentNames = ScreenshotFixtures.parentNames,
            onEventClick = {},
            contactWindows = ScreenshotFixtures.contactWindowsFor(ScreenshotFixtures.TODAY)
        )
    }

    @Test
    fun weekTimeline() = snap("home_week_timeline") {
        val entries = ScreenshotFixtures.monthEvents.values.flatten()
            .filter { it.startDateTime.toLocalDate() >= ScreenshotFixtures.TODAY }
            .sortedBy { it.startDateTime }
            .take(WEEK_ROWS)
        Column {
            entries.forEachIndexed { index, event ->
                TimelineRow(
                    entry = WeekEntry(
                        event = event,
                        dayParent = ScreenshotFixtures.custodyFor(event.startDateTime.toLocalDate()),
                        key = "${event.id}@${event.startDateTime}"
                    ),
                    parentNames = ScreenshotFixtures.parentNames,
                    isLast = index == entries.lastIndex,
                    onClick = {}
                )
            }
        }
    }

    @Test
    fun statTiles() = snap("home_stat_tiles") {
        StatTiles(
            spend = MonthSpend(
                byCurrency = listOf(
                    CurrencyAmount(currency = "CZK", amount = CZK_SPENT),
                    CurrencyAmount(currency = "EUR", amount = EUR_SPENT)
                )
            ),
            balances = listOf(
                CurrencyBalance(
                    currency = "CZK",
                    balance = ExpenseBalance(
                        momPaid = CZK_MOM_PAID,
                        dadPaid = CZK_SPENT - CZK_MOM_PAID,
                        total = CZK_SPENT,
                        netForCurrentUser = CZK_OWED_TO_ME,
                        splitKnown = true
                    )
                )
            ),
            unreadCount = UNREAD,
            onOpenExpenses = {},
            onOpenChat = {}
        )
    }

    companion object {
        private const val HANDOVER_IN_DAYS = 5L
        private const val WEEK_ROWS = 4
        private const val CZK_SPENT = 4_250.0
        private const val CZK_MOM_PAID = 3_000.0
        private const val CZK_OWED_TO_ME = 875.0
        private const val EUR_SPENT = 86.5
        private const val UNREAD = 3

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
