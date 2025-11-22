package com.coparently.app.data.remote.google

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.coparently.app.R
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
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
    private val encryptedPreferences: EncryptedPreferences
) {
    private val credentialManager = CredentialManager.create(context)
    private val _googleSignInClient: GoogleSignInClient? by lazy {
        try {
            val webClientId = getWebClientId()
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestServerAuthCode(webClientId)
                .requestEmail()
                .requestScopes(Scope(CalendarScopes.CALENDAR))
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
        private val JSON_FACTORY = GsonFactory.getDefaultInstance()
        private val HTTP_TRANSPORT = NetHttpTransport()
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
     * @return Pair of (GoogleSignInAccount?, errorMessage?)
     */
    suspend fun handleSignInResult(completedTask: Task<GoogleSignInAccount>): Pair<GoogleSignInAccount?, String?> {
        return try {
            val account = completedTask.await()
            Log.d(TAG, "Sign-in successful for: ${account.email}")

            // Получаем authorization code
            val authCode = account.serverAuthCode
            if (authCode != null) {
                // Обмениваем authorization code на access и refresh токены
                exchangeAuthCodeForTokens(authCode, account.email ?: "")
                Pair(account, null)
            } else {
                Pair(null, "No authorization code received")
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in failed with code: ${e.statusCode}", e)
            val errorMsg = when (e.statusCode) {
                12500 -> "Google Play Services not available"
                12501 -> "Sign-in cancelled by user"
                12502 -> "Sign-in failed"
                else -> "Sign-in error: ${e.message}"
            }
            Pair(null, errorMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sign-in: ${e.message}", e)
            Pair(null, "Unexpected error: ${e.message}")
        }
    }

    /**
     * Обменивает authorization code на access и refresh токены.
     *
     * @param authCode Authorization code от Google Sign-In
     * @param email Email пользователя для идентификации
     */
    private suspend fun exchangeAuthCodeForTokens(authCode: String, email: String) {
        try {
            val tokenResponse = withContext(Dispatchers.IO) {
                GoogleAuthorizationCodeTokenRequest(
                    HTTP_TRANSPORT,
                    JSON_FACTORY,
                    getWebClientId(),
                    getClientSecret(), // client secret required for web OAuth clients
                    authCode,
                    "" // redirect URI not needed for mobile apps
                ).execute()
            }

            val accessToken = tokenResponse.accessToken
            val refreshToken = tokenResponse.refreshToken
            val expiresInSeconds = tokenResponse.expiresInSeconds

            if (accessToken != null) {
                encryptedPreferences.putAccessToken(accessToken)
                encryptedPreferences.putRefreshToken(refreshToken ?: "")
                encryptedPreferences.putTokenExpiry(System.currentTimeMillis() + (expiresInSeconds ?: 3600) * 1000)
                encryptedPreferences.putUserEmail(email)

                Log.d(TAG, "Tokens obtained and stored successfully")
            } else {
                Log.e(TAG, "No access token received")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error exchanging auth code for tokens: ${e.message}", e)
            throw e
        }
    }

    /**
     * Получает access token, проверяя срок действия и обновляя при необходимости.
     * Использует сохраненные токены и refresh токен для обновления.
     *
     * @return Pair of (accessToken?, errorMessage?)
     */
    suspend fun getAccessToken(): Pair<String?, String?> {
        return try {
            val storedToken = encryptedPreferences.getAccessToken()
            val expiryTime = encryptedPreferences.getTokenExpiry()
            val refreshToken = encryptedPreferences.getRefreshToken()

            // Проверяем, истек ли токен (с запасом в 5 минут)
            if (storedToken != null && expiryTime != null &&
                expiryTime > System.currentTimeMillis() + 300000) {
                Log.d(TAG, "Using stored access token")
                return Pair(storedToken, null)
            }

            // Если токен истек или отсутствует, обновляем
            if (refreshToken.isNullOrEmpty()) {
                Log.e(TAG, "No refresh token available")
                return Pair(null, "No refresh token available. Please sign in again.")
            }

            Log.d(TAG, "Refreshing access token")
            refreshAccessToken()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token: ${e.message}", e)
            Pair(null, "Error getting access token: ${e.message}")
        }
    }

    /**
     * Обновляет access token используя refresh токен.
     *
     * @return Pair of (accessToken?, errorMessage?)
     */
    private suspend fun refreshAccessToken(): Pair<String?, String?> {
        return try {
            val refreshToken = encryptedPreferences.getRefreshToken()
                ?: return Pair(null, "No refresh token available")

            val tokenResponse = withContext(Dispatchers.IO) {
                com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest(
                    HTTP_TRANSPORT,
                    JSON_FACTORY,
                    refreshToken,
                    getWebClientId(),
                    getClientSecret() // client secret required for web OAuth clients
                ).execute()
            }

            val newAccessToken = tokenResponse.accessToken
            val expiresInSeconds = tokenResponse.expiresInSeconds

            if (newAccessToken != null) {
                encryptedPreferences.putAccessToken(newAccessToken)
                encryptedPreferences.putTokenExpiry(System.currentTimeMillis() + (expiresInSeconds ?: 3600) * 1000)

                Log.d(TAG, "Access token refreshed successfully")
                Pair(newAccessToken, null)
            } else {
                Log.e(TAG, "No access token in refresh response")
                Pair(null, "Failed to refresh token")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error refreshing token: ${e.message}", e)
            Pair(null, "Network error refreshing token: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error refreshing token: ${e.message}", e)
            Pair(null, "Error refreshing token: ${e.message}")
        }
    }

    /**
     * Проверяет, есть ли активная сессия с валидными токенами.
     */
    fun isSignedIn(): Boolean {
        val token = encryptedPreferences.getAccessToken()
        val expiry = encryptedPreferences.getTokenExpiry()
        return token != null && expiry != null && expiry > System.currentTimeMillis() + 300000
    }

    /**
     * Выполняет выход из аккаунта, очищая все сохраненные токены и завершая Google Sign-In сессию.
     */
    suspend fun signOut(): Pair<Boolean, String?> {
        return try {
            _googleSignInClient?.signOut()?.await()
            _googleSignInClient?.revokeAccess()?.await()
            encryptedPreferences.clear()
            Log.d(TAG, "User signed out successfully")
            Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out: ${e.message}", e)
            Pair(false, "Error during sign out: ${e.message}")
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
                throw IllegalStateException("Web Client ID not configured. Please set up OAuth 2.0 Client ID in Google Cloud Console and update default_web_client_id in strings.xml")
            }
            clientId
        } catch (e: Exception) {
            Log.e(TAG, "Web Client ID not configured: ${e.message}")
            throw IllegalStateException("Google OAuth not configured. Please:\n1. Go to Google Cloud Console\n2. Create OAuth 2.0 Client ID for Web application\n3. Add the Client ID to default_web_client_id in strings.xml\n4. Enable Google Calendar API")
        }
    }

    /**
     * Получает Client Secret из ресурсов или конфигурации.
     * Client Secret должен быть получен из Google Cloud Console для Web Client ID
     * и добавлен в strings.xml как google_client_secret
     */
    private fun getClientSecret(): String {
        return try {
            // Попытка получить из strings.xml
            val clientSecret = context.getString(R.string.google_client_secret)
            if (clientSecret.contains("YOUR_CLIENT_SECRET") || clientSecret.contains("PASTE_YOUR_CLIENT_SECRET")) {
                throw IllegalStateException("Client Secret not configured. Please:\n1. Go to Google Cloud Console\n2. Find your Web Client ID\n3. Copy the Client Secret\n4. Update google_client_secret in strings.xml")
            }
            clientSecret
        } catch (e: Exception) {
            Log.e(TAG, "Client Secret not configured: ${e.message}")
            throw IllegalStateException("Google OAuth client secret not configured. Please update google_client_secret in strings.xml with the client secret from Google Cloud Console")
        }
    }
}

