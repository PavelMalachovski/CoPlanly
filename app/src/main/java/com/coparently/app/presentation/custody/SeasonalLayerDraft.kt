package com.coparently.app.presentation.custody

import com.coparently.app.domain.custody.ContactWindow
import com.coparently.app.domain.custody.SeasonalLayer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The shapes a seasonal layer can be given from the editor (MON-14). The editor offers presets
 * rather than a second copy of the base pattern's day grid: a summer or a Christmas is almost
 * always one of these three, and a parent who needs another shape can still describe it as
 * several layers.
 */
enum class LayerShape {
    /** Keep the layer's current pattern — only offered when editing one. */
    KEEP,

    /** The whole range with one parent. */
    ALL_WITH,

    /** A week each, alternating. */
    ALTERNATING_WEEKS,

    /** The first half with one parent, the second with the other. */
    SPLIT_IN_HALF
}

/**
 * What the seasonal-layer editor holds before it is saved.
 *
 * @property name What the parents call it.
 * @property from First day.
 * @property to Last day, inclusive.
 * @property shape How the days are shared.
 * @property firstSlot The slot that has the whole range ([LayerShape.ALL_WITH]) or starts it.
 */
data class SeasonalLayerDraft(
    val name: String = "",
    val from: LocalDate,
    val to: LocalDate,
    val shape: LayerShape = LayerShape.SPLIT_IN_HALF,
    val firstSlot: String = ContactWindow.SLOT_ONE
) {
    /** Days covered, both ends included. */
    val spanDays: Long get() = ChronoUnit.DAYS.between(from, to) + 1

    /** Whether the draft can become a layer: a name, a forward range, at most a year. */
    val isValid: Boolean
        get() = name.isNotBlank() && name.trim().length <= SeasonalLayer.MAX_NAME_LENGTH &&
            !to.isBefore(from) && spanDays <= SeasonalLayer.MAX_LAYER_DAYS

    /**
     * The layer this draft describes, under [id]. [existing] supplies the pattern for
     * [LayerShape.KEEP] and the priority; a KEEP whose cycle no longer fits is refused (null).
     */
    fun toLayer(id: String, existing: SeasonalLayer?): SeasonalLayer? {
        if (!isValid) return null
        val range = from..to
        val trimmed = name.trim()
        val layer = when (shape) {
            LayerShape.KEEP -> existing?.let {
                runCatching { it.copy(name = trimmed, fromDate = from, toDate = to) }.getOrNull()
            }
            LayerShape.ALL_WITH -> SeasonalLayer.allWith(id, trimmed, range, firstSlot)
            LayerShape.ALTERNATING_WEEKS -> SeasonalLayer.alternatingWeeks(id, trimmed, range, firstSlot)
            LayerShape.SPLIT_IN_HALF -> SeasonalLayer.splitInHalf(id, trimmed, range, firstSlot)
        }
        return layer?.copy(priority = existing?.priority ?: 0)
    }

    companion object {
        /** A draft opened on an existing layer: its dates and name, its pattern kept. */
        fun of(layer: SeasonalLayer): SeasonalLayerDraft = SeasonalLayerDraft(
            name = layer.name,
            from = layer.fromDate,
            to = layer.toDate,
            shape = LayerShape.KEEP
        )
    }
}
