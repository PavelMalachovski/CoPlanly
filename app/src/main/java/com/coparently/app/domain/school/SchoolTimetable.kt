package com.coparently.app.domain.school

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * What a school's timetable says a day is (MON-8).
 *
 * Bakaláři's `DayType`, which **lies in both directions**: a `Holiday` can come with a full set of
 * lessons, and a day can arrive with a value no documentation lists. So this is never read
 * alone — [SchoolDay.hasLessons] decides whether there is school, and the type only names a day
 * that has none. An unlisted value is [UNKNOWN], which counts as a day off when it has no lessons.
 */
enum class SchoolDayType {
    /** A school day. */
    WORK_DAY,

    /** Saturday or Sunday; the server rarely sends one. */
    WEEKEND,

    /** A public holiday or another day of note. */
    CELEBRATION,

    /** School holidays that are not a public holiday. */
    HOLIDAY,

    /** The head teacher's day off (*ředitelské volno*). */
    DIRECTOR_DAY,

    /** The server's own "something is wrong with this timetable". Never imported. */
    UNDEFINED,

    /** A value this build does not know. */
    UNKNOWN;

    companion object {
        /** The type Bakaláři's [raw] `DayType` names. */
        fun of(raw: String?): SchoolDayType = when (raw?.trim()) {
            "WorkDay" -> WORK_DAY
            "Weekend" -> WEEKEND
            "Celebration" -> CELEBRATION
            "Holiday" -> HOLIDAY
            "DirectorDay" -> DIRECTOR_DAY
            "Undefined" -> UNDEFINED
            else -> UNKNOWN
        }
    }
}

/**
 * One day of a child's actual timetable, reduced to what the import uses.
 *
 * @property date The day, in the school's own calendar.
 * @property type What the server called it; see [SchoolDayType] for why it decides little.
 * @property description The server's name for the day ("Nový rok"), or blank.
 * @property hasAtoms Whether the server sent any timetable cells at all, cancelled ones included.
 *   A day off is a day with **no** cells; a day whose every lesson was cancelled is not one.
 * @property firstLessonStart When the first lesson that still takes place begins, or null.
 * @property lastLessonEnd When the last lesson that still takes place ends, or null.
 */
data class SchoolDay(
    val date: LocalDate,
    val type: SchoolDayType,
    val description: String,
    val hasAtoms: Boolean,
    val firstLessonStart: LocalTime?,
    val lastLessonEnd: LocalTime?
) {
    /** Whether the child has at least one lesson that day. */
    val hasLessons: Boolean get() = firstLessonStart != null && lastLessonEnd != null
}

/**
 * One week of a timetable as it was asked for.
 *
 * @property monday The Monday of the week that was requested.
 * @property days Only the days that fall in that week. A server asked for a week outside the
 *   school year answers with a different one, and those days are dropped by the parser rather
 *   than trusted — see `BakalariTimetableParser`.
 */
data class SchoolWeek(
    val monday: LocalDate,
    val days: List<SchoolDay>
) {
    companion object {
        /** The Monday of the week holding [date]. */
        fun mondayOf(date: LocalDate): LocalDate =
            date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    }
}
