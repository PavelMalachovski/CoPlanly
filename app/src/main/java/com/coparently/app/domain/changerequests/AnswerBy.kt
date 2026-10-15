package com.coparently.app.domain.changerequests

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The ready-made deadlines a proposer can pick (phase 3a, spec §4.2).
 *
 * Each counts from the moment of sending in whole days of the device's wall clock (ruling 15): "A
 * week" sent at ten in the morning is ten in the morning a week on, whichever way the clocks changed
 * in between, and not a count of 168 hours.
 */
enum class AnswerByPreset {
    /** The same time tomorrow. */
    TOMORROW,

    /** The same time in three days. This is what a form starts with. */
    THREE_DAYS,

    /** The same time in a week. */
    WEEK
}

/**
 * The deadline a proposer chose in a form: one of the [AnswerByPreset]s, or a date.
 *
 * It is held as the choice and not as a time, because the time depends on when the request is sent
 * and on its cap. The form keeps the pick, and [AnswerBy.resolve] turns it into a deadline at the
 * moment of sending. A pick travels through a saved state as the text [encode] writes and [decode]
 * reads.
 */
sealed interface AnswerByPick {

    /**
     * One of the presets.
     *
     * @property preset Which one.
     */
    data class Preset(val preset: AnswerByPreset) : AnswerByPick

    /**
     * A date from the date picker. Its deadline is that evening, at [AnswerBy.PICKED_DATE_HOUR], unless
     * that is less than two hours away: such a date reads the Tomorrow time instead (ruling 11).
     *
     * @property date The day the proposer picked.
     */
    data class OnDate(val date: LocalDate) : AnswerByPick

    companion object {
        private const val DATE_PREFIX = "DATE:"

        /** What a form starts with: three days, the owner's default (spec §3). */
        val DEFAULT: AnswerByPick = Preset(AnswerByPreset.THREE_DAYS)

        /** [pick] as text: `TOMORROW`, `THREE_DAYS`, `WEEK`, or `DATE:` and an ISO date. */
        fun encode(pick: AnswerByPick): String = when (pick) {
            is Preset -> pick.preset.name
            is OnDate -> DATE_PREFIX + pick.date
        }

        /** The pick [text] names; [DEFAULT] for a missing one and for anything this cannot read. */
        fun decode(text: String?): AnswerByPick {
            val preset = AnswerByPreset.entries.firstOrNull { it.name == text }
            val date = if (text != null && text.startsWith(DATE_PREFIX)) {
                runCatching { LocalDate.parse(text.removePrefix(DATE_PREFIX)) }.getOrNull()
            } else {
                null
            }
            return when {
                preset != null -> Preset(preset)
                date != null -> OnDate(date)
                else -> DEFAULT
            }
        }
    }
}

/**
 * How far off the cap is, and so what a proposer is offered (spec §4.2).
 */
enum class AnswerByBand {
    /** The cap is 48 hours or more away, or there is none: the presets and a date to pick. */
    CHOICES,

    /** The cap is 2 to 48 hours away: no choice, one deadline halfway there. */
    FIXED,

    /** The cap is under 2 hours away: no deadline, and the request goes out as a plain one. */
    TOO_SOON
}

/**
 * One preset, as it reads for this request.
 *
 * @property pick Which preset this chip stands for. A chip that other presets were merged into keeps
 *   the first of them.
 * @property answerByMillis The deadline the preset gives, in epoch millis.
 * @property compressed Whether the cap shortened it: the preset would have ended later than the
 *   latest time, so it reads that time instead.
 */
data class AnswerByChip(val pick: AnswerByPick, val answerByMillis: Long, val compressed: Boolean)

/**
 * What a proposer is offered for one request at one moment: the output of [AnswerBy.offer].
 *
 * @property band Which of the three offers this is.
 * @property nowMillis The moment the offer was made for.
 * @property chips [AnswerByBand.CHOICES] only: the presets, soonest first, with the ones that land on
 *   the same time merged into one.
 * @property fixedMillis [AnswerByBand.FIXED] only: the one deadline.
 * @property latestMillis [AnswerByBand.CHOICES] only: the latest a picked date may read.
 */
