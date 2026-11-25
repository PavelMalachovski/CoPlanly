package com.coparently.app.presentation.changerequests

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.coparently.app.domain.custody.SwapSides
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * An offer's heading and its sentence are never drawn as the same line twice: where a language
 * words an "asks" offer alike in both (Czech, Russian, Ukrainian), the sentence falls away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class SwapOfferSentenceTest {

    @get:Rule
    val rule = createComposeRule()

    private val previous = Locale.getDefault()
    private val asks = SwapSides(toOther = emptyList(), toOfferer = listOf(LocalDate(2026, 11, 20)))

    @After
    fun restoreLocale() = Locale.setDefault(previous)

    private fun render(language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        rule.setContent {
            CoPlanlyTheme {
                Column {
                    Text(swapOfferHeading(asks, "Pavel", listOf("Sašík")))
                    SwapOfferSentence(asks, "Pavel", listOf("Sašík"))
                }
            }
        }
    }

    @Test
    fun `where heading and sentence differ both are drawn`() {
        render("en")

        rule.onAllNodesWithText("Sašík with Pavel on", substring = true).assertCountEquals(1)
        rule.onAllNodesWithText("Sašík will be with Pavel on", substring = true).assertCountEquals(1)
    }

    @Test
    fun `where they read alike the line is drawn once`() {
        render("cs")

        rule.onAllNodesWithText("Sašík: Pavel má", substring = true).assertCountEquals(1)
    }
}
