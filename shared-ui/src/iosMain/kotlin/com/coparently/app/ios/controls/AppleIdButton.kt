package com.coparently.app.ios.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeSignIn
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventTouchUpInside

/**
 * The system's "Sign in with Apple" button (`ASAuthorizationAppleIDButton`), in place, filling the
 * size [modifier] gives it (MON-24 phase 4, part A; `NativeControls.AppleSignInButton`).
 *
 * Apple asks for a black button on a light background and a white one on a dark background, so the
 * style follows [background]'s luminance; a style is fixed when the button is made, so a change of
 * theme makes a new button ([key]). The host view is painted [background], as every in-place
 * control's is (part C's interop rule): the button's rounded corners show what it sits on, not the
 * window. Only the parent's tap reaches [onClick]; [enabled] is set at every update.
 */
@Composable
internal fun AppleIdButton(onClick: () -> Unit, enabled: Boolean, background: Color, modifier: Modifier) {
    val callback by rememberUpdatedState(onClick)
    val dark = background.luminance() < DARK_BACKGROUND_LUMINANCE
    key(dark) {
        UIKitView(
            factory = { TappedAppleIdButton(dark) { callback() } },
            modifier = modifier,
            update = { view ->
                view.backgroundColor = uiColorOf(background)
                if (view.enabled != enabled) view.enabled = enabled
            },
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
        )
    }
}

/** Below this luminance the background counts as dark, and Apple's button is drawn white. */
private const val DARK_BACKGROUND_LUMINANCE = 0.5f

/**
 * An `ASAuthorizationAppleIDButton` of the sign-in type that is its own target, so the action and
 * what it calls live exactly as long as the view (UIKit does not retain a control's targets), as
 * `ValueChangedSwitch` is. White on a [dark] background, black otherwise.
 */
// `NSSelectorFromString` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class TappedAppleIdButton(
    dark: Boolean,
    private val onTap: () -> Unit
) : ASAuthorizationAppleIDButton(
    authorizationButtonType = ASAuthorizationAppleIDButtonTypeSignIn,
    authorizationButtonStyle = if (dark) {
        ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite
    } else {
        ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack
    }
) {

    init {
        addTarget(this, action = NSSelectorFromString("tapped:"), forControlEvents = UIControlEventTouchUpInside)
    }

    /** The target-action of `UIControlEventTouchUpInside`. */
    @ObjCAction
    fun tapped(sender: ASAuthorizationAppleIDButton) {
        onTap()
    }
}
