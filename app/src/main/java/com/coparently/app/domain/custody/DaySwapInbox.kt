package com.coparently.app.domain.custody

import java.time.LocalDate

/**
 * One day swap, with its date resolved.
 *
 * @property date The day being swapped.
 * @property override What was offered, and where it stands.
 */
data class DaySwap(val date: LocalDate, val override: DayOverride)

/**
 * Which swaps the inbox shows, and in what order.
 *
 * Pure, and tested as such, because it decides what a parent is shown about an agreement — the
 * same reason [DayOverrideTransition] is pure.
 *
 * **Bounded by the date, not by the status.** The stored map only ever grows: nothing deletes an
 * answered swap, because the answer is the record the parent who offered reads. Filtering on
 * "pending" instead would bound the list, but it would also mean a declined offer vanished
 * without ever being shown to the parent who made it — silent, which is the one thing this whole
 * family of features exists not to be. Dropping days that have already passed bounds it honestly
 * instead: a swap of a day already lived is history, and history is what the calendar is for.
 */
object DaySwapInbox {

    /**
     * The swaps worth showing, soonest first.
     *
     * @param overrides The pair's swaps, keyed by ISO date.
     * @param today The day to measure "already passed" against. Passed in rather than read from
     *   the clock so this stays pure and the boundary case is testable.
     * @return Swaps on [today] or later, oldest date first. A key that is not an ISO date is
     *   dropped rather than guessed at — the same rule the Firestore reader applies.
     */
    fun visible(overrides: Map<String, DayOverride>, today: LocalDate): List<DaySwap> =
        overrides.entries
            .mapNotNull { (iso, override) ->
                val date = runCatching { LocalDate.parse(iso) }.getOrNull()
                    ?: return@mapNotNull null
                DaySwap(date, override).takeIf { !date.isBefore(today) }
            }
            .sortedBy { it.date }

    /**
     * Whether [uid] is the one being asked to answer [swap] — which the parent who offered it
     * never is.
     */
    fun awaitsAnswerFrom(swap: DaySwap, uid: String): Boolean =
        swap.override.isPending && swap.override.requestedBy != uid

    /**
     * The same swaps, folded into the offers they were actually made as.
     *
     * A run of days offered together is one agreement, and until it was grouped the co-parent
     * got one inbox card, one modal dialog and one push **per day** — five of each for a school
     * holiday week, each asking the same question. Grouping is done here rather than in storage
     * for the reason [DayOverride.groupId] gives: `firestore.rules` can only validate a write
     * that names one date.
     *
     * An entry with no group id is its own group, which is what every swap written before groups
     * existed is, and what a single-day swap from an older build still is.
     *
     * @param overrides The pair's swaps, keyed by ISO date.
     * @param today The day to measure "already passed" against.
     * @return Groups ordered by their first day, each group's days ordered within it.
     */
    fun groups(overrides: Map<String, DayOverride>, today: LocalDate): List<DaySwapGroup> =
        group(visible(overrides, today))

    /**
     * Every offer, whatever its days' dates — the inbox's history, where a swap of a day already
     * lived is still the record of how it was answered. Grouped exactly as [groups] groups.
     *
     * @param overrides The pair's swaps, keyed by ISO date. A key that is not a date is dropped,
     *   as [visible] drops it.
     */
    fun allGroups(overrides: Map<String, DayOverride>): List<DaySwapGroup> =
        group(
            overrides.entries.mapNotNull { (iso, override) ->
                runCatching { LocalDate.parse(iso) }.getOrNull()?.let { DaySwap(it, override) }
            }
        )

    /** One group per offer, ordered by its first day, each group's days ordered within it. */
    private fun group(swaps: List<DaySwap>): List<DaySwapGroup> =
        // A null id must not collapse every ungrouped swap into one card, so each such day keys
        // on its own date instead.
        swaps
            .groupBy { it.override.groupId ?: "single:${it.date}" }
            .values
            .map { DaySwapGroup(it.sortedBy { swap -> swap.date }) }
            .sortedBy { it.firstDate }
}

