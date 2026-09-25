package com.coparently.app.domain.school

import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * The four fields an import owns on an event; everything else on the event is the parent's.
 *
 * @property title The title.
 * @property description The description, or null.
 * @property start The start, in the school's wall clock.
 * @property end The end, or null for a single all-day date.
 */
internal data class ImportFields(
    val title: String,
    val description: String?,
    val start: LocalDateTime,
    val end: LocalDateTime?
) {
    /** A short hash of the four fields, recorded to tell the school's edits from the parent's. */
    val fingerprint: String
        get() = SchoolImportIds.sha256Hex(listOf(title, description.orEmpty(), start, end ?: "").joinToString("\u0000"))
            .take(FINGERPRINT_LENGTH)

    private companion object {
        const val FINGERPRINT_LENGTH = 16
    }
}

/**
 * One calendar event the school's data asks for.
 *
 * @property id Its deterministic id.
 * @property kind What it is made from.
 * @property lastDate Its last day, for the connection's record.
 * @property fields What it says.
 */
internal data class ImportItem(
    val id: String,
    val kind: SchoolImportKind,
    val lastDate: LocalDate,
    val fields: ImportFields
)

/** The ids of imported events (MON-8); see [SchoolImportPlanner.idOf]. */
internal object SchoolImportIds {
    private const val PREFIX = "school-"
    private const val HASH_LENGTH = 32

    /** The id of the event of [kind] under [key]. */
    fun of(target: SchoolImportTarget, kind: SchoolImportKind, key: String): String =
        PREFIX + sha256Hex("bakalari|${target.baseUrl}|${target.username}|${kind.stored}|$key").take(HASH_LENGTH)

    /** The id of [date]'s school-hours event. */
    fun hours(target: SchoolImportTarget, date: LocalDate): String = of(target, SchoolImportKind.HOURS, date.toString())

    /** The id of the day-off run that starts on [date]. */
    fun dayOff(target: SchoolImportTarget, date: LocalDate): String =
        of(target, SchoolImportKind.DAY_OFF, date.toString())

    /** [text]'s SHA-256, in lower-case hex. */
    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
}

/**
 * What the school's data asks the calendar for (MON-8) — the half of [SchoolImportPlanner] that
 * reads the timetable and the events, before anything is compared with Room.
 */
internal object SchoolImportItems {

    private val DAY_OFF_TYPES = setOf(
        SchoolDayType.HOLIDAY,
        SchoolDayType.DIRECTOR_DAY,
        SchoolDayType.CELEBRATION,
        SchoolDayType.UNKNOWN
    )

    /** Everything the school's data asks for, one item per id. */
    fun all(input: SchoolImportInput): List<ImportItem> =
        (events(input) + hours(input) + daysOff(input)).distinctBy { it.id }

    /** The fetched days from today on, inside the window, one per date, in order. */
    fun daysInWindow(input: SchoolImportInput): List<SchoolDay> {
        val end = windowEnd(input)
        return input.weeks.flatMap { it.days }
            .filter { !it.date.isBefore(input.today) && it.date.isBefore(end) }
            .distinctBy { it.date }
            .sortedBy { it.date }
    }

    /** Each time block of each event for the child, from today to the end of the window. */
    fun events(input: SchoolImportInput): List<ImportItem> {
        val end = windowEnd(input)
        return input.events
            .filter { SchoolEventAudience.isForChild(it, input.student) }
            .flatMap { event ->
                val title = event.title.trim().take(SchoolImportPlanner.MAX_TITLE_LENGTH)
                event.times.mapNotNull { time ->
                    val date = time.start.toLocalDate()
                    if (title.isEmpty() || date.isBefore(input.today) || !date.isBefore(end)) return@mapNotNull null
                    val fields = time.fields(title, event.description?.trim()?.ifBlank { null })
                    ImportItem(
                        id = SchoolImportIds.of(input.target, SchoolImportKind.EVENT, "${event.id}|${time.startKey}"),
                        kind = SchoolImportKind.EVENT,
                        lastDate = fields.end?.toLocalDate() ?: date,
                        fields = fields
                    )
                }
            }
    }

