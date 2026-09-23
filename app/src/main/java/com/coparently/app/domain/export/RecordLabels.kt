package com.coparently.app.domain.export

/**
 * Every word an export prints, already in the reader's language.
 *
 * Resolved in composition from `export_strings.xml` and handed down, so the renderers stay pure
 * Kotlin and no `Context` reaches a ViewModel (CLAUDE.md, CQ-14). No templates: a word that needs
 * a number beside it ("Revision", "Page") is printed with the number after it, which reads right in
 * all five languages and keeps `String.format` out of code that runs off the main thread.
 *
 * @property statement The paragraphs that say what the record is and is not — printed first, on
 *   the face of both formats, and never shortened (`docs/DESIGN-court-record.md` §3).
 * @property plan The words of the parenting-plan section, printed only when the record carries one.
 */
data class RecordLabels(
    val title: String,
    val statement: List<String>,
    val period: String,
    val generated: String,
    val timeZone: String,
    val parents: String,
    val incomplete: String,
    val sectionEvents: String,
    val sectionMessages: String,
    val sectionExpenses: String,
    val nothingInPeriod: String,
    val columns: RecordColumns,
    val actions: RecordActions,
    val notYetOnServer: String,
    val noServerTime: String,
    val revision: String,
    val page: String,
    val verification: VerificationLabels,
    val plan: PlanLabels
)

/**
 * What the parenting-plan section prints (MON-5 in the record).
 *
 * @property section The section's heading.
 * @property disclaimer That the wording is this project's own, not the Ministry of Justice's form —
 *   the same sentence the plan screen shows (`parenting_plan_disclaimer`, CLAUDE.md item 21).
 * @property currentState That the plan is not bound to the record's period: it is printed as it
 *   stood when the record was generated.
 * @property notFromServer That the plan could not be read from the server and this is the phone's copy.
 * @property unsentHere That edits made on this phone have not reached the server and are not printed.
 * @property noPlan For a family with no plan: said, never invented.
 * @property lastChanged The label before each parent's last change to their half.
 * @property agreed A question both parents agreed on, as each other's answer reads now.
 * @property notAgreed A question at least one parent answered without the two agreeing.
 * @property notAnswered An answer, or a question, nobody has written.
 * @property retired The heading over answers to questions the plan no longer asks.
 * @property questions Each catalogue question's wording by id; an id missing here prints as its id.
 */
data class PlanLabels(
    val section: String,
    val disclaimer: String,
    val currentState: String,
    val notFromServer: String,
    val unsentHere: String,
    val noPlan: String,
    val lastChanged: String,
    val agreed: String,
    val notAgreed: String,
    val notAnswered: String,
    val retired: String,
    val questions: Map<String, String>
) {
    /** The status a question's agreement prints as. */
    fun agreement(agreement: PlanAgreement): String = when (agreement) {
        PlanAgreement.AGREED -> agreed
        PlanAgreement.NOT_AGREED -> notAgreed
        PlanAgreement.UNANSWERED -> notAnswered
    }

    /** The wording of [questionId], or the id itself when there is none — a retired question's case. */
    fun question(questionId: String): String = questions[questionId] ?: questionId
}

/**
 * What a file says about checking it (MON-16).
 *
 * @property recordId The label before the record id.
 * @property verifyAt The label before the verification page's address.
 * @property instruction One line telling a reader how to check the file, printed under the address.
 * @property instructionNoUrl The same line while no verification page is hosted: it says the file was
 *   registered, and does not point at an address that does not exist.
 * @property notRegistered The file's own statement that it cannot be checked, on its first page and
 *   in the CSV preamble.
 * @property notRegisteredShort The same, short enough for every page's footer.
 */
data class VerificationLabels(
    val recordId: String,
    val verifyAt: String,
    val instruction: String,
    val instructionNoUrl: String,
    val notRegistered: String,
    val notRegisteredShort: String
)

/** The column headers, which the PDF reuses as field labels so the two formats read alike. */
data class RecordColumns(
    val section: String,
    val item: String,
    val revision: String,
    val action: String,
    val by: String,
    val deviceTime: String,
    val serverTime: String,
    val text: String,
    val starts: String,
    val ends: String,
    val parent: String,
    val notes: String,
    val amount: String,
    val currency: String
) {
    /** In the order the CSV prints them. */
    fun all(): List<String> = listOf(
        section, item, revision, action, by, deviceTime, serverTime, text, starts, ends, parent, notes,
        amount, currency
    )
}

/**
 * What each row records.
 *
 * @property currentState An entry saved before revisions were kept: its state today, not a
 *   revision — worded so nobody reads it as the moment the entry was made.
 * @property notSent A message this phone never managed to send; it is in the record because the
 *   parent wrote it, and marked because the other parent never received it.
 */
data class RecordActions(
    val created: String,
    val updated: String,
    val deleted: String,
    val currentState: String,
    val sent: String,
    val notSent: String,
    val recorded: String
)
