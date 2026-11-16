package com.coparently.app.data.remote.google

import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.GoogleOAuthFunctions
import com.coparently.app.data.session.SignInProviderSession
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/**
 * The iPhone's Google Calendar connection (MON-24 phase 4, part A, Task 7): what Android's
 * `CredentialManagerService` and `CredentialProviderImpl` are together, without Google's sign-in
 * screen, which the shell runs (`IosGoogleCalendarSignIn`, over `GoogleSignInBridge.swift`) and
 * whose authorization code it hands to [connect].
 *
 * Every rule is Android's, in Android's order, over the same [SecurePreferences] keys (the access
 * token, the refresh token, its expiry and the account's address), so sign-out's `clear()`, an
 * account switch and deletion forget it as they forget Android's:
 *
 * - The code and every renewal go through [GoogleOAuthFunctions], the Cloud Function that holds the
 *   web client's secret (SEC-1 §2); the tokens stay on the phone.
 * - A stored refresh token is a connection, whatever the access token's expiry says ([connection]).
 * - A renewal the server refuses for good drops the tokens and keeps the address, so the connection
 *   reads as one to reconnect; any other failure keeps them for a later try ([accessToken]).
 * - A 401 from Google renews the token once and calls again; a second 401, or a 403 for the
 *   Calendar permission, ends the connection ([withCredential]).
 * - Signing out and disconnecting clear the local store **before** anything goes over the network
 *   (CLAUDE.md "Things that are easy to get wrong" item 23), then revoke the grant, best effort, as
 *   Android's `revokeAccess()` does ([endGrant]).
 *
 * **Why the revoke matters for the next connection.** Android asks Google for a refresh token on
 * every connect (`requestServerAuthCode(webClientId, forceCodeForRefreshToken = true)`). Google
 * Sign-In for iOS 9.2.0 has no such option: it issues the server's code as Google decides, and
 * Google gives a refresh token at a grant's first consent and normally not again while the grant
 * stands. So a disconnect here revokes the grant through Google's revoke endpoint ([revocation]),
 * with the refresh token read before the local clear, and ends Google Sign-In's session and grant
 * ([SignInProviderSession.disconnect]); the next connect is then a first consent again and carries
 * a refresh token. Google Sign-In's session is disconnected only when this phone held a Calendar
 * connection (a token or the connected address, read before the clear): with none, the session is
 * the app's own Google sign-in, whose grant a sign-out must not revoke (it is left to
 * `IosAccountTraces`, which only signs it out), as Android's sign-out revokes only the Calendar
 * client it holds. A grant this phone cannot revoke (offline, or ended elsewhere without a token
 * here) can still answer the next connect without one: the connection then lasts as long as its
 * access token and reads as one to reconnect, which the device checklist checks on a phone.
 *
 * What is read with the token stays on the phone (CLAUDE.md Legal item 10): `CalendarSyncRepository`
 * stores every import as a private event, on the iPhone as on Android.
 *
 * @property revocation Google's revoke endpoint ([GoogleRevokeEndpoint]); a failure is logged and
 *   never stops a sign-out or a disconnect.
 * @property providerSession Google Sign-In's own session ([SignInProviderSession]), ended and its
 *   grant revoked after the local store is cleared, only when a Calendar connection was held.
 * @property now The clock, in epoch millis; a test's own.
 */
