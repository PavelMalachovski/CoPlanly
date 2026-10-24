package com.coparently.app.presentation.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.coparently.app.R
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ParentColorChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The "My colour" picker as a grid (October 2026, owner decision): every colour reachable by its
 * name — a swatch alone says nothing to a screen reader — one tap to choose, and "Custom" still a
 * separate option that opens the wheel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w393dp-h851dp")
class ParentColorPickerTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private var chosen: String? = null

    private fun render(selected: String) {
        rule.setContent {
            CoPlanlyTheme {
                ParentColorPicker(
                    chosen = selected,
                    customHue = 0,
                    onChoose = { chosen = it },
                    onCustomHue = {}
                )
            }
        }
    }

    @Test
    fun `the grid shows every colour by name`() {
        render(selected = ParentColorChoice.PINK.name)

        ParentColorChoice.GRID.forEach { choice ->
            val node = rule.onNodeWithContentDescription(context.getString(choice.labelRes))
            if (choice == ParentColorChoice.PINK) node.assertIsSelected() else node.assertIsNotSelected()
        }
    }

    @Test
    fun `tapping a swatch chooses it`() {
        render(selected = ParentColorChoice.PINK.name)

        rule.onNodeWithContentDescription(context.getString(R.string.parent_color_tomato)).performClick()

        assertEquals(ParentColorChoice.TOMATO.name, chosen)
    }

    @Test
    fun `custom colour stays a separate option and opens the wheel`() {
        render(selected = ParentColorChoice.PINK.name)

        rule.onNodeWithText(context.getString(R.string.parent_color_custom)).performClick()

        assertEquals(CUSTOM_COLOUR, chosen)
    }

    @Test
    fun `while custom is chosen the hue slider is shown`() {
        render(selected = CUSTOM_COLOUR)

        rule.onNodeWithContentDescription(context.getString(R.string.parent_color_custom_hue)).assertExists()
    }
}
