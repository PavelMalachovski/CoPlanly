package com.coparently.app.ios.controls

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.coparently.app.presentation.common.BarMenuItem
import com.coparently.app.presentation.common.NativeControls
import com.coparently.app.presentation.common.SheetHeight
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_dialog_cancel
import com.coparently.app.resources.common_dialog_ok
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UISegmentedControl
import platform.UIKit.UISegmentedControlNoSegment
import platform.UIKit.UISwitch
import platform.UIKit.accessibilityLabel
import kotlin.time.Clock

/**
 * The iPhone's own controls (MON-24 phase 3, part C), which the shell provides to every hosted
 * screen as `LocalNativeControls`: each adaptive control of the shared screens is drawn here as
 * UIKit's, inside the Compose screen through `UIKitView`.
 */
@Suppress("TooManyFunctions") // one member per control of the interface it answers
internal object IosNativeControls : NativeControls {

    /**
     * A `UISwitch`, laid out at the switch's own intrinsic size (51 × 31 pt before iOS 26) and in
     * the system's green. [checked] and [enabled] are set from Compose at every update without the
     * switch sending `UIControlEventValueChanged`; only the parent's flip reaches [onCheckedChange].
     * Its rectangle is painted [background], so its rounded ends show what it sits on.
     */
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
        val callback by rememberUpdatedState(onCheckedChange)
        val (width, height) = switchSize
        UIKitView(
            factory = { makeSwitch { callback?.invoke(it) } },
            modifier = modifier.size(width.dp, height.dp),
            update = { view ->
                view.backgroundColor = uiColorOf(background)
                updateSwitch(view, checked, enabled && onCheckedChange != null, accessibilityLabel)
            },
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
        )
    }

    /** The system's `ASAuthorizationAppleIDButton`, in place ([AppleIdButton]). */
    @Composable
    override fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean, background: Color, modifier: Modifier) {
        AppleIdButton(onClick = onClick, enabled = enabled, background = background, modifier = modifier)
    }

    /**
     * A `UISegmentedControl` filling the width at [SEGMENTED_HEIGHT_PT]. The segments are rebuilt
     * only when [labels] change, and [selectedIndex] is set at every update without the control
     * sending `UIControlEventValueChanged`; only the parent's tap reaches [onSelect]. It sits in a
     * [SegmentedHost] painted [background], which leaves the control's own track untouched.
     */
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
        val callback by rememberUpdatedState(onSelect)
        UIKitView(
            factory = { SegmentedHost(makeSegmented(labels) { callback(it) }) },
            modifier = modifier.fillMaxWidth().height(SEGMENTED_HEIGHT_PT.dp),
            update = { host ->
                host.backgroundColor = uiColorOf(background)
                updateSegmented(host.control, labels, selectedIndex, enabled, accessibilityLabel)
            },
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
        )
    }

    /**
     * A [dateSheet] presented from the screen's own view controller while this is composed: an
     * inline calendar at the medium height. Leaving composition takes it away without calling back.
     */
    @Composable
    override fun DateDialog(
        initial: LocalDate,
        confirmLabel: String,
        dismissLabel: String,
        onConfirm: (LocalDate) -> Unit,
        onDismiss: () -> Unit
    ) {
        val confirm by rememberUpdatedState(onConfirm)
        val dismiss by rememberUpdatedState(onDismiss)
        PresentedWhileComposed {
            dateSheet(initial, confirmLabel, dismissLabel, onConfirm = { confirm(it) }, onDismiss = { dismiss() })
        }
    }

    /**
     * A [timeSheet] presented from the screen's own view controller while this is composed: wheels
     * on the phone's own clock, with the app's Cancel and OK. Leaving composition takes it away
     * without calling back.
     */
    @Composable
    override fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
        val confirm by rememberUpdatedState(onConfirm)
        val dismiss by rememberUpdatedState(onDismiss)
        val confirmLabel = stringResource(Res.string.common_dialog_ok)
        val dismissLabel = stringResource(Res.string.common_dialog_cancel)
        PresentedWhileComposed {
            timeSheet(initial, confirmLabel, dismissLabel, onConfirm = { confirm(it) }, onDismiss = { dismiss() })
        }
    }

    /**
     * A [CompactDatePill] at the compact picker's own size for the day it shows. [value] is set at
     * every update without the picker sending `UIControlEventValueChanged`; only the parent's pick
     * reaches [onChange]. The pill's host is painted [background].
     */
    @Composable
    override fun CompactDate(
        value: LocalDate?,
        onChange: (LocalDate) -> Unit,
        label: String,
        background: Color,
        modifier: Modifier
    ) {
        val callback by rememberUpdatedState(onChange)
        val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
        val (width, height) = remember(value) {
            compactPickerSize(
                UIDatePickerMode.UIDatePickerModeDate,
                nsDateFor(value ?: today),
                COMPACT_DATE_WIDTH_PT to COMPACT_HEIGHT_PT
            )
        }
        UIKitView(
            factory = { CompactDatePill { callback(it) } },
            modifier = modifier.size(width.dp, height.dp),
            update = { pill ->
                pill.backgroundColor = uiColorOf(background)
                updateCompactDate(pill, value, today, label)
            },
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
        )
    }

    /**
     * A compact time picker at its own size for the time it shows, on the phone's own clock. [value]
     * is set at every update without the picker sending `UIControlEventValueChanged`; only the
     * parent's pick reaches [onChange]. The picker's rectangle is painted [background].
     */
    @Composable
    override fun CompactTime(value: LocalTime, onChange: (LocalTime) -> Unit, background: Color, modifier: Modifier) {
        val callback by rememberUpdatedState(onChange)
        val (width, height) = remember(value) {
            compactPickerSize(
                UIDatePickerMode.UIDatePickerModeTime,
                nsDateFor(value),
                COMPACT_TIME_WIDTH_PT to COMPACT_HEIGHT_PT
            )
        }
        UIKitView(
            factory = { ValueChangedDatePicker(UIDatePickerMode.UIDatePickerModeTime) { callback(localTimeOf(it)) } },
            modifier = modifier.size(width.dp, height.dp),
            update = { picker ->
                picker.backgroundColor = uiColorOf(background)
                updateCompactTime(picker, value)
            },
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
        )
    }

    /**
     * A [MenuButtonView] at its own intrinsic size for its look: the button draws itself, with its
     * title and symbol (or the pop-up chevron), and shows the menu of [items] on a tap. The look is
     * redrawn and the menu rebuilt only when they change; each entry calls the latest items' entry at
     * its place. Its rectangle is painted [background].
     */
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
        val latest by rememberUpdatedState(items)
        val look = MenuButtonLook(title, sfSymbol, tint)
        val (width, height) = remember(look) { menuButtonSize(look) }
        UIKitView(
            factory = { MenuButtonView { index -> latest.getOrNull(index)?.onClick?.invoke() } },
            modifier = modifier.size(width.dp, height.dp),
            update = { button -> button.show(latest, look, accessibilityLabel, uiColorOf(background)) },
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
        )
    }

    /**
     * A [confirmAlert] presented from the screen's own view controller while this is composed: an
     * alert, or an action sheet when [asActionSheet]. Each answer calls its lambda once; leaving
     * composition takes it away without calling back.
     */
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
        val confirm by rememberUpdatedState(onConfirm)
        val dismiss by rememberUpdatedState(onDismiss)
        AlertWhileComposed {
            confirmAlert(
                title = title,
                message = message,
                confirmText = confirmText,
                dismissText = dismissText,
                destructive = destructive,
                asActionSheet = asActionSheet,
                onConfirm = { confirm() },
                onDismiss = { dismiss() }
            )
        }
    }

    /**
     * A [menuActionSheet] presented from the screen's own view controller while this is composed,
     * with the app's Cancel. An item runs the latest items' entry at its place; leaving composition
     * takes the sheet away without calling back.
     */
    @Composable
    override fun ActionSheet(title: String?, items: List<BarMenuItem>, onDismiss: () -> Unit) {
        val latest by rememberUpdatedState(items)
        val dismiss by rememberUpdatedState(onDismiss)
        val cancelText = stringResource(Res.string.common_dialog_cancel)
        AlertWhileComposed {
            menuActionSheet(
                title = title,
                items = items,
                cancelText = cancelText,
                onItem = { index -> latest.getOrNull(index)?.onClick?.invoke() },
                onDismiss = { dismiss() }
            )
        }
    }

    /**
     * A [ComposeSheet] presented from the screen's own view controller while this is composed,
     * holding [content] under the screen's locals; a swipe down, or the screen no longer seen,
     * calls [onDismiss] once, and leaving composition takes the sheet away without calling back.
     */
    @Composable
    override fun Sheet(
        height: SheetHeight,
        undimmed: Boolean,
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        PresentComposeSheet(height = height, undimmed = undimmed, onDismiss = onDismiss, content = content)
    }

    /** The switch's intrinsic size in points, measured once from UIKit's own switch. */
    // `CGRectMake` and `useContents` are Kotlin/Native's C interop.
    @OptIn(ExperimentalForeignApi::class)
    private val switchSize: Pair<Double, Double> by lazy {
        UISwitch(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).intrinsicContentSize.useContents {
            if (width > 0.0 && height > 0.0) width to height else SWITCH_WIDTH_PT to SWITCH_HEIGHT_PT
        }
    }

    /** The height of a segmented control, in points (UIKit's standard bar-size control). */
    private const val SEGMENTED_HEIGHT_PT = 32.0

    /** `UISwitch`'s intrinsic width before iOS 26, used if UIKit reports no size. */
    private const val SWITCH_WIDTH_PT = 51.0

    /** `UISwitch`'s intrinsic height before iOS 26, used if UIKit reports no size. */
    private const val SWITCH_HEIGHT_PT = 31.0

    /** A compact date picker's width, in points, used if UIKit reports no size. */
    private const val COMPACT_DATE_WIDTH_PT = 120.0

    /** A compact time picker's width, in points, used if UIKit reports no size. */
    private const val COMPACT_TIME_WIDTH_PT = 80.0

    /** A compact picker's height, in points, used if UIKit reports no size. */
    private const val COMPACT_HEIGHT_PT = 34.0
}

