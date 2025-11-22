package com.coparently.app.domain.model.ai

import java.time.LocalDateTime

/**
 * День 3.1: Tone analysis для сообщений
 */

/**
 * Результат анализа тона сообщения
 *
 * @property sentiment Результат анализа эмоционального окраса
 * @property tone Тон сообщения
 * @property intensity Интенсивность эмоций (1-10)
 * @property issues Потенциальные проблемы в сообщении
 * @property suggestions Предложения по улучшению
 * @property effectiveness Эффективность коммуникации (0-1)
 */
data class ToneAnalysis(
    val sentiment: SentimentResult,
    val tone: MessageTone,
    val intensity: Int, // 1-10
    val issues: List<String>,
    val suggestions: List<String>,
    val effectiveness: Double // 0-1
)

/**
 * Результат анализа эмоционального окраса
 */
data class SentimentResult(
    val score: Double, // -1 (negative) to 1 (positive)
    val magnitude: Double, // 0 to infinity (emotional strength)
    val label: SentimentLabel
)

enum class SentimentLabel {
    VERY_POSITIVE, POSITIVE, NEUTRAL, NEGATIVE, VERY_NEGATIVE
}

/**
 * Тон сообщения
 */
enum class MessageTone {
    POSITIVE, NEUTRAL, CONCERNED, FRUSTRATED, AGGRESSIVE
}

/**
 * Контекст сообщения для анализа
 */
data class MessageContext(
    val senderName: String,
    val recipientName: String,
    val topic: String? = null,
    val relationshipHistory: RelationshipHistory = RelationshipHistory.NEUTRAL
)

enum class RelationshipHistory {
    POSITIVE, NEUTRAL, STRAINED
}

/**
 * Улучшенное сообщение с предложениями
 */
data class ImprovedMessage(
    val originalMessage: String,
    val suggestions: List<String>
)

/**
 * День 3.2: Smart conversation summaries
 */

/**
 * Резюме разговора
 */
sealed class ConversationSummary {
    data object Empty : ConversationSummary()

    data class Content(
        val summary: String,
        val keyPoints: List<String>,
        val actionItems: List<String>,
        val agreements: List<String>,
        val conflicts: List<String>,
        val sentiment: MessageTone,
        val timeRange: Pair<LocalDateTime, LocalDateTime>
    ) : ConversationSummary()
}

/**
 * Элемент действия из разговора
 */
data class ActionItem(
    val id: String,
    val description: String,
    val conversationId: String,
    val assignedTo: String? = null,
    val dueDate: LocalDateTime? = null,
    val status: ActionItemStatus = ActionItemStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ActionItemStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED
}
