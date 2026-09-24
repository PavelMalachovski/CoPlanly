package com.coparently.app.domain.export

import java.time.ZoneId

/**
 * The parenting plan's rows in the CSV record ([CommunicationRecordCsv]), kept apart so the renderer
 * stays one screen of code. Every row is padded and escaped by the renderer like any other.
 */
internal object PlanCsvRows {

    /**
     * The parenting plan: its notes, when each parent last changed their half, then one row per
     * parent per question — the answer in the text column, the question's wording in the notes, the
     * agreement in the action column. A retired question has no wording left, so its notes say so.
     */
    fun rows(plan: RecordPlan, zone: ZoneId, words: PlanLabels): List<List<String>> {
        val note = { text: String -> listOf(words.section, "", "", "", "", "", "", text) }
        val notes = listOfNotNull(
            words.disclaimer,
            words.currentState,
            words.notFromServer.takeUnless { plan.serverReached },
            words.unsentHere.takeIf { plan.unsentHere }
        ).map(note)
        if (!plan.recorded) return notes + listOf(note(words.noPlan))
        val changes = plan.changes.map {
            val time = RecordFormat.instant(it.updatedAtMillis, zone)
            listOf(words.section, "", "", words.lastChanged, it.parentName, time)
        }
        val answers = plan.questions.flatMap { questionRows(it, words.question(it.questionId), words) } +
            plan.retired.flatMap { questionRows(it, words.retired, words) }
        return notes + changes + answers
    }

    private fun questionRows(question: RecordPlanQuestion, notes: String, words: PlanLabels): List<List<String>> =
        question.answers.map { answer ->
            listOf(
                words.section,
                question.questionId,
                "",
                words.agreement(question.agreement),
                answer.parentName,
                "",
                "",
                answer.text ?: words.notAnswered,
                "",
                "",
                "",
                notes
            )
        }
}
