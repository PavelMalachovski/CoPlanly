package com.coparently.app.domain.export

import com.coparently.app.domain.export.RecordFixtures.ALICE
import com.coparently.app.domain.export.RecordFixtures.BOB
import com.coparently.app.domain.export.RecordFixtures.MARCH_9_1800
import com.coparently.app.domain.export.RecordFixtures.labels
import com.coparently.app.domain.export.RecordFixtures.planOf
import com.coparently.app.domain.export.RecordFixtures.scope
import com.coparently.app.domain.export.RecordFixtures.sources
import com.coparently.app.domain.parentingplan.ParentingPlanCatalogue
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parenting plan in the communication record (MON-5 in MON-3's export).
 *
 * Pinned in all three layers the export has: what the builder puts in (agreement derived exactly as
 * the plan screen derives it, retired answers kept, no plan invented, a failed server read never
 * hidden), how the CSV carries it (same width, same formula guard), and how the PDF lays it out.
 */
class RecordPlanTest {

    private val measure: (String, LineStyle) -> Float = { text, _ -> text.length * CHAR_WIDTH }

    /** Alice and Bob agree on where the child lives; Bob ticked an older wording of Alice's weekdays. */
    private val halves = mapOf(
        ALICE to ParentingPlanEntry(
            answers = mapOf(RESIDENCE to ALICE_HOME, WEEKDAYS to "Mon-Wed with Alice, now Thursdays too"),
            agreedTo = mapOf(RESIDENCE to BOB_HOME),
            updatedAtMillis = 0L
        ),
        BOB to ParentingPlanEntry(
            answers = mapOf(RESIDENCE to BOB_HOME, WEEKDAYS to "Thu-Sun with Bob", RETIRED to "Kept from v0"),
            agreedTo = mapOf(RESIDENCE to ALICE_HOME, WEEKDAYS to "Mon-Wed with Alice"),
            updatedAtMillis = MARCH_9_1800
        )
    )

    private fun recordWith(source: PlanSource?) = CommunicationRecordBuilder.build(sources().copy(plan = source), scope())

    // ---- The builder ----------------------------------------------------------------------

    @Test
    fun `a question is agreed only when each parent ticked the other's answer as it reads now`() {
        val plan = assertNotNull(recordWith(planOf(halves)).plan)

        assertEquals(PlanAgreement.AGREED, plan.question(RESIDENCE).agreement)
        // Bob agreed to a wording Alice has since changed: the agreement lapsed with the edit.
        assertEquals(PlanAgreement.NOT_AGREED, plan.question(WEEKDAYS).agreement)
        assertEquals(PlanAgreement.UNANSWERED, plan.question("health_doctor").agreement)
    }

    @Test
    fun `every catalogue question is listed in catalogue order, each parent's answer by name`() {
        val plan = assertNotNull(recordWith(planOf(halves)).plan)

        assertEquals(ParentingPlanCatalogue.questions.map { it.id }, plan.questions.map { it.questionId })
        assertEquals(
            listOf(RecordPlanAnswer("Alice", ALICE_HOME), RecordPlanAnswer("Bob", BOB_HOME)),
            plan.question(RESIDENCE).answers
        )
        assertEquals(listOf("Alice", "Bob"), plan.question("health_doctor").answers.map { it.parentName })
        assertTrue(plan.question("health_doctor").answers.all { it.text == null })
    }

    @Test
    fun `an answer under a retired question is kept, after the catalogue, never dropped`() {
        val plan = assertNotNull(recordWith(planOf(halves)).plan)

        assertEquals(listOf(RETIRED), plan.retired.map { it.questionId })
        assertEquals(listOf(null, "Kept from v0"), plan.retired.single().answers.map { it.text })
        assertTrue(plan.questions.none { it.questionId == RETIRED })
    }

    @Test
    fun `only a half that was ever edited says when`() {
        val plan = assertNotNull(recordWith(planOf(halves)).plan)

        assertEquals(listOf(RecordPlanChange("Bob", MARCH_9_1800)), plan.changes)
    }

    @Test
    fun `a family with no plan says so rather than inventing one, and the record stays complete`() {
        val record = recordWith(planOf())
        val plan = assertNotNull(record.plan)

        assertFalse(plan.recorded)
        assertTrue(record.complete)
    }

    @Test
    fun `a plan the server could not give makes the record incomplete and says whose copy it is`() {
        val record = recordWith(planOf(halves, serverReached = false))

        val plan = assertNotNull(record.plan)

        assertFalse(record.complete)
        assertFalse(plan.serverReached)
        assertTrue(plan.recorded)
    }

    @Test
    fun `a plan left out of the export is absent, and costs the record nothing`() {
        val record = recordWith(null)

        assertNull(record.plan)
        assertTrue(record.complete)
    }

    // ---- The CSV --------------------------------------------------------------------------

    @Test
    fun `plan rows have the header's width and carry answer, question and agreement`() {
        val csv = CommunicationRecordCsv.render(recordWith(planOf(halves)), labels())
        val width = labels().columns.all().size
        val columns = labels().columns.all()

        records(csv).forEach { assertEquals(width, fields(it).size, "record: $it") }
        val alice = records(csv).map(::fields).first { it[0] == "Plan" && it[1] == RESIDENCE && it[4] == "Alice" }
        assertEquals(ALICE_HOME, alice[columns.indexOf("Text")])
        assertEquals("Where will the child live?", alice[columns.indexOf("Notes")])
        assertEquals("Agreed", alice[columns.indexOf("Action")])
    }

    @Test
    fun `the plan's notes come before its answers, and a retired answer says so`() {
        val lines = records(CommunicationRecordCsv.render(recordWith(planOf(halves)), labels()))

        val disclaimer = lines.indexOfFirst { it.contains("NOT THE MINISTRY FORM") }
        val current = lines.indexOfFirst { it.contains("CURRENT STATE AT EXPORT") }
        val firstAnswer = lines.indexOfFirst { it.startsWith("Plan,$RESIDENCE,") }
        assertTrue(disclaimer in 0 until firstAnswer && current in 0 until firstAnswer)
        assertTrue(lines.any { it.startsWith("Plan,$RETIRED,") && it.endsWith(",NO LONGER ASKED,,") })
        assertTrue(lines.any { it.startsWith("Plan,,,Last changed,Bob,2026-03-09 18:00:00 +01:00,") })
    }

    @Test
    fun `an answer the other parent wrote as a formula arrives as text`() {
        val formula = mapOf(BOB to ParentingPlanEntry(answers = mapOf(RESIDENCE to "=HYPERLINK(\"http://x\")")))
        val csv = CommunicationRecordCsv.render(recordWith(planOf(formula)), labels())

        assertTrue(csv.contains(",\"'=HYPERLINK(\"\"http://x\"\")\","))
    }

    @Test
    fun `no plan is one line, and a phone's copy is labelled as one`() {
        val none = records(CommunicationRecordCsv.render(recordWith(planOf()), labels()))
        val offlineRecord = recordWith(planOf(halves, serverReached = false))
        val offline = records(CommunicationRecordCsv.render(offlineRecord, labels()))

        assertTrue(none.any { fields(it)[7] == "NO PLAN RECORDED" })
        assertTrue(none.none { it.startsWith("Plan,$RESIDENCE,") })
        assertTrue(offline.any { fields(it)[7] == "PLAN FROM THIS PHONE" })
    }

    // ---- The PDF --------------------------------------------------------------------------

    @Test
    fun `the plan is the last section, and says what it is before any answer`() {
        val texts = RecordLayout.blocks(recordWith(planOf(halves)), labels()).map { it.text }

        val expenses = texts.indexOf("Expense")
        val section = texts.indexOf("Plan")
        val firstQuestion = texts.indexOf("Where will the child live?")
        assertTrue(expenses in 0 until section)
        assertTrue(texts.indexOf("NOT THE MINISTRY FORM") in section until firstQuestion)
        assertTrue(texts.indexOf("CURRENT STATE AT EXPORT") in section until firstQuestion)
        assertEquals(
            listOf("Agreed", "Alice: $ALICE_HOME", "Bob: $BOB_HOME"),
            texts.subList(firstQuestion + 1, firstQuestion + 4)
        )
        assertTrue("Last changed: Bob — 2026-03-09 18:00:00 +01:00" in texts)
    }

    @Test
    fun `a question nobody answered, and a retired one, are printed rather than skipped`() {
        val texts = RecordLayout.blocks(recordWith(planOf(halves)), labels()).map { it.text }

        // No wording in the fixture labels for this id: printed by its id, never left blank.
        val health = texts.indexOf("health_doctor")
        assertEquals(
            listOf("Not answered", "Alice: Not answered", "Bob: Not answered"),
            texts.subList(health + 1, health + 4)
        )
        val retired = texts.indexOf("NO LONGER ASKED")
        assertTrue(retired > health)
        assertEquals(RETIRED, texts[retired + 1])
    }

    @Test
    fun `no plan, and a plan from this phone, each say so and still paginate`() {
        val none = RecordLayout.blocks(recordWith(planOf()), labels())
        val offline = RecordLayout.blocks(recordWith(planOf(halves, serverReached = false)), labels())

        assertTrue(none.any { it.text == "NO PLAN RECORDED" })
        assertTrue(none.none { it.text == "Agreed" })
        assertTrue(offline.any { it.text == "PLAN FROM THIS PHONE" && it.style == LineStyle.EMPHASIS })
        assertTrue(offline.any { it.text == "INCOMPLETE" })
        assertTrue(RecordLayout.paginate(offline, PageGeometry(), measure).isNotEmpty())
    }

    @Test
    fun `a left-out plan prints no plan section at all`() {
        val texts = RecordLayout.blocks(recordWith(null), labels()).map { it.text }

        assertFalse("Plan" in texts)
        assertFalse("NOT THE MINISTRY FORM" in texts)
    }

    private fun RecordPlan.question(id: String) = questions.first { it.questionId == id }

    /** Records, split on CRLF outside quotes — the fixtures hold no CRLF inside one. */
    private fun records(csv: String): List<String> =
        csv.removePrefix(CommunicationRecordCsv.BOM).split("\r\n").filter { it.isNotEmpty() }

    /** A minimal RFC 4180 field splitter, as in `CommunicationRecordCsvTest`. */
    private fun fields(record: String): List<String> {
        val out = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < record.length) {
            val c = record[i]
            when {
                quoted && c == '"' && i + 1 < record.length && record[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    out += field.toString()
                    field.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        out += field.toString()
        return out
    }

    private companion object {
        const val CHAR_WIDTH = 5f
        const val RESIDENCE = "residence_home"
        const val WEEKDAYS = "care_weekday"
        const val RETIRED = "legacy_question"
        const val ALICE_HOME = "With Alice in Brno"
        const val BOB_HOME = "With Alice in Brno, weekends with Bob"
    }
}
