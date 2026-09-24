package com.coparently.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The medium- and high-contrast schemes (docs/AUDIT-2026-10-design.md D-25) against the targets
 * `tools/generate-contrast-schemes.py` aims for: Material's contrast curves at 0.5 and 1.0.
 *
 * Three properties. Every foreground meets its level's target on every background it can sit on,
 * or is already the extreme — black on a light scheme, white on a dark one — where no colour
 * reaches it. The backgrounds are the standard scheme's, untouched. And no foreground loses
 * contrast to the standard scheme on any of its backgrounds: that is what lets a screen's own
 * pairings, including the ones that are not Material's, keep working at every level.
 */
class ContrastSchemesTest {

    /** A foreground role, what it is drawn on, and its targets at medium and high contrast. */
    private class Rule(
        val role: String,
        val foreground: (ColorScheme) -> Color,
        val backgrounds: (ColorScheme) -> List<Color>,
        val medium: Float,
        val high: Float
    )

    private fun surfaces(s: ColorScheme) = listOf(
        s.background, s.surface, s.surfaceVariant, s.surfaceDim, s.surfaceBright,
        s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
        s.surfaceContainerHigh, s.surfaceContainerHighest
    )

    private fun containers(s: ColorScheme) =
        listOf(s.primaryContainer, s.secondaryContainer, s.tertiaryContainer, s.errorContainer)

    private val rules = listOf(
        Rule("onSurface", { it.onSurface }, { surfaces(it) + containers(it) }, 11f, 21f),
        Rule("onBackground", { it.onBackground }, ::surfaces, 11f, 21f),
        Rule("onSurfaceVariant", { it.onSurfaceVariant }, { surfaces(it) + containers(it) }, 7f, 11f),
        Rule("outline", { it.outline }, ::surfaces, 4.5f, 7f),
        Rule("outlineVariant", { it.outlineVariant }, ::surfaces, 3f, 4.5f),
        Rule("primary", { it.primary }, ::surfaces, 7f, 7f),
        Rule("secondary", { it.secondary }, ::surfaces, 7f, 7f),
        Rule("tertiary", { it.tertiary }, ::surfaces, 7f, 7f),
        Rule("error", { it.error }, ::surfaces, 7f, 7f),
        Rule("onPrimary", { it.onPrimary }, { listOf(it.primary) }, 11f, 21f),
        Rule("onSecondary", { it.onSecondary }, { listOf(it.secondary) }, 11f, 21f),
        Rule("onTertiary", { it.onTertiary }, { listOf(it.tertiary) }, 11f, 21f),
        Rule("onError", { it.onError }, { listOf(it.error) }, 11f, 21f),
        Rule("onPrimaryContainer", { it.onPrimaryContainer }, { listOf(it.primaryContainer) }, 7f, 11f),
        Rule("onSecondaryContainer", { it.onSecondaryContainer }, { listOf(it.secondaryContainer) }, 7f, 11f),
        Rule("onTertiaryContainer", { it.onTertiaryContainer }, { listOf(it.tertiaryContainer) }, 7f, 11f),
        Rule("onErrorContainer", { it.onErrorContainer }, { listOf(it.errorContainer) }, 7f, 11f),
        Rule("inverseOnSurface", { it.inverseOnSurface }, { listOf(it.inverseSurface) }, 11f, 21f),
        Rule("inversePrimary", { it.inversePrimary }, { listOf(it.inverseSurface) }, 7f, 7f)
    )

    @Test
    fun `the system's contrast value falls into Material's three steps`() {
        assertEquals(ContrastLevel.STANDARD, ContrastLevel.of(0f))
        assertEquals(ContrastLevel.MEDIUM, ContrastLevel.of(0.5f))
        assertEquals(ContrastLevel.HIGH, ContrastLevel.of(1f))
        // Reduced contrast is something the app has no scheme for; it keeps the standard one.
        assertEquals(ContrastLevel.STANDARD, ContrastLevel.of(-1f))
    }

    @Test
    fun `every foreground meets its target at medium and at high contrast`() {
        raisedLevels.forEach { (dark, level) ->
            val scheme = colorSchemeFor(dark, level)
            rules.forEach { rule -> assertMeetsTarget(rule, scheme, dark, level) }
        }
    }

    @Test
    fun `the backgrounds are the standard scheme's at every level`() {
        listOf(false, true).forEach { dark ->
            val standard = colorSchemeFor(dark, ContrastLevel.STANDARD)
            listOf(ContrastLevel.MEDIUM, ContrastLevel.HIGH).forEach { level ->
                val scheme = colorSchemeFor(dark, level)
                assertEquals(surfaces(standard) + containers(standard), surfaces(scheme) + containers(scheme))
                assertEquals(standard.inverseSurface, scheme.inverseSurface)
            }
        }
    }

    @Test
    fun `no foreground loses contrast to the standard scheme on any background`() {
        raisedLevels.forEach { (dark, level) ->
            val standard = colorSchemeFor(dark, ContrastLevel.STANDARD)
            val scheme = colorSchemeFor(dark, level)
            rules.forEach { rule -> assertNoLoss(rule, standard, scheme, "${label(dark)} $level ${rule.role}") }
        }
    }

    @Test
    fun `the tertiary container is emerald like tertiary, not Material's baseline pink`() {
        listOf(false, true).forEach { dark ->
            val container = colorSchemeFor(dark, ContrastLevel.STANDARD).tertiaryContainer
            assertTrue(container.green > container.red, "tertiaryContainer $container is not green")
        }
    }

    /** Both themes at medium and at high contrast. */
    private val raisedLevels = listOf(false, true).flatMap { dark ->
        listOf(ContrastLevel.MEDIUM, ContrastLevel.HIGH).map { level -> dark to level }
    }

    private fun label(dark: Boolean) = if (dark) "dark" else "light"

    private fun assertMeetsTarget(rule: Rule, scheme: ColorScheme, dark: Boolean, level: ContrastLevel) {
        val target = if (level == ContrastLevel.MEDIUM) rule.medium else rule.high
        val foreground = rule.foreground(scheme)
        rule.backgrounds(scheme).forEach { background ->
            val ratio = ParentColors.contrastRatio(foreground, background)
            assertTrue(
                ratio >= target || foreground == extremeAgainst(background),
                "${label(dark)} $level ${rule.role}: $ratio against $background, wanted $target"
            )
        }
    }

    private fun assertNoLoss(rule: Rule, standard: ColorScheme, scheme: ColorScheme, what: String) {
        rule.backgrounds(scheme).zip(rule.backgrounds(standard)).forEach { (now, before) ->
            val gained = ParentColors.contrastRatio(rule.foreground(scheme), now)
            val had = ParentColors.contrastRatio(rule.foreground(standard), before)
            assertTrue(gained >= had - EPSILON, "$what: $gained, was $had")
        }
    }

    /** Black for a light background, white for a dark one: where a foreground runs out of room. */
    private fun extremeAgainst(background: Color): Color =
        if (background.luminance() > HALF) Color.Black else Color.White

    private companion object {
        const val EPSILON = 0.001f
        const val HALF = 0.5f
    }
}
