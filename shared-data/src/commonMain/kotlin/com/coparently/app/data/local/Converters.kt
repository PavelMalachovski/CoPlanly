package com.coparently.app.data.local

import androidx.room.TypeConverter
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Type converters for Room database.
 * Converts custom types to and from database-compatible types.
 *
 * **The stored text is fixed** (MON-24 phase 2). The date-time columns hold what
 * `java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME` wrote on every phone before the entities
 * moved to common code, and the date columns what `ISO_LOCAL_DATE` wrote: [IsoText] writes and reads
 * exactly that, on Android and on the iPhone alike. It matters beyond display — a row is compared
 * whole by the sync, an older build reads what this one writes, and the calendar's range queries
 * compare these columns as text — so `ConvertersParityTest` holds both directions to `java.time`.
 * Never write these columns through `kotlinx.datetime`'s own `toString()`: it prints a fraction in
 * groups of three, where `ISO_LOCAL_DATE_TIME` trims it.
 */
class Converters {

    /**
     * Converts a LocalDateTime to a String for database storage: `ISO_LOCAL_DATE_TIME`'s text,
     * the second always printed and a fraction only when there is one, trimmed.
     */
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.let(IsoText::dateTime)
    }

    /**
     * Converts a String to a LocalDateTime from database storage, reading what
     * `LocalDateTime.parse(text, ISO_LOCAL_DATE_TIME)` read.
     */
    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let(IsoText::parseDateTime)
    }

    /**
     * Converts a List<String> to a JSON String for database storage.
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return StoredJson.encode(StoredJson.strings, value ?: emptyList())
    }

    /**
     * Converts a JSON String to a List<String> from database storage.
     */
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return StoredJson.decodeOrNull(StoredJson.strings, value).orEmpty()
    }

    /**
     * Converts a LocalDate to a String for database storage: `ISO_LOCAL_DATE`'s text.
     */
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.let(IsoText::date)
    }

    /**
     * Converts a String to a LocalDate from database storage, reading what
     * `LocalDate.parse(text, ISO_LOCAL_DATE)` read.
     */
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let(IsoText::parseDate)
    }
}
