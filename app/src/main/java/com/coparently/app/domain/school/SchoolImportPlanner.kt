package com.coparently.app.domain.school

import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.domain.model.Event
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What an imported calendar event was made from (MON-8).
 *
 * @property stored How it is written in the connection's record of what it imported, and in the
 *   event id's hash. Never renamed: renaming one would give every imported event a new id.
 */
enum class SchoolImportKind(val stored: String) {
    /** One block of time of a school event. */
    EVENT("event"),

    /** A school day, from the first lesson's start to the last lesson's end. */
    HOURS("hours"),

    /** A run of days without lessons that the app's own holiday calendar does not draw. */
    DAY_OFF("dayoff");

    companion object {
        /** The kind [stored] names, or null for one this build does not know. */
        fun of(stored: String?): SchoolImportKind? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * What a connection remembers about one event it created.
 *
 * The record is what makes "never resurrect" possible: a delivered deletion removes the event's
 * row from Room altogether, so without a record an event the parent deleted would look exactly
 * like one never imported, and the next update would put it back.
 *
 * @property kind What it was made from.
 * @property lastDate Its last day. A record whose last day has passed is forgotten, because
 *   nothing that far back is fetched again.
 * @property fingerprint The imported fields as the school last sent them; see [SchoolImportPlanner].
 */
data class SchoolImportRecord(
    val kind: SchoolImportKind,
    val lastDate: LocalDate,
    val fingerprint: String
)

/** What Room holds under an imported event's id. */
sealed interface ExistingImport {
    /** A live event, which may have been edited since it was imported. */
    data class Live(val event: Event) : ExistingImport

    /** A deletion this device has made and not yet delivered. It is never brought back. */
    data object Deleted : ExistingImport
}

/**
 * The titles an import writes, in the importing parent's language.
 *
 * Resolved once per update by the data layer, which has the resources; the planner stays a pure
 * function. A title is data once written — the co-parent reads it as it was written, like a title
 * a parent typed.
 */
interface SchoolImportWording {
    /** The title of a school day's hours ("Anna at school"). */
    val schoolHours: String

    /**
     * The title of a day without lessons.
     *
     * @param description The school's name for the day, or blank.
     * @param type What the timetable called the day.
     */
    fun dayOff(description: String, type: SchoolDayType): String
}

/**
 * Who and what an update imports for.
 *
 * @property baseUrl The school server, part of every event id.
 * @property username The account, part of every event id.
 * @property childId The CoPlanly child every imported event names in `forMembers`.
 * @property familyId The family every created event is stamped with, or null for an account in
 *   none — the connection's choice, never the family the device happens to be showing.
 * @property ownerUid The signed-in parent, who creates the events.
 * @property ownerSlot That parent's own slot, which a created event's `parentOwner` takes — the
 *   same rule a Google Calendar import follows.
 */
data class SchoolImportTarget(
    val baseUrl: String,
    val username: String,
    val childId: String,
    val familyId: String?,
    val ownerUid: String,
    val ownerSlot: String
)

/**
 * Everything one update decides from.
 *
 * @property target Who and what it imports for.
 * @property today The first day imported.
 * @property now The time a created event is stamped with.
 * @property student The child as the school describes them, to tell their events from others'.
 * @property events The school's events; empty when they were not fetched.
 * @property weeks The timetable weeks fetched; empty when none were.
 * @property drawnHolidays The days the app's own holiday calendar already draws for the parent's
 *   country and region — public holidays and school vacations. A day off on one of them is not
 *   imported, so the calendar does not say "Christmas" twice.
 * @property existing What Room holds under each id in [SchoolImportPlanner.idsToLookUp].
 * @property ledger What this connection has imported before, by event id.
 * @property wording The titles to write.
 */
data class SchoolImportInput(
    val target: SchoolImportTarget,
    val today: LocalDate,
    val now: LocalDateTime,
    val student: SchoolStudent,
    val events: List<SchoolEvent>,
    val weeks: List<SchoolWeek>,
    val drawnHolidays: Set<LocalDate>,
    val existing: Map<String, ExistingImport>,
    val ledger: Map<String, SchoolImportRecord>,
    val wording: SchoolImportWording
)

/**
 * What one update does.
 *
 * @property creates New events, to go through the ordinary create path.
 * @property updates Imported events whose school data changed, as copies of the stored events.
 * @property deletes School-hours events for days the fetched timetable now says have no school —
 *   the one deletion an import makes, through the ordinary delete path (a tombstone and a
 *   revision).
 * @property ledger The connection's record after this update.
 */
data class SchoolImportPlan(
    val creates: List<Event>,
    val updates: List<Event>,
    val deletes: List<Event>,
    val ledger: Map<String, SchoolImportRecord>
)

/**
 * Turns what a school's server said into calendar changes (MON-8) — a pure function, so every
 * rule below is a JVM test.
 *
 * Rules, all owner decisions:
 * - **Never delete by absence.** Bakaláři empties its events list halfway through the summer; an
 *   event missing from a response says nothing. The one deletion is [SchoolImportPlan.deletes].
 * - **Never resurrect.** An id Room holds as a pending deletion, or one this connection created
 *   before and Room no longer holds, is left alone.
 * - **Update only what the school changed.** An event is rewritten only when the school's fields
 *   differ from what was imported last time, so a title the parent corrected by hand survives
 *   every update until the school itself changes the event. Only title, description, start and
 *   end are rewritten; everything else is kept with `copy()`.
 * - **The next [EVENT_WINDOW_DAYS] days** of events, and **[TIMETABLE_WEEKS] weeks** of school
 *   hours and days off.
 */
object SchoolImportPlanner {

    /** The `eventType` every imported event carries: the existing "school" type. */
    const val SCHOOL_EVENT_TYPE = "school"

    /** How far ahead school events are imported. */
    const val EVENT_WINDOW_DAYS = 90L

    /** How many timetable weeks, this one first, are fetched and imported. */
    const val TIMETABLE_WEEKS = 4

    /** The longest title `EventValidator` accepts. */
    const val MAX_TITLE_LENGTH = 100

    /**
     * Every id an update may touch, for the caller to look up in Room before [plan]: what the
     * school data would produce, every school-hours and day-off id of a fetched day, and every id
     * the connection created before.
     */
    fun idsToLookUp(input: SchoolImportInput): Set<String> {
        val days = SchoolImportItems.daysInWindow(input)
        return SchoolImportItems.events(input).map { it.id }.toSet() +
            days.map { SchoolImportIds.hours(input.target, it.date) } +
            days.map { SchoolImportIds.dayOff(input.target, it.date) } +
            input.ledger.keys
    }

    /** Decides what one update creates, updates and deletes. See the object's KDoc. */
    fun plan(input: SchoolImportInput): SchoolImportPlan {
        val ledger = input.ledger.filterValues { !it.lastDate.isBefore(input.today) }.toMutableMap()
        val creates = mutableListOf<Event>()
        val updates = mutableListOf<Event>()
        for (item in SchoolImportItems.all(input)) {
            val record = SchoolImportRecord(item.kind, item.lastDate, item.fields.fingerprint)
            when (val existing = input.existing[item.id]) {
                is ExistingImport.Live -> {
                    if (item.changedFrom(existing.event, input.ledger[item.id])) {
                        updates += existing.event.withFields(item.fields, input.target.ownerUid)
                    }
                    ledger[item.id] = record
                }
                ExistingImport.Deleted -> ledger[item.id] = record
                null -> if (item.id !in input.ledger) {
                    creates += item.toEvent(input)
                    ledger[item.id] = record
                }
            }
        }
        return SchoolImportPlan(creates, updates, noSchoolDeletes(input), ledger)
    }

    /**
     * The id of the imported event of [kind] under [key] for [target]'s account:
     * `"school-"` and the first 32 hex digits of the SHA-256 of
     * `bakalari|<baseUrl>|<username>|<kind>|<key>`. Deterministic, so an update finds what the
     * last one made instead of making it again; Firestore-safe, having no slash.
     */
    fun idOf(target: SchoolImportTarget, kind: SchoolImportKind, key: String): String =
        SchoolImportIds.of(target, kind, key)

    /**
     * The one deletion: a fetched day from today on that now has no lessons loses its
     * school-hours event, if it has one.
     */
    private fun noSchoolDeletes(input: SchoolImportInput): List<Event> =
        SchoolImportItems.daysInWindow(input)
            .filterNot { it.hasLessons }
            .mapNotNull { day ->
                (input.existing[SchoolImportIds.hours(input.target, day.date)] as? ExistingImport.Live)?.event
            }

    private fun ImportItem.toEvent(input: SchoolImportInput) = Event(
        id = id,
        title = fields.title,
        description = fields.description,
        startDateTime = fields.start,
        endDateTime = fields.end,
        eventType = SCHOOL_EVENT_TYPE,
        parentOwner = input.target.ownerSlot,
        createdAt = input.now,
        updatedAt = input.now,
        createdByFirebaseUid = input.target.ownerUid,
        lastModifiedBy = input.target.ownerUid,
        isPrivate = false,
        forMembers = listOf(FamilyMemberRef.Child(input.target.childId)),
        familyId = input.target.familyId
    )

    /**
     * Whether the stored [event] needs the school's new fields: when the school sent something
     * other than it did last time, and the event does not already say it.
     */
    private fun ImportItem.changedFrom(event: Event, record: SchoolImportRecord?): Boolean {
        if (record != null && record.fingerprint == fields.fingerprint) return false
        return event.title != fields.title || event.description != fields.description ||
            event.startDateTime != fields.start || event.endDateTime != fields.end
    }

    private fun Event.withFields(fields: ImportFields, editorUid: String) = copy(
        title = fields.title,
        description = fields.description,
        startDateTime = fields.start,
        endDateTime = fields.end,
        lastModifiedBy = editorUid
    )
}
