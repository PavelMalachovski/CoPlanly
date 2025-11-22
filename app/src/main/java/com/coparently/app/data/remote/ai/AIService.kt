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

    // Day 3: Communication Intelligence

    /**
     * Analyze sentiment of a text message
     */
    suspend fun analyzeSentiment(text: String): SentimentResult

    /**
     * Analyze communication tone and effectiveness
     */
    suspend fun analyzeCommunication(prompt: String): ToneAnalysisResponse

    /**
     * Generate improved message rewrites
     */
    suspend fun generateMessageRewrites(prompt: String): List<String>

    /**
     * Generate conversation summary with key points and action items
     */
    suspend fun generateConversationSummary(prompt: String): ConversationSummaryResponse

    // Day 4: Family Insights & Analytics

    /**
     * Generate family insights based on activity and expense data
     */
    suspend fun generateFamilyInsights(prompt: String): FamilyInsightsResponse

    /**
     * Predict expense trends for family planning
     */
    suspend fun predictExpenseTrends(prompt: String): ExpenseTrendPrediction
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

/**
 * Response models for Day 3: Communication Intelligence
 */
data class ToneAnalysisResponse(
    val tone: String,
    val intensity: Int,
    val potentialIssues: List<String>,
    val improvements: List<String>,
    val effectiveness: Double
)

data class ConversationSummaryResponse(
    val summary: String,
    val keyPoints: List<String>,
    val actionItems: List<String>,
    val agreements: List<String>,
    val conflicts: List<String>,
    val overallSentiment: String
)

/**
 * Response model for Day 4: Family Insights
 */
data class FamilyInsightsResponse(
    val recommendations: List<String>,
    val concerns: List<String>,
    val positiveTrends: List<String>
)

