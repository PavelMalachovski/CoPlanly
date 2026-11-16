package com.coparently.app.ios.controls

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UISegmentedControl
import platform.UIKit.UIView

/**
 * The UIKit colour of [color], or clear for [Color.Unspecified].
 *
 * Every native control's host view is painted with the colour of what it sits on, because Compose
 * Multiplatform 1.9.3 does not draw under an interop view: it clears its own pixels in the view's
 * bounds (`drawRect(Color.Transparent, blendMode = BlendMode.Clear)` in
 * `UIKitInteropElementHolder.onPropertiesChanged`) and the native views live below the Metal view
 * (`UIKitComposeSceneLayersHolder.attach`), so around a control that is not opaque the window shows
 * through: the `ComposeView`'s own white or black (`ComposeView.updateBackgroundColor`), never the
 * card or ground the control appears to sit on.
 */
internal fun uiColorOf(color: Color): UIColor =
    if (color.isUnspecified) {
        UIColor.clearColor
    } else {
        UIColor.colorWithRed(
            red = color.red.toDouble(),
            green = color.green.toDouble(),
            blue = color.blue.toDouble(),
            alpha = color.alpha.toDouble()
        )
    }

/**
 * A plain view holding [control] at its whole size, so the colour behind a segmented control can be
 * painted ([backgroundColor]) without tinting the control's own track, which a
 * `UISegmentedControl`'s `backgroundColor` would.
 */
// `CGRectMake` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class SegmentedHost(val control: UISegmentedControl) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        addSubview(control)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        control.setFrame(bounds)
    }
}
