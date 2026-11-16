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
 * - Signing out and disconnecting clear the local store **before** Google Sign-In's session ends
 *   (CLAUDE.md "Things that are easy to get wrong" item 23).
 *
 * What is read with the token stays on the phone (CLAUDE.md Legal item 10): `CalendarSyncRepository`
 * stores every import as a private event, on the iPhone as on Android.
 *
 * @property providerSession Google Sign-In's own session ([SignInProviderSession]), ended after the
 *   local store is cleared. Google Sign-In for iOS revokes only the grant of the account it holds,
 *   which is the sign-in sheet's, not the one the server redeemed, so nothing is revoked here: the
 *   refresh token is forgotten on the phone, and a parent revokes the grant in their Google account.
 * @property now The clock, in epoch millis; a test's own.
 */
class IosGoogleCalendarAccount(
    private val preferences: SecurePreferences,
    private val oauth: GoogleOAuthFunctions,
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

    /** Signs the whole app out of Google here: the local store first, then Google's session. */
    override suspend fun signOut(): Pair<Boolean, String?> {
        preferences.clear()
        providerSession.signOut()
        return Pair(true, null)
    }

    /** Disconnects Google Calendar alone: Google's own keys first, then Google's session. */
    override suspend fun disconnect() {
        preferences.removeGoogleCalendarAccount()
        providerSession.signOut()
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