data class AnswerByOffer(
    val band: AnswerByBand,
    val nowMillis: Long,
    val chips: List<AnswerByChip> = emptyList(),
    val fixedMillis: Long? = null,
    val latestMillis: Long? = null
)

/**
 * The deadline a proposer can choose, and what the request's cap does to it (phase 3a, spec §4.2).
 *
 * The cap ([RequestCap]) is the first moment the request would change something. The whole chain,
 * last chance included, ends before it, so a request about tomorrow compresses the chain instead of
 * being refused. With `lead = cap - now` and `L = cap - 24 h`, the latest deadline that still leaves
 * a day for a last chance:
 * - `lead < 2 h`: [AnswerByBand.TOO_SOON], no deadline;
 * - `lead < 48 h`: [AnswerByBand.FIXED], one deadline halfway to the cap, rounded down;
 * - otherwise [AnswerByBand.CHOICES]: each preset ends no later than `L`, and presets that land on
 *   the same time are one chip.
 *
 * Every deadline is on a quarter hour of the device's clock, so it reads "by Tue 13 Oct, 10:15": a
 * preset is rounded up, and a limit (`L`, halfway, 30 days) is rounded down so that it never passes
 * what it limits. Nothing here reads the time or the zone itself; both are passed in, so a test can
 * stand at any moment and a screen shows what its sender will send.
 */
object AnswerBy {

    /** The hour a picked date's deadline reads: 20:00, the evening of that day. */
    const val PICKED_DATE_HOUR = 20

    /** A quarter of an hour: every deadline is a multiple of this on the device's clock. */
    const val QUARTER_HOUR_MILLIS = 900_000L

    /** A cap closer than this gets no deadline: 2 hours. */
    const val NO_DEADLINE_UNDER_MILLIS = 7_200_000L

    /** A cap this far or further gets the full choice: 48 hours. */
    const val FULL_CHOICE_FROM_MILLIS = 172_800_000L

    /** What a deadline leaves before the cap, for a last chance to fit: 24 hours. */
    const val LAST_CHANCE_RESERVE_MILLIS = 86_400_000L

    /**
     * The furthest a deadline may be from now: 30 days of elapsed time. `firestore.rules` allows 31
     * (spec §5.2) and stays at 31; the client stops a day short. A phone's clock can run ahead of the
     * server's, and a clock the rule refuses leaves the request with no deadline at all (ruling 8), so
     * a pick keeps a day clear of the bound instead of sitting on it. It is counted in elapsed time, as
     * the rule counts it; wall-clock days would pass it by an hour when the clocks go back.
     */
    const val MAX_AHEAD_MILLIS = 2_592_000_000L

    private const val SHORT_PRESET_DAYS = 3L
    private const val LONG_PRESET_DAYS = 7L

    /**
     * What a proposer is offered at [nowMillis] for a request with the given cap.
     *
     * @param capMillis The first moment the request would change something ([RequestCap]), or null
     *   when nothing ahead would; a cap already behind [nowMillis] offers no deadline.
     * @param zone The zone of the device the proposer holds: it decides the wall-clock day a preset
     *   adds and the quarter hour a time is rounded to.
     */
    fun offer(nowMillis: Long, capMillis: Long?, zone: ZoneId): AnswerByOffer = when {
        capMillis == null -> choices(nowMillis, null, zone)
        capMillis - nowMillis < NO_DEADLINE_UNDER_MILLIS -> AnswerByOffer(AnswerByBand.TOO_SOON, nowMillis)
        capMillis - nowMillis < FULL_CHOICE_FROM_MILLIS -> AnswerByOffer(
            band = AnswerByBand.FIXED,
            nowMillis = nowMillis,
            fixedMillis = quarterDown(nowMillis + (capMillis - nowMillis) / 2, zone)
        )
        else -> choices(nowMillis, capMillis, zone)
    }

