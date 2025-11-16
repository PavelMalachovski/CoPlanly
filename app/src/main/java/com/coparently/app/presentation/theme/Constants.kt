package com.coparently.app.presentation.theme

import androidx.compose.ui.unit.dp

/**
 * App-wide constants for animations, timings, and configurations.
 * Following Material 3 guidelines and Android best practices.
 */
object AnimationConstants {
    /**
     * Fast animation duration for quick transitions and feedback.
     * Used for: ripples, quick fades, micro-interactions
     */
    const val FAST_ANIMATION_DURATION = 150

    /**
     * Normal animation duration for standard UI transitions.
     * Used for: standard slides, crossfades, scale animations
     */
    const val NORMAL_ANIMATION_DURATION = 200

    /**
     * Slow animation duration for emphasized transitions.
     * Used for: emphasized reveals, complex transitions
     */
    const val SLOW_ANIMATION_DURATION = 300

    /**
     * Very slow animation for special effects.
     * Used for: pulsing animations, icon rotations
     */
    const val VERY_SLOW_ANIMATION_DURATION = 2000

    /**
     * Ripple alpha value for Material Design ripple effects.
     */
    const val RIPPLE_ALPHA = 0.12f

    /**
     * Disabled alpha value for disabled UI elements.
     * Following Material Design accessibility guidelines.
     */
    const val DISABLED_ALPHA = 0.38f

    /**
     * Hover alpha value for hover states.
     */
    const val HOVER_ALPHA = 0.08f

    /**
     * Light background alpha for tinted backgrounds.
     */
    const val LIGHT_BACKGROUND_ALPHA = 0.2f
}

/**
 * Layout constants for consistent spacing and sizing across the app.
 * Following Material 3 guidelines for touch targets and visual density.
 */
object LayoutConstants {
    /**
     * Minimum touch target size per Material Design guidelines.
     * Ensures accessibility for all users.
     */
    val MIN_TOUCH_TARGET = 48.dp

    /**
     * Standard FAB size.
     */
    val FAB_SIZE = 56.dp

    /**
     * Small icon size for compact UI elements.
     */
    val ICON_SIZE_SMALL = 20.dp

    /**
     * Medium icon size for standard UI elements (default).
     */
    val ICON_SIZE_MEDIUM = 24.dp

    /**
     * Large icon size for prominent UI elements.
     */
    val ICON_SIZE_LARGE = 32.dp

    /**
     * Extra large icon size for hero elements.
     */
    val ICON_SIZE_XLARGE = 80.dp

    /**
     * Small corner radius for subtle roundness.
     */
    val CORNER_RADIUS_SMALL = 8.dp

    /**
     * Medium corner radius for standard cards and buttons.
     */
    val CORNER_RADIUS_MEDIUM = 12.dp

    /**
     * Large corner radius for emphasized elements.
     */
    val CORNER_RADIUS_LARGE = 16.dp

    /**
     * Extra large corner radius for pill-shaped elements.
     */
    val CORNER_RADIUS_XLARGE = 24.dp

    /**
     * Standard border width for outlined elements.
     */
    val BORDER_WIDTH = 2.dp

    /**
     * Event indicator dot size (base size before scaling).
     */
    val EVENT_DOT_SIZE = 6.dp

    /**
     * Custody indicator dot size.
     */
    val CUSTODY_DOT_SIZE = 6.dp

    /**
     * Today indicator size for calendar days.
     */
    val TODAY_INDICATOR_SIZE = 32.dp

    /**
     * Regular day indicator size for calendar days.
     */
    val DAY_INDICATOR_SIZE = 28.dp
}

/**
 * Calendar-specific constants for layout and behavior.
 */
object CalendarConstants {
    /**
     * Number of hours to display in day/week views.
     */
    const val HOURS_IN_DAY = 24

    /**
     * Number of days in a week.
     */
    const val DAYS_IN_WEEK = 7

    /**
     * Number of days to show in "3 Days" view.
     */
    const val THREE_DAYS_COUNT = 3

    /**
     * Number of weeks typically shown in month view.
     */
    const val WEEKS_TO_SHOW = 6

    /**
     * Default starting hour for day/week views (6 AM).
     * Optimized for typical family schedules.
     */
    const val DEFAULT_START_HOUR = 6

    /**
     * Swipe threshold in pixels for view changes.
     * Used in gesture detection for swiping between days/weeks.
     */
    const val SWIPE_THRESHOLD = 200f

    /**
     * Number of months to show in the past.
     */
    const val MONTHS_IN_PAST = 12

    /**
     * Number of months to show in the future.
     */
    const val MONTHS_IN_FUTURE = 12
}

/**
 * Time picker constants.
 */
object TimePickerConstants {
    /**
     * Use 24-hour format for time pickers.
     */
    const val USE_24_HOUR_FORMAT = true

    /**
     * Default hour for new events (9 AM).
     */
    const val DEFAULT_EVENT_HOUR = 9

    /**
     * Default minute for new events (0 minutes).
     */
    const val DEFAULT_EVENT_MINUTE = 0

    /**
     * Default event duration in hours.
     */
    const val DEFAULT_EVENT_DURATION_HOURS = 1
}

/**
 * Scale factors for dynamic sizing.
 */
object ScaleConstants {
    /**
     * Scale multiplier for selected/focused items.
     */
    const val SELECTED_SCALE = 1.05f

    /**
     * Scale multiplier for emphasized items (today in calendar).
     */
    const val EMPHASIZED_SCALE = 1.1f

    /**
     * Scale multiplier for normal items.
     */
    const val NORMAL_SCALE = 1f

    /**
     * Scale multiplier for event dots on "today".
     */
    const val TODAY_DOT_SCALE = 1.33f
}

