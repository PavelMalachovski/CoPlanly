@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.presentation.auth

import cocoapods.FirebaseAuth.FIRAuthErrorDomain
import com.coparently.app.data.remote.firebase.IosFirebaseAuth
import com.coparently.app.ios.TestFirebase
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.FirebaseTooManyRequestsException
import dev.gitlive.firebase.auth.FirebaseAuthEmailException
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.auth
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSError
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRunLoop
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.runMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * Which [AuthError] a failed sign-in on the iPhone is ([IosAuthErrors]), as `AuthErrorTest` holds
 * Android's mapping.
 *
 * The failures are built as GitLive 2.7.0 builds them: Firebase Auth answers with an `NSError` in
 * `FIRAuthErrorDomain` whose code is an `AuthErrorCode`, and GitLive's private `NSError.toException`
 * turns it into one of its exception classes carrying that code as text (network failures into
 * [FirebaseNetworkException], with none). [gitLiveFailure] makes the same choice from the same
 * `NSError`, and [IosAuthErrors] reads only what GitLive copies out of it: the class for the network
 * and the code for the rest. The last test takes the real path, a wrong password on the Firebase
 * Auth emulator, so the copy cannot drift from what GitLive does unnoticed.
 */
class IosAuthErrorsTest {

    @Test
    fun `a wrong password and a refused credential are invalid credentials`() {
        assertEquals(AuthError.InvalidCredentials, IosAuthErrors.fromEmailPassword(gitLiveFailure(WRONG_PASSWORD)))
        assertEquals(AuthError.InvalidCredentials, IosAuthErrors.fromEmailPassword(gitLiveFailure(INVALID_CREDENTIAL)))
        assertEquals(AuthError.InvalidCredentials, IosAuthErrors.fromEmailPassword(gitLiveFailure(USER_NOT_FOUND)))
    }

    @Test
    fun `an address already taken is said so`() {
        assertEquals(AuthError.EmailAlreadyInUse, IosAuthErrors.fromEmailPassword(gitLiveFailure(EMAIL_ALREADY_IN_USE)))
        assertEquals(
            AuthError.EmailAlreadyInUse,
            IosAuthErrors.fromEmailPassword(gitLiveFailure(ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL))
        )
    }

    @Test
    fun `each other reason Firebase names is its own`() {
        assertEquals(AuthError.WeakPassword, IosAuthErrors.fromEmailPassword(gitLiveFailure(WEAK_PASSWORD)))
        assertEquals(AuthError.InvalidEmail, IosAuthErrors.fromEmailPassword(gitLiveFailure(INVALID_EMAIL)))
        assertEquals(AuthError.UserDisabled, IosAuthErrors.fromEmailPassword(gitLiveFailure(USER_DISABLED)))
        assertEquals(AuthError.TooManyRequests, IosAuthErrors.fromEmailPassword(gitLiveFailure(TOO_MANY_REQUESTS)))
        assertEquals(AuthError.Network, IosAuthErrors.fromEmailPassword(gitLiveFailure(NETWORK_ERROR)))
    }

    @Test
    fun `anything else is unknown`() {
        assertEquals(AuthError.Unknown, IosAuthErrors.fromEmailPassword(gitLiveFailure(INTERNAL_ERROR)))
        assertEquals(AuthError.Unknown, IosAuthErrors.fromEmailPassword(gitLiveFailure(QUOTA_EXCEEDED)))
        val otherDomain = NSError(domain = NSURLErrorDomain, code = -1L, userInfo = null)
        assertEquals(AuthError.Unknown, IosAuthErrors.fromEmailPassword(FirebaseAuthException(otherDomain.toString())))
        assertEquals(AuthError.Unknown, IosAuthErrors.fromEmailPassword(IllegalStateException("not Firebase")))
    }

    @Test
    fun `a cancelled Google sheet is no error and the Google path maps the Firebase reasons`() {
        assertNull(IosAuthErrors.fromGoogle(GoogleSignInCancelled()))
        assertEquals(AuthError.InvalidCredentials, IosAuthErrors.fromGoogle(gitLiveFailure(INVALID_CREDENTIAL)))
        assertEquals(AuthError.Unknown, IosAuthErrors.fromGoogle(IllegalStateException("not Firebase")))
    }

    @Test
    fun `the saved-password path maps the Firebase reasons`() {
        assertEquals(AuthError.InvalidCredentials, IosAuthErrors.fromSavedPassword(gitLiveFailure(WRONG_PASSWORD)))
        assertEquals(AuthError.Unknown, IosAuthErrors.fromSavedPassword(IllegalStateException("not Firebase")))
    }