/**
 * Presents the sheet [build] makes from the screen's own view controller when this enters
 * composition, keeps it while it stays, and takes it away without calling back when it leaves: a
 * dialog closed by the screen (its flag cleared, the screen gone) leaves no sheet behind.
 */
@Composable
private fun PresentedWhileComposed(build: () -> PickerSheet) {
    val host = LocalUIViewController.current
    DisposableEffect(Unit) {
        val sheet = build()
        sheet.present(host)
        onDispose { sheet.close() }
    }
}

/**
 * Presents the alert [build] makes from the screen's own view controller when this enters
 * composition, once, and takes it away without calling back when it leaves, as
 * [PresentedWhileComposed] does for a picker sheet.
 */
@Composable
private fun AlertWhileComposed(build: () -> AlertSheet) {
    val host = LocalUIViewController.current
    DisposableEffect(Unit) {
        val alert = build()
        alert.present(host)
        onDispose { alert.close() }
    }
}

/**
 * A new switch whose target-action, `UIControlEventValueChanged`, calls [onValueChanged] with the
 * switch's new value. UIKit sends that event only for the parent's own flip, never for a value set
 * in code, which is what [updateSwitch] does.
 */
internal fun makeSwitch(onValueChanged: (Boolean) -> Unit): UISwitch = ValueChangedSwitch(onValueChanged)

