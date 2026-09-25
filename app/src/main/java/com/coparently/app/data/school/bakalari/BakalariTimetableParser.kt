package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.bakalari.BakalariJson.obj
import com.coparently.app.data.school.bakalari.BakalariJson.objects
import com.coparently.app.data.school.bakalari.BakalariJson.text
import com.coparently.app.domain.school.SchoolDay
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolWeek
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads `GET /api/3/timetable/actual?date=` into the days the import needs (MON-8).
 *
 * Only arrival and pickup are kept — the first lesson's start and the last lesson's end — and
 * whether the day has any timetable cells at all. Subjects, teachers, rooms and homework are
 * dropped here and never stored: the owner imports school hours, not lessons.
 *
 * Four traps, each from the API research:
 * - **The week that comes back may not be the week asked for.** Asked for a date outside the
 *   school year, the server answers with the first school week or the last week of the summer
 *   holidays. Every day is checked against the requested week and a day outside it is dropped.
 * - **`Hours[]` is the source of truth for times.** `Change.Time` can disagree with it and
 *   nothing documents which slot it describes.
 * - **`HourId` is a number on one server and a string on another**, and ids can carry leading
 *   spaces. Both are read as text and joined verbatim.
 * - **A removed or cancelled lesson is not a lesson.** `Removed` and `Canceled` changes are
 *   excluded, and so, defensively, is a cell with no subject and no change abbreviation that was
 *   not added.
 */
object BakalariTimetableParser {

    private const val REMOVED = "Removed"
    private const val CANCELED = "Canceled"
    private const val ADDED = "Added"
    private val TIME = DateTimeFormatter.ofPattern("H:mm")
    private const val DAYS_IN_WEEK = 7L

    /**
     * The week holding [requested], as far as [json] describes it.
     *
     * @throws BakalariException.Malformed when [json] is not a JSON object.
     */
    fun parse(json: String, requested: LocalDate): SchoolWeek {
        val root = BakalariJson.objectOf(json)
        val monday = SchoolWeek.mondayOf(requested)
        val sunday = monday.plusDays(DAYS_IN_WEEK - 1)
        val hours = root.objects("Hours").mapNotNull { hour ->
            val id = hour.text("Id") ?: return@mapNotNull null
            val begin = time(hour.text("BeginTime")) ?: return@mapNotNull null
            val end = time(hour.text("EndTime")) ?: return@mapNotNull null
            id to (begin to end)
        }.toMap()
        val days = root.objects("Days").mapNotNull { day ->
            val date = BakalariJson.wallClock(day.text("Date"))?.toLocalDate() ?: return@mapNotNull null
            if (date.isBefore(monday) || date.isAfter(sunday)) return@mapNotNull null
            dayOf(day, date, hours)
        }
        return SchoolWeek(monday, days.distinctBy { it.date }.sortedBy { it.date })
    }

    private fun dayOf(day: JsonObject, date: LocalDate, hours: Map<String, Pair<LocalTime, LocalTime>>): SchoolDay {
        val atoms = day.objects("Atoms")
        val slots = atoms.filter(::takesPlace).mapNotNull { atom -> atom.text("HourId")?.let(hours::get) }
        return SchoolDay(
            date = date,
            type = SchoolDayType.of(day.text("DayType")),
            description = day.text("DayDescription").orEmpty(),
            hasAtoms = atoms.isNotEmpty(),
            firstLessonStart = slots.minOfOrNull { it.first },
            lastLessonEnd = slots.maxOfOrNull { it.second }
        )
    }

    /** Whether a timetable cell is a lesson the child attends; see the object's KDoc. */
    private fun takesPlace(atom: JsonObject): Boolean {
        val change = atom.obj("Change")
        val changeType = change?.text("ChangeType")
        if (changeType == REMOVED || changeType == CANCELED) return false
        val noSubject = atom.text("SubjectId").isNullOrBlank()
        val noAbbreviation = change?.text("TypeAbbrev").isNullOrBlank()
        return !(noSubject && noAbbreviation && changeType != ADDED)
    }

    private fun time(text: String?): LocalTime? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalTime.parse(value, TIME)
        } catch (ignored: DateTimeParseException) {
            runCatching { LocalTime.parse(value) }.getOrNull()
        }
    }
}
