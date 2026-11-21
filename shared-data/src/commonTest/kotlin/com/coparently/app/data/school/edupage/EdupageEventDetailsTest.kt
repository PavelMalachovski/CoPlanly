package com.coparently.app.data.school.edupage

import com.coparently.app.data.school.edupage.EdupageEventDetails.Note
import com.coparently.app.data.school.edupage.EdupageEventDetails.Target
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Details from the school's messages. The texts are the shapes one live Czech school sent in November
 * 2026, with names and places replaced.
 */
class EdupageEventDetailsTest {

    private val minigolf = Target("e1", "Svítící minigolf", LocalDate(2026, 11, 19))
    private val skatingDec = Target("e2", "Bruslení na ledu", LocalDate(2026, 12, 21))
    private val skatingJan = Target("e3", "Bruslení na ledu", LocalDate(2027, 1, 27))
    private val market = Target("e4", "Vánoční jarmark", LocalDate(2026, 12, 2))

    private val tripMessage = "Vážení rodiče,\n\nzítra jdeme na svítící minigolf.\n\n" +
        "Odcházet budeme cca v 8:50, návrat kolem 12:00.\n\nS sebou: svačinu, pití a oblečení dle počasí."

    @Test
    fun `a message about one event is quoted whole under the day it was sent`() {
        val note = Note(LocalDate(2026, 11, 18), tripMessage)

        val details = EdupageEventDetails.describe(listOf(minigolf), listOf(note))

        assertEquals("18.11.2026\n$tripMessage", details["e1"])
    }

    @Test
    fun `the name is matched without case or diacritics`() {
        val note = Note(LocalDate(2026, 11, 10), "Ve čtvrtek SVITICI  MINIGOLF, prosím o svačinu.")

        assertTrue(EdupageEventDetails.describe(listOf(minigolf), listOf(note))["e1"]!!.contains("svačinu"))
    }

    @Test
    fun `a post on the event's own day or later is not attached`() {
        val notes = listOf(
            Note(LocalDate(2026, 11, 19), "Dnešní výlet na svítící minigolf se vydařil."),
            Note(LocalDate(2026, 11, 20), "Svítící minigolf byl skvělý.")
        )

        assertNull(EdupageEventDetails.describe(listOf(minigolf), notes)["e1"])
    }

    @Test
    fun `a digest gives only the paragraph that names the event`() {
        val digest = "Milí rodiče,\n\nVánoční jarmark bude 2. 12. 2026 ve třídě 5. B, můžete přispět výrobkem.\n\n" +
            "Bruslení na ledu bude 21. 12. a 27. 1., s sebou brusle a helmu."
        val details = EdupageEventDetails.describe(listOf(market), listOf(Note(LocalDate(2026, 11, 20), digest)))

        assertEquals(
            "20.11.2026\nVánoční jarmark bude 2. 12. 2026 ve třídě 5. B, můžete přispět výrobkem.",
            details["e4"]
        )
    }

    @Test
    fun `two events of one name need the date - and get only their own`() {
        val decOnly = Note(LocalDate(2026, 12, 1), "Bruslení na ledu 21.12.: s sebou brusle a helmu.")
        val noDate = Note(LocalDate(2026, 12, 1), "Na bruslení na ledu nezapomeňte rukavice.")

        val details = EdupageEventDetails.describe(listOf(skatingDec, skatingJan), listOf(decOnly, noDate))

        assertEquals("1.12.2026\nBruslení na ledu 21.12.: s sebou brusle a helmu.", details["e2"])
        assertNull(details["e3"])
    }

    @Test
    fun `an inflected name is not a match`() {
        val note = Note(LocalDate(2026, 11, 20), "Zamluvila jsem bruslení na Sofijském náměstí 21. 12.")
        val target = Target("e5", "Bruslení na ledu - Sofijské náměstí", LocalDate(2026, 12, 21))

        assertNull(EdupageEventDetails.describe(listOf(target), listOf(note))["e5"])
    }

