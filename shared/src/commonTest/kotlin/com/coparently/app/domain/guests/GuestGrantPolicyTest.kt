package com.coparently.app.domain.guests

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Whether a guest may still read a child's record.
 *
 * Every case here decides access to a child's medical record, so the rule throughout is **fail
 * closed**: anything this cannot make sense of is inactive. An expiry that silently reads as
 * "never" is the failure that leaves a grandparent's weekend access in place for good.
 *
 * The expected milliseconds were computed with `java.time`, which this policy used until MON-24
 * phase 2 (Task 8e).
 */
class GuestGrantPolicyTest {

    private val expiry: Instant = Instant.parse("2026-09-01T12:00:00Z")

    @Test
    fun `a grant is active before it expires`() {
        assertTrue(GuestGrantPolicy.isActive(grant(), at = expiry - 1.seconds))
    }

    @Test
    fun `a grant is inactive after it expires`() {
        assertFalse(GuestGrantPolicy.isActive(grant(), at = expiry + 1.seconds))
    }

    @Test
    fun `a grant is inactive exactly at its expiry`() {
        // The boundary belongs to the closed side. "Expires at noon" reads as "not after noon",
        // and an access token is the wrong place to round in the holder's favour. The rule and
        // the sweep implement the same strict comparison, so all three agree on this instant.
        assertFalse(GuestGrantPolicy.isActive(grant(), at = expiry))
    }

    @Test
    fun `an expiry that is not a time at all is inactive - never active`() {
        // A field that was absent, defaulted, or written by something that did not know it had
        // to fill it in. Reading zero as "no expiry" would turn a weekend into forever.
        assertFalse(GuestGrantPolicy.isActive(grant(expiresAtMillis = 0L), at = expiry))
        assertFalse(GuestGrantPolicy.isActive(grant(expiresAtMillis = -1L), at = expiry))
    }

    @Test
    fun `two readers in different zones agree about the same grant`() {
        // The trap CLAUDE.md records against the custody document's naive local `lastModifiedAt`,
        // which can pick the wrong winner by exactly the offset between two parents. An expiry
        // decides who may read a child's medical record; it must not depend on where the reader
        // is standing. Millis names one instant and there is no zone to get wrong.
        val prague = TimeZone.of("Europe/Prague")
        val ends = LocalDateTime(2026, 9, 1, 14, 0).toInstant(prague)
        val grant = grant(expiresAtMillis = ends.toEpochMilliseconds())
        val inPrague = LocalDateTime(2026, 9, 1, 13, 0).toInstant(prague)
        val inTokyo = LocalDateTime(2026, 9, 1, 20, 0).toInstant(TimeZone.of("Asia/Tokyo"))

        assertEquals(inPrague, inTokyo)
        assertTrue(GuestGrantPolicy.isActive(grant, at = inPrague))
        assertTrue(GuestGrantPolicy.isActive(grant, at = inTokyo))

        assertFalse(GuestGrantPolicy.isActive(grant, at = inPrague + 2.hours))
    }

    @Test
    fun `the default expiry is thirty days out`() {
        val granted = Instant.parse("2026-08-23T09:30:00Z")

        assertEquals(1_790_069_400_000L, GuestGrantPolicy.defaultExpiry(from = granted))
    }

    @Test
    fun `a grant written by defaultExpiry is active now and inactive in thirty-one days`() {
        val granted = Instant.parse("2026-08-23T09:30:00Z")
        val grant = grant(expiresAtMillis = GuestGrantPolicy.defaultExpiry(from = granted))

        assertTrue(GuestGrantPolicy.isActive(grant, at = granted))
        assertFalse(GuestGrantPolicy.isActive(grant, at = granted + (31L * 24 * 60 * 60).seconds))
    }

    @Test
    fun `the active grants of a record are the ones a reader may see`() {
        val active = grant(uid = "gran-uid")
        val expired = grant(
            uid = "old-uid",
            expiresAtMillis = Instant.parse("2026-08-01T00:00:00Z").toEpochMilliseconds()
        )

        val visible = GuestGrantPolicy.active(
            listOf(active, expired),
            at = expiry - 1.seconds
        )

        assertEquals(listOf("gran-uid"), visible.map { it.uid })
    }

    private fun grant(
        uid: String = "gran-uid",
        expiresAtMillis: Long = 1_788_264_000_000L
    ) = GuestGrant(
        uid = uid,
        name = "Nina",
        grantedBy = "alice-uid",
        grantedAtMillis = 1_787_477_400_000L,
        expiresAtMillis = expiresAtMillis
    )
}
