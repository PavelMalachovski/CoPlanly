package com.coparently.app.domain.export

import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.domain.parentingplan.ParentingPlanComparison
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.coparently.app.domain.parentingplan.PlanQuestionStatus

/**
 * The family's parenting plan as the export reads it (MON-5 in MON-3's record).
 *
 * @property parentUids The two parents, the signed-in one first — the order the record prints the
 *   answers in. One uid when the account has no co-parent.
 * @property halves Each parent's half, by author uid: the server's copy when [serverReached], this
 *   phone's otherwise. Empty for a family with no plan document, or no family at all.
 * @property serverReached False when the server could not be asked, so [halves] is this phone's copy.
 * @property unsentHere True when the server was read but this phone still holds edits to the
 *   signed-in parent's half that have not reached it — the record then says the server's copy is
 *   what it prints.
 */
data class PlanSource(
    val parentUids: List<String>,
    val halves: Map<String, ParentingPlanEntry>,
    val serverReached: Boolean,
    val unsentHere: Boolean
)

/**
 * Where one question of the plan stands in the record.
 *
 * Coarser than [PlanQuestionStatus], whose "only yours" and "only theirs" are relative to the reader
 * of a screen; a document handed to a third person names the parents in the answers instead.
 */
enum class PlanAgreement {
    /** Both parents answered and each ticked the other's answer as it reads now. */
    AGREED,

    /** At least one parent answered, and the two have not agreed on what is written. */
    NOT_AGREED,

    /** Neither parent answered. */
    UNANSWERED
}

/**
 * The parenting plan section of a record.
 *
 * **Not bound to the record's period**: a plan has no date range, so this is its state when the
 * record was generated, and both formats say so.
 *
 * @property serverReached False when this is this phone's copy, printed as such.
 * @property unsentHere See [PlanSource.unsentHere].
 * @property changes When each parent last changed their half, by their device's clock.
 * @property questions Every catalogue question, in catalogue order, answered or not.
 * @property retired Answers under question ids the catalogue no longer asks — kept by the app
 *   (CLAUDE.md item 21), so printed rather than silently dropped, after the catalogue.
 */
data class RecordPlan(
    val serverReached: Boolean,
    val unsentHere: Boolean,
    val changes: List<RecordPlanChange>,
    val questions: List<RecordPlanQuestion>,
    val retired: List<RecordPlanQuestion>
) {
    /** Whether either parent has written anything — false prints "no parenting plan recorded". */
    val recorded: Boolean get() = (questions + retired).any { question -> question.answers.any { it.text != null } }
}

/** When [parentName] last changed their half of the plan, epoch millis from their device. */
data class RecordPlanChange(val parentName: String, val updatedAtMillis: Long)

/**
 * One question and both parents' answers to it.
 *
 * @property answers One per parent, in [PlanSource.parentUids] order.
 */
data class RecordPlanQuestion(
    val questionId: String,
    val answers: List<RecordPlanAnswer>,
    val agreement: PlanAgreement
)

/** One parent's answer, by name; [text] is null when they have not answered. */
data class RecordPlanAnswer(val parentName: String, val text: String?)

/**
 * Builds the parenting-plan section of a record from a [PlanSource].
 *
 * Pure, like [CommunicationRecordBuilder], which calls it. Agreement is never stored and never
 * decided here: it is [ParentingPlanComparison.statusOf] — each parent ticked the *text* the other
 * has now — so the record and the plan screen cannot disagree about which questions are settled.
 */
object RecordPlanBuilder {

    /** The section for [source], naming every parent through [nameForUid] — never a role. */
    fun build(source: PlanSource, nameForUid: (String) -> String): RecordPlan {
        val authors = source.parentUids + (source.halves.keys - source.parentUids.toSet()).sorted()
        val yours = source.parentUids.getOrNull(0)?.let { source.halves[it] } ?: ParentingPlanEntry()
        val theirs = source.parentUids.getOrNull(1)?.let { source.halves[it] }
        val question = { id: String ->
            RecordPlanQuestion(
                questionId = id,
                answers = authors.map { uid -> RecordPlanAnswer(nameForUid(uid), source.halves[uid]?.answerTo(id)) },
                agreement = agreementOf(ParentingPlanComparison.statusOf(id, yours, theirs))
            )
        }
        val retiredIds = source.halves.values
            .flatMap { half -> half.answers.filterValues { it.isNotBlank() }.keys }
            .filter { it !in ParentingPlanCatalogue.questionIds }
            .distinct()
            .sorted()
        return RecordPlan(
            serverReached = source.serverReached,
            unsentHere = source.unsentHere,
            changes = authors.mapNotNull { uid ->
                source.halves[uid]?.updatedAtMillis?.takeIf { it > 0L }?.let { RecordPlanChange(nameForUid(uid), it) }
            },
            questions = ParentingPlanCatalogue.questions.map { question(it.id) },
            retired = retiredIds.map(question)
        )
    }

    private fun agreementOf(status: PlanQuestionStatus): PlanAgreement = when (status) {
        PlanQuestionStatus.AGREED -> PlanAgreement.AGREED
        PlanQuestionStatus.UNANSWERED -> PlanAgreement.UNANSWERED
        PlanQuestionStatus.ONLY_YOURS,
        PlanQuestionStatus.ONLY_THEIRS,
        PlanQuestionStatus.OPEN -> PlanAgreement.NOT_AGREED
    }
}
