package com.coparently.app.ios.shell

import androidx.compose.ui.graphics.Color
import com.coparently.app.presentation.theme.ContrastLevel
import com.coparently.app.presentation.theme.DarkColorScheme
import com.coparently.app.presentation.theme.LightMediumContrastColorScheme
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleDark
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleLight
import platform.UIKit.UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.resolvedColorWithTraitCollection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window's side of the app's theme (MON-24 phase 3, part D, Task 1): System leaves the style
 * unspecified so the system's own decides and nothing feeds back, Dark and Light force it, a
 * controller in the window follows it, and the tint is the active scheme's primary.
 */
@OptIn(ExperimentalForeignApi::class)
class WindowAppearanceTest {

    private val standard: () -> ContrastLevel = { ContrastLevel.STANDARD }

    private fun window() = UIWindow(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT))

    @Test
    fun systemChoiceLeavesStyleUnspecified() {
        val window = window()
        WindowAppearance.apply(window, true, standard)

        WindowAppearance.apply(window, null, standard)

        assertEquals(UIUserInterfaceStyleUnspecified, window.overrideUserInterfaceStyle)
    }

    @Test
    fun darkChoiceForcesDark() {
        val window = window()

        WindowAppearance.apply(window, true, standard)
        assertEquals(UIUserInterfaceStyleDark, window.overrideUserInterfaceStyle)

        WindowAppearance.apply(window, false, standard)
        assertEquals(UIUserInterfaceStyleLight, window.overrideUserInterfaceStyle)
    }

    @Test
    fun tintIsPrimaryOfScheme() {
        val window = window()
        WindowAppearance.apply(window, null, standard)
        assertColor(INDIGO, resolve(window, UIUserInterfaceStyleLight))
        assertColor(DarkColorScheme.primary, resolve(window, UIUserInterfaceStyleDark))
        assertColor(LAVENDER, resolve(window, UIUserInterfaceStyleDark))

        WindowAppearance.apply(window, null) { ContrastLevel.MEDIUM }
        assertColor(LightMediumContrastColorScheme.primary, resolve(window, UIUserInterfaceStyleLight))
    }

    @Test
    fun presentedControllerFollowsWindowStyle() {
        val window = window()
        val controller = UIViewController(nibName = null, bundle = null)
        window.rootViewController = controller
        window.makeKeyAndVisible()
        try {
            WindowAppearance.apply(window, true, standard)
            window.layoutIfNeeded()
            assertEquals(UIUserInterfaceStyleDark, controller.traitCollection.userInterfaceStyle)

            WindowAppearance.apply(window, false, standard)
            window.layoutIfNeeded()
            assertEquals(UIUserInterfaceStyleLight, controller.traitCollection.userInterfaceStyle)
        } finally {
            // Never left key for the test classes that run after this one.
            window.hidden = true
        }
    }

    /** The window's tint as it resolves under [style]. */
    private fun resolve(window: UIWindow, style: UIUserInterfaceStyle): UIColor =
        checkNotNull(window.tintColor)
            .resolvedColorWithTraitCollection(UITraitCollection.traitCollectionWithUserInterfaceStyle(style))

    private fun assertColor(expected: Color, actual: UIColor) = memScoped {
        val red = alloc<DoubleVar>()
        val green = alloc<DoubleVar>()
        val blue = alloc<DoubleVar>()
        val alpha = alloc<DoubleVar>()
        assertTrue(actual.getRed(red.ptr, green.ptr, blue.ptr, alpha.ptr), "not an RGB colour: $actual")
        val channels = listOf(
            expected.red to red.value,
            expected.green to green.value,
            expected.blue to blue.value,
            expected.alpha to alpha.value
        )
        channels.forEach { (want, got) ->
            val message = "expected $expected, got r=${red.value} g=${green.value} b=${blue.value}"
            assertTrue(abs(want - got) <= ONE_STEP, message)
        }
    }

    private companion object {
        const val WIDTH = 390.0
        const val HEIGHT = 844.0

        /** One step of an 8-bit channel. */
        const val ONE_STEP = 1.0 / 255.0

        /** The light standard scheme's primary, the app's indigo. */
        val INDIGO = Color(0xFF4F46E5)

        /** The dark standard scheme's primary. */
        val LAVENDER = Color(0xFFC2C1FF)
    }
}
