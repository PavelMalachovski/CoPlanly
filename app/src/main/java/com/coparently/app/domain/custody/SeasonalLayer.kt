package com.coparently.app.domain.custody

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A stretch of dates on which a different pattern replaces the base one (MON-14): "the summer
 * holidays, two weeks each", "Christmas with Dad".
 *
 * **A layer is not a second model.** It lives inside the pair's one custody document and is read
 * through the one question the app already asks — `CustodyModel.getCustodyFor` resolves the
 * highest-priority layer covering a date first and the base pattern second, and `CustodyResolver`
 * puts accepted one-off swaps above both. Every caller that asks "whose day is this" — the grid,
 * the handover walk, Home, the export, the calendar feed — therefore follows a layer without
 * knowing layers exist.
 *
 * Inside its range a layer is a complete pattern of its own, in the base pattern's vocabulary:
 * a cycle of [patternDays] anchored on [startDate], with [momDayIndices] naming slot 1's days and
 * [contactWindows] repeating with that cycle. Item 24's rule holds within a layer too — a window
 * sits on top of a whole day and never splits it.
 *
 * @property id Stable identifier, so an edit replaces the layer rather than adding a second one.
 *   Letters, digits and `-` only: it is a field of the wire string (see [SeasonalLayerCodec]).
 * @property name What the parents call it — "Summer", "Christmas". The parents' own words, so it
 *   is data, not UI text, and is never translated.
 * @property fromDate First day the layer covers.
 * @property toDate Last day the layer covers, **inclusive** — the way a parent says "1 July to
 *   31 August".
 * @property patternDays Length of the layer's own cycle, `1..MAX_LAYER_DAYS`.
 * @property momDayIndices Cycle positions slot 1 has, as in `CustodyModel.momDayIndices`.
 * @property startDate Day 0 of the layer's cycle. Normally [fromDate]; kept separately so an
 *   alternating-weeks summer can continue the rhythm of the school year if the parents want it.
 * @property contactWindows Contact afternoons inside the layer, positions in its own cycle.
 * @property priority Which layer wins where two overlap: higher first. A Christmas layer inside a
 *   winter one is the usual case.
 */