class IosGoogleCalendarAccount(
    private val preferences: SecurePreferences,
    private val oauth: GoogleOAuthFunctions,
    private val revocation: GoogleGrantRevocation,
    private val providerSession: SignInProviderSession,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : GoogleCalendarAccount, CredentialProvider {

    /**
     * Redeems [authCode], which Google's sheet answered for the Calendar scope, and stores the
     * tokens and [email], as Android's `handleSignInResult` does.
     *
     * @return null when the connection is stored, else the step that failed: [GoogleSignInFailure.Server]
     *   with the server's reason when the code was not redeemed, [GoogleSignInFailure.Unexpected]
     *   when storing failed on this phone.
     */
    @Suppress("TooGenericExceptionCaught") // a store failure is reported, never thrown at the screen
    suspend fun connect(authCode: String, email: String): GoogleSignInFailure? {
        val tokens = oauth.exchangeAuthCode(authCode).getOrElse { e ->
            val diagnosis = GoogleOAuthFunctions.diagnosisOf(e)
            logger.e(TAG, "Error exchanging auth code for tokens: $diagnosis", e)
            return GoogleSignInFailure.Server(diagnosis)
        }
        return try {
            // A refresh token kept from an earlier connection belongs to the Google account that made
            // it: one that comes back as somebody else without a new one must not inherit it.
            if (tokens.refreshToken.isEmpty() && preferences.getUserEmail() != email) {
                preferences.removeGoogleTokens()
            }
            preferences.putAccessToken(tokens.accessToken)
            // Only when one came back: an empty one must never overwrite a stored token.
            if (tokens.refreshToken.isNotEmpty()) {
                preferences.putRefreshToken(tokens.refreshToken)
            }
            preferences.putTokenExpiry(expiryFrom(tokens.expiresInSeconds))
            preferences.putUserEmail(email)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Could not store the Google tokens", e)
            GoogleSignInFailure.Unexpected
        }
    }

    override fun connection(): GoogleCalendarConnection {
        val accessToken = preferences.getAccessToken()
        val expiresAt = preferences.getTokenExpiry() ?: 0L
        return when {
            !preferences.getRefreshToken().isNullOrEmpty() -> GoogleCalendarConnection.CONNECTED
            accessToken != null && expiresAt > now() -> GoogleCalendarConnection.CONNECTED
            // Connected here once, and nothing left to renew with: Google ended it.
            preferences.getUserEmail() != null -> GoogleCalendarConnection.NEEDS_RECONNECT
            else -> GoogleCalendarConnection.NOT_CONNECTED
        }
    }

    override suspend fun accessToken(forceRefresh: Boolean): GoogleAccess {
        val stored = preferences.getAccessToken()
        val expiresAt = preferences.getTokenExpiry() ?: 0L
        val now = now()
        val refreshToken = preferences.getRefreshToken()?.takeIf { it.isNotEmpty() }
        return when {
            !forceRefresh && stored != null && expiresAt > now + REFRESH_MARGIN_MILLIS ->
                GoogleAccess.Granted(stored)
            refreshToken != null -> refresh(refreshToken)
            // Nothing to renew with: a token that has not run out is still good for what is left
            // of it, unless Google has just refused it.
            !forceRefresh && stored != null && expiresAt > now -> GoogleAccess.Granted(stored)
            else -> nothingToRenewWith()
        }
    }

    /** Signs the whole app out of Google here: the local store first, then the grant ([endGrant]). */
    override suspend fun signOut(): Pair<Boolean, String?> {
        val token = grantToken()
        val calendarHeld = calendarHeld(token)
        preferences.clear()
        endGrant(token, calendarHeld)
        return Pair(true, null)
    }

    /** Disconnects Google Calendar alone: Google's own keys first, then the grant ([endGrant]). */
    override suspend fun disconnect() {
        val token = grantToken()
        val calendarHeld = calendarHeld(token)
        preferences.removeGoogleCalendarAccount()
        endGrant(token, calendarHeld)
    }

    /** The token whose grant a disconnect revokes: the refresh token, else the access token, read before the clear. */
    private fun grantToken(): String? =
        preferences.getRefreshToken()?.takeIf { it.isNotEmpty() }
            ?: preferences.getAccessToken()?.takeIf { it.isNotEmpty() }

    /**
     * Whether this phone held a Google Calendar connection, read before the clear: a [token], or the
     * address a connect stored (a connection Google has since ended, whose grant may still stand).
     * Only the Calendar connect writes that address, so neither is there for an app sign-in alone.
     */
    private fun calendarHeld(token: String?): Boolean = token != null || preferences.getUserEmail() != null

    /**
     * Revokes [token]'s grant and, when [calendarHeld], ends Google Sign-In's session and grant, best
     * effort: the credential is already gone from this phone, so a failure is logged and the caller
     * goes on. Without a Calendar connection the session is the app's own Google sign-in, and its
     * grant is left standing.
     */
    @Suppress("TooGenericExceptionCaught") // a revoke's failure must never stop a sign-out
    private suspend fun endGrant(token: String?, calendarHeld: Boolean) {
        if (token != null) {
            try {
                revocation.revoke(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "Google revoke failed after the local credential was cleared", e)
            }
        }
        if (calendarHeld) providerSession.disconnect()
    }

    override suspend fun getCredential(forceRefresh: Boolean): GoogleCredential =
        when (val access = accessToken(forceRefresh)) {
            is GoogleAccess.Granted -> GoogleCredential(access.accessToken)
            is GoogleAccess.Denied -> {
                logger.w(TAG, "No Google credential: ${access.failure}")
                throw GoogleAccessException(access.failure)
            }
        }

    override suspend fun <T> withCredential(call: suspend (GoogleCredential) -> T): T {
        val credential = getCredential()
        return try {
            call(credential)
        } catch (e: GoogleCalendarHttpException) {
            when {
                e.statusCode == HTTP_UNAUTHORIZED -> callAgainRenewed(call)
                // A renewed token would carry the same permissions.
                lacksCalendarScope(e) -> endConnection()
                else -> throw e
            }
        }
    }

    /** Runs [call] once more with a token renewed just now; a second refusal ends the connection. */
    private suspend fun <T> callAgainRenewed(call: suspend (GoogleCredential) -> T): T {
        val renewed = getCredential(forceRefresh = true)
        return try {
            call(renewed)
        } catch (e: GoogleCalendarHttpException) {
            if (e.statusCode == HTTP_UNAUTHORIZED || lacksCalendarScope(e)) endConnection() else throw e
        }
    }

    /** Trades [refreshToken] for a new access token, through the function that holds the secret. */
    private suspend fun refresh(refreshToken: String): GoogleAccess {
        val tokens = oauth.refreshAccessToken(refreshToken).getOrElse { e -> return renewalFailed(e) }
        preferences.putAccessToken(tokens.accessToken)
        preferences.putTokenExpiry(expiryFrom(tokens.expiresInSeconds))
        return GoogleAccess.Granted(tokens.accessToken)
    }

    /** A refusal for good drops the tokens; anything else keeps them for a later try. */
    private fun renewalFailed(e: Throwable): GoogleAccess {
        val failure = (e as? GoogleAccessException)?.failure ?: GoogleAccessFailure.UNAVAILABLE
        logger.w(TAG, "Could not renew the Google access token: $failure", e)
        if (failure == GoogleAccessFailure.NEEDS_RECONNECT) markNeedsReconnect()
        return GoogleAccess.Denied(failure)
    }

    /** No usable token and none to renew with: a connection Google ended, if there was one. */
    private fun nothingToRenewWith(): GoogleAccess =
        if (preferences.getUserEmail() == null) {
            GoogleAccess.Denied(GoogleAccessFailure.NOT_CONNECTED)
        } else {
            markNeedsReconnect()
            GoogleAccess.Denied(GoogleAccessFailure.NEEDS_RECONNECT)
        }

    /** Ends a connection Google refused for good, and tells the caller to ask for a reconnect. */
    private fun endConnection(): Nothing {
        markNeedsReconnect()
        throw GoogleAccessException(GoogleAccessFailure.NEEDS_RECONNECT)
    }

    /** Forgets the tokens and keeps the address, so [connection] reads as one to reconnect. */
    private fun markNeedsReconnect() = preferences.removeGoogleTokens()

    /** When a token that lives [expiresInSeconds] from now runs out, as epoch millis. */
    private fun expiryFrom(expiresInSeconds: Long): Long =
        now() + (expiresInSeconds.takeIf { it > 0L } ?: DEFAULT_TOKEN_LIFETIME_SECONDS) * MILLIS_PER_SECOND

    private companion object {
        const val TAG = "IosGoogleCalendar"

        /** What an access token is assumed to live for when Google says nothing, as on Android. */
        const val DEFAULT_TOKEN_LIFETIME_SECONDS = 3600L
        const val MILLIS_PER_SECOND = 1000L

        /** How long before its expiry a token is renewed rather than used, as on Android. */
        const val REFRESH_MARGIN_MILLIS = 5 * 60 * 1000L

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val INSUFFICIENT_PERMISSIONS = "insufficientPermissions"
        const val INSUFFICIENT_SCOPE = "insufficient_scope"

        /** Whether Google refused [e] for want of the Calendar permission, as `CredentialProviderImpl` reads it. */
        fun lacksCalendarScope(e: GoogleCalendarHttpException): Boolean {
            if (e.statusCode != HTTP_FORBIDDEN) return false
            return INSUFFICIENT_PERMISSIONS in e.reasons || e.authenticate?.contains(INSUFFICIENT_SCOPE) == true
        }
    }
}
