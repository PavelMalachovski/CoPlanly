package com.coparently.app.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import com.coparently.app.domain.changerequests.AnswerBy
import com.coparently.app.domain.changerequests.AnswerByOffer
import com.coparently.app.domain.changerequests.AnswerByPick
import com.coparently.app.presentation.common.AnswerByField
import com.coparently.app.presentation.parentingplan.coParentLabel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The deadline block of the four request forms (phase 3a, spec §6.1) in each of its three offers: the
 * choices, a choice the cap shortened, the one fixed time, and the sentence for a request too near to
 * have a deadline. Text-heavy — dates, plurals and helper lines in five languages at 2.0× in German —
 * so the full [ScreenshotVariants.TEXT_HEAVY] set.
 *
 * Every offer is made for Wednesday 13 May 2026, 10:07 in Prague, so the times in the images do not move
 * with the day the suite runs on.
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

        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.TEXT_HEAVY)
    }
}
