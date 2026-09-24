package com.coparently.app.screenshots

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Three components at high contrast, in both themes (docs/AUDIT-2026-10-design.md D-25).
 *
 * The raised-contrast schemes move every foreground and leave every background where it was, so
 * the question a picture answers is whether what is drawn on a background of its own still reads:
 * the month grid's custody bands, holiday numerals and event dots, a Settings group's rows and its
 * parent-coloured pill, and a banner with its buttons. The same components' standard images sit
 * beside these in their directories, which is where the comparison is made.
 *
 * @param variant One of [ScreenshotVariants.CONTRAST]
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class ContrastScreenshots(variant: ScreenshotVariant) : ScreenshotMatrix(variant) {

    @Test
    fun monthGrid() = snap("calendar_month_grid") { MonthGridFixture() }

    @Test
    fun settingsFamilyGroup() = snap("settings_family_group") { SettingsFamilyGroupFixture() }

    @Test
    fun inlineBanner() = snap("common_inline_banner") { InlineBannerFixture() }

    companion object {
        /** The variants this class runs over. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ScreenshotVariants.parameters(ScreenshotVariants.CONTRAST)
    }
}
