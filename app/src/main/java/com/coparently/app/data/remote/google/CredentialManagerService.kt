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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.api.services.calendar.CalendarScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сервис для аутентификации Google с использованием нового Credential Manager API.
 * Заменяет deprecated GoogleSignIn API.
 *
 * @see <a href="https://developer.android.com/training/sign-in/credential-manager">Credential Manager Guide</a>
 */
@Singleton
class CredentialManagerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPreferences: EncryptedPreferences
) {
    private val credentialManager = CredentialManager.create(context)

    companion object {
        private const val TAG = "CredentialManager"

        // Получить Web Client ID из strings.xml или google-services.json
        // Это значение должно совпадать с OAuth 2.0 Client ID из Google Cloud Console
        private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID" // TODO: Replace with actual client ID
    }

    /**
     * Получает учетные данные Google ID с помощью Credential Manager.
     * Это современная замена GoogleSignIn.getSignedInAccountFromIntent().
     *
     * @param filterByAuthorizedAccounts Если true, показывать только авторизованные аккаунты
     * @return Pair of (GoogleIdTokenCredential?, errorMessage?)
     */
    suspend fun getGoogleIdCredential(
        filterByAuthorizedAccounts: Boolean = true
    ): Pair<GoogleIdTokenCredential?, String?> {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(getWebClientId())
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            handleSignInResult(result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Error getting credential: ${e.message}", e)
            Pair(null, "Authentication failed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            Pair(null, "Unexpected error: ${e.message}")
        }
    }

    /**
     * Обрабатывает результат получения учетных данных.
     */
    private fun handleSignInResult(result: GetCredentialResponse): Pair<GoogleIdTokenCredential?, String?> {
        return when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                        // Сохраняем ID токен
                        encryptedPreferences.putGoogleIdToken(googleIdTokenCredential.idToken)

                        Log.d(TAG, "Successfully signed in with user: ${googleIdTokenCredential.id}")
                        Pair(googleIdTokenCredential, null)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Invalid Google ID token: ${e.message}", e)
                        Pair(null, "Invalid Google ID token")
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type: ${credential.type}")
                    Pair(null, "Unexpected credential type")
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential class: ${credential::class.java.name}")
                Pair(null, "Unexpected credential type")
            }
        }
    }

    /**
     * Получает access token для доступа к Google Calendar API.
     * Использует GoogleAuthUtil для получения токена с нужными scopes.
     *
     * @param email Email пользователя из GoogleIdTokenCredential
     * @return Pair of (accessToken?, errorMessage?)
     */
    suspend fun getAccessToken(email: String): Pair<String?, String?> {
        return try {
            val scopeString = "oauth2:${CalendarScopes.CALENDAR}"
            Log.d(TAG, "Requesting access token with scope: $scopeString")

            val token = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(
                    context,
                    email,
                    scopeString
                )
            }

            if (token.isNotBlank()) {
                encryptedPreferences.putAccessToken(token)
                Log.d(TAG, "Access token obtained successfully")
                Pair(token, null)
            } else {
                Log.e(TAG, "Token is blank")
                Pair(null, "Token is empty")
            }
        } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
            Log.e(TAG, "UserRecoverableAuthException: ${e.message}", e)
            Pair(null, "Permission required: ${e.message}")
        } catch (e: com.google.android.gms.auth.GoogleAuthException) {
            Log.e(TAG, "GoogleAuthException: ${e.message}", e)
            val errorMsg = when {
                e.message?.contains("API", ignoreCase = true) == true ->
                    "Google Calendar API is not enabled"
                e.message?.contains("OAuth", ignoreCase = true) == true ||
                e.message?.contains("client", ignoreCase = true) == true ->
                    "OAuth 2.0 Client ID is not configured"
                else ->
                    "Authentication error: ${e.message}"
            }
            Pair(null, errorMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token: ${e.message}", e)
            Pair(null, "Error: ${e.message}")
        }
    }

    /**
     * Обновляет access token.
     * Следует вызывать перед API вызовами, если токен может быть истекшим.
     */
    suspend fun refreshAccessToken(email: String): String? {
        val (token, error) = getAccessToken(email)
        if (error != null) {
            Log.e(TAG, "Failed to refresh token: $error")
        }
        return token
    }

    /**
     * Проверяет, есть ли сохраненный Google ID токен.
     */
    fun isSignedIn(): Boolean {
        return encryptedPreferences.getGoogleIdToken() != null
    }

    /**
     * Выполняет выход из аккаунта, очищая все сохраненные токены.
     */
    fun signOut() {
        encryptedPreferences.clear()
        Log.d(TAG, "User signed out")
    }

    /**
     * Получает Web Client ID из ресурсов или конфигурации.
     */
    private fun getWebClientId(): String {
        return try {
            // Попытка получить из strings.xml
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            Log.w(TAG, "Web Client ID not found in resources, using default")
            WEB_CLIENT_ID
        }
    }
}

