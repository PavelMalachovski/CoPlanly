package com.coparently.app.domain.usecase.ai

import android.util.Log
import com.coparently.app.data.remote.ai.AIService
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ai.*
import com.coparently.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Use case for generating smart event suggestions
 * Day 2 - Feature 2.2: Smart event suggestions
 */
class EventSuggestionEngineUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val aiService: AIService
) {
    companion object {
        private const val TAG = "EventSuggestionEngine"
        private const val HISTORY_DAYS = 90L
    }

    suspend fun generateEventSuggestions(
        userId: String,
        context: SuggestionContext = SuggestionContext(userId = userId)
    ): List<EventSuggestion> {
        return try {
            // Get user event history (last 90 days)
            val startDate = LocalDate.now().minusDays(HISTORY_DAYS).atStartOfDay()
            val endDate = LocalDate.now().plusDays(7).atStartOfDay()

            val userHistory = eventRepository.getEventsByDateRange(startDate, endDate).first()
            val currentSchedule = eventRepository.getEventsByDateRange(
                LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(14).atStartOfDay()
            ).first()

            val prompt = buildSuggestionPrompt(userHistory, currentSchedule, context)
            aiService.generateEventSuggestions(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating event suggestions", e)
            emptyList()
        }
    }

    private fun buildSuggestionPrompt(
        history: List<Event>,
        currentSchedule: List<Event>,
        context: SuggestionContext
    ): String {
        return """
            Analyze user's event history and suggest new events they might want to add.

            User history (last ${HISTORY_DAYS} days):
            ${history.take(50).joinToString("\n") {
                "- ${it.title} (${it.eventType}) on ${it.startDateTime.dayOfWeek}"
            }}

            Current upcoming events:
            ${currentSchedule.joinToString("\n") {
                "- ${it.title} on ${it.startDateTime}"
            }}

            Context:
            - Current date: ${context.currentDate}
            - Child age: ${context.childAge ?: "unknown"}
            - Family location: ${context.location ?: "unknown"}
            - Weather: ${context.weather ?: "unknown"}

            Suggest events based on:
            1. Recurring patterns (weekly activities, monthly appointments)
            2. Seasonal activities (school events, holiday activities)
            3. Age-appropriate activities for ${context.childAge ?: "the child"}
            4. Location-based suggestions
            5. Weather-appropriate activities

            Return 3-5 relevant suggestions as JSON array:
            [{"title": "...", "description": "...", "suggestedDateTime": "2024-01-01T10:00:00", "eventType": "...", "confidence": 0.85, "reasoning": "...", "category": "RECURRING"}]

            Categories: RECURRING, SEASONAL, AGE_APPROPRIATE, LOCATION_BASED, WEATHER_BASED
        """.trimIndent()
    }

    suspend fun learnFromUserAction(
        suggestion: EventSuggestion,
        action: UserAction
    ) {
        Log.d(TAG, "Learning from user action: $action for suggestion: ${suggestion.title}")
        // In a real implementation, this would update ML model weights
        // For now, we just log the action
    }
}
