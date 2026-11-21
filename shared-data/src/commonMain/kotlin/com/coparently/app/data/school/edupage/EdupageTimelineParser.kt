package com.coparently.app.data.school.edupage

import com.coparently.app.data.school.edupage.EdupageJson.entry
import com.coparently.app.data.school.edupage.EdupageJson.items
import com.coparently.app.data.school.edupage.EdupageJson.obj
import com.coparently.app.data.school.edupage.EdupageJson.text
import com.coparently.app.domain.school.SchoolDay
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolEventTime
import com.coparently.app.domain.school.SchoolTask
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Reads EduPage's timeline answer (`timelineItems`) for one pupil. */
object EdupageTimelineParser {

    /** What a timeline holds for the pupil: [events], dated [tasks] and [daysOff]. */
    data class Result(val events: List<SchoolEvent>, val tasks: List<SchoolTask>, val daysOff: List<SchoolDay>)

    private val EVENT_TYPES =
        setOf("event", "schoolevent", "trip", "excursion", "culture", "parentsevening", "ctevent")
    private val TASK_TYPES = setOf("homework", "bexam", "sexam", "oexam", "rexam", "pexam", "testing")
    private val DAY_OFF_TYPES = setOf("freeday", "holiday", "sholiday")
    private val WHOLE_SCHOOL = setOf("*", "celáškola")

    private class Stamp(val date: LocalDate, val time: LocalTime, val raw: String)

    /**
     * The events, tasks and days off in [json] that are meant for [child].
     *
     * An item is the child's when its `user` (the audience EduPage addressed it to: `Trieda-12`,
     * `Student-345`, `CustPlan678`, `*`) is one of the answer's `childGroups` for the child, as
     * recorded live (November 2026). An answer without them falls back to the audience's name
     * (`user_meno`) matched against the class, the child's name or the whole school.
     *
     * @throws EdupageException.Server when [json] is not an object holding `timelineItems` (at the
     *   top or under `r`): an unreadable answer is not an empty timeline.
     */
    fun parse(json: String, child: EdupageChild): Result {
        val root = EdupageJson.parseOrNull(json) as? JsonObject
        val holder = listOfNotNull(root, root?.obj("r")).firstOrNull { "timelineItems" in it }
            ?: throw EdupageException.Server(OK_STATUS)
        val book = Book.of(holder, child)
        val items = holder.items("timelineItems")
        val events = ArrayList<SchoolEvent>()
        val tasks = ArrayList<SchoolTask>()
        val daysOff = ArrayList<SchoolDay>()
        items.filter { it.text("removed") !in REMOVED && book.isForChild(it) }
            .mapNotNull { item -> stampOf(item)?.let { item to it } }
            .forEach { (item, stamp) ->
                when (item.text("typ")) {
                    in EVENT_TYPES -> eventOf(item, stamp, book)?.let(events::add)
                    in TASK_TYPES -> taskOf(item, stamp, book)?.let(tasks::add)
                    in DAY_OFF_TYPES -> daysOff.add(dayOffOf(item, stamp))
                }
            }
        return Result(events, tasks, daysOff)
    }