data class SeasonalLayer(
    val id: String,
    val name: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val patternDays: Int,
    val momDayIndices: Set<Int>,
    val startDate: LocalDate = fromDate,
    val contactWindows: List<ContactWindow> = emptyList(),
    val priority: Int = 0
) {
    init {
        require(ID.matches(id)) { "Not a layer id: $id" }
        require(name.length <= MAX_NAME_LENGTH) { "A layer name is at most $MAX_NAME_LENGTH characters" }
        require(!toDate.isBefore(fromDate)) { "A layer ends on or after it starts: $fromDate-$toDate" }
        require(spanDays <= MAX_LAYER_DAYS) { "A layer covers at most $MAX_LAYER_DAYS days" }
        require(patternDays in 1..MAX_LAYER_DAYS) { "A layer's cycle is 1..$MAX_LAYER_DAYS days" }
        require(momDayIndices.all { it in 0 until patternDays }) { "A day index is outside the cycle" }
        require(contactWindows.all { it.dayIndex < patternDays }) { "A window is outside the cycle" }
        require(priority in PRIORITY_RANGE) { "Priority out of range: $priority" }
    }

    /** How many days the layer covers, both ends included. */
    val spanDays: Long get() = ChronoUnit.DAYS.between(fromDate, toDate) + 1

    /** Whether [date] falls inside the layer. */
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(fromDate) && !date.isAfter(toDate)

    /**
     * Whose day [date] is under this layer's own cycle — `"mom"` or `"dad"`. Meaningful only for
     * a date the layer [contains]; the caller asks that first.
     */
    fun custodyFor(date: LocalDate): String =
        if (cycleIndex(date) in momDayIndices) ContactWindow.SLOT_ONE else ContactWindow.SLOT_TWO

    /** The layer's contact windows on [date], earliest first — every one, as `CustodyModel.contactWindowsOn`. */
    fun contactWindowsOn(date: LocalDate): List<ContactWindow> {
        if (contactWindows.isEmpty()) return emptyList()
        val index = cycleIndex(date)
        return contactWindows.filter { it.dayIndex == index }.sortedBy { it.start }
    }

    /** This layer with the two slots swapped, for `CustodyModel.complemented`. */
    fun withOtherParent(): SeasonalLayer = copy(
        momDayIndices = (0 until patternDays).toSet() - momDayIndices,
        contactWindows = contactWindows.map { it.withOtherParent() }
    )

    private fun cycleIndex(date: LocalDate): Int =
        Math.floorMod(ChronoUnit.DAYS.between(startDate, date), patternDays.toLong()).toInt()

    companion object {
        /** No layer is longer than a year, and neither is its cycle (the base pattern's bound). */
        const val MAX_LAYER_DAYS = 366

        /** How long a layer's name may be. */
        const val MAX_NAME_LENGTH = 80

        /** Every priority a layer may carry. */
        val PRIORITY_RANGE: IntRange = -1000..1000

        private val ID = Regex("[A-Za-z0-9-]{1,64}")

        /** Days in a week, for the alternating-weeks preset. */
        private const val DAYS_IN_WEEK = 7

        /** The alternating-weeks preset's cycle. */
        private const val FORTNIGHT = 14

        /**
         * Which layer decides a date where several cover it: the highest [priority]; then the one
         * that starts later, which is the inner one when a layer sits inside another; then the
         * id, so the answer never depends on list order. `functions/calendar-feed.js` ports this.
         */
        val PRECEDENCE: Comparator<SeasonalLayer> =
            compareByDescending<SeasonalLayer> { it.priority }
                .thenByDescending { it.fromDate }
                .thenBy { it.id }

        /** The whole of [range] with the parent in [slot]. */
        fun allWith(id: String, name: String, range: ClosedRange<LocalDate>, slot: String) =
            SeasonalLayer(
                id = id,
                name = name,
                fromDate = range.start,
                toDate = range.endInclusive,
                patternDays = 1,
                momDayIndices = if (slot == ContactWindow.SLOT_ONE) setOf(0) else emptySet()
            )

        /** A week each across [range], starting with [firstSlot] on its first day. */
        fun alternatingWeeks(id: String, name: String, range: ClosedRange<LocalDate>, firstSlot: String) =
            SeasonalLayer(
                id = id,
                name = name,
                fromDate = range.start,
                toDate = range.endInclusive,
                patternDays = FORTNIGHT,
                momDayIndices = if (firstSlot == ContactWindow.SLOT_ONE) {
                    (0 until DAYS_IN_WEEK).toSet()
                } else {
                    (DAYS_IN_WEEK until FORTNIGHT).toSet()
                }
            )

        /**
         * The first half of [range] with [firstSlot] and the second with the other parent — the
         * "two blocks" summer. An odd number of days gives the extra day to [firstSlot].
         */
        fun splitInHalf(id: String, name: String, range: ClosedRange<LocalDate>, firstSlot: String): SeasonalLayer {
            val span = (ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1).toInt()
                .coerceIn(1, MAX_LAYER_DAYS)
            val firstHalf = (0 until (span + 1) / 2).toSet()
            return SeasonalLayer(
                id = id,
                name = name,
                fromDate = range.start,
                toDate = range.endInclusive,
                patternDays = span,
                momDayIndices = if (firstSlot == ContactWindow.SLOT_ONE) {
                    firstHalf
                } else {
                    (0 until span).toSet() - firstHalf
                }
            )
        }
    }
}

