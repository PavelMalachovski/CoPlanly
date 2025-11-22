package com.coparently.app.utils

import com.coparently.app.domain.model.Event
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility object for accessibility features.
 * Provides helper functions for creating descriptive content for screen readers (TalkBack).
 */
object AccessibilityUtils {

    /**
     * Creates a comprehensive description of an event for screen readers.
     * Includes title, type, description, time, and parent owner information.
     *
     * @param event The event to describe
     * @return A detailed, screen-reader-friendly description
     */
    fun createEventDescription(event: Event): String {
        return buildString {
            // Event title
            append(event.title)
            append(", ")

            // Event type
            append(event.eventType.replaceFirstChar { it.uppercase() })
            append(" event")

            // Description if available
            if (event.description?.isNotBlank() == true) {
                append(". ")
                append(event.description)
            }

            // Start time
            append(". Starts ")
            append(event.startDateTime.formatForAccessibility())

            // End time if available
            if (event.endDateTime != null) {
                append(", ends ")
                append(event.endDateTime.formatForAccessibility())
            } else {
                append(", no end time")
            }

            // Parent owner
            if (event.parentOwner.isNotBlank()) {
                append(". Assigned to ")
                append(event.parentOwner)
            }

            // Recurring information
            if (event.isRecurring && event.recurrencePattern != null) {
                append(". Recurring ")
                append(event.recurrencePattern)
            }
        }
    }

    /**
     * Formats a LocalDateTime for accessibility (screen readers).
     * Uses a verbose format that is easy to understand when spoken.
     *
     * Example: "Monday, January 15, 2024 at 2:30 PM"
     *
     * @return Accessibility-friendly date/time string
     */
    fun LocalDateTime.formatForAccessibility(): String {
        return format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a"))
    }

    /**
     * Creates a short description for event type.
     * Useful for quick announcements.
     *
     * @param eventType The event type string
     * @return Human-readable event type
     */
    fun getEventTypeDescription(eventType: String): String {
        return when (eventType.lowercase()) {
            "mom" -> "Mom's event"
            "dad" -> "Dad's event"
            "training" -> "Training event"
            "doctor" -> "Doctor appointment"
            "school" -> "School event"
            "activity" -> "Activity"
            "other" -> "Other event"
            else -> eventType.replaceFirstChar { it.uppercase() } + " event"
        }
    }

    /**
     * Creates a description for parent owner.
     *
     * @param parentOwner The parent owner string ("mom" or "dad")
     * @return Human-readable parent description
     */
    fun getParentOwnerDescription(parentOwner: String): String {
        return when (parentOwner.lowercase()) {
            "mom" -> "Mother"
            "dad" -> "Father"
            else -> parentOwner.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Creates a description for recurrence pattern.
     *
     * @param pattern The recurrence pattern string
     * @return Human-readable recurrence description
     */
    fun getRecurrenceDescription(pattern: String?): String {
        return when (pattern?.lowercase()) {
            "daily" -> "Repeats daily"
            "weekly" -> "Repeats weekly"
            "monthly" -> "Repeats monthly"
            "yearly" -> "Repeats yearly"
            null -> "Does not repeat"
            else -> "Repeats $pattern"
        }
    }
}
