package com.coparently.app.presentation.common

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.coparently.app.testing.RobolectricTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The native controls seam (MON-24 phase 3, part C, Task 1): without `LocalNativeControls` an
 * adaptive control is Material's, as Android draws it; with it, the shell's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = RobolectricTestApplication::class, qualifiers = "w393dp-h851dp")
class NativeControlsTest {

    @get:Rule
    val rule = createComposeRule()

    private val fake = RecordingNativeControls()

    private val materialSwitch =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `without native controls the switch is Material's`() {
        rule.setContent { AdaptiveSwitch(checked = true, onCheckedChange = {}) }

        val on = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)
        rule.onNode(materialSwitch.and(on)).assertExists()
        assertTrue(fake.switches.isEmpty())
    }

    @Test
    fun `with native controls the switch is the fake's`() {
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                AdaptiveSwitch(checked = true, onCheckedChange = {})
            }
        }
        rule.waitForIdle()

        assertEquals(listOf(RecordingNativeControls.SwitchCall(checked = true, enabled = true)), fake.switches)
        rule.onNodeWithTag(RecordingNativeControls.SWITCH_TAG).assertExists()
        rule.onNode(materialSwitch).assertDoesNotExist()
    }

    @Test
    fun `a section row toggles as a whole only under native controls`() {
        var nativeToggles = 0
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                SectionRow(
                    title = NATIVE_ROW,
                    trailing = { AdaptiveSwitch(checked = false, onCheckedChange = {}) },
                    onToggle = { nativeToggles++ }
                )
            }
        }
        rule.onNodeWithText(NATIVE_ROW).performClick()
        assertEquals(1, nativeToggles)
    }

    @Test
    fun `without native controls a section row ignores its toggle`() {
        var materialToggles = 0
        rule.setContent {
            SectionRow(
                title = MATERIAL_ROW,
                trailing = { AdaptiveSwitch(checked = false, onCheckedChange = {}) },
                onToggle = { materialToggles++ }
            )
        }
        rule.onNodeWithText(MATERIAL_ROW).performClick()
        assertEquals(0, materialToggles)
    }

    @Test
    fun `segmented without native controls draws the site's row`() {
        var materialRan = false
        rule.setContent {
            AdaptiveSegmented(labels = VIEW_LABELS, selectedIndex = 0, onSelect = {}) {
                materialRan = true
                Text(MATERIAL_SEGMENTS)
            }
        }
        rule.onNodeWithText(MATERIAL_SEGMENTS).assertExists()
        assertTrue(materialRan)
        assertTrue(fake.segmented.isEmpty())
    }

    @Test
    fun `segmented with native controls`() {
        var materialRan = false
        var chosen = -1
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                AdaptiveSegmented(labels = VIEW_LABELS, selectedIndex = 0, onSelect = { chosen = it }) {
                    materialRan = true
                }
            }
        }
        rule.waitForIdle()

        assertEquals(
            RecordingNativeControls.SegmentedCall(labels = VIEW_LABELS, selectedIndex = 0, enabled = true),
            fake.segmented.last()
        )
        rule.onNodeWithTag(RecordingNativeControls.SEGMENTED_TAG).assertExists()
        assertFalse(materialRan)
        fake.lastSegmentSelect?.invoke(2)
        assertEquals(2, chosen)
    }

    private companion object {
        val VIEW_LABELS = listOf("Month", "Week", "Day")
        const val MATERIAL_SEGMENTS = "Material segments"
        const val NATIVE_ROW = "Pause before sending"
        const val MATERIAL_ROW = "Share usage data"
    }
}
