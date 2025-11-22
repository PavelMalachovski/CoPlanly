package com.coparently.app.domain.model

/**
 * Domain model representing a message template for common situations.
 * This is the clean architecture model used in the domain layer.
 *
 * @property id Unique identifier for the template
 * @property category Category of the template
 * @property title Display title of the template
 * @property content Template content with optional placeholders
 * @property placeholders List of placeholder names that can be filled in
 */
data class MessageTemplate(
    val id: String,
    val category: TemplateCategory,
    val title: String,
    val content: String,
    val placeholders: List<String> = emptyList()
)

/**
 * Categories for message templates.
 */
enum class TemplateCategory {
    PICKUP_DROP,
    ILLNESS,
    SCHOOL_EVENTS,
    HOLIDAYS,
    CONFLICT_RESOLUTION;

    val displayName: String
        get() = when (this) {
            PICKUP_DROP -> "Pickup & Drop-off"
            ILLNESS -> "Illness & Medical"
            SCHOOL_EVENTS -> "School Events"
            HOLIDAYS -> "Holidays & Vacation"
            CONFLICT_RESOLUTION -> "Conflict Resolution"
        }
}

/**
 * Default message templates for common situations.
 */
object DefaultMessageTemplates {
    fun getAll(): List<MessageTemplate> = listOf(
        MessageTemplate(
            id = "pickup_delay",
            category = TemplateCategory.PICKUP_DROP,
            title = "Задержка с передачей ребенка",
            content = "Привет! Я немного задержусь с передачей [ребенка]. Буду через [время] минут. Извини за неудобство!",
            placeholders = listOf("ребенка", "время")
        ),
        MessageTemplate(
            id = "pickup_early",
            category = TemplateCategory.PICKUP_DROP,
            title = "Ранний приезд",
            content = "Привет! Я приеду раньше обычного, примерно в [время]. Это удобно?",
            placeholders = listOf("время")
        ),
        MessageTemplate(
            id = "doctor_visit",
            category = TemplateCategory.ILLNESS,
            title = "Визит к врачу",
            content = "[Ребенок] сегодня был у врача по поводу [жалоба]. Диагноз: [диагноз]. Нужно [рекомендации врача].",
            placeholders = listOf("Ребенок", "жалоба", "диагноз", "рекомендации врача")
        ),
        MessageTemplate(
            id = "child_sick",
            category = TemplateCategory.ILLNESS,
            title = "Ребенок заболел",
            content = "[Ребенок] плохо себя чувствует. Симптомы: [симптомы]. Температура [температура]. Я остаюсь дома с ним/ней.",
            placeholders = listOf("Ребенок", "симптомы", "температура")
        ),
        MessageTemplate(
            id = "school_event",
            category = TemplateCategory.SCHOOL_EVENTS,
            title = "Школьное мероприятие",
            content = "В школе будет [мероприятие] [дата] в [время]. Ты сможешь присутствовать?",
            placeholders = listOf("мероприятие", "дата", "время")
        ),
        MessageTemplate(
            id = "parent_teacher_meeting",
            category = TemplateCategory.SCHOOL_EVENTS,
            title = "Родительское собрание",
            content = "Родительское собрание назначено на [дата] в [время]. Учитель хочет обсудить [тема].",
            placeholders = listOf("дата", "время", "тема")
        ),
        MessageTemplate(
            id = "holiday_plan",
            category = TemplateCategory.HOLIDAYS,
            title = "План на каникулы",
            content = "Давай обсудим план на [каникулы]. Я предлагаю [предложение]. Что думаешь?",
            placeholders = listOf("каникулы", "предложение")
        ),
        MessageTemplate(
            id = "schedule_change",
            category = TemplateCategory.CONFLICT_RESOLUTION,
            title = "Изменение расписания",
            content = "Мне нужно изменить расписание на [дата]. Можем поменяться? Я возьму [альтернативная дата].",
            placeholders = listOf("дата", "альтернативная дата")
        )
    )
}
