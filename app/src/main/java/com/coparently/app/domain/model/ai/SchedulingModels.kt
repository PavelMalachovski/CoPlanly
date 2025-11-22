package com.coparently.app.domain.model.ai

import java.time.LocalDateTime

/**
 * Represents a request to create a new event
 */
data class EventRequest(
    val title: String,
    val duration: java.time.Duration,
    val preferredTimes: List<LocalDateTime>,
    val eventType: String,
    val childAge: Int? = null,
    val specialRequirements: List<String> = emptyList()
)

/**
 * Represents a suggested time slot for an event
 */
data class TimeSlotSuggestion(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val score: Double, // 0-1, how optimal
    val reasoning: String,
    val alternativeDates: List<LocalDateTime> = emptyList()
)

/**
 * Represents a conflict detected in the schedule
 */
data class Conflict(
    val timeSlot: Pair<LocalDateTime, LocalDateTime>,
    val conflictingEventId: String,
    val conflictingEventTitle: String,
    val parentName: String,
    val severity: ConflictSeverity,
    val description: String
)

/**
 * Severity levels for conflicts
 */
enum class ConflictSeverity {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromConfidence(confidence: Double): ConflictSeverity {
            return when {
                confidence >= 0.8 -> HIGH
                confidence >= 0.5 -> MEDIUM
                else -> LOW
            }
        }
    }
}

/**
 * Represents a predicted upcoming conflict
 */
data class UpcomingConflict(
    val title: String,
    val description: String,
    val suggestedSolutions: List<String>,
    val timeUntil: java.time.Duration,
    val severity: ConflictSeverity
)

/**
 * Calendar data for a parent
 */
data class CalendarData(
    val parentName: String,
    val parentId: String,
    val events: List<com.coparently.app.domain.model.Event>
)

/**
 * Insights from calendar analysis
 */
data class CalendarInsights(
    val busyPeriods: List<BusyPeriod>,
    val freeTimePatterns: List<FreeTimePattern>,
    val predictedConflicts: List<PredictedConflict>,
    val schedulingEfficiency: Double // 0-1 score
)

data class BusyPeriod(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val intensity: Double // 0-1
)

data class FreeTimePattern(
    val dayOfWeek: java.time.DayOfWeek,
    val startHour: Int,
    val endHour: Int,
    val confidence: Double
)

/**
 * Predicted conflict in the future
 */
data class PredictedConflict(
    val conflictType: String, // "double_booking", "travel_conflict", etc.
    val expectedTime: LocalDateTime,
    val confidence: Double, // 0-1
    val description: String,
    val solutions: List<String>
)
