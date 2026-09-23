package com.coparently.app.domain.export

import java.time.ZoneId

/**
 * How a printed record lays out the parenting plan (MON-5 in MON-3's record).
 *
 * Pure, like [RecordLayout], which calls it. The section says what it is before any answer: the
 * wording is this project's and not the Ministry's form, the plan is printed as it stood when the
 * record was generated rather than for the record's period, and — when it is — that the server
 * could not be read and this is the phone's copy. A family with no plan gets one line saying so.
 *
 * Each question is its wording, then where the two parents stand, then each parent's answer under
 * their name. Answers to questions the plan no longer asks follow under their own heading, by id,
 * because the app keeps them and a record that dropped them would be quieter than the app.
 */
object RecordPlanLayout {

    /** The plan section as paragraphs, in reading order. */
    fun blocks(plan: RecordPlan, zone: ZoneId, words: PlanLabels): List<RecordBlock> {
        val heading = RecordBlock(words.section, LineStyle.HEADING, spaceBefore = RecordLayout.SECTION_GAP)
        val notes = listOfNotNull(
            RecordBlock(words.disclaimer, LineStyle.SMALL),
            RecordBlock(words.currentState, LineStyle.SMALL),
            words.notFromServer.takeUnless { plan.serverReached }?.let {
                RecordBlock(it, LineStyle.EMPHASIS, spaceBefore = RecordLayout.SMALL_GAP)
            },
            words.unsentHere.takeIf { plan.unsentHere }?.let { RecordBlock(it, LineStyle.SMALL) }
        )
        if (!plan.recorded) {
            val none = RecordBlock(words.noPlan, LineStyle.BODY, spaceBefore = RecordLayout.ITEM_GAP)
            return listOf(heading) + notes + none
        }
        val changes = plan.changes.mapIndexed { index, change ->
            RecordBlock(
                "${words.lastChanged}: ${change.parentName} — ${RecordFormat.instant(change.updatedAtMillis, zone)}",
                LineStyle.SMALL,
                spaceBefore = if (index == 0) RecordLayout.SMALL_GAP else 0f
            )
        }
        val questions = plan.questions.flatMap { question(it, words.question(it.questionId), words) }
        val retired = if (plan.retired.isEmpty()) {
            emptyList()
        } else {
            listOf(RecordBlock(words.retired, LineStyle.SUBHEADING, spaceBefore = RecordLayout.SECTION_GAP)) +
                plan.retired.flatMap { question(it, it.questionId, words) }
        }
        return listOf(heading) + notes + changes + questions + retired
    }

    private fun question(question: RecordPlanQuestion, title: String, words: PlanLabels): List<RecordBlock> =
        listOf(
            RecordBlock(title, LineStyle.SUBHEADING, spaceBefore = RecordLayout.ITEM_GAP),
            RecordBlock(words.agreement(question.agreement), LineStyle.EMPHASIS, spaceBefore = RecordLayout.SMALL_GAP)
        ) + question.answers.map { answer ->
            RecordBlock("${answer.parentName}: ${answer.text ?: words.notAnswered}", LineStyle.BODY, indent = 1)
        }
}
