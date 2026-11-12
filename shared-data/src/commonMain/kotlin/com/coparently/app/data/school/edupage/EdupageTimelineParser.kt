package com.coparently.app.data.school.edupage

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
import kotlinx.serialization.json.JsonObject

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

    /** The events, tasks and days off in [json] that are meant for [child]. */
    fun parse(json: String, child: EdupageChild): Result {
        val root = EdupageJson.parseOrNull(json) as? JsonObject
        val items = (root?.items("timelineItems") ?: root?.obj("r")?.items("timelineItems")).orEmpty()
        val events = ArrayList<SchoolEvent>()
        val tasks = ArrayList<SchoolTask>()
        val daysOff = ArrayList<SchoolDay>()
        for (item in items) {
            if (item.text("removed") == "1" || !isForChild(item, child)) continue
            val stamp = stampOf(item) ?: continue
            when (item.text("typ")) {
                in EVENT_TYPES -> eventOf(item, stamp)?.let(events::add)
                in TASK_TYPES -> taskOf(item, stamp)?.let(tasks::add)
                in DAY_OFF_TYPES -> daysOff.add(dayOffOf(item, stamp))
            }
        }
        return Result(events, tasks, daysOff)
    }

    private fun isForChild(item: JsonObject, child: EdupageChild): Boolean {
        val target = squash(item.text("user_meno") ?: return false)
        return target in WHOLE_SCHOOL ||
            target == squash(child.name) ||
            (child.className.isNotBlank() && target == squash(child.className))
    }

    private fun squash(text: String): String = text.filterNot { it.isWhitespace() }.lowercase()

    private fun stampOf(item: JsonObject): Stamp? {
        val raw = item.text("cas_udalosti")?.trim() ?: return null
        val parts = raw.split(' ', limit = 2)
        val date = EdupageJson.date(parts[0]) ?: return null
        val time = if (parts.size > 1) EdupageJson.time(parts[1]) ?: return null else LocalTime(0, 0)
        return Stamp(date, time, raw)
    }

    private fun dataOf(item: JsonObject): JsonObject? = when (val data = item["data"]) {
        is JsonObject -> data
        null -> null
        else -> item.text("data")?.let { EdupageJson.parseOrNull(it) as? JsonObject }
    }

    private fun titleOf(item: JsonObject): String =
        item.text("text")?.takeIf { it.isNotBlank() } ?: dataOf(item)?.text("nazov").orEmpty()

    private fun eventOf(item: JsonObject, stamp: Stamp): SchoolEvent? {
        val id = item.text("timelineid") ?: return null
        val wholeDay = stamp.time == LocalTime(0, 0)
        val time = if (wholeDay) {
            val end = stamp.date.atTime(WHOLE_DAY_END_HOUR, WHOLE_DAY_END_MINUTE)
            SchoolEventTime(true, stamp.date.atTime(0, 0), end, stamp.raw)
        } else {
            SchoolEventTime(false, LocalDateTime(stamp.date, stamp.time), null, stamp.raw)
        }
        return SchoolEvent(id, titleOf(item), null, listOf(time), emptyList(), emptyList())
    }

    private fun taskOf(item: JsonObject, stamp: Stamp): SchoolTask? {
        val id = item.text("timelineid") ?: return null
        val subject = dataOf(item)?.text("predmet")?.takeIf { it.isNotBlank() }
        val title = titleOf(item)
        return SchoolTask(id, stamp.date, if (subject != null) "$subject: $title" else title, null)
    }

    private fun dayOffOf(item: JsonObject, stamp: Stamp): SchoolDay =
        SchoolDay(stamp.date, SchoolDayType.HOLIDAY, titleOf(item), false, null, null)

    private const val WHOLE_DAY_END_HOUR = 23
    private const val WHOLE_DAY_END_MINUTE = 59
}