    /**
     * The deadline [pick] gives, in epoch millis, or null when the request gets none.
     *
     * What the offer is decides how much of the pick counts:
     * - [AnswerByBand.TOO_SOON] gives null, whatever the pick;
     * - [AnswerByBand.FIXED] gives its one deadline, whatever the pick;
     * - [AnswerByBand.CHOICES] gives a preset its chip's time, merged or not. A date reads its
     *   evening, and is clamped rather than refused (ruling 11): one whose evening is less than
     *   [NO_DEADLINE_UNDER_MILLIS] away, the band's own minimum, reads the Tomorrow time (today stays
     *   pickable until two hours before its 20:00, and a date that has gone by is always too near),
     *   and one past the latest time reads that.
     */
    fun resolve(pick: AnswerByPick, nowMillis: Long, capMillis: Long?, zone: ZoneId): Long? {
        val offer = offer(nowMillis, capMillis, zone)
        return when (offer.band) {
            AnswerByBand.TOO_SOON -> null
            AnswerByBand.FIXED -> offer.fixedMillis
            AnswerByBand.CHOICES -> offer.latestMillis?.let { pickMillis(pick, nowMillis, it, zone) }
        }
    }

    private fun choices(nowMillis: Long, capMillis: Long?, zone: ZoneId): AnswerByOffer {
        val latest = latestMillis(nowMillis, capMillis, zone)
        val chips = AnswerByPreset.entries
            .map { preset ->
                val wanted = presetMillis(preset, nowMillis, zone)
                AnswerByChip(AnswerByPick.Preset(preset), minOf(wanted, latest), compressed = wanted > latest)
            }
            .distinctBy { it.answerByMillis }
        return AnswerByOffer(AnswerByBand.CHOICES, nowMillis, chips = chips, latestMillis = latest)
    }

    /**
     * The latest a deadline may read: a day before the cap, so a last chance still fits, and never
     * past [MAX_AHEAD_MILLIS]. Both are rounded down to a quarter hour, so that neither is passed.
     */
    private fun latestMillis(nowMillis: Long, capMillis: Long?, zone: ZoneId): Long {
        val farthest = quarterDown(nowMillis + MAX_AHEAD_MILLIS, zone)
        if (capMillis == null) return farthest
        return minOf(quarterDown(capMillis - LAST_CHANCE_RESERVE_MILLIS, zone), farthest)
    }

    private fun pickMillis(pick: AnswerByPick, nowMillis: Long, latest: Long, zone: ZoneId): Long = when (pick) {
        is AnswerByPick.Preset -> minOf(presetMillis(pick.preset, nowMillis, zone), latest)
        is AnswerByPick.OnDate -> {
            val evening = pick.date.atTime(PICKED_DATE_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
            val chosen = if (evening - nowMillis < NO_DEADLINE_UNDER_MILLIS) {
                presetMillis(AnswerByPreset.TOMORROW, nowMillis, zone)
            } else {
                evening
            }
            minOf(chosen, latest)
        }
    }

    /** [preset] counted from [nowMillis] in wall-clock days of [zone], then rounded up (ruling 15). */
    private fun presetMillis(preset: AnswerByPreset, nowMillis: Long, zone: ZoneId): Long {
        val days = when (preset) {
            AnswerByPreset.TOMORROW -> 1L
            AnswerByPreset.THREE_DAYS -> SHORT_PRESET_DAYS
            AnswerByPreset.WEEK -> LONG_PRESET_DAYS
        }
        val later = Instant.ofEpochMilli(nowMillis).atZone(zone).plusDays(days).toInstant().toEpochMilli()
        return quarterUp(later, zone)
    }

    /**
     * [millis] rounded up to the next moment [zone]'s clock reads a quarter hour; a time already on
     * a quarter is unchanged. Working on the zone's offset at [millis] keeps this monotonic across a
     * clock change: the result is never before [millis].
     */
    private fun quarterUp(millis: Long, zone: ZoneId): Long {
        val offset = offsetMillis(millis, zone)
        return (millis + offset + QUARTER_HOUR_MILLIS - 1).floorDiv(QUARTER_HOUR_MILLIS) * QUARTER_HOUR_MILLIS - offset
    }

    /** [millis] rounded down to the last moment [zone]'s clock read a quarter hour; the mirror of [quarterUp]. */
    private fun quarterDown(millis: Long, zone: ZoneId): Long {
        val offset = offsetMillis(millis, zone)
        return (millis + offset).floorDiv(QUARTER_HOUR_MILLIS) * QUARTER_HOUR_MILLIS - offset
    }

    private fun offsetMillis(millis: Long, zone: ZoneId): Long =
        TimeUnit.SECONDS.toMillis(zone.rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds.toLong())
}
