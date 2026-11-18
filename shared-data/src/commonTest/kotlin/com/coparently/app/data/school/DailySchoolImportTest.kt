package com.coparently.app.data.school

import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.remote.firebase.SignedInAccount
import com.coparently.app.data.remote.firebase.SignedInUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The iPhone's daily school import (phase 4 part D): at most once a local day per account, after
 * any sync, and an import that threw is not recorded as done.
 */
class DailySchoolImportTest {

    private val preferences = SealedSecurePreferences(MemoryTextStore())
    private val user = SignedInAs("alice")
    private val clock = MovableClock(Instant.parse("2026-11-18T07:00:00Z"))
    private var imports = 0
    private var failNext = false

    private val daily = DailySchoolImport(
        syncAll = {
            imports++
            if (failNext) {
                failNext = false
                error("school offline")
            }
            1
        },
        preferences = preferences,
        signedInUser = user,
        clock = clock,
        zone = { TimeZone.of("Europe/Prague") }
    )

    @Test
    fun runsOnceADayPerAccount() = runTest {
        assertTrue(daily.runIfDue())
        clock.now += 10.hours
        assertFalse(daily.runIfDue())

        assertEquals(1, imports)
    }

    @Test
    fun runsAgainTheNextDay() = runTest {
        assertTrue(daily.runIfDue())
        // 07:00 UTC is 08:00 in Prague; 17 hours later it is 01:00 the next local day.
        clock.now += 17.hours
        assertTrue(daily.runIfDue())

        assertEquals(2, imports)
    }

    @Test
    fun anotherAccountRunsItsOwn() = runTest {
        assertTrue(daily.runIfDue())
        user.uid = "bob"
        assertTrue(daily.runIfDue())
        user.uid = "alice"
        assertFalse(daily.runIfDue())

        assertEquals(2, imports)
    }

    @Test
    fun nobodySignedInDoesNothing() = runTest {
        user.uid = null

        assertFalse(daily.runIfDue())
        assertEquals(0, imports)
    }

    @Test
    fun aFailedImportIsNotRecordedAsDone() = runTest {
        failNext = true
        assertFailsWith<IllegalStateException> { daily.runIfDue() }

        assertTrue(daily.runIfDue())
        assertEquals(2, imports)
        assertFalse(daily.runIfDue())
    }

    /** A clock the test moves. */
    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    /** A [SignedInUser] whose uid the test sets. */
    private class SignedInAs(var uid: String?) : SignedInUser {
        override fun getCurrentUid(): String? = uid
        override fun getCurrentDisplayName(): String? = null
        override fun getCurrentEmail(): String? = null
        override fun getCurrentPhotoUrl(): String? = null
        override fun observeCurrentUid(): Flow<String?> = flowOf(uid)
        override fun getCurrentAccount(): SignedInAccount? = null
    }
}
