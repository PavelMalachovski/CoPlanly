package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

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
     * @param modifier Modifier applied to the switch
     */
    @Composable
    fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        enabled: Boolean,
        accessibilityLabel: String?,
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
     * @param modifier Modifier applied to the control
     */
    @Composable
    fun Segmented(
        labels: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        enabled: Boolean,
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
