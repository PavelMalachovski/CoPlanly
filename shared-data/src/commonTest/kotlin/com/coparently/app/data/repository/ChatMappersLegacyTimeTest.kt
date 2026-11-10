package com.coparently.app.data.repository

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A legacy message timestamp — a naive ISO wall clock — at the two moments a wall clock names no
 * single instant, read in Europe/Prague (MON-24 phase 2, Task 8b).
 *
 * `java.time`'s `atZone` read these until the move: a time in the spring gap moves forward by the
 * gap, and one in the autumn overlap takes the earlier offset. On Android kotlinx-datetime delegates
 * to `atZone`; on the iPhone it resolves the zone itself, so this test, in commonTest, is what holds
 * the simulator to the same answers.
 */
class ChatMappersLegacyTimeTest {

    private val prague = TimeZone.of("Europe/Prague")

    @Test
    fun `a time in the spring gap moves forward by the gap`() {
        val document: Map<String, Any> = mapOf("timestamp" to "2026-03-29T02:30:00")

        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z").toEpochMilliseconds(),
            document.sentAtMillisOrNull("timestamp", prague)
        )
    }

    @Test
    fun `a time in the autumn overlap takes the earlier offset`() {
        val document: Map<String, Any> = mapOf("timestamp" to "2026-10-25T02:30:00")

        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z").toEpochMilliseconds(),
            document.sentAtMillisOrNull("timestamp", prague)
        )
    }
}
