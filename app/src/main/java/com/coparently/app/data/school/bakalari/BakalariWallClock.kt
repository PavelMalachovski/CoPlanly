@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.school.bakalari

import com.coparently.app.domain.InternalSharedApi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * An ISO date-time with an offset ("2020-03-02T00:00:00+01:00") as the school's own wall clock:
 * the offset is the school's zone, and the calendar keeps naive local times (CLAUDE.md item 13).
 * Falls back to a date-time without an offset; null when neither parses.
 *
 * The one reader of [BakalariJson] that needs `java.time`, so it stays in the app with the parsers
 * when the rest of the object moved to `:shared-data` (MON-24).
 */
internal fun BakalariJson.wallClock(text: String?): LocalDateTime? {
    val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return parseOrNull { OffsetDateTime.parse(value).toLocalDateTime() }?.toKotlinLocalDateTime()
        ?: parseOrNull { java.time.LocalDateTime.parse(value) }?.toKotlinLocalDateTime()
}

private fun <T> parseOrNull(parse: () -> T): T? = try {
    parse()
} catch (ignored: DateTimeParseException) {
    null
}
