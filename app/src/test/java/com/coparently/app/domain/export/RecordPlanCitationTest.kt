package com.coparently.app.domain.export

import com.coparently.app.domain.export.RecordFixtures.labels
import com.coparently.app.domain.export.RecordFixtures.proposalCard
import com.coparently.app.domain.export.RecordFixtures.scope
import com.coparently.app.domain.export.RecordFixtures.sources
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A schedule proposal that cited the parenting plan (MON-21), as the record prints it.
 *
 * The custody document forgets a citation once the proposal is answered, so the record reads it
 * from the proposal's chat card, which nobody can edit. What is printed is the question the
 * proposal cited **when it was made** — in both formats, and only on the card that carried one.
 */
class RecordPlanCitationTest {

    private val cited = "FROM THE PLAN ANSWER TO: Who cares for the child on which weekdays?"

    @Test
    fun `the builder names the cited question and nothing for a card that cites none`() {
        val record = CommunicationRecordBuilder.build(
            sources(messages = listOf(proposalCard(), proposalCard(null).copy(messageId = "m-older"))),
            scope()
        )

        assertEquals("care_weekday", record.messages.first { it.messageId == "m-proposal" }.citedPlanQuestionId)
        assertNull(record.messages.first { it.messageId == "m-older" }.citedPlanQuestionId)
    }

    @Test
    fun `a citation this build cannot read names nothing rather than a guess`() {
        val record = CommunicationRecordBuilder.build(
            sources(messages = listOf(proposalCard("p2|care_weekday|0123456789abcdef|more"))),
            scope()
        )

        assertNull(record.messages.single().citedPlanQuestionId)
    }

    @Test
    fun `the CSV puts the cited question in the message row's notes`() {
        val csv = CommunicationRecordCsv.render(
            CommunicationRecordBuilder.build(sources(messages = listOf(proposalCard())), scope()),
            labels()
        )

        // No field in this row holds a comma or a quote, so a plain split reads it back exactly.
        val row = csv.split("\r\n").first { it.startsWith("Message,") }.split(",")
        assertEquals(cited, row[labels().columns.all().indexOf("Notes")])
        assertEquals("Bob proposed a new custody schedule", row[labels().columns.all().indexOf("Text")])
    }

    @Test
    fun `the PDF prints the cited question under the message, and only there`() {
        val withCitation = RecordLayout.blocks(
            CommunicationRecordBuilder.build(sources(messages = listOf(proposalCard())), scope()),
            labels()
        ).map { it.text }
        val without = RecordLayout.blocks(
            CommunicationRecordBuilder.build(sources(messages = listOf(proposalCard(null))), scope()),
            labels()
        ).map { it.text }

        assertEquals(withCitation.indexOf("Bob proposed a new custody schedule") + 1, withCitation.indexOf(cited))
        assertFalse(without.any { it.startsWith("FROM THE PLAN ANSWER TO") })
    }

    @Test
    fun `a question the plan no longer asks is printed by its id, never dropped`() {
        val record = CommunicationRecordBuilder.build(
            sources(messages = listOf(proposalCard("p1|care_retired|0123456789abcdef"))),
            scope()
        )

        val texts = RecordLayout.blocks(record, labels()).map { it.text }

        assertTrue("FROM THE PLAN ANSWER TO: care_retired" in texts)
    }
}
