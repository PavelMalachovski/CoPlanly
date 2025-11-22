package com.coparently.app.data.remote.ai

import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ai.*

/**
 * AI Service interface for interacting with Gemini AI
 */
interface AIService {
    /**
     * Generate time slot suggestions based on scheduling conflicts
     */
    suspend fun generateTimeSlotSuggestions(prompt: String): List<TimeSlotSuggestion>

    /**
     * Analyze calendar patterns and provide insights
     */
    suspend fun analyzeCalendarPatterns(calendars: List<CalendarData>): CalendarInsights

    /**
     * Suggest event improvements based on context
     */
    suspend fun suggestEventImprovements(event: Event, context: EventContext): List<EventSuggestion>

    /**
     * Parse natural language text into structured event data
     */
    suspend fun parseNaturalLanguage(prompt: String): AIParseResponse

    /**
     * Generate event suggestions based on user history and context
     */
    suspend fun generateEventSuggestions(prompt: String): List<EventSuggestion>
}

/**
 * Event context for AI suggestions
 */
data class EventContext(
    val recentEvents: List<Event>,
    val userPreferences: Map<String, Any> = emptyMap()
)

/**
 * Response from AI parsing
 */
data class AIParseResponse(
    val parsedEvent: ParsedEvent,
    val confidence: Double,
    val rawResponse: String
)
