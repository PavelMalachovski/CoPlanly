package com.coparently.app.presentation.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.theme.CoPlanlyColors
import com.coparently.app.presentation.theme.ParentColors

/*
 * The colours of a month cell's layers and of its event dots, shared by `MonthView` and the mini
 * month so that the two cannot draw one day two ways. `DayCellFills` decides which layers a cell
 * has; this file says what each layer looks like. Lifted out of `MonthView` unchanged.
 */

/** Public-holiday tint strength, drawn over the cell's base fill. */
internal const val HOLIDAY_TINT_ALPHA = 0.10f

/** Most event dots a cell shows before collapsing the rest into a "+". */
internal const val MAX_EVENT_DOTS = 3

/** Diameter of an event dot in a month cell. */
internal val EVENT_DOT_SIZE = 6.dp

/** Width of the ring of the cell's base around an event dot, which keeps it 3:1 on any wash. */
internal val EVENT_DOT_HALO = 1.dp

/** Dot opacity on leading/trailing days from the neighbouring months. */
internal const val OUTSIDE_MONTH_DOT_ALPHA = 0.4f

/**
 * A cell's base layer: the weekend grey or the surface.
 *
 * Reads the actually-rendered theme (the app can force light while the system is dark);
 * `isSystemInDarkTheme()` would pick the dark weekend fill on a light grid.
 */
@Composable
internal fun dayCellBaseColor(base: DayCellBase): Color = when (base) {
    DayCellBase.WEEKEND ->
        if (MaterialTheme.colorScheme.surface.luminance() < CoPlanlyColors.DARK_LUMINANCE_THRESHOLD) {
            CoPlanlyColors.WeekendBackgroundDark
        } else {
            CoPlanlyColors.WeekendBackgroundLight
        }
    DayCellBase.SURFACE -> MaterialTheme.colorScheme.surface
}

/**
 * A cell's overlay: the custody wash, the public-holiday tint, or nothing.
 *
 * The custody wash is the parent's colour at [ParentColors.custodyTintAlpha], scaled down on a
 * day borrowed from a neighbouring month (see [adjacentScale]).
 */
@Composable
internal fun dayCellOverlayColor(fill: DayCellFill): Color {
    val custodyAlpha = ParentColors.custodyTintAlpha * adjacentScale(fill.isAdjacentMonth)
    return when (fill.overlay) {
        DayCellOverlay.CUSTODY_MOM -> ParentColors.container("mom", alpha = custodyAlpha)
        DayCellOverlay.CUSTODY_DAD -> ParentColors.container("dad", alpha = custodyAlpha)
        DayCellOverlay.PUBLIC_HOLIDAY -> CoPlanlyColors.HolidayRed.copy(alpha = HOLIDAY_TINT_ALPHA)
        DayCellOverlay.TODAY, DayCellOverlay.NONE -> Color.Transparent
    }
}

/**
 * Dot colour for an event, dimmed on days outside the shown month.
 *
 * Full-strength parent hue — a dot is a fill, not text, so the AA floor that forces the event
 * *chip* onto the darker `MomChipFill`/`DadChipFill` variants does not apply here.
 *
 * An event a calendar friend takes part in wears the friend's teal instead (item 16). It still
 * belongs to a parent — `parentOwner` is untouched, because whose day it falls on is a fact
 * about custody — but the dot answers the question the friend's presence actually raises:
 * *somebody outside the two of us is involved in this one*.
 */
@Composable
internal fun eventDotColor(
    parentOwner: String,
    isCurrentMonth: Boolean,
    friendParticipates: Boolean = false
): Color {
    val base = when {
        friendParticipates -> CoPlanlyColors.FriendTeal
        parentOwner == "mom" -> ParentColors.fill("mom")
        parentOwner == "dad" -> ParentColors.fill("dad")
        else -> MaterialTheme.colorScheme.tertiary
    }
    return if (isCurrentMonth) base else base.copy(alpha = OUTSIDE_MONTH_DOT_ALPHA)
}

/**
 * How much of the custody tint a cell gets, given whether it belongs to a neighbouring month.
 *
 * A function rather than an `if` inside the cell composable, which is long enough that detekt
 * counts its branches: this is a lookup, not a decision the cell makes.
 */
internal fun adjacentScale(isAdjacentMonth: Boolean): Float =
    if (isAdjacentMonth) CoPlanlyColors.ADJACENT_MONTH_TINT_SCALE else 1f