/**
 * What a stored list of layers holds: the entries this build can read, and the ones it cannot.
 *
 * @property layers Readable layers, in canonical order.
 * @property unreadable Entries this build could not read, **verbatim** — a layer written by a
 *   newer build, or one whose fields do not validate. Kept rather than dropped for the reason
 *   `FamilyMemberRef.Unknown` survives a round trip: an older build that edits the layers must
 *   not erase one a newer build wrote. They play no part in resolving a date — guessing their
 *   meaning would put a child with the wrong parent — and are written back unchanged.
 */
data class DecodedLayers(
    val layers: List<SeasonalLayer> = emptyList(),
    val unreadable: List<String> = emptyList()
)

/**
 * The one wire form of a [SeasonalLayer], used by Room and by Firestore alike.
 *
 * One string per layer, fields separated by `;`:
 *
 * ```
 * L1;<id>;<priority>;<from>;<to>;<anchor>;<patternDays>;<slot-1 days>;<windows>;<name>
 * L1;summer-26;0;2026-07-01;2026-08-31;2026-07-01;14;0,1,2,3,4,5,6;;Summer
 * ```
 *
 * The slot-1 days and the windows are comma-separated. Windows are [ContactWindowCodec]
 * strings, which contain neither `;` nor `,`; the name is percent-encoded (UTF-8, everything
 * but `A–Z a–z 0–9 - _ . ~`), so no text a parent types can break the fields apart.
 * Deliberately **not** Gson over the data class — R8 renamed a Gson model's fields once already
 * and it shipped — and plain strings are what `firestore.rules` can compare for equality.
 *
 * **The canonical form is what makes the rules work**, as for contact windows: [encodeAll] is
 * sorted and de-duplicated, so a device that decoded a list and wrote it back cannot have changed
 * it. The repository goes further and carries the stored list verbatim on proposal and swap
 * writes (`SharedCustody.seasonalLayersWire`).
 *
 * **An entry this build cannot read is preserved, not repaired and not dropped** — see
 * [DecodedLayers.unreadable]. The `L1` prefix is the format's version: a future format uses
 * another and is carried untouched by this build.
 */
object SeasonalLayerCodec {

    private const val VERSION = "L1"
    private const val SEPARATOR = ';'
    private const val LIST_SEPARATOR = ','
    private const val FIELDS = 10
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xFF
    private const val ESCAPE = '%'
    private const val ESCAPE_LENGTH = 3
    private const val ASCII_LIMIT = 128
    private val UNRESERVED = setOf('-', '_', '.', '~')
    private const val HEX_DIGITS = "0123456789ABCDEFabcdef"
    private val INTEGER = Regex("-?\\d+")
    private val COUNT = Regex("\\d+")
    private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

    /**
     * How many layers a document may carry before the rest are treated as unreadable. Far above
     * any real family's count; it bounds the scans `CustodyModel.isEquivalentTo` runs over a
     * document synced, unvalidated, from the other phone.
     */
    const val MAX_LAYERS = 32

    private const val IDX_ID = 1
    private const val IDX_PRIORITY = 2
    private const val IDX_FROM = 3
    private const val IDX_TO = 4
    private const val IDX_ANCHOR = 5
    private const val IDX_CYCLE = 6
    private const val IDX_DAYS = 7
    private const val IDX_WINDOWS = 8
    private const val IDX_NAME = 9

    /** [layer] as its wire string. */
    fun encode(layer: SeasonalLayer): String = listOf(
        VERSION,
        layer.id,
        layer.priority.toString(),
        layer.fromDate.toString(),
        layer.toDate.toString(),
        layer.startDate.toString(),
        layer.patternDays.toString(),
        layer.momDayIndices.sorted().joinToString(LIST_SEPARATOR.toString()),
        ContactWindowCodec.encodeAll(layer.contactWindows).joinToString(LIST_SEPARATOR.toString()),
        percentEncode(layer.name)
    ).joinToString(SEPARATOR.toString())

