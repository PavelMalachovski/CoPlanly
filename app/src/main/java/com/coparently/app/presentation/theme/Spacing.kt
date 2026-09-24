package com.coparently.app.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing steps: paddings, the gaps of `Arrangement.spacedBy` and spacers
 * (docs/AUDIT-2026-10-design.md D-19).
 *
 * A 4 dp grid, as Material lays out, plus a 2 dp hairline for the gap inside a dense element.
 * The audit counted 873 literal dp in `presentation/`; every padding, gap and spacer on these
 * steps now names one, so a screen reads as "a large gap", and a change of rhythm is one edit.
 * What is still a literal is either not spacing (a size, a height, an offset), a dense calendar
 * mark's own interior, or the one deliberate value off the grid — the calendar banners' 9 dp —
 * which says why beside it. The 6, 10, 14, 18 and 20 dp that week 5 left as literals were snapped
 * to these steps in week 7 (docs/AUDIT-2026-10-release.md §4).
 */
object Spacing {
    /** Inside a dense element: between a dot and its label, the lines of a two-line chip. */
    val XXS = 2.dp

    /** Between an icon and its text inside a control; a chip's inner padding. */
    val XS = 4.dp

    /** The default gap between items in a row or a column. */
    val S = 8.dp

    /** Between groups inside a card; a card's padding on a compact element. */
    val M = 12.dp

    /** A screen's horizontal margin and a card's padding. */
    val L = 16.dp

    /** Between sections of a screen; a dialog's or a sheet's padding. */
    val XL = 24.dp

    /** Around an empty or first-run screen's content. */
    val XXL = 32.dp
}
