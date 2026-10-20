package com.coparently.app.domain.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** A family friend may be given a dated access or one that lasts while the parents are linked. */
class FriendAccessDurationTest {

    private val now = Instant.parse("2026-10-20T10:00:00Z")

    @Test
    fun `dated choices end that many days from now`() {
        assertEquals(now.plus(Duration.ofDays(7)).toEpochMilli(), FriendAccessDuration.WEEK.expiryFrom(now))
        assertEquals(now.plus(Duration.ofDays(30)).toEpochMilli(), FriendAccessDuration.MONTH.expiryFrom(now))
        assertEquals(now.plus(Duration.ofDays(90)).toEpochMilli(), FriendAccessDuration.QUARTER.expiryFrom(now))
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
            assertFalse(it.name, FriendAccessDuration.isWhileLinked(it.expiryFrom(now)))
        }
    }

    @Test
    fun `the invite sheet still starts on thirty days`() {
        assertEquals(FriendAccessDuration.MONTH, FriendAccessDuration.DEFAULT)
    }
}
