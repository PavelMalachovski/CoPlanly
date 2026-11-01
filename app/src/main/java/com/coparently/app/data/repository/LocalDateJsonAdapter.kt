package com.coparently.app.data.repository

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.lang.reflect.Type

/**
 * Reads and writes a [LocalDate] as an ISO string.
 *
 * Gson ships no adapter for a date type, and its reflective fallback serialises a date's private
 * fields — a shape that is not a date to anything else and that Android's stricter reflection
 * rules can refuse outright. Every date this project sends to Firestore is already an ISO string,
 * so this keeps one representation rather than adding a second.
 *
 * The date is a kotlinx-datetime one since `Vaccination.date` moved to `:shared` (MON-24), and
 * the text goes through `java.time` on purpose: `java.time.LocalDate.toString` wrote every stored
 * medical profile and pet vaccination list, and `java.time.LocalDate.parse` read them, so the
 * stored bytes, what is accepted and what throws stay exactly as they were.
 * `LocalDateJsonAdapterParityTest` holds this to the `java.time` adapter it replaced.
 */
class LocalDateJsonAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

    override fun serialize(
        src: LocalDate?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement = JsonPrimitive(src?.toJavaLocalDate()?.toString())

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): LocalDate? = json?.asString?.takeIf { it.isNotBlank() }
        ?.let(java.time.LocalDate::parse)
        ?.toKotlinLocalDate()
}
