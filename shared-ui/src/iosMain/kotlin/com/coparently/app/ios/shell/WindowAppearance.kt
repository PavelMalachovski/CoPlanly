package com.coparently.app.ios.shell

import com.coparently.app.ios.controls.uiColorOf
import com.coparently.app.presentation.theme.ContrastLevel
import com.coparently.app.presentation.theme.colorSchemeFor
import platform.UIKit.UIColor
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleLight
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/**
 * The window's side of the app's theme on the iPhone (MON-24 phase 3, part D): the interface style
 * the app's theme choice asks for, and the tint every native control draws in, so the system's
 * bars, alerts, sheets and pickers agree with the Compose screens they sit beside.
 *
 * - **The style.** A choice of System leaves the window's style
 *   [UIUserInterfaceStyleUnspecified], never the resolved one: Compose Multiplatform's
 *   `isSystemInDarkTheme` reads the hosting view's trait collection, so writing the style the system
 *   resolved would feed the app's own override back as "the system's" and keep it after the phone
 *   changes. Dark and Light force [UIUserInterfaceStyleDark] and [UIUserInterfaceStyleLight]. Every
 *   controller the window shows, a presented alert, page sheet or picker sheet included, inherits
 *   the window's style, so a choice changed while one is up flips it with the window.
 * - **The status bar** follows the window's style: the hosting controllers keep UIKit's default
 *   `preferredStatusBarStyle`, which reads it.
 * - **The tint** is the active colour scheme's `primary` ([colorSchemeFor]), as a dynamic colour
 *   resolved again on every trait change: `#4F46E5` in the light standard scheme, `#C2C1FF` in the
 *   dark one and the generated scheme's primary at a raised contrast level, read through
 *   [contrast][apply] each time it resolves. The asset catalog's `AccentColor` is the tint only
 *   until the shell applies this.
 */
internal object WindowAppearance {

    /**
     * Applies [choice] (null System, true Dark, false Light, as `PreferencesRepository
     * .getDarkThemeFlow` answers) and the tint of the scheme [contrast] names to [window]. Call again
     * when either changes; nothing else needs to be told.
     */
    fun apply(window: UIWindow, choice: Boolean?, contrast: () -> ContrastLevel) {
        window.overrideUserInterfaceStyle = styleFor(choice)
        window.tintColor = tintColor(contrast)
    }

    /** The interface style [choice] asks for: unspecified, so the system's, for null. */
    fun styleFor(choice: Boolean?): UIUserInterfaceStyle = when (choice) {
        null -> UIUserInterfaceStyleUnspecified
        true -> UIUserInterfaceStyleDark
        false -> UIUserInterfaceStyleLight
    }

    /** The scheme's `primary` for whichever style the traits resolve to, at [contrast]'s level. */
    fun tintColor(contrast: () -> ContrastLevel): UIColor =
        UIColor(dynamicProvider = { traits: UITraitCollection? ->
            uiColorOf(colorSchemeFor(darkTheme = isDark(traits), contrast = contrast()).primary)
        })

    private fun isDark(traits: UITraitCollection?): Boolean = traits?.userInterfaceStyle == UIUserInterfaceStyleDark
}

/**
 * The shell's root controller: a plain container that reports the window it appears in, once it is
 * in one, so the shell applies the theme to its own window ([WindowAppearance]) rather than to one
 * found through the scenes.
 */
internal class WindowReportingController(
    private val onWindow: (UIWindow) -> Unit
) : UIViewController(nibName = null, bundle = null) {

    override fun viewIsAppearing(animated: Boolean) {
        super.viewIsAppearing(animated)
        view.window?.let(onWindow)
    }
}
