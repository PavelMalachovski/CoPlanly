@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.school.bakalari

import com.coparently.app.data.http.Outgoing
import com.coparently.app.data.http.PercentEncoding
import com.coparently.app.data.http.Redirects
import com.coparently.app.data.http.WebAddress
import com.coparently.app.data.http.send
import com.coparently.app.data.school.bakalari.BakalariJson.text
import com.coparently.app.domain.InternalSharedApi
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/**
 * A signed-in session's two tokens (MON-8).
 *
 * @property accessToken Sent as `Authorization: Bearer` on every resource call.
 * @property refreshToken Redeemed for a new pair. **It rotates**: every refresh returns a new one,
 *   and only the latest pair may be kept.
 * @property expiresAtMillis When [accessToken] stops working, from the server's `expires_in` —
 *   3599 s on newer servers, 599 s on older ones, so never assumed.
 */
data class BakalariTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)

/**
 * The phone's direct line to one school's Bakaláři server (MON-8, API v3), on Ktor since MON-24.
 *
 * Every request goes from the phone to the school; nothing passes through CoPlanly's servers.
 * The password is sent once, to [signIn], and returned nowhere. `https://` only: a Bakaláři
 * server is reached with the parent's credentials, and an address that is not encrypted is
 * refused before anything is sent to it.
 *
 * Stateless, and it only fetches: refreshing, the rotation of the refresh token and writing it down
 * before it is used are `BakalariAuthorizer`'s job, and reading the timetable, the events and the
 * account out of what the school answers is the app's school import's (its parsers build the
 * `java.time` school domain, which has not moved yet). Each request is the one the OkHttp client
 * made before MON-24, method, address, headers and form body alike; `BakalariRequestShapeTest`
 * holds them. Redirects are followed as OkHttp 4.12 followed them ([Redirects.OKHTTP_4]): a school
 * whose server answers the sign-in with a 307 or 308 gets the form again, as it did.
 */
