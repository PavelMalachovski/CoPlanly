package com.coparently.app.domain.model

import com.coparently.app.resources.Res
import com.coparently.app.resources.chat_template_acknowledge_content
import com.coparently.app.resources.chat_template_acknowledge_title
import com.coparently.app.resources.chat_template_category_expenses
import com.coparently.app.resources.chat_template_category_handover
import com.coparently.app.resources.chat_template_category_health
import com.coparently.app.resources.chat_template_category_holidays
import com.coparently.app.resources.chat_template_category_replies
import com.coparently.app.resources.chat_template_category_school
import com.coparently.app.resources.chat_template_child_sick_content
import com.coparently.app.resources.chat_template_child_sick_title
import com.coparently.app.resources.chat_template_confirm_agreement_content
import com.coparently.app.resources.chat_template_confirm_agreement_title
import com.coparently.app.resources.chat_template_doctor_visit_content
import com.coparently.app.resources.chat_template_doctor_visit_title
import com.coparently.app.resources.chat_template_expense_share_content
import com.coparently.app.resources.chat_template_expense_share_title
import com.coparently.app.resources.chat_template_handover_confirm_content
import com.coparently.app.resources.chat_template_handover_confirm_title
import com.coparently.app.resources.chat_template_holiday_plan_content
import com.coparently.app.resources.chat_template_holiday_plan_title
import com.coparently.app.resources.chat_template_packing_content
import com.coparently.app.resources.chat_template_packing_title
import com.coparently.app.resources.chat_template_parent_teacher_meeting_content
import com.coparently.app.resources.chat_template_parent_teacher_meeting_title
import com.coparently.app.resources.chat_template_pickup_delay_content
import com.coparently.app.resources.chat_template_pickup_delay_title
import com.coparently.app.resources.chat_template_pickup_early_content
import com.coparently.app.resources.chat_template_pickup_early_title
import com.coparently.app.resources.chat_template_schedule_change_content
import com.coparently.app.resources.chat_template_schedule_change_title
import com.coparently.app.resources.chat_template_school_event_content
import com.coparently.app.resources.chat_template_school_event_title
import com.coparently.app.resources.chat_template_school_update_content
import com.coparently.app.resources.chat_template_school_update_title
import com.coparently.app.resources.chat_template_travel_abroad_content
import com.coparently.app.resources.chat_template_travel_abroad_title
import org.jetbrains.compose.resources.StringResource

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
 * added without its mapping would render blank. The resources are Compose Multiplatform's
 * `StringResource`s since MON-24 phase 2 (Task 11, slice 11b), when this file left the app's stay
 * list for `:shared-ui` (a resource is a `:shared-ui` type, which `:shared` cannot name); they were
 * `@StringRes` ids before, and no `Context` or Android runtime type was ever held here.
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
    val titleRes: StringResource,
    val contentRes: StringResource,
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
enum class TemplateCategory(val labelRes: StringResource) {
    HANDOVER(Res.string.chat_template_category_handover),
    HEALTH(Res.string.chat_template_category_health),
    SCHOOL(Res.string.chat_template_category_school),
    HOLIDAYS(Res.string.chat_template_category_holidays),
    EXPENSES(Res.string.chat_template_category_expenses),
    REPLIES(Res.string.chat_template_category_replies)
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
            titleRes = Res.string.chat_template_handover_confirm_title,
            contentRes = Res.string.chat_template_handover_confirm_content,
            placeholders = listOf("day", "time", "place")
        ),
        MessageTemplate(
            id = "pickup_delay",
            category = TemplateCategory.HANDOVER,
            titleRes = Res.string.chat_template_pickup_delay_title,
            contentRes = Res.string.chat_template_pickup_delay_content,
            placeholders = listOf("minutes", "time")
        ),
        MessageTemplate(
            id = "pickup_early",
            category = TemplateCategory.HANDOVER,
            titleRes = Res.string.chat_template_pickup_early_title,
            contentRes = Res.string.chat_template_pickup_early_content,
            placeholders = listOf("day", "time")
        ),
        MessageTemplate(
            id = "packing",
            category = TemplateCategory.HANDOVER,
            titleRes = Res.string.chat_template_packing_title,
            contentRes = Res.string.chat_template_packing_content,
            placeholders = listOf("day", "items", "purpose")
        ),
        MessageTemplate(
            id = "schedule_change",
            category = TemplateCategory.HANDOVER,
            titleRes = Res.string.chat_template_schedule_change_title,
            contentRes = Res.string.chat_template_schedule_change_content,
            placeholders = listOf("date", "other date", "reply by")
        ),
        MessageTemplate(
            id = "child_sick",
            category = TemplateCategory.HEALTH,
            titleRes = Res.string.chat_template_child_sick_title,
            contentRes = Res.string.chat_template_child_sick_content,
            placeholders = listOf("child", "symptoms", "temperature", "update by")
        ),
        MessageTemplate(
            id = "doctor_visit",
            category = TemplateCategory.HEALTH,
            titleRes = Res.string.chat_template_doctor_visit_title,
            contentRes = Res.string.chat_template_doctor_visit_content,
            placeholders = listOf("child", "reason", "diagnosis", "medicine", "next check-up")
        ),
        MessageTemplate(
            id = "school_event",
            category = TemplateCategory.SCHOOL,
            titleRes = Res.string.chat_template_school_event_title,
            contentRes = Res.string.chat_template_school_event_content,
            placeholders = listOf("event", "date", "time")
        ),
        MessageTemplate(
            id = "parent_teacher_meeting",
            category = TemplateCategory.SCHOOL,
            titleRes = Res.string.chat_template_parent_teacher_meeting_title,
            contentRes = Res.string.chat_template_parent_teacher_meeting_content,
            placeholders = listOf("date", "time", "topic")
        ),
        MessageTemplate(
            id = "school_update",
            category = TemplateCategory.SCHOOL,
            titleRes = Res.string.chat_template_school_update_title,
            contentRes = Res.string.chat_template_school_update_content,
            placeholders = listOf("child", "what happened")
        ),
        MessageTemplate(
            id = "holiday_plan",
            category = TemplateCategory.HOLIDAYS,
            titleRes = Res.string.chat_template_holiday_plan_title,
            contentRes = Res.string.chat_template_holiday_plan_content,
            placeholders = listOf("holiday", "proposal", "reply by")
        ),
        MessageTemplate(
            id = "travel_abroad",
            category = TemplateCategory.HOLIDAYS,
            titleRes = Res.string.chat_template_travel_abroad_title,
            contentRes = Res.string.chat_template_travel_abroad_content,
            placeholders = listOf("child", "country", "from", "to", "address", "phone", "reply by")
        ),
        MessageTemplate(
            id = "expense_share",
            category = TemplateCategory.EXPENSES,
            titleRes = Res.string.chat_template_expense_share_title,
            contentRes = Res.string.chat_template_expense_share_content,
            placeholders = listOf("what", "date", "total", "share", "pay by")
        ),
        MessageTemplate(
            id = "acknowledge",
            category = TemplateCategory.REPLIES,
            titleRes = Res.string.chat_template_acknowledge_title,
            contentRes = Res.string.chat_template_acknowledge_content,
            placeholders = listOf("topic", "reply by")
        ),
        MessageTemplate(
            id = "confirm_agreement",
            category = TemplateCategory.REPLIES,
            titleRes = Res.string.chat_template_confirm_agreement_title,
            contentRes = Res.string.chat_template_confirm_agreement_content,
            placeholders = listOf("agreement")
        )
    )
}
