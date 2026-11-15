package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The phone's own controls, where a shell draws them (MON-24 phase 3, part C).
 *
 * Only the iPhone's shell provides one, through [LocalNativeControls], as it provides
 * [LocalBarHost]: its implementation draws each control as UIKit's. Android provides none, so every
 * adaptive control (`AdaptiveSwitch`, …) draws the Material component the screen drew before, with
 * the same arguments. A screen never asks which platform it runs on; it asks [nativeControls].
 *
 * One `@Composable` member per control. A new control adds a member here, its iPhone half in
 * `IosNativeControls` and its recording in the tests' `RecordingNativeControls`.
 */
interface NativeControls {

    /**
     * An on/off switch: `UISwitch` on the iPhone, at its own size and in the system's colours.
     *
     * The value is [checked] at every composition; setting it from here never reports a change.
     * Only the parent's own flip calls [onCheckedChange].
     *
     * @param checked Whether the switch is on
     * @param onCheckedChange Called with the new value when the parent flips it, or null for a
     *   switch that only shows a value
     * @param enabled Whether the parent can flip it now
     * @param accessibilityLabel What the switch turns on and off, for VoiceOver, or null when the
     *   [modifier]'s semantics already say it
     * @param background The colour of what the switch sits on: the host view's background on the
     *   iPhone, because Compose clears its own pixels under a native view and the window shows
     *   through around a control that is not opaque
     * @param modifier Modifier applied to the switch
     */
    @Composable
    @Suppress("LongParameterList") // the control's values, its callback, the colour under it and the modifier
    fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        enabled: Boolean,
        accessibilityLabel: String?,
        background: Color,
        modifier: Modifier
    )

    /**
     * A segmented choice of one among [labels]: `UISegmentedControl` on the iPhone, filling the
     * width at 32 pt.
     *
     * The selected segment is [selectedIndex] at every composition (-1 for none); setting it from
     * here never reports a selection. Only the parent's own tap calls [onSelect], with the tapped
     * segment's index.
     *
     * @param labels The segments' titles, in order
     * @param selectedIndex The chosen segment, or -1 when none is chosen
     * @param onSelect Called with the index of the segment the parent tapped
     * @param enabled Whether the parent can choose now
     * @param background The colour of what the control sits on, as for [Switch]
     * @param modifier Modifier applied to the control
     */
    @Composable
    @Suppress("LongParameterList") // the control's values, its callback, the colour under it and the modifier
    fun Segmented(
        labels: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        enabled: Boolean,
        background: Color,
        modifier: Modifier
    )

    /**
     * A date asked for in a sheet: on the iPhone a sheet at the medium height holding an inline
     * `UIDatePicker` in date mode, with [dismissLabel] and [confirmLabel] in its bar. Shown while
     * this composable is composed; leaving composition closes it without calling back.
     *
     * Done reports the picked day through [onConfirm] and then calls [onDismiss], as Material's
     * dialog does; Cancel or a swipe down calls only [onDismiss]. [onDismiss] is called once.
     *
     * @param initial The day the picker opens on
     * @param confirmLabel The confirm button's text
     * @param dismissLabel The cancel button's text
     * @param onConfirm Receives the picked day, the calendar day the parent saw
     * @param onDismiss Called once when the sheet closes, whichever way
     */
    @Composable
    fun DateDialog(
        initial: LocalDate,
        confirmLabel: String,
        dismissLabel: String,
        onConfirm: (LocalDate) -> Unit,
        onDismiss: () -> Unit
    )

    /**
     * A time asked for in a sheet: on the iPhone a `UIDatePicker` in time mode with wheels, on the
     * phone's own 12- or 24-hour clock. It behaves as [DateDialog]: Done reports through
     * [onConfirm] and then calls [onDismiss]; Cancel or a swipe down calls only [onDismiss].
     *
     * @param initial The time the wheels open on
     * @param onConfirm Receives the picked hour and minute
     * @param onDismiss Called once when the sheet closes, whichever way
     */
    @Composable
    fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit)

    /**
     * A date in place: on the iPhone a `UIDatePicker` in its compact style, the system's pill, which
     * opens the system's own calendar. A value set from here is shown without reporting a change.
     *
     * @param value The day shown, or null while none is chosen
     * @param onChange Called with the day the parent picks
     * @param label What the date is, for VoiceOver
     * @param background The colour of what the pill sits on, as for [Switch]
     * @param modifier Modifier applied to the pill
     */
    @Composable
    fun CompactDate(
        value: LocalDate?,
        onChange: (LocalDate) -> Unit,
        label: String,
        background: Color,
        modifier: Modifier
    )

    /**
     * A time in place: on the iPhone a compact `UIDatePicker` in time mode, which opens the system's
     * wheels. A value set from here is shown without reporting a change.
     *
     * @param value The time shown
     * @param onChange Called with the time the parent picks
     * @param background The colour of what the pill sits on, as for [Switch]
     * @param modifier Modifier applied to the pill
     */
    @Composable
    fun CompactTime(value: LocalTime, onChange: (LocalTime) -> Unit, background: Color, modifier: Modifier)

    /**
     * A button that opens a menu of [items]: on the iPhone a `UIButton` in the plain style that
     * draws itself, with [title] and the SF Symbol [sfSymbol], whose `UIMenu` it shows as its
     * primary action. Without a symbol it is an option row's pop-up button: [title] (the chosen
     * option, or nothing when none matches) followed by the system's up-and-down chevron. Each
     * entry calls the latest [items]' entry at its place; a [BarMenuItem.checked] entry carries the
     * system's checkmark, a destructive one is drawn so and a disabled one dimmed. Nothing is laid
     * over Compose content: the button takes its own size, at its intrinsic content size.
     *
     * @param items The menu's entries, in order
     * @param title The button's text, or null for none
     * @param sfSymbol The button's SF Symbol (e.g. `plus`), or null for an option row's chevron
     * @param accessibilityLabel What VoiceOver reads for the button, always set, e.g. "Repeat, Weekly"
     * @param tint The text and symbol colour, or [Color.Unspecified] for the system's secondary label
     *   grey (an option row's value)
     * @param background The colour of what the button sits on, as for [Switch]
     * @param modifier Modifier applied to the button
     */
    @Composable
    @Suppress("LongParameterList") // a menu, its two looks, its name and its two colours
    fun MenuButton(
        items: List<BarMenuItem>,
        title: String?,
        sfSymbol: String?,
        accessibilityLabel: String,
        tint: Color,
        background: Color,
        modifier: Modifier
    )
}

/**
 * The shell's native controls, or null where the screen draws Material's own (Android, and every
 * test that provides none). Provided only by the iPhone's shell, per screen.
 */
val LocalNativeControls: ProvidableCompositionLocal<NativeControls?> = staticCompositionLocalOf { null }

/** The native controls the shell provides, or null: the one question an adaptive control asks. */
@Composable
fun nativeControls(): NativeControls? = LocalNativeControls.current
