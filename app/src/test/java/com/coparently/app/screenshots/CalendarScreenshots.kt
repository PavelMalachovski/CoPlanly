package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.calendar.MonthView
import com.coparently.app.presentation.calendar.components.ChangeRequestBanner
import com.coparently.app.presentation.calendar.components.CustodyChangedBanner
import com.coparently.app.presentation.calendar.components.VacationBanner
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The calendar's month grid with every layer `DayCellFills` stacks — the weekend base, the custody
 * band with its Monday handover diagonal, the two public holidays, event dots, a pending swap and
 * the Wednesday contact-window corners (MON-6b) — plus the three banners that sit over it.
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
    fun monthGrid() = snap("calendar_month_grid") {
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
                pendingSwapDates = setOf(ScreenshotFixtures.MONTH.atDay(PENDING_SWAP_DAY))
            )
        }
    }

    @Test
    fun banners() = snap("calendar_banners") {
        Column(verticalArrangement = Arrangement.spacedBy(BANNER_GAP)) {
            ChangeRequestBanner(pendingCount = PENDING_REQUESTS, onReview = {})
            VacationBanner(label = "Summer holidays · 27 Jun – 31 Aug")
            CustodyChangedBanner(byName = "Pavel", onDismiss = {})
        }
    }

    companion object {
        private val MONTH_GRID_HEIGHT = 560.dp
        private val BANNER_GAP = 8.dp
        private const val PENDING_SWAP_DAY = 27
        private const val PENDING_REQUESTS = 2

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
