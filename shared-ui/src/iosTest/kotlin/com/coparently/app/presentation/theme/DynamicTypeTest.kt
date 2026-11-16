package com.coparently.app.presentation.theme

import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryUnspecified
import platform.UIKit.UIScreen
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Dynamic Type on the iPhone (MON-24 phase 3, part D, Task 6). A doc test: the app maps nothing,
 * because Compose Multiplatform 1.9.3 already does.
 *
 * The finding, from CMP 1.9.3's `ui-uikit` sources: `androidx/compose/ui/uikit/Extensions.uikit.kt`,
 * `internal val UIView.density`, builds every hosted composition's `Density` with
 * `fontScale = uiContentSizeCategoryToFontScaleMap[traitCollection.preferredContentSizeCategory] ?: 1f`
 * (XS 0.8, S 0.85, M 0.9, L 1.0, XL 1.1, XXL 1.2, XXXL 1.3, AX1–AX5 1.4–1.8; an unspecified category
 * is 1.0). `ComposeSceneMediator.updateLayout` sets the scene's density from it again on each layout
 * of its view, which is how a change of the text size reaches a running app. So
 * `LocalDensity.current.fontScale` follows the category, and a second mapping in the shell would
 * scale the text twice.
 *
 * shared-ui's iosTest has no Compose UI test harness and its test binary runs without a
 * `UIApplication`, so this does not compose: it pins the half that can be read here, that the
 * simulator's default category is one CMP reads as a font scale of 1. The scaled text itself is seen
 * in the tour's large-text pass.
 */
class DynamicTypeTest {

    @Test
    fun cmpFontScaleFollowsCategory() {
        val category = UIScreen.mainScreen.traitCollection.preferredContentSizeCategory
        assertTrue(
            category == UIContentSizeCategoryLarge || category == UIContentSizeCategoryUnspecified,
            "the simulator's default text size is $category, which CMP does not read as a font scale of 1"
        )
    }
}
