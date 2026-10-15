package com.coparently.app.data.remote.google

import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.remote.firebase.GoogleTokens
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.ACCESS
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.EMAIL
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.FRESH
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.HOUR
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.REFRESH
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Tasks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Google Calendar stays connected (#9).
 *
 * "Connected" used to mean "the access token is good for five more minutes", so every connection
 * read as gone about 55 minutes after it was made: Settings said "not signed in" on the next
 * launch, and the import gave up before the refresh token it was holding was ever used. And a
 * failed renewal could not tell "Google ended this" from "the network blinked". These pin the
 * reading of the stored connection, the renewal, and what each kind of failure leaves behind.
 */
class CredentialManagerServiceTest {

    private val google = GoogleCredentialsFixture()

    @After
    fun tearDown() {
        google.close()
    }

    // ---- connection() -------------------------------------------------------------------

    @Test
    fun `a stored refresh token is a connection after its access token ran out`() {
        google.connect(expiresInMillis = -HOUR)

        assertEquals(GoogleCalendarConnection.CONNECTED, google.service.connection())
    }

    @Test
    fun `an access token that has not run out is a connection without a refresh token`() {
        google.connect(refreshToken = null)

        assertEquals(GoogleCalendarConnection.CONNECTED, google.service.connection())
    }

    @Test
    fun `a device that never connected is not connected`() {
        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, google.service.connection())
    }

    @Test
    fun `a run-out token with nothing to renew it is a connection to make again`() {
        // A connection made while Google still left the refresh token out of a repeat consent.
        google.connect(expiresInMillis = -HOUR, refreshToken = null)

        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, google.service.connection())
    }

    @Test
    fun `the connection reads the same on the next launch`() {
        google.connect(expiresInMillis = -HOUR)

        val nextLaunch = CredentialManagerService(google.context, google.reopened(), google.oauth)

        assertEquals(GoogleCalendarConnection.CONNECTED, nextLaunch.connection())
    }

    // ---- accessToken() ------------------------------------------------------------------

    @Test
    fun `an expired access token is renewed with the refresh token and the new one returned`() = runTest {
        google.connect(expiresInMillis = -HOUR)
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns google.renewed()

        val access = google.service.accessToken()

        assertEquals(GoogleAccess.Granted(FRESH), access)
        assertEquals(FRESH, google.preferences.getAccessToken())
        assertTrue(
            checkNotNull(google.preferences.getTokenExpiry()) > System.currentTimeMillis() + HOUR / 2,
            "the new token's expiry is stored"
        )
        assertEquals(REFRESH, google.preferences.getRefreshToken(), "the refresh grant keeps the stored token")
    }

    @Test
    fun `a token about to run out is renewed before it is used`() = runTest {
        google.connect(expiresInMillis = 2 * 60 * 1000L)
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns google.renewed()

        assertEquals(GoogleAccess.Granted(FRESH), google.service.accessToken())
    }

    @Test
    fun `a token that is still good is used without asking the server`() = runTest {
        google.connect()

        assertEquals(GoogleAccess.Granted(ACCESS), google.service.accessToken())
        coVerify(exactly = 0) { google.oauth.refreshAccessToken(any()) }
    }

    @Test
    fun `a forced renewal asks the server even while the token looks good`() = runTest {
        // What a caller does once after the Calendar API refused a token (401).
        google.connect()
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns google.renewed()

        assertEquals(GoogleAccess.Granted(FRESH), google.service.accessToken(forceRefresh = true))
    }

    @Test
    fun `a refusal for good drops the tokens, keeps the address and asks for a reconnect`() = runTest {
        google.connect(expiresInMillis = -HOUR)
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns
            Result.failure(GoogleAccessException(GoogleAccessFailure.NEEDS_RECONNECT))

        val access = google.service.accessToken()

        assertEquals(GoogleAccess.Denied(GoogleAccessFailure.NEEDS_RECONNECT), access)
        assertNull(google.preferences.getRefreshToken(), "a dead token is never tried again")
        assertNull(google.preferences.getAccessToken())
        assertEquals(EMAIL, google.preferences.getUserEmail())
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, google.service.connection())
        assertNull(google.reopened().getRefreshToken(), "and not on the next launch either")
    }

    @Test
    fun `a renewal that could not reach Google keeps the connection`() = runTest {
        google.connect(expiresInMillis = -HOUR)
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns
            Result.failure(GoogleAccessException(GoogleAccessFailure.UNAVAILABLE))

        assertEquals(GoogleAccess.Denied(GoogleAccessFailure.UNAVAILABLE), google.service.accessToken())
        assertEquals(REFRESH, google.preferences.getRefreshToken())
        assertEquals(GoogleCalendarConnection.CONNECTED, google.service.connection())
    }

    @Test
    fun `a failure of no known kind keeps the connection too`() = runTest {
        google.connect(expiresInMillis = -HOUR)
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns Result.failure(IllegalStateException("boom"))

        assertEquals(GoogleAccess.Denied(GoogleAccessFailure.UNAVAILABLE), google.service.accessToken())
        assertEquals(REFRESH, google.preferences.getRefreshToken())
    }

    @Test
    fun `with nothing to renew it, a token is used for what is left of it`() = runTest {
        google.connect(expiresInMillis = 2 * 60 * 1000L, refreshToken = null)

        assertEquals(GoogleAccess.Granted(ACCESS), google.service.accessToken())
    }

    @Test
    fun `a refused token with nothing to renew it ends the connection`() = runTest {
        google.connect(refreshToken = null)

        val access = google.service.accessToken(forceRefresh = true)

        assertEquals(GoogleAccess.Denied(GoogleAccessFailure.NEEDS_RECONNECT), access)
        assertNull(google.preferences.getAccessToken())
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, google.service.connection())
    }

    @Test
    fun `a device that never connected is told so, not asked to reconnect`() = runTest {
        assertEquals(GoogleAccess.Denied(GoogleAccessFailure.NOT_CONNECTED), google.service.accessToken())
    }

    @Test
    fun `marking a refused connection keeps it recognisable as one to reconnect`() {
        google.connect()

        google.service.markNeedsReconnect()

        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, google.service.connection())
        assertEquals(EMAIL, google.preferences.getUserEmail())
    }

    // ---- connecting ---------------------------------------------------------------------

    @Test
    fun `connecting stores both tokens and the address`() = runTest {
        coEvery { google.oauth.exchangeAuthCode("code-1") } returns
            Result.success(GoogleTokens(accessToken = ACCESS, refreshToken = REFRESH, expiresInSeconds = 3599))

        val (account, _) = google.service.handleSignInResult(Tasks.forResult(signedIn(EMAIL)))

        assertNotNull(account)
        assertEquals(ACCESS, google.preferences.getAccessToken())
        assertEquals(REFRESH, google.preferences.getRefreshToken())
        assertEquals(EMAIL, google.preferences.getUserEmail())
        assertEquals(GoogleCalendarConnection.CONNECTED, google.service.connection())
    }

    @Test
    fun `another Google account never inherits the first one's refresh token`() = runTest {
        // Were the exchange to come back without a refresh token, the stored one belongs to Alice:
        // renewing it under Bob's address would import Alice's calendar as Bob's.
        google.connect()
        coEvery { google.oauth.exchangeAuthCode("code-1") } returns
            Result.success(GoogleTokens(accessToken = "access-bob", refreshToken = "", expiresInSeconds = 3599))

        google.service.handleSignInResult(Tasks.forResult(signedIn("bob@example.test")))

        assertNull(google.preferences.getRefreshToken())
        assertEquals("access-bob", google.preferences.getAccessToken())
        assertEquals("bob@example.test", google.preferences.getUserEmail())
    }

    @Test
    fun `the same account coming back without a refresh token keeps the one it has`() = runTest {
        google.connect()
        coEvery { google.oauth.exchangeAuthCode("code-1") } returns
            Result.success(GoogleTokens(accessToken = "access-2", refreshToken = "", expiresInSeconds = 3599))

        google.service.handleSignInResult(Tasks.forResult(signedIn(EMAIL)))

        assertEquals(REFRESH, google.preferences.getRefreshToken())
        assertEquals("access-2", google.preferences.getAccessToken())
    }

    @Test
    fun `an exchange that failed leaves a reconnect to be tried again`() = runTest {
        google.connect(accessToken = null, refreshToken = null)
        coEvery { google.oauth.exchangeAuthCode("code-1") } returns
            Result.failure(GoogleAccessException(GoogleAccessFailure.UNAVAILABLE))

        val (account, _) = google.service.handleSignInResult(Tasks.forResult(signedIn(EMAIL)))

        assertNull(account)
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, google.service.connection())
    }

    // ---- disconnecting ------------------------------------------------------------------

    @Test
    fun `disconnecting Google Calendar forgets the connection and nothing else`() = runTest {
        google.connect()
        google.preferences.putString(PreferenceKeys.PUSH_ENABLED, "false")
        google.preferences.putString("${PreferenceKeys.SELECTED_FAMILY_PREFIX}u1", "u1__u2")
        google.preferences.putBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, true)

        google.service.disconnect()

        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, google.service.connection())
        assertNull(google.preferences.getUserEmail())
        assertNull(google.preferences.getRefreshToken())
        assertEquals("false", google.preferences.getString(PreferenceKeys.PUSH_ENABLED))
        assertEquals("u1__u2", google.preferences.getString("${PreferenceKeys.SELECTED_FAMILY_PREFIX}u1"))
        assertTrue(google.preferences.getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false))
    }

    @Test
    fun `signing out of the app still clears the whole store`() = runTest {
        google.connect()
        google.preferences.putString(PreferenceKeys.PUSH_ENABLED, "false")
        google.preferences.putBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, true)

        val (signedOut, _) = google.service.signOut()

        assertTrue(signedOut)
        assertEquals(GoogleCalendarConnection.NOT_CONNECTED, google.service.connection())
        assertNull(google.preferences.getString(PreferenceKeys.PUSH_ENABLED))
        assertFalse(google.preferences.getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false))
    }

    /** The account Google's sign-in hands back for [address], with its one-time code. */
    private fun signedIn(address: String): GoogleSignInAccount = mockk {
        every { email } returns address
        every { serverAuthCode } returns "code-1"
    }
}
