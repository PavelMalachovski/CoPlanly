package com.coparently.app.presentation.onboarding

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.coparently.app.R
import com.coparently.app.presentation.settings.CoParentColour
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentPalette
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The onboarding's colour step says when the linked co-parent's colour would move this parent's
 * pick, in the words Settings' picker uses: the second parent always reaches this step linked, and
 * the four originals make a clash likely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class OnboardingColourClashTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val zuzanaOnBlue = CoParentColour(
        name = "Zuzana Nováková",
        slot = "mom",
        colorCode = ParentColorChoice.BLUE.storedCode,
        mySlot = "dad"
    )

    private fun render(selected: ParentColorChoice?, coParent: CoParentColour?) {
        rule.setContent {
            CoPlanlyTheme {
                ParentColorSwatches(selected = selected, onSelect = {}, coParent = coParent)
            }
        }
    }

    @Test
    fun `a pick the calendar would move says so with the colour it gets`() {
        render(selected = ParentColorChoice.BLUE, coParent = zuzanaOnBlue)

        val shown = ParentPalette.shownFor("dad", ParentColorChoice.BLUE.storedCode, ParentColorChoice.BLUE.storedCode)
        val text = context.getString(
            R.string.parent_color_clash_named,
            "Zuzana",
            context.getString(R.string.parent_color_blue),
            runBlocking { getString(requireNotNull(shown.preset).label) }
        )
        rule.onNodeWithText(text).assertExists()
    }

    @Test
    fun `a pick that stays as chosen draws no notice`() {
        render(selected = ParentColorChoice.PINK, coParent = zuzanaOnBlue)

        rule.onNodeWithText("Pick another colour", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing picked yet draws no notice`() {
        render(selected = null, coParent = zuzanaOnBlue)

        rule.onNodeWithText("Pick another colour", substring = true).assertDoesNotExist()
    }

    @Test
    fun `without a linked co-parent nothing is added`() {
        render(selected = ParentColorChoice.BLUE, coParent = null)

        rule.onNodeWithText("Pick another colour", substring = true).assertDoesNotExist()
    }
}
