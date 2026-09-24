package com.coparently.app.domain.custody

import com.coparently.app.domain.family.FamilyMemberRef
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One child's own custody pattern, overriding the family's (FAM-4): "the baby stays with Mum",
 * "the eldest negotiated alternate weeks while the younger two keep 2-2-3".
 *
 * **The family schedule stays the default.** A child with no override follows the pair's one
 * pattern — its seasonal layers, its contact windows and its accepted swaps — exactly as before;
 * an override exists only for the rarer family whose children do not all move together.
 * `docs/DESIGN-custody-per-child.md` is the design.
 *
 * **An override is complete and self-contained.** For its child it replaces the family pattern on
 * every date, in the base pattern's vocabulary: a cycle of [patternDays] anchored on [startDate],
 * [momDayIndices] naming slot 1's days, [contactWindows] repeating with the cycle (item 24 holds
 * inside it — a window sits on a whole day and never splits it). Deliberately **not** applied to
 * an overridden child, and stated here so nobody "fixes" it silently:
 * - **the family's seasonal layers** (MON-14). A summer of alternating weeks agreed for the family
 *   would otherwise move an infant whose whole point is that they stay put. A per-child layer is a
 *   later, separate decision;
 * - **accepted one-off swaps** (`DayOverride`). A swap is offered and answered against the grid
 *   both parents see, which is the family schedule; nobody agreed to it about this child.
 *
 * It lives inside the pair's one custody document, under the key `childOverrides`, as
 * [ChildOverrideCodec] strings — never a second document per child, which would multiply the
 * last-writer comparison SEC-4 had to fix before this item could start.
 *
 * @property childId The child it is about — `ChildInfo.id`, the id [FamilyMemberRef.Child] names.
 *   Letters, digits, `-` and `_` only: it is a field of the wire string.
 * @property patternDays Length of the child's cycle, `1..MAX_CYCLE_DAYS`.
 * @property momDayIndices Cycle positions slot 1 has, as in `CustodyModel.momDayIndices`.
 * @property startDate Day 0 of the child's cycle.
 * @property contactWindows Contact afternoons inside the child's cycle, positions in that cycle.
 */
data class ChildScheduleOverride(
    val childId: String,
    val patternDays: Int,
    val momDayIndices: Set<Int>,
    val startDate: LocalDate,
    val contactWindows: List<ContactWindow> = emptyList()
) {
    init {
        require(CHILD_ID.matches(childId)) { "Not a child id: $childId" }
        require(patternDays in 1..MAX_CYCLE_DAYS) { "A child's cycle is 1..$MAX_CYCLE_DAYS days" }
        require(momDayIndices.all { it in 0 until patternDays }) { "A day index is outside the cycle" }
        require(contactWindows.all { it.dayIndex < patternDays }) { "A window is outside the cycle" }
    }

    /** The child this override is about, as the reference every per-member record uses (FAM-2). */
    val member: FamilyMemberRef.Child get() = FamilyMemberRef.Child(childId)

    /** Whose day [date] is for this child — `"mom"` or `"dad"`. */
    fun custodyFor(date: LocalDate): String =
        if (cycleIndex(date) in momDayIndices) ContactWindow.SLOT_ONE else ContactWindow.SLOT_TWO

    /** Every contact window on [date], earliest first — as `CustodyModel.contactWindowsOn`. */
    fun contactWindowsOn(date: LocalDate): List<ContactWindow> {
        if (contactWindows.isEmpty()) return emptyList()
        val index = cycleIndex(date)
        return contactWindows.filter { it.dayIndex == index }.sortedBy { it.start }
    }

    /**
     * This override with the two slots swapped, for `CustodyModel.complemented` once the model
     * carries overrides: when pairing moves this device to the other slot, "slot 1's days" must
     * keep meaning the same person's days.
     */
    fun withOtherParent(): ChildScheduleOverride = copy(
        momDayIndices = (0 until patternDays).toSet() - momDayIndices,
        contactWindows = contactWindows.map { it.withOtherParent() }
    )

    private fun cycleIndex(date: LocalDate): Int =
        Math.floorMod(ChronoUnit.DAYS.between(startDate, date), patternDays.toLong()).toInt()

    companion object {
        /** No child's cycle is longer than a year — the base pattern's and a layer's bound. */
        const val MAX_CYCLE_DAYS = 366

        /** What a child id may contain; `ChildInfo` ids are UUIDs, well inside it. */
        val CHILD_ID = Regex("[A-Za-z0-9_-]{1,128}")
    }
}

/**
 * What a stored `childOverrides` list holds: the overrides this build can read, and the entries it
 * cannot, **verbatim**.
 *
 * Unreadable entries are kept for the reason `DecodedLayers.unreadable` is: an older build that
 * edits the list must not erase an entry a newer build wrote. That includes a second entry for a
 * child that already has one — one override per child is the semantics, the first in canonical
 * order decides, and the other is carried untouched rather than silently deleted.
 *
 * @property overrides Readable overrides, one per child, ordered by child id.
 * @property unreadable Everything else, verbatim.
 */
