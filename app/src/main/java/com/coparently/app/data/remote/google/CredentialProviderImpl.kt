package com.coparently.app.data.remote.google

import com.coparently.app.data.platform.logger

/**
 * [CredentialProvider] over the tokens [CredentialManagerService] keeps.
 *
 * Every credential comes through [CredentialManagerService.accessToken], which renews an expired
 * token. This used to check `isSignedIn()` first — "the access token is good for five more
 * minutes" — and return null when it was not, so the renewal it was written to perform never ran,
 * and an import failed about an hour after Google Calendar was connected.
 *
 * A credential is the access token alone, sent as `Authorization: Bearer` (MON-24). The
 * google-api-client `Credential` it replaced sent exactly that: its request initializer, which
 * read the stored token again, ran only for refreshes against a token server, and it held no
 * refresh token, so it never refreshed or retried by itself.
 */
class CredentialProviderImpl(
    private val credentialManagerService: CredentialManagerService
) : CredentialProvider {

    override suspend fun getCredential(forceRefresh: Boolean): GoogleCredential =
        when (val access = credentialManagerService.accessToken(forceRefresh)) {
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

    /**
     * Runs [call] once more with a token renewed just now. Google refusing that one too — minutes
     * old, from Google's own token endpoint — is not something any further try can answer.
     */
    private suspend fun <T> callAgainRenewed(call: suspend (GoogleCredential) -> T): T {
        val renewed = getCredential(forceRefresh = true)
        return try {
            call(renewed)
        } catch (e: GoogleCalendarHttpException) {
            if (e.statusCode == HTTP_UNAUTHORIZED || lacksCalendarScope(e)) endConnection() else throw e
        }
    }

    /** Ends a connection Google refused for good, and tells the caller to ask for a reconnect. */
    private fun endConnection(): Nothing {
        credentialManagerService.markNeedsReconnect()
        throw GoogleAccessException(GoogleAccessFailure.NEEDS_RECONNECT)
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
        internal fun lacksCalendarScope(e: GoogleCalendarHttpException): Boolean {
            if (e.statusCode != HTTP_FORBIDDEN) return false
            return INSUFFICIENT_PERMISSIONS in e.reasons || e.authenticate?.contains(INSUFFICIENT_SCOPE) == true
        }
    }
}
