package com.coparently.app.domain.parentingplan

import com.coparently.app.domain.files.toHex
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Which agreed parenting-plan answer a custody proposal was built from (MON-21).
 *
 * A **citation**, never a parse: the parent builds the pattern or the seasonal layer themselves
 * in the ordinary editor, and the proposal merely says which answer they were reading. Nothing
 * turns the plan's free text into a schedule — two people's wording is not a machine format, and
 * a schedule guessed from a sentence would be a proposal neither of them wrote.
 *
 * @property questionId The [ParentingPlanCatalogue.Question.id] the agreed answer belongs to.
 * @property answerHash The first [PlanCitationCodec.HASH_HEX_CHARS] lower-case hex characters of
 *   the SHA-256 of [PlanScheduleLink.agreedText] at the moment of proposing. It lets the reader's
 *   phone tell whether the plan still says what the proposal was built from, without the
 *   proposal carrying a paragraph of somebody's words in the custody document.
 */
data class PlanCitation(val questionId: String, val answerHash: String)

/**
 * What a stored citation decodes to.
 *
 * Two shapes, like `DecodedLayers`: an entry this build can read, and one it cannot, kept
 * **verbatim** so a swap write that re-sends the proposal never erases what a newer build wrote.
 */
sealed interface DecodedCitation {

    /** A citation in a format this build understands. */
    data class Readable(val citation: PlanCitation) : DecodedCitation

    /** A string this build cannot read — a later format, or damage. It decides nothing. */
    data class Unreadable(val wire: String) : DecodedCitation
}

/**
 * The one definition of a citation's wire form (MON-21, CLAUDE.md item 32):
 * `"p1|<questionId>|<16 hex chars>"`, stored as the custody document's top-level
 * `proposalPlanCitation` beside a pending `proposal`.
 *
 * A codec string rather than a Gson map, for the reasons `ContactWindowCodec` and
 * `SeasonalLayerCodec` give: R8 has renamed a Gson model's fields in this project before, a rule
 * can bound a string's size in one clause, and the `p1` prefix leaves room for a later format
 * without breaking the one written today.
 */
object PlanCitationCodec {

    /** The format written by this build. */
    const val VERSION: String = "p1"

    /** The longest string `firestore.rules` admits under `proposalPlanCitation`. */
    const val MAX_WIRE_LENGTH: Int = 128

    /** How much of the SHA-256 is kept: 64 bits is plenty to notice an edit, and short to store. */
    const val HASH_HEX_CHARS: Int = 16

    private const val SEPARATOR = "|"
    private const val PARTS = 3
    private val QUESTION_ID = Regex("[a-z0-9_]{1,64}")
    private val HASH = Regex("[0-9a-f]{$HASH_HEX_CHARS}")

    /** [citation] as it is stored. */
    fun encode(citation: PlanCitation): String =
        listOf(VERSION, citation.questionId, citation.answerHash).joinToString(SEPARATOR)

    /**
     * [wire] decoded, or null when there is no citation at all — a missing key is what a proposal
     * from a build that predates MON-21 looks like, and it is never an error.
     */
    fun decode(wire: String?): DecodedCitation? {
        if (wire.isNullOrBlank()) return null
        val parts = wire.split(SEPARATOR)
        val readable = parts.size == PARTS &&
            parts[0] == VERSION &&
            QUESTION_ID.matches(parts[1]) &&
            HASH.matches(parts[2])
        return if (readable) {
            DecodedCitation.Readable(PlanCitation(questionId = parts[1], answerHash = parts[2]))
        } else {
            DecodedCitation.Unreadable(wire)
        }
    }

    /** The truncated SHA-256 of [text], as [PlanCitation.answerHash] stores it. */
    fun hashOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.toHex().take(HASH_HEX_CHARS)
    }
}
