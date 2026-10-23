package com.coparently.app.data.remote.firebase

import com.coparently.app.data.remote.google.GoogleAccessException
import com.coparently.app.data.remote.google.GoogleAccessFailure
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Google returned for an authorization code.
 *
 * @property accessToken The short-lived token the Calendar API is called with.
 * @property refreshToken Empty when Google did not issue one — which it does not when the
 *   account has already granted consent. An empty value must never overwrite a stored token.
 * @property expiresInSeconds How long [accessToken] is good for.
 */
data class GoogleTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long
)

/**
 * The Google OAuth token exchange, performed by a Cloud Function (SEC-1 §2).
 *
 * **Why this exists.** Google's *web* OAuth client — the one the app uses, because that is the
 * client type the Calendar scope is granted through — requires a client secret to redeem an
 * authorization code and to refresh an access token. That secret used to be compiled into the
 * APK as `BuildConfig.GOOGLE_CLIENT_SECRET`. An APK is not a secret: anybody who installs the
 * app has it, and with it can mint tokens against the project's OAuth client. It now lives only
 * in the functions' environment and this class is how the app reaches it.
 *
 * **The tokens still live on the device**, in `EncryptedPreferences`, exactly as before. Moving
 * their storage server-side is a larger change and a separate decision; this one closes the hole
 * the secret was.
 *
 * **Failures say whether the connection survives.** Each comes back as a [GoogleAccessException]
 * carrying [GoogleAccessFailure.NEEDS_RECONNECT] or [GoogleAccessFailure.UNAVAILABLE] — the
 * caller's only two responses, "ask the parent to connect again" and "try again later". This
 * used to be decided by whether a token came back at all, which answered "try again" for a token
 * Google had revoked: the app kept refreshing it and never offered a reconnect. See [failureOf]
 * for the table, two reasons long.
 */
@Singleton
class GoogleOAuthFunctions @Inject constructor(
    private val functions: FirebaseFunctions
) {

    /**
     * Redeems an authorization code for tokens.
     *
     * @param authCode The code Google Sign-In returned.
     */
    suspend fun exchangeAuthCode(authCode: String): Result<GoogleTokens> =
        call("exchangeGoogleAuthCode", mapOf("authCode" to authCode)) { data ->
            GoogleTokens(
                accessToken = data["accessToken"] as? String
                    ?: error("exchangeGoogleAuthCode returned no accessToken"),
                refreshToken = data["refreshToken"] as? String ?: "",
                expiresInSeconds = (data["expiresInSeconds"] as? Number)?.toLong() ?: 0L
            )
        }

    /**
     * Trades a refresh token for a fresh access token.
     *
     * The callable refuses a refresh token it did not issue to this account, and passes on
     * Google's own refusal of a revoked or expired one; both fail with
     * [GoogleAccessFailure.NEEDS_RECONNECT]. That includes every account whose Calendar was
     * connected before SEC-1 §2 shipped: their token predates the fingerprints the function checks
     * against, and they consent once more.
     *
     * @param refreshToken The stored refresh token.
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<GoogleTokens> =
        call("refreshGoogleAccessToken", mapOf("refreshToken" to refreshToken)) { data ->
            GoogleTokens(
                accessToken = data["accessToken"] as? String
                    ?: error("refreshGoogleAccessToken returned no accessToken"),
                // The refresh grant never returns a new refresh token; the stored one stands.
                refreshToken = "",
                expiresInSeconds = (data["expiresInSeconds"] as? Number)?.toLong() ?: 0L
            )
        }

    private suspend fun <T> call(
        name: String,
        payload: Map<String, Any>,
        parse: (Map<*, *>) -> T
    ): Result<T> = try {
        val result = functions.getHttpsCallable(name).call(payload).await()
        Result.success(parse((result.getData() as? Map<*, *>) ?: emptyMap<String, Any>()))
    } catch (e: CancellationException) {
        // Cancellation must propagate rather than be reported as a sign-in failure: navigating
        // away mid-call would otherwise surface a spurious error.
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.failure(GoogleAccessException(failureOf(e), e))
    }

    companion object {
        /**
         * The `reason`s that mean a refresh token or code can never work again.
         *
         * `reconnect` is what the callables send for Google's `invalid_grant` (revoked, expired,
         * already redeemed — or seven days old while the consent screen is in Testing) and for a
         * token they hold no fingerprint for. `unknown-refresh-token` is what a backend deployed
         * before `reconnect` existed says for the latter.
         */
        private val RECONNECT_REASONS = setOf("reconnect", "unknown-refresh-token")

        /**
         * What a failed call means for the connection.
         *
         * Only a refusal the server names ends it. Everything else — `unavailable`, a deadline,
         * `internal` (Google refusing this deployment's own client among it), an unconfigured
         * backend, a malformed answer — keeps the token to be tried again, because dropping one that
         * still works costs the parent a fresh consent for nothing.
         */
        /**
         * A short description of a failed call, for the parent's screen and the log: the reason
         * the callable named (with Google's own error code when it passed one on), else the
         * Firebase status code, else the exception's class. Never the message, which for a
         * failure from Google's token endpoint quotes Google's answer.
         *
         * Unwraps the [GoogleAccessException] [call] hands a failure on in.
         */
        fun diagnosisOf(e: Throwable): String {
            val failure = (e as? GoogleAccessException)?.cause ?: e
            val functionsFailure = failure as? FirebaseFunctionsException
            val details = functionsFailure?.details as? Map<*, *>
            val reason = details?.get("reason") as? String
            val cause = details?.get("cause") as? String
            return when {
                reason != null && cause != null -> "$reason: $cause"
                reason != null -> reason
                functionsFailure != null -> functionsFailure.code.name
                else -> failure.javaClass.simpleName
            }
        }

        fun failureOf(e: Throwable): GoogleAccessFailure {
            val reason = ((e as? FirebaseFunctionsException)?.details as? Map<*, *>)?.get("reason") as? String
            return if (reason != null && reason in RECONNECT_REASONS) {
                GoogleAccessFailure.NEEDS_RECONNECT
            } else {
                GoogleAccessFailure.UNAVAILABLE
            }
        }
    }
}