    /**
     * A wrong password on the Firebase Auth emulator, through `IosFirebase`'s emulator configuration
     * and [IosFirebaseAuth], is [AuthError.InvalidCredentials]. It skips itself without an emulator,
     * and fails then when one is required, on the rule `shared-data`'s `AuthEmulator` states.
     */
    @Test
    fun `a wrong password on the emulator is invalid credentials`() {
        val host = variable("COPLANLY_EMULATOR_HOST")
        if (host == null) {
            if (variable("COPLANLY_REQUIRE_EMULATORS") == "1") {
                fail("COPLANLY_REQUIRE_EMULATORS=1 but no COPLANLY_EMULATOR_HOST reached the test")
            }
            println("COPLANLY_EMULATOR_TEST_SKIPPED wrong password mapped")
            return
        }
        TestFirebase.configureOnce(host)
        val auth = IosFirebaseAuth(Firebase.auth)
        val email = "iphone-${NSUUID().UUIDString.lowercase()}@example.test"
        try {
            runOnMainRunLoop {
                assertTrue(auth.createAccountWithEmail(email, "correct-horse-battery").isSuccess)
                assertTrue(auth.signOutCompletely().isSuccess)

                val failure = auth.signInWithEmail(email, "not-the-password").exceptionOrNull()

                assertEquals(AuthError.InvalidCredentials, IosAuthErrors.fromEmailPassword(checkNotNull(failure)))
            }
        } finally {
            runOnMainRunLoop { auth.signOutCompletely() }
        }
        println("COPLANLY_EMULATOR_TEST_RAN wrong password mapped")
    }

    private fun variable(name: String): String? =
        (NSProcessInfo.processInfo.environment[name] as? String)?.takeIf { it.isNotBlank() }

    private companion object {
        // Firebase Auth's `AuthErrorCode` (FirebaseAuth/Sources/Swift/Utilities/AuthErrors.swift,
        // 11.8.0); the Objective-C headers GitLive binds name the domain, not the codes.
        const val INVALID_CREDENTIAL = 17004L
        const val USER_DISABLED = 17005L
        const val EMAIL_ALREADY_IN_USE = 17007L
        const val INVALID_EMAIL = 17008L
        const val WRONG_PASSWORD = 17009L
        const val TOO_MANY_REQUESTS = 17010L
        const val USER_NOT_FOUND = 17011L
        const val ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL = 17012L
        const val NETWORK_ERROR = 17020L
        const val WEAK_PASSWORD = 17026L
        const val QUOTA_EXCEEDED = 17052L
        const val INTERNAL_ERROR = 17999L

        /**
         * What GitLive 2.7.0's `NSError.toException` (`appleMain/.../auth/auth.kt`, private) throws
         * for an `NSError` in `FIRAuthErrorDomain` with [code], for the codes this test uses.
         */
        fun gitLiveFailure(code: Long): Throwable {
            val error = NSError(domain = FIRAuthErrorDomain, code = code, userInfo = null)
            val message = error.toString()
            val text = error.code.toString()
            return when (code) {
                INVALID_EMAIL -> FirebaseAuthEmailException(message, text)
                INVALID_CREDENTIAL, WRONG_PASSWORD -> FirebaseAuthInvalidCredentialsException(message, text)
                WEAK_PASSWORD -> FirebaseAuthWeakPasswordException(message, text)
                QUOTA_EXCEEDED -> FirebaseTooManyRequestsException(message)
                EMAIL_ALREADY_IN_USE, ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL ->
                    FirebaseAuthUserCollisionException(message, text)
                NETWORK_ERROR -> FirebaseNetworkException(message)
                else -> FirebaseAuthException(message, text)
            }
        }
    }
}

/**
 * Runs [block] on the main queue and turns the main run loop until it ends, as GitLive's own Apple
 * tests do: Firebase answers on the main queue, and the Kotlin/Native test runner runs every test on
 * the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun runOnMainRunLoop(block: suspend CoroutineScope.() -> Unit) {
    val scope = MainScope()
    val run = scope.async { withTimeout(60.seconds) { block() } }
    while (run.isActive) {
        NSRunLoop.mainRunLoop.runMode(
            NSDefaultRunLoopMode,
            beforeDate = NSDate.create(timeInterval = 0.05, sinceDate = NSDate())
        )
    }
    scope.cancel()
    run.getCompletionExceptionOrNull()?.let { throw it }
}
