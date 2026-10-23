package com.coparently.app.data.remote.google

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import com.coparently.app.R
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.firebase.GoogleOAuthFunctions
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сервис для аутентификации Google с использованием Google Sign-In API и OAuth2.
 * Обеспечивает полный OAuth2 flow с access и refresh токенами.
 *
 * @see <a href="https://developers.google.com/identity/sign-in/android/start">Google Sign-In for Android</a>
 */
@Singleton
class CredentialManagerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPreferences: EncryptedPreferences,
    private val googleOAuthFunctions: GoogleOAuthFunctions
) {
    private val credentialManager: CredentialManager? = try {
        CredentialManager.create(context)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create CredentialManager", e)
        null
    }
    private val _googleSignInClient: GoogleSignInClient? by lazy {
        try {
            // One client id for both, and it cannot be otherwise: `GoogleSignInOptions.Builder`
            // rejects an id token and a server auth code naming different web clients
            // ("two different server client ids provided") from inside `requestServerAuthCode`,
            // before any request reaches Google. Splitting them was tried and threw on the
            // builder, taking Google sign-in down with it.
            //
            // So the Cloud Function that redeems the code must hold *this* client's secret.
            val webClientId = getWebClientId()
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                // `true` (forceCodeForRefreshToken): a refresh token on every connect. Without it
                // Google issues one only at an account's first consent, so a reinstall, a second
                // phone, another Google account or an exchange whose answer was lost connected
                // with an access token alone — and dropped off an hour later. The price is the
                // consent screen on every connect, which is a deliberate act in Settings.
                .requestServerAuthCode(webClientId, true)
                .requestEmail()
                .requestScopes(Scope(GoogleCalendarAccess.SCOPE))
                .build()
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create GoogleSignInClient", e)
            null
        }
    }

    // Separate client for authentication (without calendar scope)
    private val _authGoogleSignInClient: GoogleSignInClient? by lazy {
        try {
            val webClientId = getWebClientId()
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create auth GoogleSignInClient", e)
            null
        }
    }

    companion object {
        private const val TAG = "CredentialManager"

        /**
         * What an access token is assumed to live for when Google says nothing.
         *
         * One hour, which is what Google issues in practice. A wrong guess here costs at worst
         * one refused API call and a refresh, so a default is safer than treating a missing
         * value as "already expired" and refreshing on every single call.
         */
        private const val DEFAULT_TOKEN_LIFETIME_SECONDS = 3600L

        /**
         * How long before its expiry an access token is renewed rather than used: five minutes,
         * so a token is never sent that runs out during the call it was sent with.
         */
        private const val REFRESH_MARGIN_MILLIS = 5 * 60 * 1000L
    }

    /**
     * Получает Google Sign-In Client для использования в UI компонентах.
     */
    fun getGoogleSignInClient(): GoogleSignInClient? {
        return _googleSignInClient
    }

    /**
     * Получает Google Sign-In Client для аутентификации (без calendar scope).
     */
    fun getAuthGoogleSignInClient(): GoogleSignInClient? {
        return _authGoogleSignInClient
    }

    /**
     * Выполняет аутентификацию через Google Sign-In и получает OAuth2 токены.
     * Это полноценный OAuth2 flow для Google Calendar API.
     *
     * @return Pair of (GoogleSignInAccount?, errorMessage?)
     */
    suspend fun signInWithGoogle(): Pair<GoogleSignInAccount?, String?> {
        return try {
            val client = _googleSignInClient
            if (client == null) {
                Pair(null, "Google Sign-In client is not available. OAuth may not be configured.")
            } else {
                val signInIntent = client.signInIntent
                // Note: This method assumes the sign-in intent is handled by an Activity
                // The actual implementation should be in an Activity or Fragment
                Pair(null, "Sign-in intent should be handled by Activity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting sign-in: ${e.message}", e)
            Pair(null, "Failed to start sign-in: ${e.message}")
        }
    }

    /**
     * Обрабатывает результат Google Sign-In и обменивает authorization code на токены.
     *
     * @param completedTask Task с результатом Google Sign-In
     * @return The account, or why connecting stopped and at which step ([GoogleSignInFailure]).
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun handleSignInResult(
        completedTask: Task<GoogleSignInAccount>
    ): Pair<GoogleSignInAccount?, GoogleSignInFailure?> {
        return try {
            val account = completedTask.await()
            Log.d(TAG, "Sign-in successful for: ${account.email}")
            val authCode = account.serverAuthCode ?: return Pair(null, GoogleSignInFailure.NoAuthCode)
            // Обмениваем authorization code на access и refresh токены
            exchangeAuthCodeForTokens(authCode, account.email ?: "")
            Pair(account, null)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed with code: ${e.statusCode}", e)
            val failure = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                GoogleSignInFailure.Cancelled
            } else {
                GoogleSignInFailure.Google(e.statusCode)
            }
            Pair(null, failure)
        } catch (e: GoogleAccessException) {
            Pair(null, GoogleSignInFailure.Server(GoogleOAuthFunctions.diagnosisOf(e)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign-in: ${e.message}", e)
            Pair(null, GoogleSignInFailure.Unexpected)
        }
    }

    /**
     * Обменивает authorization code на access и refresh токены.
     *
     * @param authCode Authorization code от Google Sign-In
     * @param email Email пользователя для идентификации
     */
    private suspend fun exchangeAuthCodeForTokens(authCode: String, email: String) {
        // Through the Cloud Function, not directly (SEC-1 §2). The web OAuth client needs a
        // client secret for this grant, and that secret used to be compiled into the APK —
        // where it was available to anybody who installed the app.
        val tokens = googleOAuthFunctions.exchangeAuthCode(authCode).getOrElse { e ->
            Log.e(TAG, "Error exchanging auth code for tokens: ${GoogleOAuthFunctions.diagnosisOf(e)}", e)
            throw e
        }

        // A refresh token kept from an earlier connection belongs to the Google account that made
        // it. A connection that comes back as somebody else without a new one must not inherit it:
        // the next renewal would read the first account's calendar under the second's address.
        if (tokens.refreshToken.isEmpty() && encryptedPreferences.getUserEmail() != email) {
            encryptedPreferences.removeGoogleTokens()
        }
        encryptedPreferences.putAccessToken(tokens.accessToken)
        // Only when one came back. Google omits the refresh token when the account has already
        // granted consent, and blanking the stored one there would cost the parent a re-consent
        // for nothing. Connecting asks for one every time now (`forceCodeForRefreshToken`), so
        // this is the exception, not the rule.
        if (tokens.refreshToken.isNotEmpty()) {
            encryptedPreferences.putRefreshToken(tokens.refreshToken)
        }
        encryptedPreferences.putTokenExpiry(expiryFrom(tokens.expiresInSeconds))
        encryptedPreferences.putUserEmail(email)

        Log.d(TAG, "Tokens obtained and stored successfully")
    }

    /** When a token that lives [expiresInSeconds] from now runs out, as epoch millis. */
    private fun expiryFrom(expiresInSeconds: Long): Long =
        System.currentTimeMillis() +
            (expiresInSeconds.takeIf { it > 0L } ?: DEFAULT_TOKEN_LIFETIME_SECONDS) * 1000

    /**
     * Whether Google Calendar is connected on this device, from what it stores and without asking
     * Google — so Settings can say it truthfully on every launch.
     *
     * A stored refresh token is a connection, whatever the access token's expiry says: renewing
     * the access token is [accessToken]'s job. This used to be `isSignedIn()`, "the access token is
     * good for five more minutes", which every connection stopped being about 55 minutes after it
     * was made — Settings then said "not signed in", and the import refused to start.
     */
    fun connection(): GoogleCalendarConnection {
        val accessToken = encryptedPreferences.getAccessToken()
        val expiresAt = encryptedPreferences.getTokenExpiry() ?: 0L
        return when {
            !encryptedPreferences.getRefreshToken().isNullOrEmpty() -> GoogleCalendarConnection.CONNECTED
            accessToken != null && expiresAt > System.currentTimeMillis() -> GoogleCalendarConnection.CONNECTED
            // Connected here once, and nothing left to renew with: Google ended it.
            encryptedPreferences.getUserEmail() != null -> GoogleCalendarConnection.NEEDS_RECONNECT
            else -> GoogleCalendarConnection.NOT_CONNECTED
        }
    }

    /**
     * An access token to call Google with, renewed through the token function when the stored one
     * has run out, is about to, or [forceRefresh] says Google has just refused it.
     *
     * Each way this can fail asks something different of the parent, so each is typed:
     * [GoogleAccessFailure.NEEDS_RECONNECT] drops the dead tokens (keeping the address, so the
     * connection reads as one to reconnect), [GoogleAccessFailure.UNAVAILABLE] keeps everything for
     * a later try, and [GoogleAccessFailure.NOT_CONNECTED] means nothing was ever stored here.
     *
     * @param forceRefresh Renew even though the stored expiry says the token is still good — what a
     *   caller does once after the Calendar API refused it (401).
     */
    suspend fun accessToken(forceRefresh: Boolean = false): GoogleAccess {
        val stored = encryptedPreferences.getAccessToken()
        val expiresAt = encryptedPreferences.getTokenExpiry() ?: 0L
        val now = System.currentTimeMillis()
        val refreshToken = encryptedPreferences.getRefreshToken()?.takeIf { it.isNotEmpty() }
        return when {
            !forceRefresh && stored != null && expiresAt > now + REFRESH_MARGIN_MILLIS ->
                GoogleAccess.Granted(stored)
            refreshToken != null -> refresh(refreshToken)
            // Nothing to renew with: a token that has not run out is still good for what is left
            // of it — unless Google has just refused it.
            !forceRefresh && stored != null && expiresAt > now -> GoogleAccess.Granted(stored)
            else -> nothingToRenewWith()
        }
    }

    /** Trades [refreshToken] for a new access token, through the function that holds the secret. */
    private suspend fun refresh(refreshToken: String): GoogleAccess {
        val tokens = googleOAuthFunctions.refreshAccessToken(refreshToken).getOrElse { e ->
            return renewalFailed(e)
        }
        encryptedPreferences.putAccessToken(tokens.accessToken)
        encryptedPreferences.putTokenExpiry(expiryFrom(tokens.expiresInSeconds))
        Log.d(TAG, "Access token refreshed successfully")
        return GoogleAccess.Granted(tokens.accessToken)
    }

    /**
     * What a failed renewal leaves behind. A refusal for good — Google's `invalid_grant`, or a
     * token the function holds no fingerprint for — drops the tokens, because no later try can
     * bring them back. Anything else keeps them: dropping a token that still works would cost the
     * parent a consent screen for a network blip.
     */
    private fun renewalFailed(e: Throwable): GoogleAccess {
        val failure = (e as? GoogleAccessException)?.failure ?: GoogleAccessFailure.UNAVAILABLE
        Log.w(TAG, "Could not renew the Google access token: $failure", e)
        if (failure == GoogleAccessFailure.NEEDS_RECONNECT) {
            markNeedsReconnect()
        }
        return GoogleAccess.Denied(failure)
    }

    /**
     * No usable access token and no refresh token. On a device that was connected — the address
     * is still stored — that is a connection Google ended: what is left of it is dropped, and it
     * reads as one to reconnect rather than one never made.
     */
    private fun nothingToRenewWith(): GoogleAccess =
        if (encryptedPreferences.getUserEmail() == null) {
            GoogleAccess.Denied(GoogleAccessFailure.NOT_CONNECTED)
        } else {
            markNeedsReconnect()
            GoogleAccess.Denied(GoogleAccessFailure.NEEDS_RECONNECT)
        }

    /**
     * Ends a connection Google refused for good — a revoked or expired grant, a token the function
     * no longer recognises, a Calendar permission the account never granted. Forgets the tokens and
     * keeps the address, so [connection] reads [GoogleCalendarConnection.NEEDS_RECONNECT] and
     * Settings offers Reconnect.
     */
    fun markNeedsReconnect() {
        encryptedPreferences.removeGoogleTokens()
    }

    /**
     * Signs the whole app out of Google on this device: clears every stored preference
     * ([EncryptedPreferences.clear], with its exemptions) and ends the Google sign-in. What "Sign
     * out of app" runs; disconnecting Google Calendar alone is [disconnect].
     */
    suspend fun signOut(): Pair<Boolean, String?> {
        // The local credential goes first, unconditionally. `revokeAccess()` is a network call,
        // and while it sat *before* the clear, an offline sign-out threw past it and left the
        // refresh token, the access token and the calendar id on disk — for the next account to
        // sign in on this phone and find its Settings saying "connected as" the previous one,
        // and its import pulling that person's calendar into this family.
        encryptedPreferences.clear()
        endGoogleSignIn()
        return Pair(true, null)
    }

    /**
     * Disconnects Google Calendar on this device and nothing else: Google's own keys go
     * ([EncryptedPreferences.removeGoogleCalendarAccount]), then the Google sign-in ends. What
     * "Sign out from Calendar sync" runs.
     *
     * It used to be [signOut], whose `clear()` also took the push opt-out, the telemetry answer,
     * the family on screen, the sync cursors and the calendar filters with it — and the "Enable
     * sync" switch, which now stays to be found as it was on a reconnect.
     */
    suspend fun disconnect() {
        // Local first, for the reason [signOut] gives.
        encryptedPreferences.removeGoogleCalendarAccount()
        endGoogleSignIn()
    }

    /**
     * Ends the Google sign-in and revokes the grant, best effort: the credential is already gone
     * from this device, and the token expires on its own.
     */
    private suspend fun endGoogleSignIn() {
        try {
            _googleSignInClient?.signOut()?.await()
            _googleSignInClient?.revokeAccess()?.await()
            Log.d(TAG, "User signed out successfully")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Google revoke failed after the local credential was cleared", e)
        }
    }

    /**
     * Получает email текущего пользователя.
     */
    fun getCurrentUserEmail(): String? {
        return encryptedPreferences.getUserEmail()
    }

    /**
     * Получает Web Client ID из ресурсов или конфигурации.
     * Web Client ID должен быть настроен в Google Cloud Console как OAuth 2.0 Client ID для веб-приложения
     * и добавлен в strings.xml как default_web_client_id
     */
    private fun getWebClientId(): String {
        return try {
            // Попытка получить из strings.xml
            val clientId = context.getString(R.string.default_web_client_id)
            if (clientId.contains("YOUR_WEB_CLIENT_ID")) {
                throw IllegalStateException(
                    "Web Client ID not configured. Please set up OAuth 2.0 Client ID in Google Cloud " +
                        "Console and update default_web_client_id in strings.xml"
                )
            }
            clientId
        } catch (e: Exception) {
            Log.e(TAG, "Web Client ID not configured: ${e.message}")
            throw IllegalStateException(
                "Google OAuth not configured. Please:\n1. Go to Google Cloud Console\n" +
                    "2. Create OAuth 2.0 Client ID for Web application\n" +
                    "3. Add the Client ID to default_web_client_id in strings.xml\n" +
                    "4. Enable Google Calendar API"
            )
        }
    }
}