    /** What the answer says beside its items: the child's groups, the bell times and the subjects. */
    private class Book(
        val child: EdupageChild,
        val groups: Set<String>?,
        val periods: Map<String, Pair<LocalTime, LocalTime>>,
        val dbi: JsonObject?
    ) {
        fun isForChild(item: JsonObject): Boolean =
            if (groups != null) inGroups(item.text("user"), groups) else byName(item.text("user_meno"))

        /**
         * The timeline's own groups name the class without the school year EduPage puts on an event's
         * audience (`yd2026@Trieda-12` against `Trieda-12`; recorded live).
         */
        private fun inGroups(user: String?, groups: Set<String>): Boolean =
            user != null && (user in groups || user.replace(SCHOOL_YEAR_PREFIX, "") in groups)

        private fun byName(audience: String?): Boolean {
            val target = squash(audience ?: return false)
            return target in WHOLE_SCHOOL ||
                target == squash(child.name) ||
                (child.className.isNotBlank() && target == squash(child.className))
        }

        fun subjectName(id: String?): String? =
            id?.let { dbi?.entry("subjects", it)?.text("name") }?.takeIf(String::isNotBlank)

        companion object {
            fun of(holder: JsonObject, child: EdupageChild): Book {
                val groups = (holder.obj("childGroups")?.get(child.studentId) as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                val dbi = holder.obj("dbi")
                val periods = dbi?.items("periods").orEmpty().mapNotNull { row ->
                    val id = row.text("id") ?: return@mapNotNull null
                    val start = EdupageJson.time(row.text("starttime")) ?: return@mapNotNull null
                    val end = EdupageJson.time(row.text("endtime")) ?: return@mapNotNull null
                    id to (start to end)
                }.toMap()
                return Book(child, groups, periods, dbi)
            }
        }
    }

    private fun squash(text: String): String = text.filterNot { it.isWhitespace() }.lowercase()

    private fun stampOf(item: JsonObject): Stamp? {
        val raw = item.text("cas_udalosti")?.trim() ?: return null
        val parts = raw.split(' ', limit = 2)
        val date = EdupageJson.date(parts[0])
        val time = if (parts.size > 1) EdupageJson.time(parts[1]) else LocalTime(0, 0)
        return if (date != null && time != null) Stamp(date, time, raw) else null
    }

    private fun dataOf(item: JsonObject): JsonObject? = when (val data = item["data"]) {
        is JsonObject -> data
        null -> null
        else -> item.text("data")?.let { EdupageJson.parseOrNull(it) as? JsonObject }
    }

    private fun titleOf(item: JsonObject): String =
        item.text("text")?.takeIf { it.isNotBlank() } ?: dataOf(item)?.text("nazov").orEmpty()

    private fun eventOf(item: JsonObject, stamp: Stamp, book: Book): SchoolEvent? {
        val id = item.text("timelineid") ?: return null
        val data = dataOf(item)
        if (data?.text("cancelled") == "true") return null
        val title = data?.text("name")?.trim()?.takeIf(String::isNotBlank) ?: titleOf(item)
        val time = periodTime(data, stamp, book) ?: if (stamp.time == LocalTime(0, 0)) {
            val end = stamp.date.atTime(WHOLE_DAY_END_HOUR, WHOLE_DAY_END_MINUTE)
            SchoolEventTime(true, stamp.date.atTime(0, 0), end, stamp.raw)
        } else {
            SchoolEventTime(false, LocalDateTime(stamp.date, stamp.time), null, stamp.raw)
        }
        return SchoolEvent(id, title, null, listOf(time), emptyList(), emptyList())
    }

    /**
     * The event's time from its lesson periods: `data.period` and `data.durationperiods` (recorded
     * live, November 2026: a photo session "from period 1 for 5 periods"), mapped through the answer's
     * bell times (`dbi.periods`). Null when the event is all-day or names no period the bells know.
     */
    private fun periodTime(data: JsonObject?, stamp: Stamp, book: Book): SchoolEventTime? {
        val first = data?.takeIf { it.text("allday") != "true" }?.text("period")?.toIntOrNull()
        val count = data?.text("durationperiods")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val start = first?.let { book.periods["$it"]?.first }
        val end = first?.let { book.periods["${it + count - 1}"]?.second }
        return if (start != null && end != null) {
            SchoolEventTime(false, LocalDateTime(stamp.date, start), LocalDateTime(stamp.date, end), stamp.raw)
        } else {
            null
        }
    }

    private fun taskOf(item: JsonObject, stamp: Stamp, book: Book): SchoolTask? {
        val id = item.text("timelineid") ?: return null
        val data = dataOf(item)
        val subject = data?.text("predmet")?.takeIf { it.isNotBlank() } ?: book.subjectName(data?.text("predmetid"))
        val name = data?.text("nazov")?.takeIf(String::isNotBlank)?.let { withoutChangeMark(it, data) }
        val title = (name ?: titleOf(item)).trim()
        return SchoolTask(id, stamp.date, if (subject != null) "$subject: $title" else title, null)
    }

    /**
     * [name] without the one-word mark EduPage puts before an edited homework's name in the school's
     * language ("Změněné: PS str.72/1,2" with `zmena: true`; recorded live), so a teacher's edit does not
     * rename the event in the calendar. Only when `zmena` says so, and only a single word before ": ".
     */
    private fun withoutChangeMark(name: String, data: JsonObject): String {
        if (data.text("zmena") != "true") return name
        val mark = name.substringBefore(": ", missingDelimiterValue = "")
        val single = mark.isNotEmpty() && mark.length <= CHANGE_MARK_MAX && mark.none { it.isWhitespace() }
        return if (single) name.substring(mark.length + 2).ifBlank { name } else name
    }

    private fun dayOffOf(item: JsonObject, stamp: Stamp): SchoolDay =
        SchoolDay(stamp.date, SchoolDayType.HOLIDAY, titleOf(item), false, null, null)

    private val REMOVED = setOf("1", "true")
    private const val CHANGE_MARK_MAX = 20
    private val SCHOOL_YEAR_PREFIX = Regex("^yd\\d+@")
    private const val OK_STATUS = 200
    private const val WHOLE_DAY_END_HOUR = 23
    private const val WHOLE_DAY_END_MINUTE = 59
}