    /** The layer [value] names, or null when it is not one this build can read. */
    fun decode(value: String): SeasonalLayer? {
        val parts = value.split(SEPARATOR)
        if (parts.size != FIELDS || parts[0] != VERSION) return null
        return runCatching {
            // As strict as `functions/calendar-feed.js`' port, so the two read the same entries.
            require(parts[IDX_PRIORITY].matches(INTEGER) && parts[IDX_CYCLE].matches(COUNT))
            require(listOf(IDX_FROM, IDX_TO, IDX_ANCHOR).all { parts[it].matches(ISO_DATE) })
            require(parts[IDX_DAYS].splitList().all { it.matches(COUNT) })
            val windowsWire = parts[IDX_WINDOWS].splitList()
            val windows = windowsWire.map { requireNotNull(ContactWindowCodec.decode(it)) }
            SeasonalLayer(
                id = parts[IDX_ID],
                name = requireNotNull(percentDecode(parts[IDX_NAME])),
                fromDate = LocalDate.parse(parts[IDX_FROM]),
                toDate = LocalDate.parse(parts[IDX_TO]),
                patternDays = parts[IDX_CYCLE].toInt(),
                momDayIndices = parts[IDX_DAYS].splitList().map { it.toInt() }.toSet(),
                startDate = LocalDate.parse(parts[IDX_ANCHOR]),
                contactWindows = ContactWindowCodec.canonical(windows),
                priority = parts[IDX_PRIORITY].toInt()
            )
        }.getOrNull()
    }

    /**
     * [layers] and [unreadable] as the canonical wire list: every string, sorted, duplicates
     * removed. Always a list — `[]` for none — because a pattern write states what the layers
     * are, and "none" is an answer (item 24's rule for `contactWindows`).
     */
    fun encodeAll(layers: Collection<SeasonalLayer>, unreadable: Collection<String> = emptyList()): List<String> =
        (layers.map(::encode) + unreadable).distinct().sorted()

    /**
     * What a stored list names. A null list is no layers. Readable layers past [MAX_LAYERS] are
     * kept as unreadable rather than dropped, so nothing a newer or busier build wrote is lost.
     */
    fun decodeAll(values: List<*>?): DecodedLayers {
        val strings = values.orEmpty().filterIsInstance<String>().distinct()
        val layers = mutableListOf<SeasonalLayer>()
        val unreadable = mutableListOf<String>()
        strings.sorted().forEach { raw ->
            val layer = decode(raw)
            if (layer != null && layers.size < MAX_LAYERS) layers += layer else unreadable += raw
        }
        return DecodedLayers(layers.sortedWith(compareBy({ it.fromDate }, { it.id })), unreadable)
    }

    private fun String.splitList(): List<String> =
        if (isEmpty()) emptyList() else split(LIST_SEPARATOR)

    /** `encodeURIComponent`, minus the few marks it also leaves alone, so JS decodes it as is. */
    private fun percentEncode(text: String): String = buildString {
        text.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
            val c = (byte.toInt() and BYTE_MASK).toChar()
            if (c.isLetterOrDigit() && c.code < ASCII_LIMIT || c in UNRESERVED) {
                append(c)
            } else {
                append(ESCAPE)
                append((byte.toInt() and BYTE_MASK).toString(HEX_RADIX).uppercase().padStart(2, '0'))
            }
        }
    }

    /** The text [value] percent-encodes, or null when it is not valid percent-encoded UTF-8. */
    private fun percentDecode(value: String): String? = runCatching {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == ESCAPE) {
                val hex = value.substring(i + 1, i + ESCAPE_LENGTH)
                require(hex.all { it in HEX_DIGITS })
                bytes.write(hex.toInt(HEX_RADIX))
                i += ESCAPE_LENGTH
            } else {
                require(c.code < ASCII_LIMIT && (c.isLetterOrDigit() || c in UNRESERVED))
                bytes.write(c.code)
                i++
            }
        }
        StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }.getOrNull()
}
