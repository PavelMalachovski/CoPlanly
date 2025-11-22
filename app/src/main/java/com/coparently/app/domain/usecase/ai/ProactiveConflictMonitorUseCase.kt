package com.coparently.app.domain.usecase.ai

import android.util.Log
import com.coparently.app.data.remote.ai.AIService
import com.coparently.app.domain.model.ai.*
import com.coparently.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Use case for proactive conflict monitoring
 * Day 1 - Feature 1.2: Proactive conflict alerts
 */
class ProactiveConflictMonitorUseCase @Inject constructor(
    private val eventRepository: EventRepository,
    private val aiService: AIService
) {
    companion object {
        private const val TAG = "ProactiveConflictMonitor"
        private const val LOOK_AHEAD_DAYS = 7L
    }

    suspend fun checkForUpcomingConflicts(): List<UpcomingConflict> {
        val now = LocalDateTime.now()
        val checkUntil = now.plusDays(LOOK_AHEAD_DAYS)

        // Get all events in the time range
        val events = eventRepository.getEventsByDateRange(now, checkUntil).first()

        // Group by parent
        val parentCalendars = events.groupBy { it.parentOwner }
            .map { (parentName, parentEvents) ->
                CalendarData(
                    parentName = parentName,
                    parentId = parentName,
                    events = parentEvents
                )
            }

        return findUpcomingConflicts(parentCalendars, now, checkUntil)
    }

    private suspend fun findUpcomingConflicts(
        calendars: List<CalendarData>,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<UpcomingConflict> {
        val conflicts = mutableListOf<UpcomingConflict>()

        try {
            // Analyze patterns and predict potential conflicts
            val insights = aiService.analyzeCalendarPatterns(calendars)

            insights.predictedConflicts.forEach { predicted ->
                if (predicted.confidence > 0.7) { // High confidence
                    conflicts.add(
                        UpcomingConflict(
                            title = "Potential ${predicted.conflictType.replace("_", " ")} conflict",
                            description = predicted.description,
                            suggestedSolutions = predicted.solutions,
                            timeUntil = Duration.between(from, predicted.expectedTime),
                            severity = ConflictSeverity.fromConfidence(predicted.confidence)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding upcoming conflicts", e)
        }

        return conflicts.filter { it.severity >= ConflictSeverity.MEDIUM }
    }
}