/**
 * One offer, however many days it covers.
 *
 * @property swaps The days of the offer, soonest first. Never empty.
 */
data class DaySwapGroup(val swaps: List<DaySwap>) {

    /** The offer's first day, which is what the inbox orders on. */
    val firstDate: LocalDate get() = swaps.first().date

    /** The offer's last day. Equal to [firstDate] for a single-day swap. */
    val lastDate: LocalDate get() = swaps.last().date

    /** How many days the offer covers. */
    val dayCount: Int get() = swaps.size

    /**
     * A stable key for this group, for a `LazyColumn` and for the "put it off" set on Home.
     *
     * The group id when there is one, else the single date — so a dismissed card does not come
     * back under a new key on the next recomposition.
     */
    val key: String get() = swaps.first().override.groupId ?: "single:$firstDate"

    /**
     * [key] plus what the offer says — each day, who it moves to, and when it was asked — so a
     * parent who put the offer off ("Later") is asked again once it changes, and not before
     * (release audit R-2).
     */
    val revision: String
        get() = key + "@" + swaps.joinToString(",") {
            "${it.date}:${it.override.toParent}:${it.override.requestedAt}"
        }

    /** The ISO dates the offer covers, which is what a group answer is applied to. */
    val dates: List<String> get() = swaps.map { it.date.toString() }

    /** Whether [uid] is the one being asked. True when any day of the offer is waiting on them. */
    fun awaitsAnswerFrom(uid: String): Boolean =
        swaps.any { DaySwapInbox.awaitsAnswerFrom(it, uid) }

    /** Whether [uid] made this offer and may still take it back: any of its days is theirs and unanswered. */
    fun withdrawableBy(uid: String): Boolean =
        swaps.any { it.override.isPending && it.override.requestedBy == uid }

    /**
     * Which way the offer moves its days, from the side of the parent who made it (second audit:
     * the inbox said "Bob is offering you a day" over a day Bob was asking for). Since 21 October
     * an offer may give some days and take others.
     *
     * Decided from the reader's own slot first, because that is the one slot this phone always
     * holds (its own Room row); the co-parent's is used only when the reader's is not known. Home's
     * swap dialog and the inbox card both ask here, so they cannot word one offer two ways again
     * (UX audit H1: the dialog said "Bob gets the day" while the card said "Bob is offering you 1
     * day", because the card read an unknown offerer slot as "giving").
     *
     * @param offeredByReader Whether the parent reading is the one who made the offer.
     * @param readerSlot The reading parent's slot, or null when it is not known.
     * @param coParentSlot The other parent's slot, or null when it is not known.
     * @return [SwapDirection.UNKNOWN] when neither slot is known, or when both parents still read
     *   the same slot — never a guess.
     */
    fun direction(offeredByReader: Boolean, readerSlot: String?, coParentSlot: String?): SwapDirection {
        if (readerSlot != null && readerSlot == coParentSlot) return SwapDirection.UNKNOWN
        val toOfferer = when {
            readerSlot != null -> swaps.count { (it.override.toParent == readerSlot) == offeredByReader }
            !offeredByReader && coParentSlot != null -> swaps.count { it.override.toParent == coParentSlot }
            else -> return SwapDirection.UNKNOWN
        }
        return when (toOfferer) {
            0 -> SwapDirection.GIVES
            swaps.size -> SwapDirection.ASKS
            else -> SwapDirection.BOTH
        }
    }
}

/** Which way a day-swap offer moves its days, from the offering parent's side. */
enum class SwapDirection {
    /** Every day goes to the other parent: the offerer gives them. */
    GIVES,

    /** Every day goes to the offerer: they ask for them. */
    ASKS,

    /** Some days go each way. */
    BOTH,

    /** Neither parent's slot can be told apart yet, so the offer is described without a direction. */
    UNKNOWN
}
