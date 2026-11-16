package com.coparently.app.data.remote.google

import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.remote.firebase.CallableCode
import com.coparently.app.data.remote.firebase.GoogleOAuthFunctions
import com.coparently.app.data.remote.firebase.RecordingCallableFunctions
import com.coparently.app.data.session.SignInProviderSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [IosGoogleCalendarAccount], the iPhone's Google Calendar connection (MON-24 phase 4, part A, Task
 * 7), over a real sealed store in memory and a recorded token function: it holds Android's rules
 * (`CredentialManagerService`, `CredentialProviderImpl`) under Android's keys.
 */
class IosGoogleCalendarAccountTest {

    private val preferences = SealedSecurePreferences(MemoryTextStore())
    private val functions = RecordingCallableFunctions()
    private var sessionEnded = 0

    /** What the store held when Google's session was ended: the order item 23 asks for. */
    private var accessTokenWhenSessionEnded: String? = "unset"
    private val session = SignInProviderSession {
        sessionEnded++
        accessTokenWhenSessionEnded = preferences.getAccessToken()
    }
    private val account = IosGoogleCalendarAccount(preferences, GoogleOAuthFunctions(functions), session) { NOW }

    @Test
    fun connectionReflectsStoredTokens() {
        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, account.connection())

        preferences.putUserEmail("alice@example.com")
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, account.connection())

        preferences.putAccessToken("access")
        preferences.putTokenExpiry(NOW - 1)
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, account.connection())

        preferences.putTokenExpiry(NOW + 1)
        assertEquals(GoogleCalendarConnection.CONNECTED, account.connection())

        // A refresh token is a connection, whatever the access token's expiry says.
        preferences.putTokenExpiry(NOW - HOUR)
        preferences.putRefreshToken("refresh")
        assertEquals(GoogleCalendarConnection.CONNECTED, account.connection())
    }

    @Test
    fun refreshUsesServerAndStoresNewToken() = runTest {
        preferences.putUserEmail("alice@example.com")
        preferences.putAccessToken("old")
        preferences.putTokenExpiry(NOW + 60_000)
        preferences.putRefreshToken("refresh")
        functions.answer = { _, _ -> mapOf("accessToken" to "new", "expiresInSeconds" to 1800) }

        val access = account.accessToken()

        assertEquals(GoogleAccess.Granted("new"), access)
        assertEquals(
            listOf("refreshGoogleAccessToken" to mapOf<String, Any>("refreshToken" to "refresh")),
            functions.calls
        )
        assertEquals("new", preferences.getAccessToken())
        assertEquals(NOW + 1800 * 1000L, preferences.getTokenExpiry())
        assertEquals("refresh", preferences.getRefreshToken())
    }

    @Test
    fun aTokenStillGoodIsUsedWithoutTheServer() = runTest {
        preferences.putAccessToken("good")
        preferences.putTokenExpiry(NOW + HOUR)
        preferences.putRefreshToken("refresh")

        assertEquals(GoogleAccess.Granted("good"), account.accessToken())
        assertTrue(functions.calls.isEmpty())
    }

    @Test
    fun aRefusalForGoodDropsTheTokensAndKeepsTheAddress() = runTest {
        preferences.putUserEmail("alice@example.com")
        preferences.putRefreshToken("revoked")
        functions.answer = { _, _ ->
            throw RecordingCallableFunctions.refusal(CallableCode.FAILED_PRECONDITION, "reconnect")
        }

        assertEquals(GoogleAccess.Denied(GoogleAccessFailure.NEEDS_RECONNECT), account.accessToken())
        assertNull(preferences.getRefreshToken())
        assertEquals("alice@example.com", preferences.getUserEmail())
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, account.connection())
    }

    @Test
    fun anOutageKeepsTheTokens() = runTest {
        preferences.putRefreshToken("refresh")
        functions.answer = { _, _ -> throw RecordingCallableFunctions.refusal(CallableCode.UNAVAILABLE) }

        assertEquals(GoogleAccess.Denied(GoogleAccessFailure.UNAVAILABLE), account.accessToken())
        assertEquals("refresh", preferences.getRefreshToken())
    }

    @Test
    fun connectRedeemsTheCodeAndStoresTheConnection() = runTest {
        functions.answer = { _, _ ->
            mapOf("accessToken" to "access", "refreshToken" to "refresh", "expiresInSeconds" to 3600)
        }

        assertNull(account.connect("code", "alice@example.com"))

        assertEquals(listOf("exchangeGoogleAuthCode" to mapOf<String, Any>("authCode" to "code")), functions.calls)
        assertEquals("access", preferences.getAccessToken())
        assertEquals("refresh", preferences.getRefreshToken())
        assertEquals(NOW + HOUR, preferences.getTokenExpiry())
        assertEquals("alice@example.com", preferences.getUserEmail())
        assertEquals(GoogleCalendarConnection.CONNECTED, account.connection())
    }

    @Test
    fun anotherAccountWithoutARefreshTokenDoesNotInheritTheOldOne() = runTest {
        preferences.putUserEmail("alice@example.com")
        preferences.putRefreshToken("alice-refresh")
        functions.answer = { _, _ -> mapOf("accessToken" to "bob-access", "expiresInSeconds" to 3600) }

        assertNull(account.connect("code", "bob@example.com"))

        assertNull(preferences.getRefreshToken())
        assertEquals("bob-access", preferences.getAccessToken())
        assertEquals("bob@example.com", preferences.getUserEmail())
    }

    @Test
    fun aCodeTheServerRefusesIsAServerFailureAndStoresNothing() = runTest {
        functions.answer = { _, _ ->
            throw RecordingCallableFunctions.refusal(CallableCode.INTERNAL, "google-refused", "unauthorized_client")
        }

        assertEquals(
            GoogleSignInFailure.Server("google-refused: unauthorized_client"),
            account.connect("code", "alice@example.com")
        )
        assertNull(preferences.getAccessToken())
        assertNull(preferences.getUserEmail())
    }

    @Test
    fun disconnectClearsKeys() = runTest {
        preferences.putAccessToken("access")
        preferences.putRefreshToken("refresh")
        preferences.putTokenExpiry(NOW + HOUR)
        preferences.putUserEmail("alice@example.com")
        preferences.putCalendarId("alice@example.com")
        preferences.putString(OTHER_KEY, "kept")

        account.disconnect()

        assertNull(preferences.getAccessToken())
        assertNull(preferences.getRefreshToken())
        assertNull(preferences.getTokenExpiry())
        assertNull(preferences.getUserEmail())
        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, account.connection())
        assertEquals("kept", preferences.getString(OTHER_KEY))
        // The local credential went first, then Google's session (item 23).
        assertEquals(1, sessionEnded)
        assertNull(accessTokenWhenSessionEnded)
    }

    @Test
    fun signOutClearsTheStoreBeforeGooglesSession() = runTest {
        preferences.putRefreshToken("refresh")
        preferences.putAccessToken("access")
        preferences.putString(OTHER_KEY, "gone")

        assertEquals(Pair(true, null), account.signOut())

        assertNull(preferences.getRefreshToken())
        assertNull(preferences.getString(OTHER_KEY))
        assertEquals(1, sessionEnded)
        assertNull(accessTokenWhenSessionEnded)
    }

    @Test
    fun withCredentialRenewsOnceOnA401() = runTest {
        preferences.putAccessToken("stale")
        preferences.putTokenExpiry(NOW + HOUR)
        preferences.putRefreshToken("refresh")
        functions.answer = { _, _ -> mapOf("accessToken" to "renewed", "expiresInSeconds" to 3600) }
        val used = mutableListOf<String>()

        val answer = account.withCredential { credential ->
            used += credential.accessToken
            if (credential.accessToken == "stale") throw GoogleCalendarHttpException(401, emptyList(), null)
            "events"
        }

        assertEquals("events", answer)
        assertEquals(listOf("stale", "renewed"), used)
    }

    @Test
    fun withCredentialEndsTheConnectionWithoutTheCalendarPermission() = runTest {
        preferences.putUserEmail("alice@example.com")
        preferences.putAccessToken("access")
        preferences.putTokenExpiry(NOW + HOUR)
        preferences.putRefreshToken("refresh")

        val failure = assertFailsWith<GoogleAccessException> {
            account.withCredential<Unit> {
                throw GoogleCalendarHttpException(403, listOf("insufficientPermissions"), null)
            }
        }

        assertEquals(GoogleAccessFailure.NEEDS_RECONNECT, failure.failure)
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, account.connection())
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val HOUR = 3_600_000L
        const val OTHER_KEY = "some_other_setting"
    }
}
