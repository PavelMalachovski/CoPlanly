package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * A stand-in for the iPhone's native controls in a Robolectric test: each control records what it
 * was asked to draw and draws a plain tagged box, so a test reaches the shell's branch of an
 * adaptive control without UIKit. Later controls add a list and a member here.
 */
class RecordingNativeControls : NativeControls {

    /** One drawn switch: its value and whether it could be flipped. */
    data class SwitchCall(val checked: Boolean, val enabled: Boolean)

    /** Every switch drawn, in the order of composition. */
    val switches = mutableListOf<SwitchCall>()

    /** The latest switch's callback, for a test that flips it. */
    var lastSwitchChange: ((Boolean) -> Unit)? = null

    /** One drawn segmented control: its titles, chosen index and whether it could be used. */
    data class SegmentedCall(
        val labels: List<String>,
        val selectedIndex: Int,
        val enabled: Boolean,
        val accessibilityLabel: String? = null
    )

    /** Every segmented control drawn, in the order of composition. */
    val segmented = mutableListOf<SegmentedCall>()

    /** The latest segmented control's callback, for a test that taps a segment. */
    var lastSegmentSelect: ((Int) -> Unit)? = null

    /** One date dialog shown: the day it opened on and its two labels. */
    data class DateDialogCall(val initial: LocalDate, val confirmLabel: String, val dismissLabel: String)

    /** Every date dialog composed, in the order of composition. */
    val dateDialogs = mutableListOf<DateDialogCall>()

    /** The latest date dialog's confirm, for a test that picks a day. */
    var lastDateConfirm: ((LocalDate) -> Unit)? = null

    /** The latest date or time dialog's dismiss, for a test that closes it. */
    var lastDialogDismiss: (() -> Unit)? = null

    /** Every time dialog composed: the time it opened on. */
    val timeDialogs = mutableListOf<LocalTime>()

    /** The latest time dialog's confirm, for a test that picks a time. */
    var lastTimeConfirm: ((LocalTime) -> Unit)? = null

    /** One compact date pill drawn: its day and its label. */
    data class CompactDateCall(val value: LocalDate?, val label: String)

    /** Every compact date pill drawn, in the order of composition. */
    val compactDates = mutableListOf<CompactDateCall>()

    /** The latest compact date pill's callback, for a test that picks a day in it. */
    var lastCompactDateChange: ((LocalDate) -> Unit)? = null

    /** Every compact time pill drawn: its time. */
    val compactTimes = mutableListOf<LocalTime>()

    /** The latest compact time pill's callback. */
    var lastCompactTimeChange: ((LocalTime) -> Unit)? = null

    /** One drawn menu button: its entries, its title, its symbol and its VoiceOver name. */
    data class MenuButtonCall(
        val items: List<BarMenuItem>,
        val title: String?,
        val sfSymbol: String?,
        val accessibilityLabel: String
    )

    /** Every menu button drawn, in the order of composition. */
    val menuButtons = mutableListOf<MenuButtonCall>()

    /** One confirmation asked: its question, answers and how it was to be shown. */
    data class ConfirmCall(
        val title: String,
        val message: String,
        val confirmText: String,
        val dismissText: String,
        val destructive: Boolean,
        val asActionSheet: Boolean
    )

    /** Every confirmation composed, in the order of composition. */
    val confirms = mutableListOf<ConfirmCall>()

    /** The latest confirmation's confirm, for a test that answers it. */
    var lastConfirm: (() -> Unit)? = null

    /** The latest confirmation's or action sheet's dismiss, for a test that cancels it. */
    var lastAlertDismiss: (() -> Unit)? = null

    /** One action sheet shown: its title and its entries. */
    data class ActionSheetCall(val title: String?, val items: List<BarMenuItem>)

    /** Every action sheet composed, in the order of composition. */
    val actionSheets = mutableListOf<ActionSheetCall>()

    /** The height of every sheet composed, in the order of composition. */
    val sheets = mutableListOf<SheetHeight>()

    /** Whether each sheet composed left the screen above it usable, in the order of composition. */
    val sheetsUndimmed = mutableListOf<Boolean>()

    /** The latest sheet's dismiss, for a test that swipes it down. */
    var lastSheetDismiss: (() -> Unit)? = null

    /** The colour each native control was told it sits on, in the order of composition. */
    val backgrounds = mutableListOf<Color>()

    @Composable
    @Suppress("LongParameterList") // the interface's member
    override fun MenuButton(
        items: List<BarMenuItem>,
        title: String?,
        sfSymbol: String?,
        accessibilityLabel: String,
        tint: Color,
        background: Color,
        modifier: Modifier
    ) {
        menuButtons += MenuButtonCall(items, title, sfSymbol, accessibilityLabel)
        backgrounds += background
        Box(modifier.size(80.dp, 44.dp).testTag(MENU_BUTTON_TAG))
    }

