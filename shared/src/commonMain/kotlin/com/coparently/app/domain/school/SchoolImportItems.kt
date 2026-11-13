package com.coparently.app.domain.school

import com.coparently.app.domain.events.AllDayEvent
import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.platform.Sha256
import com.coparently.app.domain.text.Utf8
import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

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
    /**
     * A short hash of the four fields, recorded to tell the school's edits from the parent's. The
     * times are hashed as `java.time.LocalDateTime.toString()` wrote them ([IsoText.dateTimeShortest]),
     * so a fingerprint recorded before MON-24 still matches.
     */
    val fingerprint: String
        get() {
            val times = listOf(IsoText.dateTimeShortest(start), end?.let(IsoText::dateTimeShortest).orEmpty())
            return SchoolImportIds.sha256Hex((listOf(title, description.orEmpty()) + times).joinToString("\u0000"))
                .take(FINGERPRINT_LENGTH)
        }

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

    /**
     * The id of the event of [kind] under [key]. Bakaláři's names the server address and the
     * login, as it always has. EduPage's names the school's host and the pupil, never the login,
     * so two parents connecting the same child de-duplicate.
     */
    fun of(target: SchoolImportTarget, kind: SchoolImportKind, key: String): String {
        val identity = when (target.system) {
            SchoolSystem.BAKALARI -> "bakalari|${target.baseUrl}|${target.username}"
            SchoolSystem.EDUPAGE -> "edupage|${hostOf(target.baseUrl)}|${target.studentKey}"
        }
        return PREFIX + sha256Hex("$identity|${kind.stored}|$key").take(HASH_LENGTH)
    }

    /** The lower-case host of [address] without scheme, credentials, port or path; [address] if none. */
    private fun hostOf(address: String): String {
        val afterScheme = address.substringAfter("://", address)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        return host.ifEmpty { address }
    }

    /** The id of [date]'s school-hours event. */
    fun hours(target: SchoolImportTarget, date: LocalDate): String =
        of(target, SchoolImportKind.HOURS, IsoText.date(date))

    /** The id of the day-off run that starts on [date]. */
    fun dayOff(target: SchoolImportTarget, date: LocalDate): String =
        of(target, SchoolImportKind.DAY_OFF, IsoText.date(date))

    /**
     * [text]'s SHA-256, in lower-case hex, over the bytes the JVM's `toByteArray(UTF_8)` writes
     * ([Utf8.encode]): an unpaired surrogate is `?` on every platform, so an iPhone derives the
     * ids an Android phone derived from the same school data.
     */
    fun sha256Hex(text: String): String =
        Sha256.digest(Utf8.encode(text)).toHex()
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
        (events(input) + hours(input) + daysOff(input) + tasks(input)).distinctBy { it.id }

    /** The fetched days from today on, inside the window, one per date, in order. */
    fun daysInWindow(input: SchoolImportInput): List<SchoolDay> {
        val end = windowEnd(input)
        return input.weeks.flatMap { it.days }
            .filter { it.date >= input.today && it.date < end }
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
                    val date = time.start.date
                    if (title.isEmpty() || date < input.today || date >= end) return@mapNotNull null
                    val fields = time.fields(title, event.description?.trim()?.ifBlank { null })
                    ImportItem(
                        id = SchoolImportIds.of(input.target, SchoolImportKind.EVENT, "${event.id}|${time.startKey}"),
                        kind = SchoolImportKind.EVENT,
                        lastDate = fields.end?.date ?: date,
                        fields = fields
                    )
                }
            }
    }

    /** Each task due from today to the end of the window, as an all-day event on its date. */
    fun tasks(input: SchoolImportInput): List<ImportItem> {
        val end = windowEnd(input)
        return input.tasks.mapNotNull { task ->
            val title = task.title.trim().take(SchoolImportPlanner.MAX_TITLE_LENGTH)
            if (title.isEmpty() || task.date < input.today || task.date >= end) return@mapNotNull null
            ImportItem(
                id = SchoolImportIds.of(input.target, SchoolImportKind.TASK, task.id),
                kind = SchoolImportKind.TASK,
                lastDate = task.date,
                fields = ImportFields(
                    title = title,
                    description = task.description?.trim()?.ifBlank { null },
                    start = task.date.atStartOfDay(),
                    end = task.date.atTime(AllDayEvent.LAST_MINUTE)
                )
            )
        }
    }

    /**
     * A whole-day block is an all-day event from its start date — through its end date when that
     * is a later day; the server ends a single day at 23:59 of the same date. A timed block keeps
     * the school's wall-clock times.
     */
    private fun SchoolEventTime.fields(title: String, description: String?): ImportFields = if (wholeDay) {
        val date = start.date
        val lastDay = end?.date?.takeIf { it > date }
        ImportFields(title, description, date.atStartOfDay(), lastDay?.atStartOfDay())
    } else {
        ImportFields(title, description, start, end?.takeIf { it >= start })
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
                val start = event.startDateTime.date
                val end = event.endDateTime?.date ?: start
                event.title == run.title && start < run.first &&
                    (end >= run.first || onlyWeekendBetween(end, run.first))
            } ?: return null
        return ImportItem(
            id = earlier.id,
            kind = SchoolImportKind.DAY_OFF,
            lastDate = run.last,
            fields = ImportFields(
                run.title,
                null,
                earlier.startDateTime,
                run.last.atStartOfDay()
            )
        )
    }

    private fun DayOffRun.toItem(target: SchoolImportTarget) = ImportItem(
        id = SchoolImportIds.dayOff(target, first),
        kind = SchoolImportKind.DAY_OFF,
        lastDate = last,
        fields = ImportFields(title, null, first.atStartOfDay(), last.takeIf { it > first }?.atStartOfDay())
    )

    private data class DayOffRun(val first: LocalDate, val last: LocalDate, val title: String)
}

/** The day after the last imported one. */
private fun windowEnd(input: SchoolImportInput): LocalDate =
    input.today.plus(SchoolImportPlanner.EVENT_WINDOW_DAYS, DateTimeUnit.DAY)

/** Midnight at the start of this day, as `java.time.LocalDate.atStartOfDay()` gave it: a wall clock. */
private fun LocalDate.atStartOfDay(): LocalDateTime = atTime(0, 0)

/** Whether [later] follows [earlier] with nothing but a weekend between them. */
private fun onlyWeekendBetween(earlier: LocalDate, later: LocalDate): Boolean {
    if (later <= earlier) return false
    var day = earlier.plus(1, DateTimeUnit.DAY)
    while (day < later) {
        if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) return false
        day = day.plus(1, DateTimeUnit.DAY)
    }
    return true
}
