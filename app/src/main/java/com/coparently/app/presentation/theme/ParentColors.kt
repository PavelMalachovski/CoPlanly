package com.coparently.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The palette of the family on screen, for every [ParentColors] call beneath it (UX-15).
 *
 * Provided **once**, in `MainActivity`, from `ParentPaletteViewModel` — which derives it from
 * `ParentsSource`, the single place the two parents are joined — so no screen has to remember to
 * thread it and no call site can quietly draw the default instead of the family's choice.
 * [ParentColors.fill], [ParentColors.text], [ParentColors.container] and [ParentColors.chipFill]
 * read it as their default argument.
 *
 * The default is pink and blue, which is what a preview, a test host, the auth screen and the
 * first frame before the parents have loaded draw — exactly what the app drew before anyone
 * could choose. `static` because the value changes about once per session (when the parents
 * load, or when a parent picks a colour), and on that change the whole tree genuinely does
 * have to repaint.
 */
val LocalParentPalette = staticCompositionLocalOf { ParentPalette.Default }

/**
 * Parent identity colours, resolved for the theme that is actually being painted.
 *
 * A colour identifies a *parent*, not a role: the app no longer shows the words Mom and Dad,
 * and `"mom"`/`"dad"` survive only as slot identifiers assigned by pairing. Which person holds
 * which slot is decided in `functions/index.js` (`assignSlots`) and shown by name everywhere
 * else — see `presentation/common/ParentLabels.kt`.
 *
 * **Which colour a slot draws in is no longer fixed.** Each parent chooses their own — one of
 * four presets ([ParentColorChoice]) or any hue on the wheel ([ParentColor.custom]) — and the
 * family's two answers arrive here as a [ParentPalette]. The default is the pink and blue the app
 * has always used, so a call site that does not have the parents to hand — and a family where
 * nobody has chosen — looks exactly as it did.
 *
 * `CoPlanlyColors.MomPink`/`DadBlue` are **fill-only** — neither clears 4.5:1 as a foreground
 * in either theme — so any screen that wants to write a parent's name in pink has to reach for
 * the theme-aware `*Light`/`*Dark` partner instead. Three screens (home dashboard, calendar
 * ribbon, expenses split) need that choice, and the luminance test behind it was already
 * copy-pasted into `MonthView` and `DayWeekView`. These helpers are the one place that decision
 * lives.
 *
 * The saturation rule keeps its shape — a custody day background is the hue at a low alpha
 * ([custodyTintAlpha]: 20 % light, 30 % dark since October 2026), a chip or dot is the same hue
 * at full strength.
 *
 * The test is on `MaterialTheme.colorScheme.surface.luminance()`, never `isSystemInDarkTheme()`:
 * the in-app theme setting can force light while the system is dark, and the colour has to
 * follow what is actually on screen.
 */
object ParentColors {

    /** True when the rendered theme is dark, whatever the system is set to. */
    val isDarkTheme: Boolean
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface.luminance() < CoPlanlyColors.DARK_LUMINANCE_THRESHOLD

    /**
     * The parent's identity hue at full strength, for **fills only** — dots, bars, borders,
     * custody tints. Never use as a text colour; use [text] for that.
     *
     * In the dark theme this is the colour's [ParentColor.darkFill], a brighter partner of the
     * light-theme hue, which every colour has since October 2026.
     *
     * @param parent `"mom"` or `"dad"`; anything else falls back to slot 1.
     * @param palette The family's two chosen colours. Defaults to [LocalParentPalette], the
     *   palette of the family on screen; pass one explicitly only to draw a different family.
     */
    @Composable
    @ReadOnlyComposable
    fun fill(parent: String, palette: ParentPalette = LocalParentPalette.current): Color =
        choiceFill(palette.of(parent))

    /**
     * A colour's fill for the theme on screen — what the pickers' swatches draw, so a swatch
     * shows the hue the calendar will actually use.
     *
     * @param colour The colour a parent chose, or is about to.
     */
    @Composable
    @ReadOnlyComposable
    fun choiceFill(colour: ParentColor): Color = fillFor(colour, isDarkTheme)

    /** [choiceFill] for one of the four presets, as the onboarding swatches hold them. */
    @Composable
    @ReadOnlyComposable
    fun choiceFill(choice: ParentColorChoice): Color = choiceFill(choice.color)

    /**
     * The custody wash's alpha in the theme on screen: stronger in the dark theme, where the same
     * alpha over a near-black surface shows far less of the hue (October 2026, "bright colours").
     */
    val custodyTintAlpha: Float
        @Composable @ReadOnlyComposable
        get() = custodyTintAlpha(isDarkTheme)

    /** The pending-proposal wash's alpha in the theme on screen; always under [custodyTintAlpha]. */
    val proposalTintAlpha: Float
        @Composable @ReadOnlyComposable
        get() = proposalTintAlpha(isDarkTheme)