    @Composable
    @Suppress("LongParameterList") // the interface's member
    override fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        enabled: Boolean,
        accessibilityLabel: String?,
        background: Color,
        modifier: Modifier
    ) {
        backgrounds += background
        switches += SwitchCall(checked = checked, enabled = enabled)
        lastSwitchChange = onCheckedChange
        Box(modifier.size(51.dp, 31.dp).testTag(SWITCH_TAG))
    }

    @Composable
    @Suppress("LongParameterList") // the interface's member
    override fun Segmented(
        labels: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        enabled: Boolean,
        background: Color,
        accessibilityLabel: String?,
        modifier: Modifier
    ) {
        backgrounds += background
        segmented += SegmentedCall(
            labels = labels,
            selectedIndex = selectedIndex,
            enabled = enabled,
            accessibilityLabel = accessibilityLabel
        )
        lastSegmentSelect = onSelect
        Box(modifier.size(200.dp, 32.dp).testTag(SEGMENTED_TAG))
    }

    @Composable
    override fun DateDialog(
        initial: LocalDate,
        confirmLabel: String,
        dismissLabel: String,
        onConfirm: (LocalDate) -> Unit,
        onDismiss: () -> Unit
    ) {
        dateDialogs += DateDialogCall(initial = initial, confirmLabel = confirmLabel, dismissLabel = dismissLabel)
        lastDateConfirm = onConfirm
        lastDialogDismiss = onDismiss
        Box(Modifier.size(1.dp).testTag(DATE_DIALOG_TAG))
    }

    @Composable
    override fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
        timeDialogs += initial
        lastTimeConfirm = onConfirm
        lastDialogDismiss = onDismiss
        Box(Modifier.size(1.dp).testTag(TIME_DIALOG_TAG))
    }

    @Composable
    override fun CompactDate(
        value: LocalDate?,
        onChange: (LocalDate) -> Unit,
        label: String,
        background: Color,
        modifier: Modifier
    ) {
        backgrounds += background
        compactDates += CompactDateCall(value = value, label = label)
        lastCompactDateChange = onChange
        Box(modifier.size(110.dp, 34.dp).testTag(COMPACT_DATE_TAG))
    }

    @Composable
    override fun CompactTime(value: LocalTime, onChange: (LocalTime) -> Unit, background: Color, modifier: Modifier) {
        backgrounds += background
        compactTimes += value
        lastCompactTimeChange = onChange
        Box(modifier.size(80.dp, 34.dp).testTag(COMPACT_TIME_TAG))
    }

    @Composable
    @Suppress("LongParameterList") // the interface's member
    override fun Confirm(
        title: String,
        message: String,
        confirmText: String,
        dismissText: String,
        destructive: Boolean,
        asActionSheet: Boolean,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        confirms += ConfirmCall(title, message, confirmText, dismissText, destructive, asActionSheet)
        lastConfirm = onConfirm
        lastAlertDismiss = onDismiss
        Box(Modifier.size(1.dp).testTag(CONFIRM_TAG))
    }

    @Composable
    override fun ActionSheet(title: String?, items: List<BarMenuItem>, onDismiss: () -> Unit) {
        actionSheets += ActionSheetCall(title, items)
        lastAlertDismiss = onDismiss
        Box(Modifier.size(1.dp).testTag(ACTION_SHEET_TAG))
    }

    @Composable
    override fun Sheet(height: SheetHeight, undimmed: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
        sheets += height
        sheetsUndimmed += undimmed
        lastSheetDismiss = onDismiss
        Box(Modifier.testTag(SHEET_TAG)) { content() }
    }

    companion object {
        /** The test tag of the box a sheet's content is drawn in by this fake. */
        const val SHEET_TAG = "native-sheet"

        /** The test tag of a confirmation this fake drew. */
        const val CONFIRM_TAG = "native-confirm"

        /** The test tag of an action sheet this fake drew. */
        const val ACTION_SHEET_TAG = "native-action-sheet"

        /** The test tag of a menu button this fake drew. */
        const val MENU_BUTTON_TAG = "native-menu-button"

        /** The test tag of a compact date pill this fake drew. */
        const val COMPACT_DATE_TAG = "native-compact-date"

        /** The test tag of a compact time pill this fake drew. */
        const val COMPACT_TIME_TAG = "native-compact-time"

        /** The test tag of a date dialog this fake drew. */
        const val DATE_DIALOG_TAG = "native-date-dialog"

        /** The test tag of a time dialog this fake drew. */
        const val TIME_DIALOG_TAG = "native-time-dialog"

        /** The test tag of a segmented control this fake drew. */
        const val SEGMENTED_TAG = "native-segmented"

        /** The test tag of a switch this fake drew. */
        const val SWITCH_TAG = "native-switch"
    }
}
