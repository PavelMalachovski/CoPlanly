package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.presentation.calendar.MonthView
import com.coparently.app.presentation.calendar.SwapDaysButton
import com.coparently.app.presentation.calendar.SwapMark
import com.coparently.app.presentation.calendar.components.ChangeRequestBanner
import com.coparently.app.presentation.calendar.components.CustodyChangedBanner
import com.coparently.app.presentation.calendar.components.CustodyLegend
import com.coparently.app.presentation.calendar.components.DaySwapSelectionBar
import com.coparently.app.presentation.calendar.components.DaySwapSheetContent
import com.coparently.app.presentation.calendar.components.SwapSheetActions
import com.coparently.app.presentation.calendar.components.SwapSheetOffer
import com.coparently.app.presentation.calendar.components.VacationBanner
import com.coparently.app.presentation.calendar.swapMiniMonths
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The calendar's month grid with every layer `DayCellFills` stacks — the weekend base, the custody
 * band with its Monday handover diagonal, the two public holidays, event dots, a pending swap,
 * the Wednesday contact-window corners (MON-6b) and the school-vacation line across the month's
 * end (MON-13) — plus the three banners that sit over it.
 *
 * The grid is where a raw `MomPink`/`DadBlue` would be most visible, and where light theme was
 * least reviewed; its weekday header and banners are translated, so it takes the full set.
 *
 * @param variant Language, theme, font scale and palette for this run
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class CalendarScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun monthGrid() = snap("calendar_month_grid") { MonthGridFixture() }

    @Test
    fun banners() = snap("calendar_banners") {
        Column(verticalArrangement = Arrangement.spacedBy(BANNER_GAP)) {
            ChangeRequestBanner(pendingCount = PENDING_REQUESTS, onReview = {})
            VacationBanner(label = "Summer holidays · 27 Jun – 31 Aug")
            CustodyChangedBanner(byName = "Pavel", onDismiss = {})
        }
    }

    /**
     * The legend over the grid: both parents, the handover diagonal, the contact-time corner, a
     * swap waiting for an answer, and the "⇄" that starts a swap.
     */
    @Test
    fun legend() = snap("calendar_legend") {
        CustodyLegend(
            parentNames = ScreenshotFixtures.parentNames,
            showContactWindows = true,
            showPendingSwap = true,
            trailing = { SwapDaysButton(onClick = {}) }
        )
    }

    /** The bar while days are chosen: a line each way, and a day nobody is recorded for. */
    @Test
    fun selectionBar() = snap("calendar_swap_selection_bar") {
        val month = ScreenshotFixtures.MONTH
        DaySwapSelectionBar(
            sides = SwapSides(
                toOther = listOf(month.atDay(SWAP_GIVE_DAY), month.atDay(SWAP_GIVE_DAY + 1)),
                toOfferer = listOf(month.atDay(SWAP_TAKE_DAY)),
                unknown = listOf(month.atDay(SWAP_TAKE_DAY + 1))
            ),
            parentNames = ScreenshotFixtures.parentNames,
            onNext = {},
            onCancel = {}
        )
    }

    /** The review sheet for an offer that gives two days and takes one, with its month. */
    @Test
    fun swapSheet() = snap("calendar_swap_sheet") {
        val month = ScreenshotFixtures.MONTH
        val give = listOf(month.atDay(SWAP_GIVE_DAY), month.atDay(SWAP_GIVE_DAY + 1))
        val take = listOf(month.atDay(SWAP_TAKE_DAY))
        DaySwapSheetContent(
            offer = SwapSheetOffer(
                sides = SwapSides(toOther = give, toOfferer = take),
                coParentName = "Bob",
                months = swapMiniMonths(
                    days = give.associateWith { "dad" } + take.associateWith { "mom" },
                    familyEvents = emptyList(),
                    custodyFor = ScreenshotFixtures::custodyFor
                )
            ),
            parentNames = ScreenshotFixtures.parentNames,
            actions = SwapSheetActions(onOffer = { _, _, _ -> }, onCancel = {}, onChangeDays = {})
        )
    }

    companion object {
        private val BANNER_GAP = 8.dp
        private const val PENDING_REQUESTS = 2
        private const val SWAP_GIVE_DAY = 13
        private const val SWAP_TAKE_DAY = 20

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}

private val MONTH_GRID_HEIGHT = 560.dp
private const val PENDING_SWAP_DAY = 27

/** The day the fixture shows being chosen for a swap. */
private const val CHOSEN_SWAP_DAY = 20

/**
 * May 2026's grid with every `DayCellFills` layer: custody with contact windows, a holiday,
 * school vacation, a swap waiting for an answer, a day being chosen for one, and events. Shared
 * with [ContrastScreenshots].
 */
@Composable
internal fun MonthGridFixture() {
    Box(modifier = Modifier.height(MONTH_GRID_HEIGHT)) {
        MonthView(
            selectedMonth = ScreenshotFixtures.MONTH,
            eventsByDay = ScreenshotFixtures.monthEvents,
            getCustody = ScreenshotFixtures::custodyFor,
            getContactWindows = ScreenshotFixtures::contactWindowsFor,
            parentNames = ScreenshotFixtures.parentNames,
            onDayClick = {},
            onMonthChange = {},
            holidays = ScreenshotFixtures.holidays,
            schoolVacationDays = ScreenshotFixtures.schoolVacationDays,
            // Both forms of a swap mark: a day waiting for an answer, and one being chosen.
            swapMarks = mapOf(
                ScreenshotFixtures.MONTH.atDay(PENDING_SWAP_DAY) to SwapMark(toSlot = "dad", pending = true),
                ScreenshotFixtures.MONTH.atDay(CHOSEN_SWAP_DAY) to SwapMark(toSlot = "mom", pending = false)
            )
        )
    }
}
