package com.coparently.app.data.local

import androidx.room.TypeConverter
import com.coparently.app.domain.serialization.StoredJson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Type converters for Room database.
 * Converts custom types to and from database-compatible types.
 */
class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Converts a LocalDateTime to a String for database storage.
     */
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.format(formatter)
    }

    /**
     * Converts a String to a LocalDateTime from database storage.
     */
    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, formatter) }
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
     * Converts a LocalDate to a String for database storage.
     */
    @TypeConverter
    fun fromLocalDate(value: java.time.LocalDate?): String? {
        return value?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * Converts a String to a LocalDate from database storage.
     */
    @TypeConverter
    fun toLocalDate(value: String?): java.time.LocalDate? {
        return value?.let { java.time.LocalDate.parse(it, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) }
    }
}
