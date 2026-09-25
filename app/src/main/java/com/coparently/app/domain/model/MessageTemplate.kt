package com.coparently.app.domain.model

import androidx.annotation.StringRes
import com.coparently.app.R

/**
 * A ready-made message for a situation co-parents run into often.
 *
 * The visible text is held as **string resource ids**, not as strings. These templates used to
 * carry Russian literals, so every user was offered Russian no matter which of the five
 * languages the app was running in; the text belongs in the `chat_strings.xml` files.
 *
 * This deliberately differs from `ChangeRequestStatus` and `CustodyModelType`, which keep an
 * English `displayName` on the enum and are mapped to resources by the screen that renders
 * them. That shape fits a closed enum: the `when` in the presentation layer is exhaustive, so
 * the compiler catches a constant nobody mapped. The templates are a *list of data* instead —
 * a lookup keyed on [id] would be a parallel table the compiler cannot check, and a template
 * added without its mapping would render blank. `@StringRes` is a compile-time annotation over
 * a plain `Int`, so the domain layer still holds no `Context` and no Android runtime type.
 *
 * @property id Unique identifier for the template
 * @property category Category of the template
 * @property titleRes Display title of the template
 * @property contentRes Message body. It carries hints in square brackets for the user to
 *   replace before sending.
 * @property placeholders Locale-independent names of the values a template asks the user to
 *   fill in. **They are not substrings of the rendered [contentRes]**: the bracketed hints in
 *   the body are translated per locale, because the body is seeded into the composer for the
 *   user to edit by hand and a Czech parent should be prompted in Czech. Nothing substitutes
 *   them today — `ChatScreen` seeds the composer with the body verbatim, and no code has ever
 *   read this list — so keeping the old Russian words here would have been neither a working
 *   mechanism nor readable. A substitution feature added later must resolve the visible token
 *   from resources; matching these names against the rendered text would only ever work in
 *   English.
 */
data class MessageTemplate(
    val id: String,
    val category: TemplateCategory,
    @StringRes val titleRes: Int,
    @StringRes val contentRes: Int,
    val placeholders: List<String> = emptyList()
)

/**
 * Categories for message templates.
 *
 * The sheet groups the list by category in first-seen order, so the order of
 * [DefaultMessageTemplates.getAll] decides the order of the headings; this enum only names them.
 *
 * @property labelRes Group heading shown above the category's templates.
 */
enum class TemplateCategory(@StringRes val labelRes: Int) {
    HANDOVER(R.string.chat_template_category_handover),
    HEALTH(R.string.chat_template_category_health),
    SCHOOL(R.string.chat_template_category_school),
    HOLIDAYS(R.string.chat_template_category_holidays),
    EXPENSES(R.string.chat_template_category_expenses),
    REPLIES(R.string.chat_template_category_replies)
}

/**
 * Default message templates for common co-parenting situations.
 *
 * Written to the BIFF shape used for high-conflict co-parenting (brief, informative, friendly,
 * firm): one topic per message, about the children, no blame, and where something is asked, a
 * concrete proposal with a day or time to answer by. Some templates ask for nothing on purpose
 * (a school update, an acknowledgement), because sharing information without a demand is half of
 * what keeps a thread calm. In Czech, Russian and Ukrainian the bodies avoid past-tense verbs
 * whose ending would assume the sender's or the child's gender. `docs/TEMPLATES-AUDIT-2026-09.md`
 * records the reasoning; a new template follows it and arrives in all five locales.
 *
 * A template only prepares text for the composer. None may mention or promise something the app
 * does (design refresh item 8): the parent edits it, sends it or discards it.
 */
object DefaultMessageTemplates {
    /** Every template, in the order the sheet shows them. */
    fun getAll(): List<MessageTemplate> = ALL

