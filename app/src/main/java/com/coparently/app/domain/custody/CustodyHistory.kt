package com.coparently.app.domain.custody

import com.coparently.app.domain.model.CustodyModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Whether this layer is a stretch of an earlier schedule kept by [CustodyHistory], not one the parents made. */
val SeasonalLayer.isHistory: Boolean
    get() = id.startsWith(CustodyHistory.ID_PREFIX)

/**
 * Keeps the past when a paired family's schedule changes (owner decision, 24 October 2026: "change
 * the colour only into the future; the past stays as it was").
 *
 * A custody model is one pattern repeating from its anchor in both directions, so accepting a new
 * one used to repaint every day already lived. Accepting now freezes the outgoing base pattern as
 * **history layers** over the days before the new schedule takes effect — [effectiveFrom], the
 * later of the start date the proposer chose and the day the co-parent accepted.
 *
 * **Layers, not a new key**, because every reader already resolves layers: an older build on the
 * other phone, the calendar feed, the export, Home and the widget draw the kept past with no
 * change, and `firestore.rules` already lets an accept rewrite `seasonalLayers`. A history layer is
 * an ordinary [SeasonalLayer] with three marks:
 * - its id starts with [ID_PREFIX] ([isHistory]), so the layer editor can hide it;
 * - it has the lowest [PRIORITY], so a real layer on a past date (a past summer) still decides it,
 *   while it still beats the new base pattern;
 * - its name is stored data in English ([layerName]) — only an older build's layer list shows it.
 *
 * History reaches back to the outgoing anchor, never before (those days were never a schedule
 * anyone agreed), and at most [MAX_YEARS_BACK] years; it is cut into layers of at most
 * [SeasonalLayer.MAX_LAYER_DAYS] days, because an older build cannot read a longer one; and inside
 * the document's [SeasonalLayerCodec.MAX_LAYERS] the oldest history goes first, never a real layer.
 *
 * Only an accepted proposal freezes (`CustodyProposalTransition.accept`). An unpaired parent's
 * save rewrites the past on purpose — it corrects a setup mistake — and an accepter on an older
 * build makes no history (an accepted loss, owner decision).
 */
object CustodyHistory {

    /** The id prefix of a history layer. Letters and `-` only: it is a field of the wire string. */
    const val ID_PREFIX = "hist-"

    /** A history layer's priority: the lowest a layer may carry. */
    val PRIORITY: Int = SeasonalLayer.PRIORITY_RANGE.first

    /** How far back history reaches from the day a new schedule takes effect. */
    const val MAX_YEARS_BACK = 3L

    private val ID_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    /**
     * The first day [proposed] decides when it is accepted on [acceptedOn]: its own start date, or
     * the acceptance day when that start date has already passed. The calendar's proposal preview
     * starts here too, so it never previews a repainted past.
     */
    fun effectiveFrom(proposed: CustodyModel, acceptedOn: LocalDate): LocalDate =
        maxOf(proposed.startDate, acceptedOn)

    /** The stored name of a history layer ending on [until]. Stored data, never translated. */
    fun layerName(until: LocalDate): String = "Earlier schedule until $until"

    /**
     * [incoming] with the days before its effective date kept as [outgoing] drew them.
     *
     * Unchanged when the two base patterns agree (nothing in the past would move) or when the
     * outgoing pattern cannot be a layer. Existing history is kept, cut short where it reaches
     * the new effective date (a change that starts sooner than an earlier one still waiting to).
     */
    fun freeze(outgoing: CustodyModel, incoming: CustodyModel, acceptedOn: LocalDate): CustodyModel {
        if (outgoing.patternDays !in 1..SeasonalLayer.MAX_LAYER_DAYS) return incoming
        if (baseOf(outgoing).isEquivalentTo(baseOf(incoming))) return incoming
        val effective = effectiveFrom(incoming, acceptedOn)
        val lastDay = effective.minusDays(1)

        val kept = (outgoing.seasonalLayers + incoming.seasonalLayers)
            .filter { it.isHistory }
            .distinctBy { it.id }
            .mapNotNull { layer -> cutAt(layer, lastDay) }
        val from = maxOf(
            kept.maxOfOrNull { it.toDate }?.plusDays(1) ?: outgoing.startDate,
            outgoing.startDate,
            effective.minusYears(MAX_YEARS_BACK)
        )
        val added = chunks(from, lastDay).map { range ->
            SeasonalLayer(
                // Named by its own first day: history never overlaps, so no two stretches share
                // it, while two changes may share an effective date (one cut short by a sooner one).
                id = "$ID_PREFIX${range.start.format(ID_DATE)}",
                name = layerName(lastDay),
                fromDate = range.start,
                toDate = range.endInclusive,
                patternDays = outgoing.patternDays,
                momDayIndices = outgoing.momDayIndices.filter { it in 0 until outgoing.patternDays }.toSet(),
                startDate = outgoing.startDate,
                contactWindows = outgoing.contactWindows.filter { it.dayIndex < outgoing.patternDays },
                priority = PRIORITY
            )
        }
        val real = incoming.seasonalLayers.filterNot { it.isHistory }
        val budget = SeasonalLayerCodec.MAX_LAYERS - real.size - incoming.unreadableLayers.size
        val history = (kept + added).sortedBy { it.fromDate }.takeLast(budget.coerceAtLeast(0))
        return incoming.copy(seasonalLayers = real + history)
    }

    /** [model]'s base pattern alone: what a schedule change replaces. */
    private fun baseOf(model: CustodyModel): CustodyModel = model.copy(
        seasonalLayers = emptyList(),
        unreadableLayers = emptyList(),
        childOverrides = emptyList(),
        unreadableChildOverrides = emptyList()
    )

    /** [layer] ending no later than [lastDay], or null when it starts after it. */
    private fun cutAt(layer: SeasonalLayer, lastDay: LocalDate): SeasonalLayer? = when {
        layer.fromDate.isAfter(lastDay) -> null
        layer.toDate.isAfter(lastDay) -> layer.copy(toDate = lastDay)
        else -> layer
    }

    /**
     * [from]..[to] in consecutive stretches of at most a layer's length, oldest first; empty when
     * [from] is after [to].
     */
    private fun chunks(from: LocalDate, to: LocalDate): List<ClosedRange<LocalDate>> {
        val out = mutableListOf<ClosedRange<LocalDate>>()
        var start = from
        while (!start.isAfter(to)) {
            val end = minOf(start.plusDays(SeasonalLayer.MAX_LAYER_DAYS - 1L), to)
            out += start..end
            start = end.plusDays(1)
        }
        return out
    }
}
