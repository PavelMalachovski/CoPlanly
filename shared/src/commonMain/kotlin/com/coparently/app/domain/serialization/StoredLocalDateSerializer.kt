package com.coparently.app.domain.serialization

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A stored date as ISO text (`2026-05-01`), read and written as the Gson adapter it replaced
 * (`LocalDateJsonAdapter`, MON-24) read and wrote it: `Vaccination.date` in a child's medical
 * profile and a pet's vaccination list.
 *
 * A null date is left out of the text. Reading, `null` and a blank text are no date, and anything
 * else is the text Gson's `getAsString` gave the adapter — a string, a number or a boolean as
 * written, the one element of a one-element array — parsed as ISO, so a value that failed to read
 * before fails now. On Android the text goes through `java.time`, which wrote and read every stored
 * date; on the iPhone through kotlinx-datetime ([isoDateText], [parseIsoDate]).
 */
object StoredLocalDateSerializer : KSerializer<LocalDate?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.coparently.app.domain.serialization.StoredLocalDate", PrimitiveKind.STRING)
            .nullable

    override fun serialize(encoder: Encoder, value: LocalDate?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(isoDateText(value))
    }

    override fun deserialize(decoder: Decoder): LocalDate? {
        val text = if (decoder is JsonDecoder) {
            gsonText(decoder.decodeJsonElement())
        } else {
            if (decoder.decodeNotNullMark()) decoder.decodeString() else decoder.decodeNull()
        }
        return text?.takeIf { it.isNotBlank() }?.let(::parseIsoDate)
    }

    /** Gson's `JsonElement.getAsString`, with `null` as no text. */
    private fun gsonText(element: JsonElement): String? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> element.content
        is JsonArray -> {
            check(element.size == 1) { "Array must have size 1, but has size ${element.size}" }
            val only = element.single()
            if (only === JsonNull) throw UnsupportedOperationException("JsonNull")
            gsonText(only)
        }
        is JsonObject -> throw UnsupportedOperationException("JsonObject")
    }
}

/** [date] as ISO text, as the platform's stored dates have always been written. */
internal expect fun isoDateText(date: LocalDate): String

/** ISO [text] as a date, accepting and refusing what the platform's stored dates always did. */
internal expect fun parseIsoDate(text: String): LocalDate
