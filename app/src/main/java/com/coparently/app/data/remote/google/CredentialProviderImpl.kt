package com.coparently.app.data.remote.google

import android.util.Log
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpResponseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CredentialProvider] over the tokens [CredentialManagerService] keeps.
 *
 * Every credential comes through [CredentialManagerService.accessToken], which renews an expired
 * token. This used to check `isSignedIn()` first — "the access token is good for five more
 * minutes" — and return null when it was not, so the renewal it was written to perform never ran,
 * and an import failed about an hour after Google Calendar was connected.
 */
@Singleton
class CredentialProviderImpl @Inject constructor(
    private val credentialManagerService: CredentialManagerService,
    private val encryptedPreferences: EncryptedPreferences
) : CredentialProvider {

    override suspend fun getCredential(forceRefresh: Boolean): Credential =
        when (val access = credentialManagerService.accessToken(forceRefresh)) {
            is GoogleAccess.Granted -> credentialFor(access.accessToken)
            is GoogleAccess.Denied -> {
                Log.w(TAG, "No Google credential: ${access.failure}")
                throw GoogleAccessException(access.failure)
            }
        }

    override suspend fun <T> withCredential(call: suspend (Credential) -> T): T {
        val credential = getCredential()
        return try {
            call(credential)
        } catch (e: HttpResponseException) {
            when {
                e.statusCode == HTTP_UNAUTHORIZED -> callAgainRenewed(call)
                // A renewed token would carry the same permissions.
                lacksCalendarScope(e) -> endConnection()
                else -> throw e
            }
        }
    }

    /**
     * Runs [call] once more with a token renewed just now. Google refusing that one too — minutes
     * old, from Google's own token endpoint — is not something any further try can answer.
     */
    private suspend fun <T> callAgainRenewed(call: suspend (Credential) -> T): T {
        val renewed = getCredential(forceRefresh = true)
        return try {
            call(renewed)
        } catch (e: HttpResponseException) {
            if (e.statusCode == HTTP_UNAUTHORIZED || lacksCalendarScope(e)) endConnection() else throw e
        }
    }

    /** Ends a connection Google refused for good, and tells the caller to ask for a reconnect. */
    private fun endConnection(): Nothing {
        credentialManagerService.markNeedsReconnect()
        throw GoogleAccessException(GoogleAccessFailure.NEEDS_RECONNECT)
    }

    /** A credential that sends [accessToken] as a bearer token. */
    private fun credentialFor(accessToken: String): Credential =
        Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
            .setRequestInitializer(object : HttpRequestInitializer {
                override fun initialize(request: HttpRequest) {
                    // Always use the latest token from preferences
                    val currentToken = encryptedPreferences.getAccessToken()
                    if (currentToken != null) {
                        request.headers.setAuthorization("Bearer $currentToken")
                    }
                }
            })
            .build()
            .apply {
                setAccessToken(accessToken)
                // Don't set expiration to allow automatic refresh handling
            }

    companion object {
        private const val TAG = "CredentialProvider"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        /** The Calendar API's reason for a token that lacks the scope the call needs. */
        private const val INSUFFICIENT_PERMISSIONS = "insufficientPermissions"

        /** The same refusal in the `WWW-Authenticate` header (RFC 6750 §3.1). */
        private const val INSUFFICIENT_SCOPE = "insufficient_scope"

        /**
         * Whether Google refused [e] for want of the Calendar permission — an account that unticked
         * Calendar on Google's consent screen, or a grant made without the scope. A 403 for
         * anything else (a rate limit, an API not enabled for the project) is not about the
         * connection and is left to the caller.
         */
        internal fun lacksCalendarScope(e: HttpResponseException): Boolean {
            if (e.statusCode != HTTP_FORBIDDEN) return false
            val reasons = (e as? GoogleJsonResponseException)?.details?.errors.orEmpty().map { it.reason }
            return INSUFFICIENT_PERMISSIONS in reasons ||
                e.headers?.authenticate?.contains(INSUFFICIENT_SCOPE) == true
        }
    }
}
