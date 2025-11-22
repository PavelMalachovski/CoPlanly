package com.coparently.app.domain.model.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration

/**
 * День 4.1: AI-powered parenting insights
 */

/**
 * Семейная аналитика и insights
 */
data class FamilyInsights(
    val timeDistribution: TimeDistribution,
    val activityPatterns: ActivityPatterns,
    val expenseAnalysis: ExpenseAnalysis,
    val recommendations: List<String>,
    val concerns: List<String>,
    val positiveTrends: List<String>,
    val generatedAt: LocalDateTime
)

/**
 * Распределение времени
 */
data class TimeDistribution(
    val byParent: Map<String, Double>, // Percentage of time with each parent
    val byActivity: Map<String, Double>  // Percentage by activity type
)

/**
 * Паттерны активностей
 */
data class ActivityPatterns(
    val byDayOfWeek: Map<DayOfWeek, Int>,
    val byHourOfDay: Map<Int, Int>
)

/**
 * Анализ расходов
 */
data class ExpenseAnalysis(
    val totalSpent: Double,
    val averagePerWeek: Double,
    val byCategory: Map<String, Double>,
    val topExpenses: List<ExpenseItem>,
    val trend: ExpenseTrend
)

data class ExpenseItem(
    val description: String,
    val amount: Double,
    val category: String,
    val date: LocalDate
)

enum class ExpenseTrend {
    INCREASING, STABLE, DECREASING
}

/**
 * Диапазон дат для аналитики
 */
enum class DateRange {
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS,
    LAST_6_MONTHS,
    LAST_12_MONTHS;

    fun toDays(): Int = when (this) {
        LAST_7_DAYS -> 7
        LAST_30_DAYS -> 30
        LAST_90_DAYS -> 90
        LAST_6_MONTHS -> 180
        LAST_12_MONTHS -> 365
    }
}

/**
 * День 4.2: Predictive analytics для семейного планирования
 */

/**
 * Предсказание расписания
 */
data class SchedulePrediction(
    val date: LocalDate,
    val predictedEvents: List<PredictedEvent>,
    val confidence: Double,
    val reasoning: String
)

/**
 * Предсказанное событие
 */
data class PredictedEvent(
    val title: String,
    val eventType: String,
    val startTime: LocalTime,
    val duration: kotlin.time.Duration,
    val parentOwner: String
)

/**
 * Предсказание трендов расходов
 */
data class ExpenseTrendPrediction(
    val monthlyPredictions: List<MonthlyExpensePrediction>,
    val seasonalVariations: Map<String, Double>, // Season -> variation %
    val expectedCostIncrease: Double, // Percentage
    val budgetRecommendations: List<String>
)

data class MonthlyExpensePrediction(
    val month: String,
    val predictedAmount: Double,
    val confidence: Double,
    val breakdown: Map<String, Double> // Category -> amount
)
