package com.coparently.app.domain.parentingplan

/** Which schedule editor an agreed plan answer is turned into a proposal with (MON-21). */
enum class PlanScheduleTarget {
    /** The base custody pattern — `CustodySetupScreen`'s form. */
    BASE_PATTERN,

    /** A seasonal layer over the pattern (MON-14) — `SeasonalLayerDialog`. */
    SEASONAL_LAYER
}

/**
 * An agreed plan answer, quoted at the top of the schedule editor while the parent builds the
 * pattern it describes.
 *
 * @property questionId The question the answer belongs to.
 * @property target Which editor it opens.
 * @property yourAnswer This parent's answer as it reads now.
 * @property theirAnswer The co-parent's answer as it reads now. Equal to [yourAnswer] whenever the
 *   two wrote the same words; each parent has ticked the other's, which is what agreed means.
 * @property citation What the proposal will carry, hashed from [PlanScheduleLink.agreedText].
 */
data class PlanReference(
    val questionId: String,
    val target: PlanScheduleTarget,
    val yourAnswer: String,
    val theirAnswer: String,
    val citation: PlanCitation
) {
    /** [citation] in its stored form, ready for the proposal write. */
    val citationWire: String get() = PlanCitationCodec.encode(citation)

    /** True when both parents wrote the very same words, so the card quotes them once. */
    val sameWording: Boolean get() = yourAnswer == theirAnswer
}

/** What the parent answering a proposal is told about its source. */
sealed interface CitationStatus {

    /** No citation, or one this build cannot read or name — the card says nothing extra. */
    data object None : CitationStatus

    /** The plan still says what the proposal was built from. */
    data class Current(val questionId: String) : CitationStatus

    /**
     * The cited answer has been edited, or is no longer agreed, since the proposal was made. The
     * reader is told so rather than shown a source the plan no longer contains.
     */
    data class Changed(val questionId: String) : CitationStatus
}

/**
 * From an agreed parenting-plan answer to a proposed schedule, and back (MON-21, CLAUDE.md
 * item 32).
 *
 * **The plan never becomes a schedule by itself.** An agreed answer only offers a way into the
 * existing editors; the parent builds the pattern, and saving goes through the ordinary proposal
 * flow, so a paired family gets a proposal the co-parent accepts or declines — never an
 * overwrite. What this file adds is the thread between the two: which questions offer it, the
 * text the proposal cites, and whether that text still stands when the co-parent reads it.
 */
object PlanScheduleLink {

    /**
     * The catalogue questions that describe a schedule, and the editor each one opens.
     *
     * Chosen by what the answer *is*, not by its section:
     * - `care_weekday` ("how will you divide weekdays, weekends and school holidays") is the
     *   rotation itself — the base pattern.
     * - `holidays_school` and `holidays_special` are about named stretches of the year — the
     *   summer, Christmas, a birthday — which is exactly what a seasonal layer replaces the
     *   pattern with.
     *
     * Deliberately **not** `care_handover` (where and who drives: a place and a person, not whose
     * day it is), nor `residence_*` (an address, not a rotation). Offering a schedule editor for an
     * answer no schedule can express would be design rule 8's empty promise.
     */
    val SCHEDULE_QUESTIONS: Map<String, PlanScheduleTarget> = mapOf(
        "care_weekday" to PlanScheduleTarget.BASE_PATTERN,
        "holidays_school" to PlanScheduleTarget.SEASONAL_LAYER,
        "holidays_special" to PlanScheduleTarget.SEASONAL_LAYER
    )

    /** Joins two different wordings in [agreedText]; a NUL cannot occur in typed text. */
    private const val WORDING_SEPARATOR = "\u0000"

    /** The editor [questionId] opens, or null when it is not about a schedule. */
    fun targetOf(questionId: String): PlanScheduleTarget? = SCHEDULE_QUESTIONS[questionId]

    /**
     * The text an agreement on [questionId] consists of, or null unless it is
     * [PlanQuestionStatus.AGREED].
     *
     * Both answers, because an agreement is each parent ticking the *other's* wording
     * ([ParentingPlanEntry.agreedTo]) and the two need not be the same words. Sorted, so either
     * parent's phone — which holds the same two halves the other way round — derives the same
     * string, and a single text when they are identical, so the common case hashes exactly the
     * words both parents see.
     */
    fun agreedText(questionId: String, yours: ParentingPlanEntry, theirs: ParentingPlanEntry?): String? {
        val agreed = ParentingPlanComparison.statusOf(questionId, yours, theirs) == PlanQuestionStatus.AGREED
        val mine = yours.answerTo(questionId)
        val other = theirs?.answerTo(questionId)
        return if (agreed && mine != null && other != null) {
            listOf(mine, other).distinct().sorted().joinToString(WORDING_SEPARATOR)
        } else {
            null
        }
    }

    /**
     * Whether the plan screen offers "Propose as the schedule" on [questionId].
     *
     * Only on a schedule question, only once both parents agree, only when paired, and not while
     * the co-parent's own custody proposal waits for this parent — the repository cannot put a
     * second proposal over theirs, and its fallback is a local save, so the seasonal section
     * already refuses to send then. The plan screen says why instead of offering the action.
     */
    fun offersProposal(
        questionId: String,
        yours: ParentingPlanEntry,
        theirs: ParentingPlanEntry?,
        paired: Boolean,
        coParentProposalPending: Boolean
    ): Boolean = paired &&
        !coParentProposalPending &&
        targetOf(questionId) != null &&
        agreedText(questionId, yours, theirs) != null

    /**
     * The reference the editor quotes, or null when [questionId] is not an agreed schedule
     * question — the editor then opens exactly as it always did.
     */
    fun reference(questionId: String, yours: ParentingPlanEntry, theirs: ParentingPlanEntry?): PlanReference? {
        val target = targetOf(questionId) ?: return null
        val text = agreedText(questionId, yours, theirs) ?: return null
        return PlanReference(
            questionId = questionId,
            target = target,
            yourAnswer = yours.answerTo(questionId).orEmpty(),
            theirAnswer = theirs?.answerTo(questionId).orEmpty(),
            citation = PlanCitation(questionId, PlanCitationCodec.hashOf(text))
        )
    }

    /**
     * What the reader of a proposal is told about [wire], its citation, given the plan as this
     * phone holds it now.
     *
     * A missing citation is an older build's proposal and says nothing; so does one this build
     * cannot read, and one naming a question the catalogue does not ask — there is no wording to
     * quote. A readable one is [CitationStatus.Current] while the agreed text still hashes to what
     * it carries, and [CitationStatus.Changed] once either parent has edited it or the agreement
     * has lapsed.
     */
    fun citationStatus(wire: String?, yours: ParentingPlanEntry, theirs: ParentingPlanEntry?): CitationStatus {
        val citation = (PlanCitationCodec.decode(wire) as? DecodedCitation.Readable)?.citation
            ?: return CitationStatus.None
        if (citation.questionId !in ParentingPlanCatalogue.questionIds) return CitationStatus.None
        val now = agreedText(citation.questionId, yours, theirs)?.let(PlanCitationCodec::hashOf)
        return if (now == citation.answerHash) {
            CitationStatus.Current(citation.questionId)
        } else {
            CitationStatus.Changed(citation.questionId)
        }
    }
}
