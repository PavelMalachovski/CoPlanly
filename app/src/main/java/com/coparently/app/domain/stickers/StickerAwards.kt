package com.coparently.app.domain.stickers

import com.coparently.app.domain.changerequests.RequestClockOutcome
import com.coparently.app.domain.changerequests.RequestDeadline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * When the app gives a sticker itself (October 2026, owner decisions;
 * `docs/DESIGN-stickers-and-subscription.md`). Pure, so every threshold is a JVM test.
 *
 * - **On time**: this parent answered a request before its deadline. At most once an ISO week —
 *   the sticker's id spells the week, and `firestore.rules` makes it create-once.
 * - **Regularity**: four ISO weeks in a row, each with an on-time sticker, and no request to this
 *   parent left without an answer in them. Given for the fourth week.
 */
object StickerAwards {

    /** How many weeks in a row make "regularity". */
    const val REGULAR_WEEKS = 4

    /** The ISO week of [date], as the sticker ids spell it: `2026-W43`. */
    fun isoWeek(date: LocalDate): String =
        String.format(
            Locale.ROOT,
            "%04d-W%02d",
            date.get(IsoFields.WEEK_BASED_YEAR),
            date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        )

    /** The id of the app's [kind] sticker for [uid] in [week]; one per week, by the rules. */
    fun appStickerId(uid: String, kind: StickerKind, week: String): String = "app_${uid}_${kind.wire}_$week"

    /**
     * Whether [clock] was answered on time by [myUid] when it is answered at [nowMillis]: the
     * request waited on this parent and its deadline has not passed.
     */
    fun answeredOnTime(clock: RequestDeadline, myUid: String, nowMillis: Long): Boolean =
        clock.responderUid == myUid && nowMillis <= clock.answerByMillis

    /**
     * Whether the [REGULAR_WEEKS] weeks ending with the week of [today] earn the regularity
     * sticker: [received] holds an on-time sticker for each of them and no regularity sticker in
     * any of them — one run, one sticker — and none of [clocks] waiting on [myUid] ended without
     * an answer in them.
     */
    fun regular(
        received: List<Sticker>,
        clocks: List<RequestDeadline>,
        myUid: String,
        today: LocalDate,
        zone: ZoneId
    ): Boolean {
        val weeks = (0 until REGULAR_WEEKS).map { isoWeek(today.minusWeeks(it.toLong())) }.toSet()
        if (!weeksOf(received, myUid, StickerKind.ON_TIME).containsAll(weeks)) return false
        if (weeksOf(received, myUid, StickerKind.REGULAR).any { it in weeks }) return false
        return clocks.none { clock ->
            clock.responderUid == myUid &&
                clock.outcome == RequestClockOutcome.NO_ANSWER &&
                isoWeek(Instant.ofEpochMilli(clock.answerByMillis).atZone(zone).toLocalDate()) in weeks
        }
    }

    /** The weeks [stickers] hold the app's [kind] sticker for [myUid], read off their ids. */
    fun weeksOf(stickers: List<Sticker>, myUid: String, kind: StickerKind): Set<String> {
        val prefix = "app_${myUid}_${kind.wire}_"
        return stickers.filter { it.kind == kind && it.id.startsWith(prefix) }
            .map { it.id.removePrefix(prefix) }
            .toSet()
    }
}
