package com.coparently.app.presentation.platform

import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.platform.SealedTextStore
import com.coparently.app.data.platform.StoredText
import com.coparently.app.data.remote.firebase.CallableCode
import com.coparently.app.data.remote.firebase.CallableException
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.GoogleOAuthFunctions
import com.coparently.app.data.remote.google.GoogleCalendarAccess
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import com.coparently.app.data.remote.google.GoogleGrantRevocation
import com.coparently.app.data.remote.google.GoogleSignInFailure
import com.coparently.app.data.remote.google.IosGoogleCalendarAccount
import com.coparently.app.data.session.SignInProviderSession
import com.coparently.app.ios.GoogleIdTokenSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The iPhone's [IosGoogleCalendarSignIn] (MON-24 phase 4, part A, Task 7) over a fake sheet and the
 * real [IosGoogleCalendarAccount]: it is offered only where the sheet runs and the build names the
 * web client, it asks for the read-only scope named in one place, and every way the sheet ends
 * reaches the shell as an answer `SyncViewModel` redeems. The real sheet is the owner's to check on a
 * Mac.
 */
class IosGoogleCalendarSignInTest {

    private val preferences = SealedSecurePreferences(MemoryStore())
    private val server = FakeFunctions()
    private val account =
        IosGoogleCalendarAccount(
            preferences,
            GoogleOAuthFunctions(server),
            GoogleGrantRevocation { },
            SignInProviderSession.None
        )
    private val answers = mutableListOf<GoogleSignInAnswer>()
    private val record: (GoogleSignInAnswer) -> Unit = { answers += it }

    @Test
    fun notOfferedWithoutTheWebClientId() {
        val sheet = FakeSheet(calendarAvailable = false)
        val signIn = IosGoogleCalendarSignIn(sheet, account, record)

        assertFalse(signIn.available)
        assertEquals(GoogleSignInStart.UNAVAILABLE, signIn.open())
        assertTrue(sheet.scopes.isEmpty())
        assertTrue(answers.isEmpty())
    }

    @Test
    fun notOfferedWithoutGooglesSheet() {
        val signIn = IosGoogleCalendarSignIn(FakeSheet(available = false), account, record)

        assertFalse(signIn.available)
        assertEquals(GoogleSignInStart.UNAVAILABLE, signIn.open())
    }

    @Test
    fun aCodeFromTheSheetConnectsTheCalendar() = runTest {
        val sheet = FakeSheet(answer = SheetAnswer(code = "code", email = "alice@example.com", name = "Alice"))
        server.answer = mapOf("accessToken" to "access", "refreshToken" to "refresh", "expiresInSeconds" to 3600)
        val signIn = IosGoogleCalendarSignIn(sheet, account, record)

        assertTrue(signIn.available)
        assertEquals(GoogleSignInStart.OPENED, signIn.open())

        assertEquals(listOf(GoogleCalendarAccess.SCOPE), sheet.scopes)
        assertEquals(
            GoogleSignInCompletion.Connected(displayName = "Alice", email = "alice@example.com"),
            answers.single().complete()
        )
        assertEquals(listOf("exchangeGoogleAuthCode"), server.calls)
        assertEquals(GoogleCalendarConnection.CONNECTED, account.connection())
    }

    @Test
    fun aDismissedSheetIsACancelAndCallsNoServer() = runTest {
        val signIn = IosGoogleCalendarSignIn(FakeSheet(answer = SheetAnswer(cancelled = true)), account, record)

        signIn.open()

        assertEquals(GoogleSignInCompletion.Failed(GoogleSignInFailure.Cancelled), answers.single().complete())
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun aSheetWithoutACodeSaysWhichStepFailed() = runTest {
        IosGoogleCalendarSignIn(FakeSheet(answer = SheetAnswer()), account, record).open()
        IosGoogleCalendarSignIn(FakeSheet(answer = SheetAnswer(error = "GIDSignInError")), account, record).open()

        assertEquals(GoogleSignInCompletion.Failed(GoogleSignInFailure.NoAuthCode), answers[0].complete())
        assertEquals(GoogleSignInCompletion.Failed(GoogleSignInFailure.Unexpected), answers[1].complete())
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun aCodeTheServerRefusesNamesTheServer() = runTest {
        server.refusal = CallableException(
            code = CallableCode.FAILED_PRECONDITION,
            details = mapOf("reason" to "oauth-not-configured"),
            message = "refused",
            cause = null
        )
        IosGoogleCalendarSignIn(FakeSheet(answer = SheetAnswer(code = "code")), account, record).open()

        assertEquals(
            GoogleSignInCompletion.Failed(GoogleSignInFailure.Server("oauth-not-configured")),
            answers.single().complete()
        )
        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, account.connection())
    }

    /** What the fake sheet ends in. */
    private data class SheetAnswer(
        val code: String? = null,
        val email: String? = null,
        val name: String? = null,
        val cancelled: Boolean = false,
        val error: String? = null,
    )

    private class FakeSheet(
        override val available: Boolean = true,
        override val calendarAvailable: Boolean = true,
        private val answer: SheetAnswer = SheetAnswer(),
    ) : GoogleIdTokenSource {
        /** The scopes each calendar request asked for. */
        val scopes = mutableListOf<String>()

        override fun requestIdToken(
            completion: (idToken: String?, accessToken: String?, cancelled: Boolean, error: String?) -> Unit
        ) = error("the calendar's sign-in never asks for an ID token")

        override fun signOut() = Unit

        override fun disconnect() = Unit

        override fun requestCalendarAccess(
            scope: String,
            completion: (String?, String?, String?, Boolean, String?) -> Unit
        ) {
            scopes += scope
            completion(answer.code, answer.email, answer.name, answer.cancelled, answer.error)
        }
    }

    /** The token function: the names called, answering [answer] or throwing [refusal]. */
    private class FakeFunctions : CallableFunctions {
        val calls = mutableListOf<String>()
        var answer: Map<String, Any> = emptyMap()
        var refusal: CallableException? = null

        override suspend fun call(name: String, payload: Map<String, Any>): Any? {
            calls += name
            refusal?.let { throw it }
            return answer
        }
    }

    /** A sealed store's text in memory. */
    private class MemoryStore : SealedTextStore {
        private var text: String? = null

        override fun read(): StoredText = text?.let { StoredText.Present(it) } ?: StoredText.Absent

        override fun write(text: String) {
            this.text = text
        }

        override fun delete() {
            text = null
        }
    }
}
