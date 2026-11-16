package com.coparently.app.data.remote.firebase

import com.coparently.app.data.platform.InstallMarker
import com.coparently.app.data.platform.KeychainTextStore
import com.coparently.app.data.platform.StoredText
import com.coparently.app.data.session.AccountSwitchFixture
import com.coparently.app.data.session.AccountSwitchFixture.Companion.ORDINARY_KEY
import com.coparently.app.data.session.AccountSwitchFixture.Companion.SCHOOL_KEY
import com.coparently.app.data.session.SignInProviderSession
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

    /**
     * Task 9: Google's access token, held beside the ID token the sheet answered
     * ([GoogleAccessTokens]), makes the credential GitLive builds from the two. The Auth emulator
     * accepts an ID token that is the claims as JSON, unsigned, as Firebase documents for testing.
     */
    @Test
    fun `a Google ID token with its held access token signs in`() = AuthEmulator.run("google signs in") { firebase ->
        val auth = IosFirebaseAuth(firebase)
        val idToken = googleIdToken()
        GoogleAccessTokens.hold(idToken, "an-access-token")

        val result = auth.signInWithGoogleIdToken(idToken)

        assertTrue(result.isSuccess, "${result.exceptionOrNull()}")
        assertEquals(SignInOutcome(isNewUser = true), result.getOrNull())
        val account = assertNotNull(auth.getCurrentAccount())
        assertTrue("google.com" in account.providers.map { it.providerId })
        assertNull(GoogleAccessTokens.take(idToken))
    }

    /**
     * Phase 4, part A (the plan's review focus 1): linking a Google account another CoPlanly account
     * already holds links nothing, and the e-mail account stays signed in, its methods unchanged.
     */
    @Test
    fun `a Google account another account holds is not linked`() = AuthEmulator.run("google in use") { firebase ->
        val auth = IosFirebaseAuth(firebase)
        val linking = IosAccountLinking(firebase, AppleTokens(NoCallables))
        val idToken = googleIdToken()
        GoogleAccessTokens.hold(idToken, "an-access-token")
        assertTrue(auth.signInWithGoogleIdToken(idToken).isSuccess)
        assertTrue(auth.signOutCompletely().isSuccess)
        assertTrue(auth.createAccountWithEmail(newEmail(), PASSWORD).isSuccess)
        val uid = assertNotNull(auth.getCurrentUid())

        GoogleAccessTokens.hold(idToken, "an-access-token")
        val failure = linking.linkGoogle(idToken).exceptionOrNull()

        assertEquals(LinkFailure.InUseByAnotherAccount, assertIs<LinkException>(failure).failure)
        assertEquals(uid, auth.getCurrentUid())
        assertEquals(setOf(SignInMethod.EMAIL), linking.linkedMethods().first())
    }

    @Test
    fun `an access token held for another ID token is not used`() =
        AuthEmulator.run("google stale token") { firebase ->
            val auth = IosFirebaseAuth(firebase)
            GoogleAccessTokens.hold(googleIdToken(), "an-access-token")

            val result = auth.signInWithGoogleIdToken(googleIdToken())

            assertTrue(result.isFailure)
            assertNull(auth.getCurrentUid())
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
            var googleSignOuts = 0
            val google = SignInProviderSession { googleSignOuts++ }

            previousInstallCleanup(marker, store, firebase, google).clearIfFirstLaunch()

            assertNull(firebase.currentUser)
            assertEquals(1, googleSignOuts, "Google Sign-In's session ends with the previous install's")
            assertNull(auth.awaitSignedInUid())
            assertEquals(StoredText.Absent, store.read())
            assertTrue(marker.set)

            // The second launch of the same install leaves the next account signed in.
            assertTrue(auth.createAccountWithEmail(newEmail(), PASSWORD).isSuccess)
            previousInstallCleanup(marker, store, firebase, google).clearIfFirstLaunch()
            assertNotNull(firebase.currentUser)
            assertEquals(1, googleSignOuts, "a later launch leaves Google's session alone")
        }

    /** An install marker as a fresh install finds it: unset. */
    private class FreshMarker : InstallMarker {
        var set = false

        override fun isSet(): Boolean = set

        override fun set() {
            set = true
        }
    }

    /** An ID token the Auth emulator accepts for a new Google account: its claims as JSON. */
    private fun googleIdToken(): String {
        val id = NSUUID().UUIDString.lowercase()
        return """{"sub":"$id","email":"iphone-$id@example.test","email_verified":true}"""
    }

    private fun newEmail(): String = "iphone-${NSUUID().UUIDString.lowercase()}@example.test"

    /** No callable is reached by linking Google; Apple's unlink alone would call one. */
    private object NoCallables : CallableFunctions {
        override suspend fun call(name: String, payload: Map<String, Any>): Any? =
            error("No callable is expected here: $name")
    }

    private companion object {
        const val PASSWORD = "correct-horse-battery"
    }
}
