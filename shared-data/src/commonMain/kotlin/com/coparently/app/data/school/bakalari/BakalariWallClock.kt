package com.coparently.app.data.school.bakalari

import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.LocalDateTime

/**
 * An ISO date-time with an offset ("2020-03-02T00:00:00+01:00") as the school's own wall clock:
 * the offset is the school's zone, and the calendar keeps naive local times (CLAUDE.md item 13).
 * Falls back to a date-time without an offset; null when neither parses.
 *
 * `OffsetDateTime.parse(…).toLocalDateTime()` and then `LocalDateTime.parse(…)` until MON-24, which
 * [IsoText.parseOffsetWallClock] and [IsoText.parseDateTime] read exactly as they did.
 */
internal fun BakalariJson.wallClock(text: String?): LocalDateTime? {
    val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return parseOrNull { IsoText.parseOffsetWallClock(value) } ?: parseOrNull { IsoText.parseDateTime(value) }
}

private fun <T> parseOrNull(parse: () -> T): T? = try {
    parse()
} catch (ignored: IllegalArgumentException) {
    null
}
