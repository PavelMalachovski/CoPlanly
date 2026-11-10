package com.coparently.app.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * Layout constants shared across screens.
 *
 * This file used to hold four objects of constants — icon sizes, corner radii, calendar paging,
 * time-picker defaults and scale factors — of which one value had a caller. The rest were
 * removed in the October 2026 audit (D-25): an unused token is worse than none, because it reads
 * as the rule while the screens follow another. Icon sizes live in [IconSizes] now.
 */
object LayoutConstants {
    /**
     * Minimum touch target size per Material Design guidelines.
     * Ensures accessibility for all users.
     */
    val MIN_TOUCH_TARGET = 48.dp
}
