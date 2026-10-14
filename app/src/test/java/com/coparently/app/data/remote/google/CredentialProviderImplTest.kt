package com.coparently.app.data.remote.google

import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.ACCESS
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.FRESH
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.HOUR
import com.coparently.app.data.remote.google.GoogleCredentialsFixture.Companion.REFRESH
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The credential the Calendar API is called with, and what each of Google's refusals of it does
 * to the connection (#9).
 *
 * `getCredential` used to ask `isSignedIn()` — "the access token is good for five more minutes" —
 * and return null when it was not, so the renewal it existed to perform never ran: an import
 * failed about an hour after Calendar was connected. And a 401 or 403 from the Calendar API ended
 * as a message only; nothing renewed a token Google had cut short, and nothing offered a
 * reconnect for a connection Google would never accept again.
 */
class CredentialProviderImplTest {

    private val google = GoogleCredentialsFixture()
    private val provider = CredentialProviderImpl(google.service, google.preferences)

    @After
    fun tearDown() {
        google.close()
    }

    @Test
    fun `an expired access token is renewed, not refused`() = runTest {
        google.connect(expiresInMillis = -HOUR)
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns google.renewed()

        val credential = provider.getCredential()

        assertEquals(FRESH, credential.accessToken)
        coVerify(exactly = 1) { google.oauth.refreshAccessToken(REFRESH) }
    }

    @Test
    fun `no connection fails saying so`() = runTest {
        val failure = assertFailsWith<GoogleAccessException> { provider.getCredential() }

        assertEquals(GoogleAccessFailure.NOT_CONNECTED, failure.failure)
    }

    @Test
    fun `a renewal that could not reach Google fails saying so, and keeps the connection`() = runTest {
        google.connect(expiresInMillis = -HOUR)
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns
            Result.failure(GoogleAccessException(GoogleAccessFailure.UNAVAILABLE))

        val failure = assertFailsWith<GoogleAccessException> { provider.getCredential() }

        assertEquals(GoogleAccessFailure.UNAVAILABLE, failure.failure)
        assertEquals(REFRESH, google.preferences.getRefreshToken())
    }

    @Test
    fun `a call refused with 401 is made once more, with a renewed token`() = runTest {
        // A token its stored expiry still calls good can have been revoked or cut short.
        google.connect()
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns google.renewed()
        val tokensSent = mutableListOf<String>()

        val result = provider.withCredential { credential ->
            tokensSent += credential.accessToken
            if (tokensSent.size == 1) throw refused(HTTP_UNAUTHORIZED) else "events"
        }

        assertEquals("events", result)
        assertEquals(listOf(ACCESS, FRESH), tokensSent)
        assertEquals(GoogleCalendarConnection.CONNECTED, google.service.connection())
    }

    @Test
    fun `a 401 on a token renewed just now ends the connection`() = runTest {
        google.connect()
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns google.renewed()
        var calls = 0

        val failure = assertFailsWith<GoogleAccessException> {
            provider.withCredential<String> {
                calls++
                throw refused(HTTP_UNAUTHORIZED)
            }
        }

        assertEquals(GoogleAccessFailure.NEEDS_RECONNECT, failure.failure)
        assertEquals(2, calls, "renewed once, and never again")
        assertNull(google.preferences.getRefreshToken(), "the dead token is dropped")
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, google.service.connection())
    }

    @Test
    fun `a renewal Google refuses for good, after a 401, ends the connection`() = runTest {
        google.connect()
        coEvery { google.oauth.refreshAccessToken(REFRESH) } returns
            Result.failure(GoogleAccessException(GoogleAccessFailure.NEEDS_RECONNECT))

        val failure = assertFailsWith<GoogleAccessException> {
            provider.withCredential<String> { throw refused(HTTP_UNAUTHORIZED) }
        }

        assertEquals(GoogleAccessFailure.NEEDS_RECONNECT, failure.failure)
        assertEquals(GoogleCalendarConnection.NEEDS_RECONNECT, google.service.connection())
    }

    @Test
    fun `a 403 for the Calendar permission ends the connection without a renewal`() = runTest {
        // Granular consent lets an account sign in and untick Calendar; a new token from the same
        // grant would carry the same permissions, so only a reconnect helps.
        google.connect()
        var calls = 0

        val failure = assertFailsWith<GoogleAccessException> {
            provider.withCredential<String> {
                calls++
                throw forbidden(reason = "insufficientPermissions")
            }
        }

        assertEquals(GoogleAccessFailure.NEEDS_RECONNECT, failure.failure)
        assertEquals(1, calls)
        assertNull(google.preferences.getRefreshToken())
        coVerify(exactly = 0) { google.oauth.refreshAccessToken(any()) }
    }

    @Test
    fun `any other refusal comes through unchanged, and the connection stays`() = runTest {
        // A 403 is not always about the connection: the Calendar API answers a rate limit with one.
        google.connect()
        val rateLimited = forbidden(reason = "rateLimitExceeded")

        val thrown = assertFailsWith<GoogleJsonResponseException> {
            provider.withCredential<String> { throw rateLimited }
        }

        assertSame(rateLimited, thrown)
        assertEquals(REFRESH, google.preferences.getRefreshToken())
        assertEquals(GoogleCalendarConnection.CONNECTED, google.service.connection())
    }

    @Test
    fun `a missing permission is read from the WWW-Authenticate header too`() {
        val headerOnly = HttpResponseException.Builder(
            HTTP_FORBIDDEN,
            "Forbidden",
            HttpHeaders().setAuthenticate("Bearer realm=\"https://accounts.google.com/\", error=\"insufficient_scope\"")
        ).build()

        assertTrue(CredentialProviderImpl.lacksCalendarScope(headerOnly))
        assertFalse(CredentialProviderImpl.lacksCalendarScope(refused(HTTP_UNAUTHORIZED)))
        assertFalse(CredentialProviderImpl.lacksCalendarScope(forbidden(reason = "rateLimitExceeded")))
    }

    private fun refused(status: Int): HttpResponseException =
        HttpResponseException.Builder(status, "Unauthorized", HttpHeaders()).build()

    /** A 403 as the Calendar API sends it, naming [reason] in its error details. */
    private fun forbidden(reason: String): GoogleJsonResponseException {
        val details = GoogleJsonError().apply {
            code = HTTP_FORBIDDEN
            errors = listOf(GoogleJsonError.ErrorInfo().apply { this.reason = reason })
        }
        return GoogleJsonResponseException(
            HttpResponseException.Builder(HTTP_FORBIDDEN, "Forbidden", HttpHeaders()),
            details
        )
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
