package com.coparently.app.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Expressive shape system for CoPlanly.
 * Generous corner radii give the app a soft, modern look consistent with
 * current Material 3 expressive styling.
 *
 * - extraSmall: chips, small controls, a day cell's selection
 * - small: text fields, menu surfaces, list rows, banners
 * - medium: cards, grouped sections
 * - large: dialogs, bottom sheets, hero cards
 * - extraLarge: hero surfaces, full-screen sheets
 *
 * **This file is the one place a corner is chosen** (docs/AUDIT-2026-10-design.md D-19): a screen
 * takes `MaterialTheme.shapes`, [CoPlanlyCorners] or [chatBubbleShape], and detekt's
 * `ForbiddenImport` refuses `RoundedCornerShape` anywhere outside `presentation/theme/`. The audit
 * counted 71 hand-written corners in 20 radii before this rule.
 */
val CoPlanlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** The corners below Material's five steps, each named for the one kind of thing it rounds. */
object CoPlanlyCorners {
    /** A mark inside a line or a cell: a banner's dash, a legend swatch, a tick. */
    val Mark: Shape = RoundedCornerShape(2.dp)

    /** A bar or a tag inside a component: a split bar, a contact window, a colour sample. */
    val Tag: Shape = RoundedCornerShape(4.dp)

    /** Ends that are half the height, however tall the text makes it: pills and chips. */
    val Pill: Shape = CircleShape
}

/**
 * A chat bubble's corners.
 *
 * The two corners on the far side from the sender stay fully round. On the sender's own side
 * the corners between adjacent bubbles of one run are the mid radius, so the run stacks into
 * one visual block; the run's *last* bubble alone takes the tight tail corner, which is what
 * points the block at its sender. A lone message is both first and last, so it gets the round
 * top and the tail. (Moved here from `MessagesList` with the rest of the app's corners.)
 *
 * @param isCurrentUser Which side the bubble sits on
 * @param startsGroup Whether this is the first bubble of a run by the same sender
 * @param endsGroup Whether this is the last bubble of a run — the tail's owner
 * @return The bubble's shape
 */
fun chatBubbleShape(isCurrentUser: Boolean, startsGroup: Boolean, endsGroup: Boolean): Shape {
    val innerTop = if (startsGroup) BUBBLE_ROUND else BUBBLE_MID
    val innerBottom = if (endsGroup) BUBBLE_TAIL else BUBBLE_MID
    return if (isCurrentUser) {
        RoundedCornerShape(
            topStart = BUBBLE_ROUND,
            topEnd = innerTop,
            bottomStart = BUBBLE_ROUND,
            bottomEnd = innerBottom
        )
    } else {
        RoundedCornerShape(
            topStart = innerTop,
            topEnd = BUBBLE_ROUND,
            bottomStart = innerBottom,
            bottomEnd = BUBBLE_ROUND
        )
    }
}

/** Fully rounded bubble corner. */
private val BUBBLE_ROUND = 18.dp

/** Sender-side corner between two adjacent bubbles of one run. */
private val BUBBLE_MID = 6.dp

/** The tail: the tight sender-side bottom corner on the last bubble of a run. */
private val BUBBLE_TAIL = 4.dp
