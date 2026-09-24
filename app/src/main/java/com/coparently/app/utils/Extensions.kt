package com.coparently.app.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Extension functions for cleaner, more readable code.
 * Provides convenient utilities for Date, Time, Color, and String operations.
 */

// ==================== LocalDate Extensions ====================

/**
 * Check if this date is today.
 * @return true if this date is the current date
 */
fun LocalDate.isToday(): Boolean = this == LocalDate.now()

/**
 * Check if this date is tomorrow.
 * @return true if this date is one day after current date
 */
fun LocalDate.isTomorrow(): Boolean = this == LocalDate.now().plusDays(1)

/**
 * Check if this date is yesterday.
 * @return true if this date is one day before current date
 */
fun LocalDate.isYesterday(): Boolean = this == LocalDate.now().minusDays(1)

/**
 * Check if this date is in the past (before today).
 * @return true if this date is before current date
 */
fun LocalDate.isInPast(): Boolean = this.isBefore(LocalDate.now())

/**
 * Check if this date is in the future (after today).
 * @return true if this date is after current date
 */
fun LocalDate.isInFuture(): Boolean = this.isAfter(LocalDate.now())

// ==================== LocalDateTime Extensions ====================

/**
 * Check if this date-time is today.
 * @return true if date component is current date
 */
fun LocalDateTime.isToday(): Boolean = toLocalDate().isToday()

// ==================== Color Extensions ====================

/**
 * Create a copy of this color with the specified alpha value.
 * @param alpha Alpha value between 0f and 1f
 * @return New color with specified alpha
 */
fun Color.withAlpha(alpha: Float): Color = copy(alpha = alpha)

/**
 * Lighten this color by the specified factor.
 * @param factor Lightening factor (0f to 1f), default 0.2f
 * @return Lightened color
 */
fun Color.lighten(factor: Float = 0.2f): Color {
    require(factor in 0f..1f) { "Factor must be between 0f and 1f" }
    return copy(
        red = red + (1 - red) * factor,
        green = green + (1 - green) * factor,
        blue = blue + (1 - blue) * factor
    )
}

/**
 * Darken this color by the specified factor.
 * @param factor Darkening factor (0f to 1f), default 0.2f
 * @return Darkened color
 */
fun Color.darken(factor: Float = 0.2f): Color {
    require(factor in 0f..1f) { "Factor must be between 0f and 1f" }
    return copy(
        red = red * (1 - factor),
        green = green * (1 - factor),
        blue = blue * (1 - factor)
    )
}

/**
 * Adjust color brightness.
 * @param amount Positive to lighten, negative to darken
 * @return Adjusted color
 */
fun Color.adjustBrightness(amount: Float): Color {
    return if (amount >= 0) {
        lighten(amount)
    } else {
        darken(-amount)
    }
}

// ==================== String Extensions ====================

/**
 * Return this string or a default if null or empty.
 * @param default Default string to return, empty string by default
 * @return This string or default
 */
fun String?.orDefault(default: String = ""): String =
    if (this.isNullOrEmpty()) default else this

/**
 * Capitalize first character of the string.
 * @return String with first character capitalized
 */
fun String.capitalizeFirst(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

/**
 * Truncate string to specified length with ellipsis.
 * @param maxLength Maximum length before truncation
 * @param ellipsis String to append when truncated, default "..."
 * @return Truncated string
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return if (length <= maxLength) {
        this
    } else {
        take(maxLength - ellipsis.length) + ellipsis
    }
}

/**
 * Check if string contains only digits.
 * @return true if all characters are digits
 */
fun String.isNumeric(): Boolean = all { it.isDigit() }

/**
 * Safe substring that doesn't throw exception on invalid indices.
 * @param startIndex Start index (inclusive)
 * @param endIndex End index (exclusive)
 * @return Substring or empty string if indices are invalid
 */
fun String.safeSubstring(startIndex: Int, endIndex: Int): String {
    val safeStart = startIndex.coerceIn(0, length)
    val safeEnd = endIndex.coerceIn(safeStart, length)
    return substring(safeStart, safeEnd)
}

// ==================== Collection Extensions ====================

/**
 * Safe get element at index or return default value.
 * @param index Index to access
 * @param default Default value if index is out of bounds
 * @return Element at index or default value
 */
fun <T> List<T>.getOrDefault(index: Int, default: T): T {
    return getOrNull(index) ?: default
}

/**
 * Check if list is not null and not empty.
 * @return true if list has elements
 */
fun <T> List<T>?.isNotNullOrEmpty(): Boolean {
    return this != null && isNotEmpty()
}

// ==================== Number Extensions ====================

/**
 * Coerce value between minimum and maximum.
 * @param min Minimum value
 * @param max Maximum value
 * @return Coerced value
 */
fun Int.coerceInRange(min: Int, max: Int): Int = coerceIn(min, max)

/**
 * Coerce value between minimum and maximum.
 * @param min Minimum value
 * @param max Maximum value
 * @return Coerced value
 */
fun Float.coerceInRange(min: Float, max: Float): Float = coerceIn(min, max)

/**
 * Convert boolean to Int (1 for true, 0 for false).
 * @return 1 if true, 0 if false
 */
fun Boolean.toInt(): Int = if (this) 1 else 0

// ==================== Context Extensions ====================

/**
 * Walks down a `Context` chain to the [Activity] hosting it.
 *
 * Needed because Credential Manager renders its UI on an `Activity` and refuses an application
 * context, while Compose hands a composable whatever context its tree was built with — often a
 * `ContextWrapper` wrapping the `Activity` rather than the `Activity` itself.
 *
 * @return the hosting [Activity], or null when this context is not attached to one.
 */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (true) {
        when {
            current is Activity -> return current
            current is ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
}
