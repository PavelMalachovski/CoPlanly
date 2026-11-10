package com.coparently.app.domain.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A family friend may be given a dated access or one that lasts while the parents are linked. */
class FriendAccessDurationTest {

    private val now = Instant.parse("2026-10-20T10:00:00Z")

    @Test
    fun `dated choices end that many days from now`() {
        // As java.time's Instant.plus(Duration.ofDays(n)).toEpochMilli() computed them.
        assertEquals(1_793_095_200_000L, FriendAccessDuration.WEEK.expiryFrom(now))
        assertEquals(1_795_082_400_000L, FriendAccessDuration.MONTH.expiryFrom(now))
        assertEquals(1_800_266_400_000L, FriendAccessDuration.QUARTER.expiryFrom(now))
    }

    @Test
    fun `while linked is a far end that reads back as while linked`() {
        val end = FriendAccessDuration.WHILE_LINKED.expiryFrom(now)
        assertEquals(FriendAccessDuration.WHILE_LINKED_EXPIRY_MILLIS, end)
        assertTrue(FriendAccessDuration.isWhileLinked(end))
    }

    @Test
    fun `no dated choice reads as while linked`() {
        FriendAccessDuration.entries.filter { it != FriendAccessDuration.WHILE_LINKED }.forEach {
            assertFalse(FriendAccessDuration.isWhileLinked(it.expiryFrom(now)), it.name)
        }
    }

    @Test
    fun `the invite sheet still starts on thirty days`() {
        assertEquals(FriendAccessDuration.MONTH, FriendAccessDuration.DEFAULT)
    }
}