    /**
     * A whole-day block is an all-day event from its start date — through its end date when that
     * is a later day; the server ends a single day at 23:59 of the same date. A timed block keeps
     * the school's wall-clock times.
     */
    private fun SchoolEventTime.fields(title: String, description: String?): ImportFields = if (wholeDay) {
        val date = start.toLocalDate()
        val lastDay = end?.toLocalDate()?.takeIf { it.isAfter(date) }
        ImportFields(title, description, date.atStartOfDay(), lastDay?.atStartOfDay())
    } else {
        ImportFields(title, description, start, end?.takeIf { !it.isBefore(start) })
    }

    /** A school-hours event for each fetched day with a lesson. */
    private fun hours(input: SchoolImportInput): List<ImportItem> =
        daysInWindow(input).mapNotNull { day ->
            val first = day.firstLessonStart ?: return@mapNotNull null
            val last = day.lastLessonEnd ?: return@mapNotNull null
            ImportItem(
                id = SchoolImportIds.hours(input.target, day.date),
                kind = SchoolImportKind.HOURS,
                lastDate = day.date,
                fields = ImportFields(
                    title = input.wording.schoolHours.take(SchoolImportPlanner.MAX_TITLE_LENGTH),
                    description = null,
                    start = day.date.atTime(first),
                    end = day.date.atTime(last)
                )
            )
        }

    /**
     * Days with no timetable cells at all whose type is a day off, minus the days the app's own
     * holiday calendar draws, coalesced into runs of one title — a run carries on over a weekend.
     * A run that continues an imported day-off event (its earlier days now behind the fetched
     * weeks) extends that event rather than starting a second one beside it.
     */
    private fun daysOff(input: SchoolImportInput): List<ImportItem> {
        val days = daysInWindow(input).filter {
            !it.hasAtoms && it.type in DAY_OFF_TYPES && it.date !in input.drawnHolidays
        }
        val runs = mutableListOf<DayOffRun>()
        for (day in days) {
            val title = input.wording.dayOff(day.description.trim(), day.type)
                .take(SchoolImportPlanner.MAX_TITLE_LENGTH)
            val current = runs.lastOrNull()
            if (current != null && current.title == title && onlyWeekendBetween(current.last, day.date)) {
                runs[runs.lastIndex] = current.copy(last = day.date)
            } else {
                runs += DayOffRun(day.date, day.date, title)
            }
        }
        return runs.map { run -> continuationOf(run, input) ?: run.toItem(input.target) }
    }

    private fun continuationOf(run: DayOffRun, input: SchoolImportInput): ImportItem? {
        val earlier = input.ledger.filterValues { it.kind == SchoolImportKind.DAY_OFF }.keys
            .mapNotNull { (input.existing[it] as? ExistingImport.Live)?.event }
            .firstOrNull { event ->
                val start = event.startDateTime.toLocalDate()
                val end = event.endDateTime?.toLocalDate() ?: start
                event.title == run.title && start.isBefore(run.first) &&
                    (!end.isBefore(run.first) || onlyWeekendBetween(end, run.first))
            } ?: return null
        return ImportItem(
            id = earlier.id,
            kind = SchoolImportKind.DAY_OFF,
            lastDate = run.last,
            fields = ImportFields(run.title, null, earlier.startDateTime, run.last.atStartOfDay())
        )
    }

    private fun DayOffRun.toItem(target: SchoolImportTarget) = ImportItem(
        id = SchoolImportIds.dayOff(target, first),
        kind = SchoolImportKind.DAY_OFF,
        lastDate = last,
        fields = ImportFields(title, null, first.atStartOfDay(), last.takeIf { it.isAfter(first) }?.atStartOfDay())
    )

    private data class DayOffRun(val first: LocalDate, val last: LocalDate, val title: String)
}

/** The day after the last imported one. */
private fun windowEnd(input: SchoolImportInput): LocalDate =
    input.today.plusDays(SchoolImportPlanner.EVENT_WINDOW_DAYS)

/** Whether [later] follows [earlier] with nothing but a weekend between them. */
private fun onlyWeekendBetween(earlier: LocalDate, later: LocalDate): Boolean {
    if (!later.isAfter(earlier)) return false
    var day = earlier.plusDays(1)
    while (day.isBefore(later)) {
        if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) return false
        day = day.plusDays(1)
    }
    return true
}