    /**
     * The parent's identity hue as a **text-grade** foreground for the current theme.
     *
     * @param parent `"mom"` or `"dad"`; anything else falls back to mom.
     * @param palette Defaults to [LocalParentPalette]; see [fill].
     */
    @Composable
    @ReadOnlyComposable
    fun text(parent: String, palette: ParentPalette = LocalParentPalette.current): Color =
        textFor(palette.of(parent), isDarkTheme)

    /**
     * A **solid** parent fill that carries text on top of it — a labelled custody band, a chip
     * with a name in it.
     *
     * Not [fill]: the full-strength hue is fill-only for a reason that holds *under* text as
     * well as beside it — white on the original pink is 4.35:1, under AA. The deep tone of each
     * [ParentColor] (the one [text] uses on a light surface) clears AA under white for every
     * preset and every hue on the wheel, which is what `CoPlanlyColors.MomChipFill`/`DadChipFill`
     * already said for pink and blue. Pair it with [onFill] rather than hard-coding white, so a future choice
     * whose deep tone is light gets dark text instead of an unreadable label.
     *
     * @param parent `"mom"` or `"dad"`; anything else falls back to slot 1.
     * @param palette Defaults to [LocalParentPalette]; see [fill].
     */
    @Composable
    @ReadOnlyComposable
    fun chipFill(parent: String, palette: ParentPalette = LocalParentPalette.current): Color =
        palette.of(parent).dark

    /**
     * Black or white, whichever contrasts more with [background] — the text colour for a label
     * drawn *on* a parent fill.
     *
     * Chosen by the WCAG contrast ratio rather than a luminance threshold, because the ratio is
     * the thing AA is stated in and a threshold only approximates it near the middle. Pure and
     * not composable, so a draw lambda can use it too.
     *
     * @param background The opaque colour the text sits on.
     */
    fun onFill(background: Color): Color =
        if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) {
            Color.White
        } else {
            Color.Black
        }

    /**
     * The WCAG 2.x contrast ratio between two opaque colours, from 1 (identical) to 21.
     *
     * @param first One colour.
     * @param second The other; the order does not matter.
     */
    fun contrastRatio(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + WCAG_FLARE) / (minOf(a, b) + WCAG_FLARE)
    }

    /**
     * The calendar friend's teal as a **text-grade** foreground for the current theme. The raw
     * [CoPlanlyColors.FriendTeal] is 3.36:1 on the dark surface, so dark theme takes the light
     * partner — the same rule [text] applies to the two parent hues.
     */
    val friendText: Color
        @Composable @ReadOnlyComposable
        get() = if (isDarkTheme) CoPlanlyColors.FriendTealLight else CoPlanlyColors.FriendTeal

    /**
     * A soft container tint in the parent's hue, for chips and hero backgrounds that carry
     * [text]-coloured content — and the calendar's custody wash itself.
     *
     * Theme-aware since October 2026: the dark theme washes the colour's [ParentColor.darkWash]
     * rather than the light theme's fill, which over a near-black surface read as maroon and
     * brown. What sits on the wash is held to AA on it by `ParentColorsTest`.
     *
     * @param parent `"mom"` or `"dad"`; anything else falls back to mom.
     * @param alpha Tint strength; the default is the calendar's custody wash, so a chip on the
     *   dashboard and a day cell in the grid read as the same system.
     * @param palette Defaults to [LocalParentPalette]; see [fill].
     */
    @Composable
    @ReadOnlyComposable
    fun container(
        parent: String,
        alpha: Float = custodyTintAlpha,
        palette: ParentPalette = LocalParentPalette.current
    ): Color = washTone(palette.of(parent), isDarkTheme).copy(alpha = alpha)

    /**
     * The full hue of [colour] for a theme — [fill] and [choiceFill] without the composition,
     * for a test or a draw lambda that has already resolved the theme.
     */
    fun fillFor(colour: ParentColor, dark: Boolean): Color = if (dark) colour.darkFill else colour.fill

    /** The text-grade tone of [colour] for a theme; see [text]. */
    fun textFor(colour: ParentColor, dark: Boolean): Color = if (dark) colour.light else colour.dark

    /** What [colour] is washed in for a theme, before its alpha; see [container]. */
    fun washTone(colour: ParentColor, dark: Boolean): Color = if (dark) colour.darkWash else colour.fill

    /** The custody wash's alpha for a theme; see [custodyTintAlpha]. */
    fun custodyTintAlpha(dark: Boolean): Float =
        if (dark) CoPlanlyColors.CUSTODY_TINT_ALPHA_DARK else CoPlanlyColors.CUSTODY_TINT_ALPHA_LIGHT

    /** The pending-proposal wash's alpha for a theme; see [proposalTintAlpha]. */
    fun proposalTintAlpha(dark: Boolean): Float =
        if (dark) CoPlanlyColors.PROPOSAL_TINT_ALPHA_DARK else CoPlanlyColors.PROPOSAL_TINT_ALPHA_LIGHT

    /** The 0.05 WCAG adds to both luminances, standing in for ambient flare on a screen. */
    private const val WCAG_FLARE = 0.05f
}