    private val ALL = listOf(
        MessageTemplate(
            id = "handover_confirm",
            category = TemplateCategory.HANDOVER,
            titleRes = R.string.chat_template_handover_confirm_title,
            contentRes = R.string.chat_template_handover_confirm_content,
            placeholders = listOf("day", "time", "place")
        ),
        MessageTemplate(
            id = "pickup_delay",
            category = TemplateCategory.HANDOVER,
            titleRes = R.string.chat_template_pickup_delay_title,
            contentRes = R.string.chat_template_pickup_delay_content,
            placeholders = listOf("minutes", "time")
        ),
        MessageTemplate(
            id = "pickup_early",
            category = TemplateCategory.HANDOVER,
            titleRes = R.string.chat_template_pickup_early_title,
            contentRes = R.string.chat_template_pickup_early_content,
            placeholders = listOf("day", "time")
        ),
        MessageTemplate(
            id = "packing",
            category = TemplateCategory.HANDOVER,
            titleRes = R.string.chat_template_packing_title,
            contentRes = R.string.chat_template_packing_content,
            placeholders = listOf("day", "items", "purpose")
        ),
        MessageTemplate(
            id = "schedule_change",
            category = TemplateCategory.HANDOVER,
            titleRes = R.string.chat_template_schedule_change_title,
            contentRes = R.string.chat_template_schedule_change_content,
            placeholders = listOf("date", "other date", "reply by")
        ),
        MessageTemplate(
            id = "child_sick",
            category = TemplateCategory.HEALTH,
            titleRes = R.string.chat_template_child_sick_title,
            contentRes = R.string.chat_template_child_sick_content,
            placeholders = listOf("child", "symptoms", "temperature", "update by")
        ),
        MessageTemplate(
            id = "doctor_visit",
            category = TemplateCategory.HEALTH,
            titleRes = R.string.chat_template_doctor_visit_title,
            contentRes = R.string.chat_template_doctor_visit_content,
            placeholders = listOf("child", "reason", "diagnosis", "medicine", "next check-up")
        ),
        MessageTemplate(
            id = "school_event",
            category = TemplateCategory.SCHOOL,
            titleRes = R.string.chat_template_school_event_title,
            contentRes = R.string.chat_template_school_event_content,
            placeholders = listOf("event", "date", "time")
        ),
        MessageTemplate(
            id = "parent_teacher_meeting",
            category = TemplateCategory.SCHOOL,
            titleRes = R.string.chat_template_parent_teacher_meeting_title,
            contentRes = R.string.chat_template_parent_teacher_meeting_content,
            placeholders = listOf("date", "time", "topic")
        ),
        MessageTemplate(
            id = "school_update",
            category = TemplateCategory.SCHOOL,
            titleRes = R.string.chat_template_school_update_title,
            contentRes = R.string.chat_template_school_update_content,
            placeholders = listOf("child", "what happened")
        ),
        MessageTemplate(
            id = "holiday_plan",
            category = TemplateCategory.HOLIDAYS,
            titleRes = R.string.chat_template_holiday_plan_title,
            contentRes = R.string.chat_template_holiday_plan_content,
            placeholders = listOf("holiday", "proposal", "reply by")
        ),
        MessageTemplate(
            id = "travel_abroad",
            category = TemplateCategory.HOLIDAYS,
            titleRes = R.string.chat_template_travel_abroad_title,
            contentRes = R.string.chat_template_travel_abroad_content,
            placeholders = listOf("child", "country", "from", "to", "address", "phone", "reply by")
        ),
        MessageTemplate(
            id = "expense_share",
            category = TemplateCategory.EXPENSES,
            titleRes = R.string.chat_template_expense_share_title,
            contentRes = R.string.chat_template_expense_share_content,
            placeholders = listOf("what", "date", "total", "share", "pay by")
        ),
        MessageTemplate(
            id = "acknowledge",
            category = TemplateCategory.REPLIES,
            titleRes = R.string.chat_template_acknowledge_title,
            contentRes = R.string.chat_template_acknowledge_content,
            placeholders = listOf("topic", "reply by")
        ),
        MessageTemplate(
            id = "confirm_agreement",
            category = TemplateCategory.REPLIES,
            titleRes = R.string.chat_template_confirm_agreement_title,
            contentRes = R.string.chat_template_confirm_agreement_content,
            placeholders = listOf("agreement")
        )
    )
}
