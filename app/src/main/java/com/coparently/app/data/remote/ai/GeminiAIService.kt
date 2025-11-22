package com.coparently.app.data.remote.ai

import android.util.Log
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.ai.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AIService using Google Gemini API
 */
@Singleton
class GeminiAIService @Inject constructor(
    private val apiKey: String,
    private val gson: Gson
) : AIService {

    companion object {
        private const val TAG = "GeminiAIService"
        private const val MODEL_NAME = "gemini-1.5-flash"
    }

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 2048
            }
        )
    }

    override suspend fun generateTimeSlotSuggestions(prompt: String): List<TimeSlotSuggestion> {
        return try {
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: return emptyList()

            Log.d(TAG, "Time slot suggestions response: $responseText")

            parseTimeSlotSuggestions(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating time slot suggestions", e)
            emptyList()
        }
    }

    override suspend fun analyzeCalendarPatterns(calendars: List<CalendarData>): CalendarInsights {
        return try {
            val prompt = buildCalendarAnalysisPrompt(calendars)
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: return CalendarInsights(
                busyPeriods = emptyList(),
                freeTimePatterns = emptyList(),
                predictedConflicts = emptyList(),
                schedulingEfficiency = 0.0
            )

            Log.d(TAG, "Calendar patterns response: $responseText")

            parseCalendarInsights(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing calendar patterns", e)
            CalendarInsights(
                busyPeriods = emptyList(),
                freeTimePatterns = emptyList(),
                predictedConflicts = emptyList(),
                schedulingEfficiency = 0.0
            )
        }
    }

    override suspend fun suggestEventImprovements(
        event: Event,
        context: EventContext
    ): List<EventSuggestion> {
        return try {
            val prompt = buildEventImprovementPrompt(event, context)
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: return emptyList()

            Log.d(TAG, "Event improvements response: $responseText")

            parseEventSuggestions(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error suggesting event improvements", e)
            emptyList()
        }
    }

    override suspend fun parseNaturalLanguage(prompt: String): AIParseResponse {
        return try {
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: throw Exception("Empty response from AI")

            Log.d(TAG, "Natural language parse response: $responseText")

            val parsedEvent = parseEventFromResponse(responseText)
            AIParseResponse(
                parsedEvent = parsedEvent,
                confidence = extractConfidence(responseText),
                rawResponse = responseText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing natural language", e)
            throw e
        }
    }

    override suspend fun generateEventSuggestions(prompt: String): List<EventSuggestion> {
        return try {
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: return emptyList()

            Log.d(TAG, "Event suggestions response: $responseText")

            parseEventSuggestions(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating event suggestions", e)
            emptyList()
        }
    }

    // Helper methods for parsing responses

    private fun parseTimeSlotSuggestions(responseText: String): List<TimeSlotSuggestion> {
        return try {
            val jsonMatch = extractJsonFromResponse(responseText)
            if (jsonMatch != null) {
                val jsonArray = JsonParser.parseString(jsonMatch).asJsonArray
                jsonArray.map { element ->
                    val obj = element.asJsonObject
                    TimeSlotSuggestion(
                        startTime = LocalDateTime.parse(
                            obj.get("startTime").asString,
                            DateTimeFormatter.ISO_DATE_TIME
                        ),
                        endTime = LocalDateTime.parse(
                            obj.get("endTime").asString,
                            DateTimeFormatter.ISO_DATE_TIME
                        ),
                        score = obj.get("score").asDouble,
                        reasoning = obj.get("reasoning").asString,
                        alternativeDates = emptyList()
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time slot suggestions", e)
            emptyList()
        }
    }

    private fun parseCalendarInsights(responseText: String): CalendarInsights {
        return try {
            // For now, return basic insights - can be enhanced with JSON parsing
            CalendarInsights(
                busyPeriods = emptyList(),
                freeTimePatterns = emptyList(),
                predictedConflicts = emptyList(),
                schedulingEfficiency = 0.75
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing calendar insights", e)
            CalendarInsights(
                busyPeriods = emptyList(),
                freeTimePatterns = emptyList(),
                predictedConflicts = emptyList(),
                schedulingEfficiency = 0.0
            )
        }
    }

    private fun parseEventSuggestions(responseText: String): List<EventSuggestion> {
        return try {
            val jsonMatch = extractJsonFromResponse(responseText)
            if (jsonMatch != null) {
                val jsonArray = JsonParser.parseString(jsonMatch).asJsonArray
                jsonArray.map { element ->
                    val obj = element.asJsonObject
                    EventSuggestion(
                        title = obj.get("title").asString,
                        description = obj.get("description").asString,
                        suggestedDateTime = LocalDateTime.parse(
                            obj.get("suggestedDateTime").asString,
                            DateTimeFormatter.ISO_DATE_TIME
                        ),
                        eventType = obj.get("eventType").asString,
                        confidence = obj.get("confidence").asDouble,
                        reasoning = obj.get("reasoning").asString,
                        category = SuggestionCategory.valueOf(
                            obj.get("category").asString.uppercase()
                        )
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing event suggestions", e)
            emptyList()
        }
    }

    private fun parseEventFromResponse(responseText: String): ParsedEvent {
        val jsonMatch = extractJsonFromResponse(responseText)
        return if (jsonMatch != null) {
            try {
                val obj = JsonParser.parseString(jsonMatch).asJsonObject
                ParsedEvent(
                    title = obj.get("title")?.asString ?: "",
                    dateTime = obj.get("dateTime")?.asString?.let {
                        LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
                    },
                    duration = obj.get("duration")?.asInt?.let {
                        java.time.Duration.ofMinutes(it.toLong())
                    },
                    location = obj.get("location")?.asString,
                    eventType = obj.get("eventType")?.asString,
                    parentOwner = obj.get("parentOwner")?.asString,
                    isRecurring = obj.get("isRecurring")?.asBoolean ?: false,
                    recurrencePattern = obj.get("recurrencePattern")?.asString,
                    notes = obj.get("notes")?.asString
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing event JSON", e)
                // Fallback: parse from plain text
                parseEventFromPlainText(responseText)
            }
        } else {
            parseEventFromPlainText(responseText)
        }
    }

    private fun parseEventFromPlainText(text: String): ParsedEvent {
        // Simple fallback parsing
        return ParsedEvent(
            title = text.take(100),
            dateTime = LocalDateTime.now(),
            duration = java.time.Duration.ofHours(1),
            eventType = "general"
        )
    }

    private fun extractConfidence(responseText: String): Double {
        return try {
            val jsonMatch = extractJsonFromResponse(responseText)
            if (jsonMatch != null) {
                val obj = JsonParser.parseString(jsonMatch).asJsonObject
                obj.get("confidence")?.asDouble ?: 0.8
            } else {
                0.8
            }
        } catch (e: Exception) {
            0.8
        }
    }

    private fun extractJsonFromResponse(text: String): String? {
        val jsonPattern = Regex("\\{.*}", RegexOption.DOT_MATCHES_ALL)
        val arrayPattern = Regex("\\[.*]", RegexOption.DOT_MATCHES_ALL)

        return arrayPattern.find(text)?.value ?: jsonPattern.find(text)?.value
    }

    private fun buildCalendarAnalysisPrompt(calendars: List<CalendarData>): String {
        return """
            Analyze these parent calendars and provide insights:

            ${calendars.joinToString("\n\n") { calendar ->
                """
                Parent: ${calendar.parentName}
                Events: ${calendar.events.joinToString("\n") {
                    "- ${it.title} on ${it.startDateTime} (${it.eventType})"
                }}
                """.trimIndent()
            }}

            Provide analysis of:
            1. Busy periods
            2. Free time patterns
            3. Potential conflicts
            4. Scheduling efficiency

            Return as plain text.
        """.trimIndent()
    }

    private fun buildEventImprovementPrompt(event: Event, context: EventContext): String {
        return """
            Suggest improvements for this event:

            Event: ${event.title}
            Type: ${event.eventType}
            Time: ${event.startDateTime}
            Description: ${event.description}

            Recent events context:
            ${context.recentEvents.take(5).joinToString("\n") { "- ${it.title}" }}

            Suggest 2-3 improvements to make this event more effective.
            Consider timing, preparation, and coordination.

            Return as JSON array with: title, description, suggestedDateTime, eventType, confidence, reasoning, category
        """.trimIndent()
    }
}
