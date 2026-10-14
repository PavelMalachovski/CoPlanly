package com.coparently.app.screenshots

import android.app.Application
import com.coparently.app.presentation.calendar.MiniMonthMark
import com.coparently.app.presentation.calendar.MiniMonthModel
import com.coparently.app.presentation.calendar.components.MiniMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    companion object {
        /** How far ahead of the fixtures' today the marked day sits. */
        private const val MARKED_DAY_OFFSET = 8L

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
