package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.InstallMarker
import com.coparently.app.data.platform.KeychainTextStore
import com.coparently.app.data.platform.StoredText
import com.coparently.app.data.session.AccountSwitchFixture
import com.coparently.app.data.session.AccountSwitchFixture.Companion.ORDINARY_KEY
import com.coparently.app.data.session.AccountSwitchFixture.Companion.SCHOOL_KEY
import com.coparently.app.data.session.previousInstallCleanup
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.flow.first
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [IosFirebaseAuth] on the Firebase Auth emulator (MON-24 phase 3, Task 5): the e-mail path the
 * shared sign-in screen takes on the iPhone, the questions the data layer asks of the account, and
 * the account switch with a real signed-in account. See [AuthEmulator] for where it runs and why it
 * skips itself without an emulator; the `ios-app` CI job runs it with one, as the check run "iPhone
 * tests on the emulators".
 *
 * Firebase Auth keeps the signed-in user in the Keychain and fails a sign-in it cannot save
 * (`ERROR_KEYCHAIN_ERROR`), so these run where there is one: on the simulator `ios-app` booted
 * (`COPLANLY_SIMULATOR_UDID`; Kotlin's default `--standalone` spawn has no Keychain), from a binary
 * carrying simulated Keychain entitlements (`gradle/ios-test-entitlements.plist`).
 */
class IosFirebaseAuthEmulatorTest {

    @Test
    fun `an account made on the emulator signs in and out`() = AuthEmulator.run("signs in and out") { firebase ->
        val auth = IosFirebaseAuth(firebase)
        val email = newEmail()

        assertTrue(auth.createAccountWithEmail(email, PASSWORD).isSuccess)
        val uid = assertNotNull(auth.getCurrentUid())
        assertEquals(uid, auth.observeCurrentUid().first { it == uid })
        assertEquals(uid, auth.awaitSignedInUid())

        val account = assertNotNull(auth.getCurrentAccount())
        assertEquals(uid, account.uid)
        assertEquals(email, account.email)
        assertEquals(email, auth.getCurrentEmail())
        assertFalse(account.isAnonymous)
        assertEquals(listOf("password"), account.providers.map { it.providerId })

        assertTrue(auth.signOutCompletely().isSuccess)
        assertNull(auth.awaitSignedInUid())
        assertNull(auth.observeCurrentUid().first { it == null })

        assertTrue(auth.signInWithEmail(email, PASSWORD).isSuccess)
        assertEquals(uid, auth.getCurrentUid())
    }

    @Test
    fun `a wrong password fails as invalid credentials`() = AuthEmulator.run("wrong password") { firebase ->
        val auth = IosFirebaseAuth(firebase)
        val email = newEmail()
        assertTrue(auth.createAccountWithEmail(email, PASSWORD).isSuccess)
        assertTrue(auth.signOutCompletely().isSuccess)

        val result = auth.signInWithEmail(email, "not-the-password")

        assertTrue(result.isFailure)
        assertIs<FirebaseAuthInvalidCredentialsException>(result.exceptionOrNull())
        assertNull(auth.getCurrentUid())
    }

    @Test
    fun `a password reset for an account is sent`() = AuthEmulator.run("password reset") { firebase ->
        val auth = IosFirebaseAuth(firebase)
        val email = newEmail()
        assertTrue(auth.createAccountWithEmail(email, PASSWORD).isSuccess)

        assertTrue(auth.sendPasswordResetEmail(email).isSuccess)
    }

    @Test
    fun `a different account signing in wipes the previous one`() = AuthEmulator.run("account switch") { firebase ->
        val auth = IosFirebaseAuth(firebase)
        assertTrue(auth.createAccountWithEmail(newEmail(), PASSWORD).isSuccess)
        val phone = AccountSwitchFixture(auth, lastUid = "the-previous-account")
        try {
            phone.fillWithThePreviousAccount()

            phone.accountSwitch.ensureAccountConsistency()

            assertEquals(emptyList<String>(), phone.database.userDao().getAllUsers().first().map { it.id })
            assertNull(phone.preferences.getString(ORDINARY_KEY))
            assertNull(phone.preferences.getString(SCHOOL_KEY))
            phone.traceFiles.forEach { assertFalse(phone.exists(it), "$it is still there") }
            assertEquals(auth.getCurrentUid(), phone.lastSignedInUid.get())
        } finally {
            phone.close()
        }
    }

    @Test
    fun `the first launch after a reinstall signs the previous account out`() =
        AuthEmulator.run("reinstall signs out") { firebase ->
            val auth = IosFirebaseAuth(firebase)
            assertTrue(auth.createAccountWithEmail(newEmail(), PASSWORD).isSuccess)
            assertNotNull(firebase.currentUser)
            // The previous install's sealed store, in an item of the test's own.
            val store = KeychainTextStore(service = "app.coplanly.test.previous-install", account = "reinstall")
            store.write("the previous install's preferences")
            val marker = FreshMarker()

            previousInstallCleanup(marker, store, firebase).clearIfFirstLaunch()

            assertNull(firebase.currentUser)
            assertNull(auth.awaitSignedInUid())
            assertEquals(StoredText.Absent, store.read())
            assertTrue(marker.set)

            // The second launch of the same install leaves the next account signed in.
            assertTrue(auth.createAccountWithEmail(newEmail(), PASSWORD).isSuccess)
            previousInstallCleanup(marker, store, firebase).clearIfFirstLaunch()
            assertNotNull(firebase.currentUser)
        }

    /** An install marker as a fresh install finds it: unset. */
    private class FreshMarker : InstallMarker {
        var set = false

        override fun isSet(): Boolean = set

        override fun set() {
            set = true
        }
    }

    private fun newEmail(): String = "iphone-${NSUUID().UUIDString.lowercase()}@example.test"

    private companion object {
        const val PASSWORD = "correct-horse-battery"
    }
}
