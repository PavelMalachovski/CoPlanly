package com.coparently.app.data.school.edupage

import com.coparently.app.domain.chat.TextFold
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the school's own messages say about an event, quoted into its description
 * (design `docs/superpowers/specs/2026-11-21-edupage-event-details-design.md`).
 *
 * EduPage's event carries no description: what to bring and when to meet is written in the class's
 * messages and noticeboard, often one digest for several events, and a fee is an item of its own.
 * Nothing links them to an event but the text, so a note is attached **only on an exact match**
 * (owner decision: better a missing detail than somebody else's):
 * - the note was sent on a day before the event's;
 * - its folded text (lower case, no diacritics, whitespace collapsed) holds the event's whole folded
 *   name;
 * - when another event of the child has the same name, it also names this event's date.
 *
 * A message that names the event in its opening and no date but the event's own is quoted whole;
 * any other (a digest, a newsletter that mentions the event further down) gives only the paragraph
 * holding the name. Each is headed by the day it was sent; a fee is the
 * school's own sentence. Nothing of ours is written, so the description reads in the school's
 * language on both parents' phones. Pure, so every rule is a test.
 */
internal object EdupageEventDetails {

    /** An event the details are for. */
    class Target(val id: String, val name: String, val date: LocalDate)

    /** A message, a noticeboard post or a published fee, as the timeline holds it. */
    class Note(val sent: LocalDate, val text: String, val feePlan: String? = null)

    /** The longest description written; cut at a paragraph, else at a word. */
    const val MAX_LENGTH = 2000

    private const val MIN_NAME_LENGTH = 4
    private const val LONG_PARAGRAPH = 600
    private const val OPENING_LENGTH = 250

    /** A date written `d.m.`, `d. m.`, optionally with the year: "2. 12. 2026", "19.11.". */
    private val DATE = Regex("""(?<![\d.])(\d{1,2})\.\s?(\d{1,2})\.(?:\s?(\d{4}))?""")
    private val SPACES = Regex(" +")
    private val PARAGRAPH = Regex("""\n\s*\n""")
    private val BLANK_LINES = Regex("""\n{3,}""")

    /** The description of each target that a note is about, by target id. */
    fun describe(targets: List<Target>, notes: List<Note>): Map<String, String> {
        val names = targets.groupBy { fold(it.name) }
        return targets.mapNotNull { target ->
            val name = fold(target.name)
            if (name.length < MIN_NAME_LENGTH) return@mapNotNull null
            val needsDate = names[name].orEmpty().any { it.date != target.date }
            val messages = notes.filter { it.feePlan == null && it.sent < target.date }
                .filter { matches(it.text, name, target.date, needsDate) }
                .sortedWith(compareBy({ it.sent }, { it.text }))
                .map { "${dayText(it.sent)}\n${excerpt(it.text, name, target.date)}" }
            val fees = notes.filter { it.feePlan != null && feeMatches(it.feePlan, name, target.date, needsDate) }
                .sortedWith(compareBy({ it.sent }, { it.text }))
                .map { it.text.trim() }
            val parts = (messages + fees).distinct()
            if (parts.isEmpty()) null else target.id to capped(parts)
        }.toMap()
    }

    private fun matches(text: String, name: String, date: LocalDate, needsDate: Boolean): Boolean =
        fold(text).contains(name) && (!needsDate || datesIn(text).any { it.sameDayAs(date) })

    private fun feeMatches(plan: String, name: String, date: LocalDate, needsDate: Boolean): Boolean {
        if (!fold(plan).contains(name)) return false
        val dates = datesIn(plan)
        return if (dates.isEmpty()) !needsDate else dates.all { it.sameDayAs(date) }
    }

    /** The whole text when it names no other date; otherwise the paragraph (or line) with the name. */
    private fun excerpt(text: String, name: String, date: LocalDate): String {
        val clean = tidy(text)
        // Whole only when the message is about this event: it names no other date, and names the
        // event in its opening (a class newsletter that mentions the event at the end is a digest).
        val opening = fold(clean.take(OPENING_LENGTH))
        if (opening.contains(name) && datesIn(clean).all { it.sameDayAs(date) }) return clean
        val paragraph = clean.split(PARAGRAPH).firstOrNull { fold(it).contains(name) }?.trim() ?: clean
        if (paragraph.length <= LONG_PARAGRAPH) return paragraph
        return paragraph.lines().firstOrNull { fold(it).contains(name) }?.trim() ?: paragraph
    }

    /** Every line trimmed, and no more than one blank line in a row. */
    private fun tidy(text: String): String =
        text.replace("\r\n", "\n").lines().joinToString("\n") { it.trim() }.replace(BLANK_LINES, "\n\n").trim()

    private fun capped(parts: List<String>): String {
        val out = StringBuilder()
        for (part in parts) {
            val next = if (out.isEmpty()) part else "\n\n$part"
            if (out.length + next.length <= MAX_LENGTH) {
                out.append(next)
            } else {
                if (out.isEmpty()) out.append(cutAtWord(part))
                break
            }
        }
        return out.toString()
    }

    private fun cutAtWord(text: String): String {
        val head = text.take(MAX_LENGTH - 1)
        val end = head.lastIndexOf(' ').takeIf { it > MAX_LENGTH / 2 } ?: head.length
        return head.take(end).trimEnd() + "…"
    }

    private class WrittenDate(val day: Int, val month: Int, val year: Int?) {
        fun sameDayAs(date: LocalDate): Boolean =
            day == date.day && month == date.month.number && (year == null || year == date.year)
    }

    private fun datesIn(text: String): List<WrittenDate> = DATE.findAll(text).mapNotNull { match ->
        val (dayText, monthText, yearText) = match.destructured
        val day = dayText.toInt()
        val month = monthText.toInt()
        val year = yearText.takeIf { it.isNotEmpty() }?.toInt()
        if (day in 1..MAX_DAY && month in 1..MAX_MONTH) WrittenDate(day, month, year) else null
    }.toList()

    private fun dayText(date: LocalDate): String = "${date.day}.${date.month.number}.${date.year}"

    private fun fold(text: String): String = TextFold.simple(text).replace(SPACES, " ").trim()

    private const val MAX_DAY = 31
    private const val MAX_MONTH = 12
}

/** Which timeline items are notes for [EdupageEventDetails], and what each says. */
internal object EdupageNotes {

    /**
     * The note an item of the timeline is, or null: a message or noticeboard post (`sprava`,
     * `nastenka`) or a published fee (`payments` with `ls` [FEE_PUBLISHED], recorded live; a
     * withdrawn fee is another string). [audienceIsTheChilds] says the item was sent to the child's
     * groups or to all the school's parents, never to this parent alone.
     */
    fun noteOf(
        typ: String?,
        audienceIsTheChilds: Boolean,
        sentAt: String?,
        text: String?,
        data: JsonObject?
    ): EdupageEventDetails.Note? {
        val sent = EdupageJson.date(sentAt?.trim()?.substringBefore(' '))
        val body = text?.trim()?.takeIf(String::isNotBlank)
        val wanted = typ in NOTE_TYPES && audienceIsTheChilds
        if (!wanted || sent == null || body == null) return null
        val plan = if (typ == FEE_TYPE) feePlanOf(data) ?: return null else null
        return EdupageEventDetails.Note(sent, body, feePlan = plan)
    }

    private fun feePlanOf(data: JsonObject?): String? {
        val published = (data?.get("ls") as? JsonPrimitive)?.content == FEE_PUBLISHED
        val plan = ((data?.get("strReplaces") as? JsonObject)?.get(FEE_PLAN_KEY) as? JsonPrimitive)?.content
        return plan?.takeIf { published && it.isNotBlank() }
    }

    private const val FEE_TYPE = "payments"
    private val NOTE_TYPES = setOf("sprava", "nastenka", FEE_TYPE)
    private const val FEE_PUBLISHED = "7814"
    private const val FEE_PLAN_KEY = "{payment_plan}"
}
