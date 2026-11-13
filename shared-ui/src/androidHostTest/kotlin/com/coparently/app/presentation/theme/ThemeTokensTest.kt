package com.coparently.app.presentation.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The November 2026 redesign's colour tokens, and the contrast every pairing of them must keep. */
class ThemeTokensTest {

    @Test
    fun `the light scheme is a lavender ground under white cards`() {
        assertEquals(Color(0xFFF5F2FC), LightColorScheme.background)
        assertEquals(Color.White, LightColorScheme.surfaceContainer)
        assertEquals(Color(0xFFF1EEFA), LightColorScheme.surfaceContainerLow)
        assertEquals(Color(0xFFE4DEFA), LightColorScheme.primaryContainer)
        assertEquals(Color(0xFF2A1F70), LightColorScheme.onPrimaryContainer)
        assertEquals(Color(0xFFECE8F6), LightColorScheme.outlineVariant)
        assertEquals(Color(0xFF4F46E5), LightColorScheme.primary)
    }

    @Test
    fun `the ground ends in the deeper lavender`() {
        assertEquals(Color(0xFFEEE9FA), CoPlanlyColors.LightGroundEnd)
        assertEquals(CoPlanlyColors.LightBackground, LightColorScheme.background)
        assertEquals(CoPlanlyColors.DarkBackground, DarkColorScheme.background)
    }

    @Test
    fun `the screen container is transparent so the ground shows through`() {
        assertEquals(Color.Transparent, ScreenContainerColor)
    }

    @Test
    fun `text reads at AA on a card, on the ground and on the primary container in both themes`() {
        mapOf("light" to LightColorScheme, "dark" to DarkColorScheme).forEach { (name, scheme) ->
            assertAtLeastAa("$name onSurface on surfaceContainer", scheme.onSurface, scheme.surfaceContainer)
            assertAtLeastAa("$name onSurfaceVariant on background", scheme.onSurfaceVariant, scheme.background)
            assertAtLeastAa(
                "$name onPrimaryContainer on primaryContainer",
                scheme.onPrimaryContainer,
                scheme.primaryContainer
            )
        }
    }

    private fun assertAtLeastAa(what: String, foreground: Color, background: Color) {
        val ratio = ParentColors.contrastRatio(foreground, background)
        assertTrue(ratio >= AA_NORMAL_TEXT, "$what: $ratio")
    }

    private companion object {
        const val AA_NORMAL_TEXT = 4.5f
    }
}
