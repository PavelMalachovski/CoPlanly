package com.coparently.app.ios.controls

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.coparently.app.presentation.common.NativeControls
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISwitch

/**
 * The iPhone's own controls (MON-24 phase 3, part C), which the shell provides to every hosted
 * screen as `LocalNativeControls`: each adaptive control of the shared screens is drawn here as
 * UIKit's, inside the Compose screen through `UIKitView`.
 */
internal object IosNativeControls : NativeControls {

    /**
     * A `UISwitch`, laid out at the switch's own intrinsic size (51 × 31 pt before iOS 26) and in
     * the system's green. [checked] and [enabled] are set from Compose at every update without the
     * switch sending `UIControlEventValueChanged`; only the parent's flip reaches [onCheckedChange].
     */
    @Composable
    override fun Switch(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        enabled: Boolean,
        accessibilityLabel: String?,
        modifier: Modifier
    ) {
        val callback by rememberUpdatedState(onCheckedChange)
        val (width, height) = switchSize
        UIKitView(
            factory = { makeSwitch { callback?.invoke(it) } },
            modifier = modifier.size(width.dp, height.dp),
            update = { view ->
                updateSwitch(view, checked, enabled && onCheckedChange != null, accessibilityLabel)
            },
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
        )
    }

    /** The switch's intrinsic size in points, measured once from UIKit's own switch. */
    // `CGRectMake` and `useContents` are Kotlin/Native's C interop.
    @OptIn(ExperimentalForeignApi::class)
    private val switchSize: Pair<Double, Double> by lazy {
        UISwitch(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).intrinsicContentSize.useContents {
            if (width > 0.0 && height > 0.0) width to height else SWITCH_WIDTH_PT to SWITCH_HEIGHT_PT
        }
    }

    /** `UISwitch`'s intrinsic width before iOS 26, used if UIKit reports no size. */
    private const val SWITCH_WIDTH_PT = 51.0

    /** `UISwitch`'s intrinsic height before iOS 26, used if UIKit reports no size. */
    private const val SWITCH_HEIGHT_PT = 31.0
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
