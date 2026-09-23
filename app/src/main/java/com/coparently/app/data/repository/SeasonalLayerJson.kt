package com.coparently.app.data.repository

import com.coparently.app.domain.custody.DecodedLayers
import com.coparently.app.domain.custody.SeasonalLayer
import com.coparently.app.domain.custody.SeasonalLayerCodec
import com.google.gson.Gson

/**
 * Room's form of a pattern's seasonal layers (MON-14): a JSON array of the [SeasonalLayerCodec]
 * strings, the same strings the Firestore document carries — unreadable entries included,
 * verbatim, so a row never loses a layer a newer build wrote.
 *
 * Gson only ever sees an array of strings, never the [SeasonalLayer] data class, so R8 has no
 * field names to rename. [ContactWindowJson]'s two rules hold: none is stored as null, and an
 * unreadable column degrades to no layers rather than throwing on the path that paints the grid.
 */
object SeasonalLayerJson {

    private val gson = Gson()

    /** The layers as JSON, or null when there are none. */
    fun encode(layers: List<SeasonalLayer>, unreadable: List<String>): String? =
        SeasonalLayerCodec.encodeAll(layers, unreadable)
            .takeIf { it.isNotEmpty() }
            ?.let { gson.toJson(it) }

    /** [json] as layers, or none when it is null, blank or unreadable. */
    fun decode(json: String?): DecodedLayers {
        if (json.isNullOrBlank()) return DecodedLayers()
        val values = runCatching { gson.fromJson(json, Array<String>::class.java) }.getOrNull()
        return SeasonalLayerCodec.decodeAll(values?.toList())
    }
}
