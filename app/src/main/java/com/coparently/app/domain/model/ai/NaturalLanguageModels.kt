package com.coparently.app.domain.model.ai

import java.time.LocalDateTime
import java.time.LocalDate

/**
 * Context for parsing natural language event input
 */
data class ParsingContext(
    val currentDate: LocalDate = LocalDate.now(),
    val timezone: String = "UTC",
    val recentEvents: List<com.coparently.app.domain.model.Event> = emptyList(),
    val commonLocations: List<String> = emptyList()
)

/**
 * Result of parsing natural language into an event
 */
sealed class ParsedEventResult {
    data class Success(
        val event: ParsedEvent,
        val confidence: Double,
        val validationIssues: List<String> = emptyList(),
        val suggestions: List<String> = emptyList()
    ) : ParsedEventResult()

    data class Error(val message: String) : ParsedEventResult()
}

/**
 * Parsed event from natural language
 */
data class ParsedEvent(
    val title: String,
    val dateTime: LocalDateTime? = null,
    val duration: java.time.Duration? = null,
    val location: String? = null,
    val eventType: String? = null,
    val parentOwner: String? = null,
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null,
    val notes: String? = null
) {
    fun toEvent(): com.coparently.app.domain.model.Event {
        val now = LocalDateTime.now()
        val startDateTime = dateTime ?: now
        return com.coparently.app.domain.model.Event(
            id = "", // Will be generated
            title = title,
            description = notes,
            startDateTime = startDateTime,
            endDateTime = startDateTime.plus(duration ?: java.time.Duration.ofHours(1)),
            eventType = eventType ?: "general",
            parentOwner = parentOwner ?: "mom",
            isRecurring = isRecurring,
            recurrencePattern = recurrencePattern,
            createdAt = now,
            updatedAt = now
        )
    }
}

/**
 * Event suggestion from AI
 */
data class EventSuggestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val suggestedDateTime: LocalDateTime,
    val eventType: String,
    val confidence: Double, // 0-1
    val reasoning: String,
    val category: SuggestionCategory,
    val context: SuggestionContext? = null
)

/**
 * Categories for event suggestions
 */
enum class SuggestionCategory {
    RECURRING, SEASONAL, AGE_APPROPRIATE, LOCATION_BASED, WEATHER_BASED
}

/**
 * Context for generating suggestions
 */
data class SuggestionContext(
    val userId: String,
    val currentDate: LocalDate = LocalDate.now(),
    val childAge: Int? = null,
    val location: String? = null,
    val weather: String? = null,
    val familySize: Int = 2
)

/**
 * User actions on suggestions
 */
enum class UserAction {
    ACCEPTED, REJECTED, MODIFIED, IGNORED
}