class BakalariClient internal constructor(
    private val http: HttpClient,
    private val requireHttps: Boolean,
    private val now: () -> Long
) {

    /** The production client: https only, on the wall clock. */
    constructor(http: HttpClient) : this(http, true, { Clock.System.now().toEpochMilliseconds() })

    /**
     * Whether [baseUrl] answers as a Bakaláři server: `GET {base}/api`, which needs no sign-in and
     * returns the API version.
     *
     * @throws BakalariException.NotBakalari when it answers with anything else.
     */
    suspend fun checkServer(baseUrl: String) {
        val (status, body) = execute(Outgoing(HttpMethod.Get, urlOf(baseUrl, "api")))
        val versioned = status == HTTP_OK && runCatching {
            BakalariJson.arrayOf(body).any { element ->
                element is JsonObject && !element.text("ApiVersion").isNullOrBlank()
            }
        }.getOrDefault(false)
        if (!versioned) throw BakalariException.NotBakalari()
    }

    /**
     * Signs in with a password. The only call that ever carries one.
     *
     * @throws BakalariException.InvalidGrant on a wrong username or password.
     */
    suspend fun signIn(baseUrl: String, username: String, password: String): BakalariTokens =
        token(
            baseUrl,
            listOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "password",
                "username" to username,
                "password" to password
            ),
            previousRefreshToken = null
        )

    /**
     * Redeems [refreshToken] for a new pair. The old refresh token must not be used again.
     *
     * @throws BakalariException.InvalidGrant when the server will not honour it any more.
     */
    suspend fun refresh(baseUrl: String, refreshToken: String): BakalariTokens =
        token(
            baseUrl,
            listOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken
            ),
            previousRefreshToken = refreshToken
        )

    /** Who the login belongs to, as the server writes it: `GET /api/3/user`. */
    suspend fun userJson(baseUrl: String, accessToken: String): String =
        get(urlOf(baseUrl, "api/3/user"), accessToken)

    /**
     * The actual timetable of the week holding [date], as the server writes it:
     * `GET /api/3/timetable/actual?date=`.
     */
    suspend fun timetableJson(baseUrl: String, accessToken: String, date: LocalDate): String =
        get(urlOf(baseUrl, "api/3/timetable/actual") + "?date=$date", accessToken)

    /** Every event visible to the account from [from] on, as the server writes it: `GET /api/3/events?from=`. */
    suspend fun eventsJson(baseUrl: String, accessToken: String, from: LocalDate): String =
        get(urlOf(baseUrl, "api/3/events") + "?from=$from", accessToken)

    private suspend fun token(
        baseUrl: String,
        fields: List<Pair<String, String>>,
        previousRefreshToken: String?
    ): BakalariTokens {
        // Encoded as OkHttp's `FormBody` encoded it, and sent as its type with no charset, as it was.
        val form = fields.joinToString("&") { (name, value) ->
            PercentEncoding.formField(name) + "=" + PercentEncoding.formField(value)
        }
        val (status, body) = execute(Outgoing(HttpMethod.Post, urlOf(baseUrl, "api/login"), form = form))
        if (status == HTTP_BAD_REQUEST || status == HTTP_UNAUTHORIZED) {
            // Matched on `error`, never on `error_description`, which is Czech free text.
            val error = runCatching { BakalariJson.objectOf(body).text("error") }.getOrNull()
            if (error == "invalid_grant") throw BakalariException.InvalidGrant()
        }
        if (status != HTTP_OK) throw BakalariException.Http(status)
        return tokensOf(body, previousRefreshToken)
    }

    private fun tokensOf(body: String, previousRefreshToken: String?): BakalariTokens {
        val root = BakalariJson.objectOf(body)
        val access = root.text("access_token")?.takeIf { it.isNotBlank() }
            ?: throw BakalariException.Malformed("No access_token")
        // An older server may leave the refresh token out of a refresh; the one just redeemed
        // is then the one to keep.
        val refresh = root.text("refresh_token")?.takeIf { it.isNotBlank() } ?: previousRefreshToken
            ?: throw BakalariException.Malformed("No refresh_token")
        val lifetimeSeconds = root.text("expires_in")?.toLongOrNull() ?: DEFAULT_LIFETIME_SECONDS
        return BakalariTokens(access, refresh, now() + lifetimeSeconds * MILLIS_PER_SECOND)
    }

    private suspend fun get(url: String, accessToken: String): String {
        val headers = listOf(
            HttpHeaders.Authorization to "Bearer $accessToken",
            HttpHeaders.Accept to "application/json"
        )
        val (status, body) = execute(Outgoing(HttpMethod.Get, url, headers))
        if (status == HTTP_OK) return body
        throw when (status) {
            HTTP_UNAUTHORIZED -> BakalariException.Unauthorized()
            HTTP_FORBIDDEN -> BakalariException.Forbidden()
            else -> BakalariException.Http(status)
        }
    }

    private suspend fun execute(outgoing: Outgoing): Pair<Int, String> = try {
        val answer = http.send(outgoing, Redirects.OKHTTP_4)
        // OkHttp's `string()` dropped a UTF-8 byte order mark, which an ASP.NET server may write.
        answer.status to answer.body.removePrefix(BYTE_ORDER_MARK)
    } catch (e: IOException) {
        throw BakalariException.Network(e)
    }

    /** `{base}/{path}`, written as OkHttp's `HttpUrl.Builder.addPathSegments` wrote it. */
    private fun urlOf(baseUrl: String, path: String): String {
        val base = WebAddress.parse(baseUrl) ?: throw BakalariException.NotBakalari("Unreadable address")
        if (requireHttps && !base.isHttps) throw BakalariException.InsecureUrl()
        return base.withPathSegments(path)
    }

    private companion object {
        /** Bakaláři's own Android client id; every known third-party client sends it. */
        const val CLIENT_ID = "ANDR"
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val DEFAULT_LIFETIME_SECONDS = 599L
        const val MILLIS_PER_SECOND = 1000L
        const val BYTE_ORDER_MARK = "\uFEFF"
    }
}
