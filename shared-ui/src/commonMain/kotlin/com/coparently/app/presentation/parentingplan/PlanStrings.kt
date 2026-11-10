package com.coparently.app.presentation.parentingplan

import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.resources.Res
import com.coparently.app.resources.parenting_plan_q_care_handover
import com.coparently.app.resources.parenting_plan_q_care_weekday
import com.coparently.app.resources.parenting_plan_q_communication_between_parents
import com.coparently.app.resources.parenting_plan_q_communication_disagreement
import com.coparently.app.resources.parenting_plan_q_education_activities
import com.coparently.app.resources.parenting_plan_q_education_school
import com.coparently.app.resources.parenting_plan_q_health_decisions
import com.coparently.app.resources.parenting_plan_q_health_doctor
import com.coparently.app.resources.parenting_plan_q_holidays_school
import com.coparently.app.resources.parenting_plan_q_holidays_special
import com.coparently.app.resources.parenting_plan_q_money_extraordinary
import com.coparently.app.resources.parenting_plan_q_money_maintenance
import com.coparently.app.resources.parenting_plan_q_residence_change
import com.coparently.app.resources.parenting_plan_q_residence_home
import com.coparently.app.resources.parenting_plan_section_care
import com.coparently.app.resources.parenting_plan_section_communication
import com.coparently.app.resources.parenting_plan_section_education
import com.coparently.app.resources.parenting_plan_section_health
import com.coparently.app.resources.parenting_plan_section_holidays
import com.coparently.app.resources.parenting_plan_section_money
import com.coparently.app.resources.parenting_plan_section_residence
import org.jetbrains.compose.resources.StringResource

/**
 * The wording for each parenting-plan section and question (MON-5).
 *
 * Kept out of `ParentingPlanCatalogue` on purpose. The catalogue is stored vocabulary — ids that
 * key answers on two phones — and it stays free of Android so the comparison logic can be tested
 * at all; the wording is five translations of a question and belongs where every other string in
 * this app lives. The join between them is one map per level, and `PlanStringsTest` fails the
 * build if an id here has no entry, because the alternative is a blank row on a screen a parent
 * may print. Compose Multiplatform [StringResource]s since the calendar's banner moved to `:shared-ui`
 * (MON-24 phase 2, slice 11c).
 */
object PlanStrings {

    private val sectionTitles: Map<String, StringResource> = mapOf(
        "residence" to Res.string.parenting_plan_section_residence,
        "care" to Res.string.parenting_plan_section_care,
        "holidays" to Res.string.parenting_plan_section_holidays,
        "education" to Res.string.parenting_plan_section_education,
        "health" to Res.string.parenting_plan_section_health,
        "money" to Res.string.parenting_plan_section_money,
        "communication" to Res.string.parenting_plan_section_communication
    )

    private val questionPrompts: Map<String, StringResource> = mapOf(
        "residence_home" to Res.string.parenting_plan_q_residence_home,
        "residence_change" to Res.string.parenting_plan_q_residence_change,
        "care_weekday" to Res.string.parenting_plan_q_care_weekday,
        "care_handover" to Res.string.parenting_plan_q_care_handover,
        "holidays_school" to Res.string.parenting_plan_q_holidays_school,
        "holidays_special" to Res.string.parenting_plan_q_holidays_special,
        "education_school" to Res.string.parenting_plan_q_education_school,
        "education_activities" to Res.string.parenting_plan_q_education_activities,
        "health_doctor" to Res.string.parenting_plan_q_health_doctor,
        "health_decisions" to Res.string.parenting_plan_q_health_decisions,
        "money_maintenance" to Res.string.parenting_plan_q_money_maintenance,
        "money_extraordinary" to Res.string.parenting_plan_q_money_extraordinary,
        "communication_between_parents" to Res.string.parenting_plan_q_communication_between_parents,
        "communication_disagreement" to Res.string.parenting_plan_q_communication_disagreement
    )

    /** The heading for [sectionId], or null when the catalogue has outrun this file. */
    fun sectionTitle(sectionId: String): StringResource? = sectionTitles[sectionId]

    /** The question text for [questionId], or null when the catalogue has outrun this file. */
    fun questionPrompt(questionId: String): StringResource? = questionPrompts[questionId]

    /** Every id this file has wording for — what the test compares against the catalogue. */
    val knownSectionIds: Set<String> get() = sectionTitles.keys

    /** Every question id this file has wording for. */
    val knownQuestionIds: Set<String> get() = questionPrompts.keys

    /** True when every id [ParentingPlanCatalogue] asks about has wording here. */
    fun isComplete(): Boolean =
        knownSectionIds.containsAll(ParentingPlanCatalogue.sections.map { it.id }) &&
            knownQuestionIds.containsAll(ParentingPlanCatalogue.questionIds)
}
