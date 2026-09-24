package com.coparently.app.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * The icon sizes a screen may choose: five steps, each named for the place it belongs
 * (docs/AUDIT-2026-10-design.md D-25).
 *
 * Material's components already size their own icons — an icon button's and a FAB's 24 dp, a
 * button's and a chip's 18 dp — so these are for the icons a screen sizes itself, and it picks
 * the step of the place the icon sits in. What the audit found was the opposite: 16, 18, 20 and
 * 22 dp chosen per call site, and three more derived from a font-scaled `iconSize` by factors
 * like 0.75 and 1.17, so a banner, a row and a chip each had an icon of its own size. A size
 * that is not here gets a step and a reason, not a literal.
 *
 * Icons do not grow with the font scale. Material sizes them in dp, and a reader at 2.0 × gets
 * larger text beside the same glyph, as on every platform screen; scaling them too made a row's
 * icon outgrow the touch target it sat in. The dense anatomy of the calendar and the chat — a
 * month cell's swap arrows, an event block's lock, a bubble's delivery ticks — keeps sizes of its
 * own, named where they are drawn, because the cell sizes them rather than a step.
 */
object IconSizes {
    /** A marker inside a line of text or a status pill: the "important" mark beside a title. */
    val Inline = 16.dp

    /** In a button, a chip, a banner or a status line — Material's `ButtonDefaults.IconSize`. */
    val Small = 18.dp

    /** Material's default: a list row's leading or trailing icon, an icon button, a FAB. */
    val Standard = 24.dp

    /** The glyph a choice card leads with, such as whose day an event is on the event form. */
    val Large = 32.dp

    /** The icon an empty, error or first-run screen leads with, on its tonal disc. */
    val Hero = 36.dp
}