    @Test
    fun `a published fee is the school's own sentence and must name the event's date if any`() {
        val fee = Note(
            LocalDate(2026, 11, 2),
            "Byl zveřejněn poplatek za Svítící minigolf 19.11.2026.",
            feePlan = "Svítící minigolf 19.11.2026"
        )
        val otherDay = Note(
            LocalDate(2026, 11, 2),
            "Byl zveřejněn poplatek za Svítící minigolf 3.12.2026.",
            feePlan = "Svítící minigolf 3.12.2026"
        )

        val details = EdupageEventDetails.describe(listOf(minigolf), listOf(fee, otherDay))

        assertEquals("Byl zveřejněn poplatek za Svítící minigolf 19.11.2026.", details["e1"])
    }

    @Test
    fun `several messages are oldest first - and a very long one is cut`() {
        val long = "Svítící minigolf: " + "slovo ".repeat(600)
        val details = EdupageEventDetails.describe(
            listOf(minigolf),
            listOf(Note(LocalDate(2026, 11, 18), tripMessage), Note(LocalDate(2026, 11, 1), long))
        )["e1"]!!

        assertTrue(details.startsWith("1.11.2026\nSvítící minigolf: slovo"))
        assertTrue(details.length <= EdupageEventDetails.MAX_LENGTH)
    }

    @Test
    fun `a newsletter that names the event only further down gives that paragraph`() {
        val newsletter = "Milí rodiče,\n\n" + "Posílám informace z prvního týdne. ".repeat(10) + "\n\n\n\n" +
            "Ve čtvrtek jdeme na svítící minigolf, od 8:50.\n\nPřeji krásný víkend."

        val note = Note(LocalDate(2026, 11, 13), newsletter)

        val details = EdupageEventDetails.describe(listOf(minigolf), listOf(note))

        assertEquals("13.11.2026\nVe čtvrtek jdeme na svítící minigolf, od 8:50.", details["e1"])
    }

    // The parser's side: which timeline items become notes.

    private fun item(typ: String, user: String, text: String, sent: String, data: String = "{}") =
        """{"timelineid":"$typ-$sent-$user","typ":"$typ","user":"$user","user_meno":"x","text":${JsonPrimitive(text)},
        "cas_pridania":"$sent 10:00:00","cas_udalosti":null,"removed":0,"data":${JsonPrimitive(data)}}"""

    private fun event(id: String, date: String) =
        """{"timelineid":"$id","typ":"event","user":"yd2026@Trieda-3","user_meno":"2.B","text":"Udalosť: x",
        "cas_pridania":"2026-11-01 10:00:00","cas_udalosti":"$date 00:00:00","removed":0,
        "data":${JsonPrimitive("""{"name":"Svítící minigolf","allday":true}""")}}"""

    private fun timeline(vararg items: String) = """{"timelineItems":[${items.joinToString(",")}],
        "childGroups":{"-80":["*","Student-80","Trieda-3","Rodicko-3"]}}"""

    private val child = EdupageChild("-80", "Eva Nováková", "2.B")

    @Test
    fun `the parser quotes class and school messages but never one to this parent alone`() {
        val result = EdupageTimelineParser.parse(
            timeline(
                event("ev1", "2026-11-19"),
                item("sprava", "yd2026@Rodicko-3", "Třída: svítící minigolf, s sebou svačinu.", "2026-11-17"),
                item("sprava", "Rodic*", "Škola: svítící minigolf je zdarma.", "2026-11-16"),
                item("sprava", "Rodic-60", "Osobně: svítící minigolf, vaše dcera…", "2026-11-15"),
                item("sprava", "yd2026@Rodicko-4", "Jiná třída: svítící minigolf.", "2026-11-14")
            ),
            child
        )
        val description = result.events.single().description!!

        assertTrue("s sebou svačinu" in description)
        assertTrue("je zdarma" in description)
        assertTrue("Osobně" !in description && "Jiná třída" !in description)
    }

    @Test
    fun `a withdrawn fee attaches nothing`() {
        val withdrawn = item(
            typ = "payments",
            user = "Student-80",
            text = "Poplatek za Svítící minigolf 19.11.2026 byl odstraněn.",
            sent = "2026-11-10",
            data = """{"ls":7812,"strReplaces":{"{payment_plan}":"Svítící minigolf 19.11.2026"}}"""
        )

        val result = EdupageTimelineParser.parse(timeline(event("ev1", "2026-11-19"), withdrawn), child)

        assertNull(result.events.single().description)
    }
}
