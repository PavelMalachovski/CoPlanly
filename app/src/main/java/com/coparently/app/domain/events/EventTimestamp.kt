package com.coparently.app.domain.events

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * When an event was last saved, as the instant two phones compare and as the text the
 * `events.updatedAt` document field carries (MON-4, the part left from the owner's answer 3).
 *
 * `ConflictResolver` decides which phone's copy of an event survives a sync conflict by this
 * value. It used to compare `EventEntity.updatedAt` — a naive `LocalDateTime`, no zone, no
 * offset — so two parents in different time zones did not order their edits by real time, and
 * the parent whose clock read later kept their edit whether or not it was. That is SEC-4's defect
 * in another table, and this is SEC-4's fix: read
 * [com.coparently.app.domain.custody.CustodyTimestamp] first, which explains the shape.
 *
 * **The field's name and its type on the wire are unchanged.** `updatedAt` is still an ISO
 * date-time string with no offset; what changed is that the string now expresses **UTC** rather
 * than the writer's wall clock. Adding an offset (`…Z`) would have been the tidier encoding and
 * is deliberately not used: an older build parses this field with `ISO_LOCAL_DATE_TIME`, which
 * rejects an offset, and its sync skips a document it cannot parse — every event this build wrote
 * would silently never reach a co-parent who has not updated. A numeric field beside it was
 * rejected too: an older build would ignore it and go on comparing the string, so the document
 * would carry the same fact twice in two forms that can disagree. (The `events` rule does not
 * gate keys with `hasOnly` the way the custody rule does, so it is not what forbids it here.)
 *
 * What this does **not** fix: a value written by an older build carries no offset to recover, so
 * reading it as UTC is wrong by that writer's offset. That is irreducible — the information was
 * never stored. Between two upgraded builds the ordering is exact; against a legacy value it is
 * no worse than the comparison it replaces. The displayed wall clock ([toWallClock]) of a legacy
 * document is shifted by the same offset, once, until the event is next saved.
 *
 * **Children and pets use the same wire form** (schema 40): `child_info` and `pets` carry an
 * `updatedAt` of exactly this shape, `ChildInfoEntity`/`PetEntity` gained the same
 * `updatedAtMillis`, and `ConflictResolver.resolveChildInfoConflict` compares it. One definition
 * rather than a copy per collection, because the three must never disagree about what the string
 * means.
 */
object EventTimestamp {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * What a write of [millis] puts in the document's `updatedAt`: the instant's UTC wall clock,
     * with no offset, in the exact form an older build has always parsed.
     */
    fun toWire(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDateTime().format(formatter)

    /**
     * The instant a document's `updatedAt` names.
     *
     * An offset-free value is read as UTC, which is what an upgraded build writes. A value that
     * does carry an offset is honoured, so a future writer that adds one is read correctly.
     *
     * Throws on text that is neither, exactly as the `LocalDateTime.parse` it replaced did: the
     * events reader treats an undated document as unreadable and skips it rather than inventing a
     * time that would win or lose conflicts it has no business deciding.
     */
    fun fromWire(iso: String): Long =
        runCatching { LocalDateTime.parse(iso, formatter).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrElse { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }

    /**
     * The instant a wall-clock [local] names in [zone] — how a save stamps the instant from the
     * `LocalDateTime.now()` every event write path already sets.
     *
     * Deriving it at the one mapping boundary, rather than asking every save site to set a second
     * field, means a new save path cannot forget it. The one ambiguity is the repeated hour when
     * clocks go back, where the earlier of the two instants is chosen — at worst an hour's error
     * one hour a year, against the zone-sized error on every cross-zone comparison it replaces.
     */
    fun ofWallClock(local: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        local.atZone(zone).toInstant().toEpochMilli()

    /** The wall clock [millis] reads as in [zone], for display — the inverse of [ofWallClock]. */
    fun toWallClock(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    /** A row whose save time is unknown. Loses every comparison. */
    const val UNDATED: Long = 0L
}
