package com.coparently.app.data.remote.google

import com.coparently.app.data.http.Outgoing
import com.coparently.app.data.http.PercentEncoding
import com.coparently.app.data.http.sendOnce
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import kotlinx.io.IOException

/**
 * Revokes the Google grant a token belongs to (MON-24 phase 4, part A): what Android's
 * `GoogleSignInClient.revokeAccess()` does when Google Calendar is disconnected or the app signs out.
 * Revoking a refresh token ends the whole grant, every access token of it included, so the next
 * consent issues a new refresh token.
 */
fun interface GoogleGrantRevocation {

    /**
     * Revokes [token]'s grant.
     *
     * @throws IOException when Google cannot be reached or refuses for any reason but a token it no
     *   longer knows (already revoked or expired), which is the outcome asked for.
     */
    suspend fun revoke(token: String)
}

/**
 * [GoogleGrantRevocation] through Google's public revoke endpoint (RFC 7009,
 * `POST https://oauth2.googleapis.com/revoke`, the token as a form field): no client secret, and
 * nothing but the token is sent.
 */
class GoogleRevokeEndpoint(private val http: HttpClient) : GoogleGrantRevocation {

    override suspend fun revoke(token: String) {
        val answer = http.sendOnce(
            Outgoing(HttpMethod.Post, REVOKE_URL, form = "token=" + PercentEncoding.formField(token))
        )
        // 400 is `invalid_token`: Google no longer knows the token, so there is no grant to end.
        if (answer.status !in HTTP_SUCCESS && answer.status != HTTP_BAD_REQUEST) {
            throw IOException("Google's revoke endpoint answered HTTP ${answer.status}")
        }
    }

    private companion object {
        const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
        val HTTP_SUCCESS = 200..299
        const val HTTP_BAD_REQUEST = 400
    }
}
