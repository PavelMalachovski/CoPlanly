package com.coparently.app.domain.custody

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Part of one cycle day that the child spends with a parent who does not have that day (MON-6b):
 * "every Wednesday 15:00–19:00 with Dad", in a fortnight that is otherwise Mum's.
 *
 * **A window sits on top of the whole-day pattern; it does not split it.** `CustodyModel` still
 * gives each day to exactly one parent (`getCustodyFor` is unchanged), because that is what the
 * calendar colours, what the handover walk reads, what a swap moves, and what every build already
 * shipped understands. The owner's decision (September 2026) was to keep one parent per day and
 * describe the contact afternoon as what it is — a few hours inside somebody else's day — rather
 * than turn a day into halves, which would have had to reach every one of those places at once.
 *
 * Windows repeat with the cycle, exactly like `momDayIndices`: [dayIndex] is a position in the
 * pattern (0 is the pattern's start date), so a fortnightly pattern that means "every Wednesday"
 * carries two windows, one per week.
 *
 * @property dayIndex Position in the custody cycle, `0 until patternDays`. An index past the cycle
 *   never matches — `CustodyModel.contactWindowsOn` reduces a date into the cycle first — so a
 *   window left behind by a shorter pattern is inert rather than wrong.
 * @property start When the window opens. Same-day only: see [end].
 * @property end When the window closes; strictly after [start]. A window never crosses midnight,
 *   because a window that did would be an overnight — and an overnight is a whole day, which is
 *   what the pattern (or MON-6's midweek day) is for.
 * @property parent The slot the child is with during the window — `"mom"` or `"dad"`, the two
 *   schema slot ids that are never renamed. A slot, not a person, for the reason
 *   `momDayIndices` is one: `CustodyModel.complemented` flips it when pairing moves this device
 *   to the other slot.
 */
data class ContactWindow(
    val dayIndex: Int,
    val start: LocalTime,
    val end: LocalTime,
    val parent: String
) {
    init {
        require(dayIndex >= 0) { "A cycle day is never negative: $dayIndex" }
        require(start < end) { "A contact window ends after it starts, on the same day: $start-$end" }
        require(parent == SLOT_ONE || parent == SLOT_TWO) { "Not a parent slot: $parent" }
    }

    /** This window with the other slot, for `CustodyModel.complemented`. */
    fun withOtherParent(): ContactWindow = copy(parent = if (parent == SLOT_ONE) SLOT_TWO else SLOT_ONE)

    companion object {
        /** Slot 1's schema id. */
        const val SLOT_ONE = "mom"

        /** Slot 2's schema id. */
        const val SLOT_TWO = "dad"
    }
}

/**
 * The one wire form of a [ContactWindow], used by Room and by Firestore alike.
 *
 * A window is written as one short string, `"<dayIndex>|<HH:mm>|<HH:mm>|<slot>"` — `"9|15:00|19:00|dad"`
 * — and a model's windows as a list of them. Deliberately **not** a Gson serialisation of the data
 * class: R8 renamed a Gson model's fields once already and it shipped (`FamilyMemberRef` records
 * the same decision), and a string a person can read in the Firestore console is also a string
 * `firestore.rules` can compare for equality.
 *
 * **The canonical form is what makes the rules work.** A proposal or swap write re-sends the whole
 * document, and `firestore.rules` refuses one whose `contactWindows` differs from what is stored.
 * [encodeAll] therefore always produces the same list for the same set — sorted, de-duplicated,
 * minutes zero-padded — so a device that decoded a list and wrote it back cannot have changed it.
 * (The repository goes further and carries the stored list verbatim on those writes; see
 * `SharedCustody.contactWindowsWire`.)
 *
 * **An entry this build cannot read is dropped from the model, never "repaired".** A malformed or
 * future-format window guessed into a slot and a time would put a child with the wrong parent on
 * the calendar both parents plan around — the rule `DayOverride` parsing already follows.
 */
object ContactWindowCodec {

    private const val SEPARATOR = '|'
    private const val FIELDS = 4
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** [window] as its wire string. */
    fun encode(window: ContactWindow): String = listOf(
        window.dayIndex.toString(),
        window.start.format(TIME),
        window.end.format(TIME),
        window.parent
    ).joinToString(SEPARATOR.toString())

    /** The window [value] names, or null when it is not one this build can read. */
    fun decode(value: String): ContactWindow? {
        val parts = value.split(SEPARATOR)
        if (parts.size != FIELDS) return null
        val (index, start, end) = parts
        val parent = parts[FIELDS - 1]
        return runCatching {
            ContactWindow(
                dayIndex = index.toInt(),
                start = LocalTime.parse(start, TIME),
                end = LocalTime.parse(end, TIME),
                parent = parent
            )
        }.getOrNull()
    }

    /** [windows] in canonical order — by day, then time, then slot — with duplicates removed. */
    fun canonical(windows: Collection<ContactWindow>): List<ContactWindow> =
        windows.distinct().sortedWith(compareBy({ it.dayIndex }, { it.start }, { it.end }, { it.parent }))

    /** [windows] as the canonical wire list. */
    fun encodeAll(windows: Collection<ContactWindow>): List<String> = canonical(windows).map(::encode)

    /**
     * The windows a stored list names, in canonical order; entries this build cannot read are
     * dropped (see the object KDoc), and a null list is no windows.
     */
    fun decodeAll(values: List<*>?): List<ContactWindow> =
        canonical(values.orEmpty().mapNotNull { (it as? String)?.let(::decode) })
}
