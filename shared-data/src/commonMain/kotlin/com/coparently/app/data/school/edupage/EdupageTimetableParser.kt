package com.coparently.app.data.school.edupage

import com.coparently.app.data.school.edupage.EdupageJson.items
import com.coparently.app.data.school.edupage.EdupageJson.obj
import com.coparently.app.data.school.edupage.EdupageJson.text
import com.coparently.app.domain.school.SchoolDay
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Reads EduPage's timetable answer (`r.ttitems`) into the school import's weeks. */
object EdupageTimetableParser {

    private const val OK_STATUS = 200

    private class Span(val start: LocalTime, val end: LocalTime)

    /**
     * The weeks covering [from] to [to]. A day holds its first lesson's start and last lesson's end;
     * a weekday in range with no lesson is a work day without hours. Only a present but empty
     * `r.ttitems` means "no lessons": an answer that is not a JSON object, has no `r` object or no
     * `ttitems` is unreadable, and reading it as an empty week would delete the school-hours events.
     *
     * @throws EdupageException.Server when EduPage answered with `r.error`, or with an answer that
     *   holds no timetable.
     */
    fun weeks(json: String, from: LocalDate, to: LocalDate): List<SchoolWeek> {
        val result = (EdupageJson.parseOrNull(json) as? JsonObject)?.obj("r")?.takeIf { readable(it) }
            ?: throw EdupageException.Server(OK_STATUS)
        val spans = lessonSpans(result.items("ttitems"))
        val days = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= to }
            .mapNotNull { dayOf(it, spans[it]) }
            .toList()
        return days.groupBy { SchoolWeek.mondayOf(it.date) }
            .map { (monday, weekDays) -> SchoolWeek(monday, weekDays) }
            .sortedBy { it.monday }
    }

    /** An answer with no `r.error` and a `ttitems` key (present, possibly empty) can be read. */
    private fun readable(result: JsonObject): Boolean {
        val error = result["error"]
        return (error == null || error is JsonNull) && "ttitems" in result
    }

    private fun lessonSpans(items: List<JsonObject>): Map<LocalDate, Span> {
        val spans = HashMap<LocalDate, Span>()
        items.filter { it.text("type") == "lesson" && it.text("removed") !in REMOVED }
            .forEach { item ->
                val date = EdupageJson.date(item.text("date"))
                val start = EdupageJson.time(item.text("starttime"))
                val end = EdupageJson.time(item.text("endtime"))
                if (date != null && start != null && end != null) {
                    val known = spans[date]
                    spans[date] = known?.let { Span(minOf(it.start, start), maxOf(it.end, end)) } ?: Span(start, end)
                }
            }
        return spans
    }

    private fun dayOf(date: LocalDate, span: Span?): SchoolDay? {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        if (span == null && weekend) return null
        return SchoolDay(date, SchoolDayType.WORK_DAY, "", false, span?.start, span?.end)
    }

    private val REMOVED = setOf("true", "1")
}