/**
 * Shows [checked] and [enabled] on [view] and names it [accessibilityLabel], without reporting a
 * change: a value changed from outside (a sync, a form's reset) is shown, not echoed back.
 */
internal fun updateSwitch(view: UISwitch, checked: Boolean, enabled: Boolean, accessibilityLabel: String?) {
    if (view.on != checked) view.setOn(checked, animated = view.window != null)
    if (view.enabled != enabled) view.enabled = enabled
    if (accessibilityLabel != null) view.accessibilityLabel = accessibilityLabel
}

/**
 * A `UISwitch` that is its own target, so the action and what it calls live exactly as long as the
 * view: UIKit does not retain a control's targets.
 */
// `CGRectMake` and `NSSelectorFromString` are Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class ValueChangedSwitch(
    private val onValueChanged: (Boolean) -> Unit
) : UISwitch(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        addTarget(this, action = NSSelectorFromString("valueChanged:"), forControlEvents = UIControlEventValueChanged)
    }

    /** The target-action of `UIControlEventValueChanged`. */
    @ObjCAction
    fun valueChanged(sender: UISwitch) {
        onValueChanged(sender.on)
    }
}

/**
 * A new segmented control with one segment per entry of [labels], whose target-action,
 * `UIControlEventValueChanged`, calls [onSelect] with the tapped segment's index. UIKit sends that
 * event only for the parent's own tap, never for an index set in code, which is what
 * [updateSegmented] does.
 */
internal fun makeSegmented(labels: List<String>, onSelect: (Int) -> Unit): UISegmentedControl =
    ValueChangedSegmented(labels, onSelect)

/**
 * Shows [labels], [selectedIndex] and [enabled] on [view] without reporting a selection, and names
 * it [accessibilityLabel] for VoiceOver when one is given. The segments are rebuilt only when the
 * labels differ from the ones shown.
 */
internal fun updateSegmented(
    view: UISegmentedControl,
    labels: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    accessibilityLabel: String? = null
) {
    if (view is ValueChangedSegmented && view.shown != labels) view.show(labels)
    val index = if (selectedIndex in labels.indices) selectedIndex.toLong() else UISegmentedControlNoSegment.toLong()
    if (view.selectedSegmentIndex != index) view.selectedSegmentIndex = index
    if (view.enabled != enabled) view.enabled = enabled
    if (accessibilityLabel != null && view.accessibilityLabel != accessibilityLabel) {
        view.accessibilityLabel = accessibilityLabel
    }
}

/**
 * A `UISegmentedControl` that is its own target, so the action and what it calls live exactly as
 * long as the view: UIKit does not retain a control's targets. It remembers the labels it shows.
 */
// `CGRectMake` and `NSSelectorFromString` are Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class ValueChangedSegmented(
    labels: List<String>,
    private val onSelect: (Int) -> Unit
) : UISegmentedControl(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    /** The titles the segments show now. */
    var shown: List<String> = emptyList()
        private set

    init {
        show(labels)
        addTarget(this, action = NSSelectorFromString("valueChanged:"), forControlEvents = UIControlEventValueChanged)
    }

    /** Replaces the segments with one per entry of [labels]. */
    fun show(labels: List<String>) {
        removeAllSegments()
        labels.forEachIndexed { index, title ->
            insertSegmentWithTitle(title, atIndex = index.toULong(), animated = false)
        }
        shown = labels
    }

    /** The target-action of `UIControlEventValueChanged`. */
    @ObjCAction
    fun valueChanged(sender: UISegmentedControl) {
        onSelect(sender.selectedSegmentIndex.toInt())
    }
}
