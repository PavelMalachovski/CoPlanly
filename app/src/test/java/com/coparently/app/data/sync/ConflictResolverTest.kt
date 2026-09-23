package com.coparently.app.data.sync

import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.domain.events.EventTimestamp
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * [ConflictResolver.resolveEventConflict] across two time zones (MON-4).
 *
 * The two parents' phones are driven explicitly — one in Prague, one in New York — the way
 * `ChatReadStateTimeZoneTest` drives chat, rather than by whatever zone the test machine is in.
 * Each side's row is what that phone's save would have written: its own wall clock in
 * `updatedAt`, and the instant that wall clock names in `updatedAtMillis`.
 */
class ConflictResolverTest {

    private val resolver = ConflictResolver()
    private val prague = ZoneId.of("Europe/Prague")
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `the later edit in real time wins, whatever the two wall clocks say`() {
        // Prague saves at 12:00 (10:00Z); New York saves at 09:00 the same morning (13:00Z) —
        // three hours later. The naive comparison read 12:00 > 09:00 and kept Prague's edit.
        val local = savedAt(LocalDateTime.of(2026, 9, 23, 12, 0), prague, by = ALICE)
        val remote = savedAt(LocalDateTime.of(2026, 9, 23, 9, 0), newYork, by = BOB)

        val resolution = resolver.resolveEventConflict(local, remote, currentUserId = ALICE)

        assertEquals(REMOTE, resolution.kept())
        assertEquals("New York's title", resolution.chosen().title)
    }

    @Test
    fun `and the other way round keeps the local edit`() {
        // New York saves at 06:00 (10:00Z); Prague at 11:00 (09:00Z) is an hour *earlier* —
        // so on the New York phone its own edit must survive, although 06:00 < 11:00.
        val local = savedAt(LocalDateTime.of(2026, 9, 23, 6, 0), newYork, by = BOB)
        val remote = savedAt(LocalDateTime.of(2026, 9, 23, 11, 0), prague, by = ALICE)

        val resolution = resolver.resolveEventConflict(local, remote, currentUserId = BOB)

        assertEquals(LOCAL, resolution.kept())
        assertEquals("New York's title", resolution.chosen().title)
    }

    @Test
    fun `a document read off the wire is compared by the instant it names`() {
        // The remote side as the sync actually builds it: through `EventDocument`, from the UTC
        // text an upgraded build wrote. 13:00Z beats a local save at 12:00 Prague time (10:00Z).
        val local = savedAt(LocalDateTime.of(2026, 9, 23, 12, 0), prague, by = ALICE)
        val remote = EventDocument.toEntity(document(updatedAt = "2026-09-23T13:00:00"))

        val resolution = resolver.resolveEventConflict(local, remote, currentUserId = ALICE)

        assertEquals(REMOTE, resolution.kept())
    }

    @Test
    fun `the same instant is a tie, and a tie keeps this user's change`() {
        // 12:00 in Prague and 06:00 in New York are the same moment — which the naive
        // comparison could never have seen as a tie.
        val local = savedAt(LocalDateTime.of(2026, 9, 23, 12, 0), prague, by = ALICE)
        val remote = savedAt(LocalDateTime.of(2026, 9, 23, 6, 0), newYork, by = BOB)

        assertEquals(LOCAL, resolver.resolveEventConflict(local, remote, currentUserId = ALICE).kept())
        assertEquals(REMOTE, resolver.resolveEventConflict(local, remote, currentUserId = BOB).kept())
    }

    @Test
    fun `an undated row loses to any dated one`() {
        // A row whose stored wall clock the 38-to-39 migration could not read lands on the
        // epoch; it must never be judged newer than an edit that has a real time.
        val local = savedAt(LocalDateTime.of(2026, 9, 23, 12, 0), prague, by = ALICE)
            .copy(updatedAtMillis = EventTimestamp.UNDATED)
        val remote = savedAt(LocalDateTime.of(2020, 1, 1, 0, 0), newYork, by = BOB)

        assertEquals(REMOTE, resolver.resolveEventConflict(local, remote, currentUserId = ALICE).kept())
    }

    /** Which side a resolution kept. */
    private fun ConflictResolution<EventEntity>.kept(): String = when (this) {
        is ConflictResolution.UseLocal -> LOCAL
        is ConflictResolution.UseRemote -> REMOTE
        is ConflictResolution.Merged -> MERGED
    }

    /** The row a resolution kept. */
    private fun ConflictResolution<EventEntity>.chosen(): EventEntity = when (this) {
        is ConflictResolution.UseLocal -> data
        is ConflictResolution.UseRemote -> data
        is ConflictResolution.Merged -> data
    }

    /** The row a phone in [zone] writes when its parent saves at [wallClock]. */
    private fun savedAt(wallClock: LocalDateTime, zone: ZoneId, by: String) = EventEntity(
        id = EVENT_ID,
        title = if (zone == newYork) "New York's title" else "Prague's title",
        startDateTime = LocalDateTime.of(2026, 10, 1, 15, 0),
        eventType = "pickup",
        parentOwner = "mom",
        createdAt = LocalDateTime.of(2026, 9, 1, 9, 0),
        updatedAt = wallClock,
        updatedAtMillis = EventTimestamp.ofWallClock(wallClock, zone),
        lastModifiedBy = by
    )

    private fun document(updatedAt: String) = mapOf<String, Any?>(
        "id" to EVENT_ID,
        "title" to "New York's title",
        "startDateTime" to "2026-10-01T15:00:00",
        "eventType" to "pickup",
        "parentOwner" to "mom",
        "createdAt" to "2026-09-01T09:00:00",
        "updatedAt" to updatedAt,
        "lastModifiedBy" to BOB
    )

    private companion object {
        const val EVENT_ID = "e1"
        const val ALICE = "alice"
        const val BOB = "bob"
        const val LOCAL = "local"
        const val REMOTE = "remote"
        const val MERGED = "merged"
    }
}
