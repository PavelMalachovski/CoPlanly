package com.coparently.app.presentation.changerequests

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.coparently.app.R
import com.coparently.app.domain.changerequests.HistoryEntry
import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.changerequests.RequestKind
import com.coparently.app.domain.changerequests.RequestOutcome
import com.coparently.app.domain.changerequests.RequestSubject
import com.coparently.app.presentation.common.NamedParent
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.common.Parents
import com.coparently.app.presentation.theme.CoPlanlyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Where a history row puts its outcome chip: beside the title at the default size, under it from
 * the large-font scale (`stacksAtLargeFont`), as every other row with a trailing chip does.
 *
 * Measured rather than screenshotted, because the defect was a position: at 150 % and 200 % in
 * German the chip kept its width beside the title and squeezed "Neuer Betreuungsplan" into a
 * column that broke the word in two places. And what a "No answer" row claims (phase 3a): its own
 * chip, and nobody named as having answered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h851dp")
class RequestHistoryGroupTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `at the default size the outcome sits beside the title`() {
        render(fontScale = 1f)

        val title = bounds(context.getString(R.string.request_history_custody))
        val chip = bounds(context.getString(R.string.change_request_status_accepted))
        assertTrue("chip $chip should start after title $title", chip.left >= title.right)
        assertTrue("chip $chip should share the title's line, title $title", chip.top < title.bottom)
    }

    @Test
    fun `at large text the outcome sits under the title`() {
        render(fontScale = 2f)

        val title = bounds(context.getString(R.string.request_history_custody))
        val chip = bounds(context.getString(R.string.change_request_status_accepted))
        assertTrue("chip $chip should sit under title $title", chip.top >= title.bottom)
    }

    @Test
    fun `a request nobody answered says so on its own chip and claims no answer`() {
        render(fontScale = 1f, entry = unanswered)

        rule.onNodeWithText(context.getString(R.string.request_clock_no_answer), useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithText(
            context.getString(R.string.request_history_yours_answered, "Pavel"),
            substring = true,
            useUnmergedTree = true
        ).assertDoesNotExist()
    }

    @Test
    fun `a request nobody answered says what its clock knows, and that the calendar did not change`() {
        val clock = RequestDeadline(
            id = "change_u1_r1",
            kind = RequestKind.CHANGE,
            ref = "r1",
            familyId = "u1__u2",
            proposerUid = MY_UID,
            responderUid = CO_PARENT_UID,
            answerByMillis = 1_778_500_000_000L,
            outcome = RequestClockOutcome.NO_ANSWER
        )
        render(fontScale = 1f, entry = unanswered.copy(clock = clock))

        rule.onNodeWithText(
            context.getString(R.string.request_history_meta_unchanged),
            substring = true,
            useUnmergedTree = true
        ).assertExists()
    }

    private fun render(fontScale: Float, entry: HistoryEntry = accepted) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CoPlanlyTheme {
                    RequestHistoryGroup(entries = listOf(entry), parentNames = names, myUid = MY_UID)
                }
            }
        }
    }

    private fun bounds(text: String): DpRect =
        rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private companion object {
        const val MY_UID = "u1"
        const val CO_PARENT_UID = "u2"

        val names = ParentNames(
            parents = Parents(
                me = NamedParent(uid = MY_UID, slot = "mom", name = "Olya"),
                coParent = NamedParent(uid = CO_PARENT_UID, slot = "dad", name = "Pavel"),
                isPaired = true,
                loaded = true
            ),
            youFallback = "You",
            coParentFallback = "Co-parent",
            unknownFallback = "Parent"
        )

        /** The co-parent's new schedule, which this parent accepted. */
        val accepted = HistoryEntry(
            key = "custody",
            subject = RequestSubject.CustodySchedule,
            outcome = RequestOutcome.ACCEPTED,
            askedBy = CO_PARENT_UID,
            decidedBy = MY_UID,
            decidedAt = LocalDateTime.of(2026, 5, 11, 9, 0)
        )

        /** This parent's new time for an event, which the co-parent never answered (phase 3a). */
        val unanswered = HistoryEntry(
            key = "cr_r1",
            subject = RequestSubject.NewTime("e1", "Swimming", LocalDateTime.of(2026, 5, 14, 17, 0)),
            outcome = RequestOutcome.NO_ANSWER,
            askedBy = MY_UID,
            decidedBy = CO_PARENT_UID,
            decidedAt = LocalDateTime.of(2026, 5, 11, 21, 0)
        )
    }
}