data class DecodedChildOverrides(
    val overrides: List<ChildScheduleOverride> = emptyList(),
    val unreadable: List<String> = emptyList()
)

/**
 * The one wire form of a [ChildScheduleOverride], for Room and Firestore alike.
 *
 * One string per override, fields separated by `;`:
 *
 * ```
 * C1;child:<id>;<anchor>;<patternDays>;<slot-1 days>;<windows>
 * C1;child:7f3a-…;2026-09-07;1;0;
 * ```
 *
 * The child is written as its [FamilyMemberRef] stored form, so the one vocabulary for "who a
 * record is about" (FAM-2) is the one this uses too; a reference that is not a child — a pet, or a
 * kind a newer build knows — makes the entry unreadable, never a guess. The slot-1 days and the
 * windows are comma-separated; windows are [ContactWindowCodec] strings, which contain neither
 * `;` nor `,`. Deliberately **not** Gson over the data class, for the reason every codec in this
 * package gives, and plain strings are what `firestore.rules` compares.
 *
 * [encodeAll] is canonical — sorted, de-duplicated — so a device that decoded the list and wrote
 * it back cannot have changed it; proposal and swap writes go further and carry the stored list
 * verbatim (`childOverridesKeptOrDropped` in `firestore.rules`). `C1` is the format's version.
 */
object ChildOverrideCodec {

    private const val VERSION = "C1"
    private const val SEPARATOR = ';'
    private const val LIST_SEPARATOR = ','
    private const val FIELDS = 6
    private const val IDX_MEMBER = 1
    private const val IDX_ANCHOR = 2
    private const val IDX_CYCLE = 3
    private const val IDX_DAYS = 4
    private const val IDX_WINDOWS = 5
    private val COUNT = Regex("\\d+")
    private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

    /**
     * How many overrides a document may carry before the rest are treated as unreadable. Far
     * above any family; it bounds what a document synced unvalidated from the other phone costs.
     */
    const val MAX_OVERRIDES = 16

    /** [override] as its wire string. */
    fun encode(override: ChildScheduleOverride): String = listOf(
        VERSION,
        override.member.stored,
        override.startDate.toString(),
        override.patternDays.toString(),
        override.momDayIndices.sorted().joinToString(LIST_SEPARATOR.toString()),
        ContactWindowCodec.encodeAll(override.contactWindows).joinToString(LIST_SEPARATOR.toString())
    ).joinToString(SEPARATOR.toString())

    /** The override [value] names, or null when it is not one this build can read. */
    fun decode(value: String): ChildScheduleOverride? {
        val parts = value.split(SEPARATOR)
        if (parts.size != FIELDS || parts[0] != VERSION) return null
        val child = FamilyMemberRef.of(parts[IDX_MEMBER]) as? FamilyMemberRef.Child ?: return null
        return runCatching {
            require(parts[IDX_ANCHOR].matches(ISO_DATE) && parts[IDX_CYCLE].matches(COUNT))
            require(parts[IDX_DAYS].splitList().all { it.matches(COUNT) })
            val windows = parts[IDX_WINDOWS].splitList().map { requireNotNull(ContactWindowCodec.decode(it)) }
            ChildScheduleOverride(
                childId = child.id,
                patternDays = parts[IDX_CYCLE].toInt(),
                momDayIndices = parts[IDX_DAYS].splitList().map { it.toInt() }.toSet(),
                startDate = LocalDate.parse(parts[IDX_ANCHOR]),
                contactWindows = ContactWindowCodec.canonical(windows)
            )
        }.getOrNull()
    }

    /**
     * [overrides] and [unreadable] as the canonical wire list: sorted, duplicates removed. Always a
     * list — `[]` for none — because a pattern write states what the overrides are (item 24's rule).
     */
    fun encodeAll(
        overrides: Collection<ChildScheduleOverride>,
        unreadable: Collection<String> = emptyList()
    ): List<String> = (overrides.map(::encode) + unreadable).distinct().sorted()

    /**
     * What a stored list names. A null list is no overrides. An entry past [MAX_OVERRIDES], or a
     * second one for a child already read, is kept as unreadable rather than dropped.
     */
    fun decodeAll(values: List<*>?): DecodedChildOverrides {
        val overrides = mutableListOf<ChildScheduleOverride>()
        val unreadable = mutableListOf<String>()
        values.orEmpty().filterIsInstance<String>().distinct().sorted().forEach { raw ->
            val override = decode(raw)
            val fits = override != null && overrides.size < MAX_OVERRIDES &&
                overrides.none { it.childId == override.childId }
            if (fits) overrides += override else unreadable += raw
        }
        return DecodedChildOverrides(overrides.sortedBy { it.childId }, unreadable)
    }

    private fun String.splitList(): List<String> =
        if (isEmpty()) emptyList() else split(LIST_SEPARATOR)
}
