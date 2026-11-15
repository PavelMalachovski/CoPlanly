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
import com.coparently.app.presentation.components.TimePickerDialog
import com.coparently.app.testing.RobolectricTestApplication
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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

    @Test
    fun `the date dialog under native controls asks the fake and does not draw Material's`() {
        val picked = mutableListOf<LocalDate>()
        var dismissed = 0
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                LocalDatePickerDialog(
                    initialDate = DAY,
                    confirmLabel = CONFIRM,
                    dismissLabel = DISMISS,
                    onConfirm = { picked += it },
                    onDismiss = { dismissed++ }
                )
            }
        }
        rule.waitForIdle()

        assertEquals(RecordingNativeControls.DateDialogCall(DAY, CONFIRM, DISMISS), fake.dateDialogs.last())
        rule.onNodeWithTag(RecordingNativeControls.DATE_DIALOG_TAG).assertExists()
        // Material's dialog draws its confirm and cancel buttons; under native controls neither is.
        rule.onNodeWithText(CONFIRM).assertDoesNotExist()
        rule.onNodeWithText(DISMISS).assertDoesNotExist()

        fake.lastDateConfirm?.invoke(LocalDate(2026, 11, 20))
        fake.lastDialogDismiss?.invoke()
        assertEquals(listOf(LocalDate(2026, 11, 20)), picked)
        assertEquals(1, dismissed)
    }

    @Test
    fun `without native controls the date dialog is Material's`() {
        rule.setContent {
            LocalDatePickerDialog(
                initialDate = DAY,
                confirmLabel = CONFIRM,
                dismissLabel = DISMISS,
                onConfirm = {},
                onDismiss = {}
            )
        }
        rule.onNodeWithText(CONFIRM).assertExists()
        assertTrue(fake.dateDialogs.isEmpty())
    }

    @Test
    fun `DatePickerField under native controls is the compact picker`() {
        var changed: LocalDate? = null
        var clicks = 0
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                DatePickerField(
                    label = FIELD_LABEL,
                    value = "1 Nov 2026",
                    onClick = { clicks++ },
                    date = DAY,
                    onDateChange = { changed = it }
                )
            }
        }
        rule.waitForIdle()

        assertEquals(RecordingNativeControls.CompactDateCall(DAY, FIELD_LABEL), fake.compactDates.last())
        rule.onNodeWithTag(RecordingNativeControls.COMPACT_DATE_TAG).assertExists()
        rule.onNodeWithText(FIELD_LABEL).assertExists()
        // The outlined field's formatted value is not drawn: the pill shows the day itself.
        rule.onNodeWithText("1 Nov 2026").assertDoesNotExist()

        fake.lastCompactDateChange?.invoke(LocalDate(2026, 11, 20))
        assertEquals(LocalDate(2026, 11, 20), changed)
        assertEquals(0, clicks)
    }

    @Test
    fun `DatePickerField without a day keeps the field under native controls`() {
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                DatePickerField(label = FIELD_LABEL, value = "1 Nov 2026", onClick = {})
            }
        }
        rule.onNodeWithText("1 Nov 2026").assertExists()
        assertTrue(fake.compactDates.isEmpty())
    }

    @Test
    fun `the time dialog under native controls asks the fake`() {
        val picked = mutableListOf<LocalTime>()
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                TimePickerDialog(initialTime = LocalTime(9, 30), onTimeSelected = { picked += it }, onDismiss = {})
            }
        }
        rule.waitForIdle()

        assertEquals(LocalTime(9, 30), fake.timeDialogs.last())
        rule.onNodeWithTag(RecordingNativeControls.TIME_DIALOG_TAG).assertExists()
        fake.lastTimeConfirm?.invoke(LocalTime(18, 0))
        assertEquals(listOf(LocalTime(18, 0)), picked)
    }

    private companion object {
        val DAY = LocalDate(2026, 11, 1)
        const val CONFIRM = "Pick this day"
        const val DISMISS = "Never mind"
        const val FIELD_LABEL = "Date of birth"
        val VIEW_LABELS = listOf("Month", "Week", "Day")
        const val MATERIAL_SEGMENTS = "Material segments"
        const val NATIVE_ROW = "Pause before sending"
        const val MATERIAL_ROW = "Share usage data"
    }
}
