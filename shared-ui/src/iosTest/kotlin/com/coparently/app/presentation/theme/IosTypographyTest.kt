package com.coparently.app.presentation.theme

import androidx.compose.ui.text.font.FontFamily
import com.coparently.app.presentation.common.wordmarkStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iPhone's type (MON-24 phase 3, part D, Task 3): the body is the system's family, SF Pro, and
 * the wordmark keeps the brand's Onest whatever the theme sets.
 *
 * Nothing here composes (shared-ui's iosTest has no Compose UI test harness), so the wordmark's
 * family is checked through [wordmarkStyle] with a stand-in for Onest, which is a composable
 * resource.
 */
class IosTypographyTest {

    @Test
    fun bodyIsSystemFamilyAndWordmarkIsOnest() {
        val typography = Typography.withFontFamily(iosBodyFontFamily())
        assertEquals(FontFamily.Default, iosBodyFontFamily())
        assertEquals(FontFamily.Default, typography.bodyLarge.fontFamily)

        val onest = FontFamily.Serif
        val wordmark = wordmarkStyle(typography.headlineSmallEmphasized, onest)
        assertEquals(onest, wordmark.fontFamily)
        assertEquals(typography.headlineSmallEmphasized.copy(fontFamily = onest), wordmark)
    }
}
