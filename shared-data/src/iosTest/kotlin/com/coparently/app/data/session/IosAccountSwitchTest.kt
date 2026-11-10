package com.coparently.app.data.session

import com.coparently.app.data.remote.firebase.SignedInAccount
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.session.AccountSwitchFixture.Companion.ORDINARY_KEY
import com.coparently.app.data.session.AccountSwitchFixture.Companion.SCHOOL_KEY
import com.coparently.app.data.session.AccountSwitchFixture.Companion.SLOT_MARKER_KEY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [IosAccountSwitch] and [IosAccountTraces] on the simulator, with the signed-in uid given by hand
 * (`IosFirebaseAuthEmulatorTest` repeats the switch with a real account on the Auth emulator).
 *
 * What a switch must leave, as Android's `AccountSwitchGuard` leaves it: no row of the previous
 * account, none of its preferences but the per-uid slot markers `clear()` exempts by design, no
 * school connection, no event reminder or request clock job, and none of its files in the caches or
 * the chat outbox (Review Focus 5 of the phase 3 plan). What it must not touch: anything, when the
 * same account comes back, nobody is signed in, or the phone has never seen an account.
 */
class IosAccountSwitchTest {

    private val signedIn = FixedSignedInUser()
    private var fixture: AccountSwitchFixture? = null

    private fun fixture(lastUid: String?) = AccountSwitchFixture(signedIn, lastUid).also { fixture = it }

    @AfterTest
    fun close() {
        fixture?.close()
    }

    @Test
    fun `a different account clears the rows the preferences the jobs and the files`() = runBlocking {
        val phone = fixture(lastUid = "previous")
        phone.fillWithThePreviousAccount()
        signedIn.uid = "next"

        phone.accountSwitch.ensureAccountConsistency()

        assertEquals(emptyList<String>(), phone.database.userDao().getAllUsers().first().map { it.id })
        assertNull(phone.preferences.getString(ORDINARY_KEY))
        assertNull(phone.preferences.getString(SCHOOL_KEY))
        assertEquals("mom", phone.preferences.getString(SLOT_MARKER_KEY))
        assertEquals(1, phone.reminders.cancelledAll)
        assertEquals(1, phone.clocks.cancelledAll)
        phone.traceFiles.forEach { assertFalse(phone.exists(it), "$it is still there") }
        assertEquals("next", phone.lastSignedInUid.get())
    }

    @Test
    fun `the same account keeps everything`() = runBlocking {
        val phone = fixture(lastUid = "previous")
        phone.fillWithThePreviousAccount()
        signedIn.uid = "previous"

        phone.accountSwitch.ensureAccountConsistency()

        assertKept(phone)
        assertEquals("previous", phone.lastSignedInUid.get())
    }

    @Test
    fun `nobody signed in keeps everything and remembers nothing new`() = runBlocking {
        val phone = fixture(lastUid = "previous")
        phone.fillWithThePreviousAccount()
        signedIn.uid = null

        phone.accountSwitch.ensureAccountConsistency()

        assertKept(phone)
        assertEquals("previous", phone.lastSignedInUid.get())
    }

    @Test
    fun `the first account on the phone is remembered and nothing is wiped`() = runBlocking {
        val phone = fixture(lastUid = null)
        phone.fillWithThePreviousAccount()
        signedIn.uid = "first"

        phone.accountSwitch.ensureAccountConsistency()

        assertKept(phone)
        assertEquals("first", phone.lastSignedInUid.get())
    }

    @Test
    fun `sign-out removes the cached files and keeps the chat outbox`() = runBlocking {
        val phone = fixture(lastUid = null)
        phone.fillWithThePreviousAccount()

        phone.traces.clearAfterSignOut()

        val (outbox, caches) = phone.traceFiles.partition { "/files/" in it }
        caches.forEach { assertFalse(phone.exists(it), "$it is still there") }
        outbox.forEach { assertTrue(phone.exists(it), "$it was removed") }
    }

    private suspend fun assertKept(phone: AccountSwitchFixture) {
        assertEquals(listOf("previous"), phone.database.userDao().getAllUsers().first().map { it.id })
        assertEquals("the previous account's", phone.preferences.getString(ORDINARY_KEY))
        assertEquals("the previous account's school tokens", phone.preferences.getString(SCHOOL_KEY))
        assertEquals(0, phone.reminders.cancelledAll)
        assertEquals(0, phone.clocks.cancelledAll)
        phone.traceFiles.forEach { assertTrue(phone.exists(it), "$it was removed") }
    }

    /** A [SignedInUser] whose uid the test sets. */
    private class FixedSignedInUser : SignedInUser {
        var uid: String? = null

        override fun getCurrentUid(): String? = uid
        override fun getCurrentDisplayName(): String? = null
        override fun getCurrentEmail(): String? = null
        override fun getCurrentPhotoUrl(): String? = null
        override fun observeCurrentUid(): Flow<String?> = flowOf(uid)
        override fun getCurrentAccount(): SignedInAccount? = null
    }
}
