package com.coparently.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalMaterial3Api::class) // the Material sheet a site draws, which the native branch must not
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
    fun `a clickable row shows a chevron only under native controls`() {
        var native by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake.takeIf { native }) {
                SectionRow(title = CHEVRON_ROW, onClick = {})
            }
        }
        rule.onNodeWithTag(CHEVRON_TAG, useUnmergedTree = true).assertDoesNotExist()
        native = true
        rule.waitForIdle()
        rule.onNodeWithTag(CHEVRON_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the group divider is inset under native controls`() {
        var native by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake.takeIf { native }) {
                SectionGroup(modifier = Modifier.testTag(GROUP_TAG)) {
                    Text("one")
                    Divider()
                    Text("two")
                }
            }
        }
        fun dividerLeftOffset(): Float {
            val group = rule.onNodeWithTag(GROUP_TAG).getUnclippedBoundsInRoot()
            val divider = rule.onNodeWithTag(DIVIDER_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
            return (divider.left - group.left).value
        }
        // Without native controls the divider is not tagged; it spans the group.
        rule.onNodeWithTag(DIVIDER_TAG, useUnmergedTree = true).assertDoesNotExist()
        native = true
        rule.waitForIdle()
        assertEquals(16f, dividerLeftOffset(), 0.5f)
    }

    @Test
    fun `a toggled row exposes its state and a tap toggles once`() {
        var toggles = 0
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                SectionRow(
                    title = NATIVE_ROW,
                    trailing = { AdaptiveSwitch(checked = true, onCheckedChange = {}) },
                    onToggle = { toggles++ },
                    toggled = true
                )
            }
        }
        rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)
                .and(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        ).assertExists()
        rule.onNodeWithText(NATIVE_ROW).performClick()
        assertEquals(1, toggles)
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

    @Test
    fun `an option row under native controls hands the fake one item per option, the selected checked, no dialog`() {
        val picked = mutableListOf<String?>()
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                OptionRow(onSelect = { picked += it })
            }
        }
        rule.waitForIdle()

        val button = fake.menuButtons.last()
        assertEquals(REPEAT_OPTIONS.map { it.second }, button.items.map { it.label })
        assertEquals(listOf(false, true, false), button.items.map { it.checked })
        assertEquals("Weekly", button.title)
        assertEquals(null, button.sfSymbol)
        assertEquals("$REPEAT_ROW, Weekly", button.accessibilityLabel)
        rule.onNodeWithTag(RecordingNativeControls.MENU_BUTTON_TAG).assertExists()
        // The row's title is drawn by Compose, as text and nothing to tap.
        rule.onNodeWithText(REPEAT_ROW).assertHasNoClickAction()
        rule.onNodeWithText(REPEAT_DIALOG).assertDoesNotExist()

        button.items[2].onClick()
        assertEquals(listOf("monthly"), picked)
    }

    @Test
    fun `an option row whose value matches no option still draws its button`() {
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                OptionRow(onSelect = {}, selected = "yearly")
            }
        }
        rule.waitForIdle()

        val button = fake.menuButtons.last()
        assertEquals(null, button.title)
        assertEquals(REPEAT_ROW, button.accessibilityLabel)
        assertEquals(listOf(false, false, false), button.items.map { it.checked })
    }

    @Test
    fun `without native controls tapping the row opens the dialog`() {
        rule.setContent { OptionRow(onSelect = {}) }

        rule.onNodeWithText(REPEAT_DIALOG).assertDoesNotExist()
        rule.onNodeWithText(REPEAT_ROW).performClick()
        rule.onNodeWithText(REPEAT_DIALOG).assertExists()
        assertTrue(fake.menuButtons.isEmpty())
    }

    @Test
    fun `a native control is told what it sits on, a card by default`() {
        var card = Color.Unspecified
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                card = MaterialTheme.colorScheme.surfaceContainer
                AdaptiveSwitch(checked = true, onCheckedChange = {})
                AdaptiveSwitch(checked = true, onCheckedChange = {}, background = Color.Red)
            }
        }
        rule.waitForIdle()

        assertEquals(listOf(card, Color.Red), fake.backgrounds.takeLast(2))
    }

    @Test
    fun `a destructive confirmation under native controls is an action sheet`() {
        var confirmed = 0
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                ConfirmationDialog(
                    title = DELETE_TITLE,
                    message = DELETE_MESSAGE,
                    confirmText = DELETE,
                    dismissText = DISMISS,
                    onConfirm = { confirmed++ },
                    onDismiss = {},
                    isDestructive = true
                )
            }
        }
        rule.waitForIdle()

        val asked = fake.confirms.last()
        assertEquals(
            RecordingNativeControls.ConfirmCall(
                title = DELETE_TITLE,
                message = DELETE_MESSAGE,
                confirmText = DELETE,
                dismissText = DISMISS,
                destructive = true,
                asActionSheet = true
            ),
            asked
        )
        rule.onNodeWithText(DELETE_TITLE).assertDoesNotExist()
        fake.lastConfirm?.invoke()
        assertEquals(1, confirmed)
    }

    @Test
    fun `discard changes is an alert`() {
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                val guard = rememberDiscardGuard(dirty = true, onLeave = {})
                Text(LEAVE, modifier = Modifier.clickable { guard() })
            }
        }
        rule.onNodeWithText(LEAVE).performClick()
        rule.waitForIdle()

        assertTrue(fake.confirms.isNotEmpty())
        val asked = fake.confirms.last()
        assertTrue(asked.destructive)
        assertFalse(asked.asActionSheet)
    }

    @Test
    fun `without native controls ConfirmationDialog is Material's`() {
        rule.setContent {
            ConfirmationDialog(
                title = DELETE_TITLE,
                message = DELETE_MESSAGE,
                onConfirm = {},
                onDismiss = {},
                isDestructive = true
            )
        }

        rule.onNodeWithText(DELETE_TITLE).assertIsDisplayed()
        assertTrue(fake.confirms.isEmpty())
    }

    @Test
    fun `a sheet under native controls hands its content to the fake and draws no ModalBottomSheet`() {
        rule.setContent {
            CompositionLocalProvider(LocalNativeControls provides fake) {
                AdaptiveSheet(
                    onDismiss = {},
                    height = SheetHeight.Medium,
                    material = { ModalBottomSheet(onDismissRequest = {}) { Text(MATERIAL_SHEET) } },
                    content = { Text(SHEET_BODY) }
                )
            }
        }
        rule.waitForIdle()

        assertEquals(SheetHeight.Medium, fake.sheets.last())
        assertFalse("A sheet is dimmed unless the site asks", fake.sheetsUndimmed.last())
        rule.onNodeWithTag(RecordingNativeControls.SHEET_TAG).assertExists()
        rule.onNodeWithText(SHEET_BODY).assertExists()
        rule.onNodeWithText(MATERIAL_SHEET).assertDoesNotExist()
    }

    @Test
    fun `dismissing the fake's sheet calls onDismiss once`() {
        var dismissed = 0
        rule.setContent {
            var open by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalNativeControls provides fake) {
                if (open) {
                    AdaptiveSheet(
                        onDismiss = {
                            dismissed++
                            open = false
                        },
                        height = SheetHeight.Large,
                        material = { Text(MATERIAL_SHEET) },
                        content = { Text(SHEET_BODY) }
                    )
                }
            }
        }
        rule.waitForIdle()
        assertTrue(fake.sheets.isNotEmpty())
        assertEquals(SheetHeight.Large, fake.sheets.last())

        rule.runOnIdle { fake.lastSheetDismiss?.invoke() }
        rule.waitForIdle()

        assertEquals(1, dismissed)
        rule.onNodeWithText(SHEET_BODY).assertDoesNotExist()
    }

    @Test
    fun `without native controls a sheet is the site's own`() {
        rule.setContent {
            AdaptiveSheet(
                onDismiss = {},
                height = SheetHeight.Large,
                material = { Text(MATERIAL_SHEET) },
                content = { Text(SHEET_BODY) }
            )
        }

        rule.onNodeWithText(MATERIAL_SHEET).assertIsDisplayed()
        rule.onNodeWithText(SHEET_BODY).assertDoesNotExist()
        assertTrue(fake.sheets.isEmpty())
    }

    /** A repeat row as the event form draws one: the row opens a dialog that names itself. */
    @Composable
    private fun OptionRow(onSelect: (String?) -> Unit, selected: String? = "weekly") {
        AdaptiveOptionRow(
            title = REPEAT_ROW,
            options = REPEAT_OPTIONS,
            selected = selected,
            onSelect = onSelect,
            row = { open -> SectionRow(title = REPEAT_ROW, onClick = open) },
            dialog = { dismiss -> Text(REPEAT_DIALOG, modifier = Modifier.clickable(onClick = dismiss)) }
        )
    }

    private companion object {
        val DAY = LocalDate(2026, 11, 1)
        const val CONFIRM = "Pick this day"
        const val DISMISS = "Never mind"
        const val FIELD_LABEL = "Date of birth"
        val VIEW_LABELS = listOf("Month", "Week", "Day")
        const val MATERIAL_SEGMENTS = "Material segments"
        const val NATIVE_ROW = "Pause before sending"
        const val CHEVRON_ROW = "Privacy policy"
        const val GROUP_TAG = "group"
        const val MATERIAL_ROW = "Share usage data"
        const val DELETE_TITLE = "Delete event"
        const val DELETE_MESSAGE = "This cannot be undone."
        const val DELETE = "Delete"
        const val LEAVE = "Leave the form"
        const val MATERIAL_SHEET = "Material's sheet"
        const val SHEET_BODY = "What the sheet holds"
        const val REPEAT_ROW = "Repeat"
        const val REPEAT_DIALOG = "Choose how it repeats"
        val REPEAT_OPTIONS = listOf<Pair<String?, String>>(
            null to "Never",
            "weekly" to "Weekly",
            "monthly" to "Monthly"
        )
    }
}
