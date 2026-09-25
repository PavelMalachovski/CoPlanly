package com.coparently.app.data.school.bakalari

import com.coparently.app.data.school.bakalari.BakalariJson.text
import com.coparently.app.di.SchoolHttpClient
import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

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
 * The phone's direct line to one school's Bakaláři server (MON-8, API v3).
 *
 * Every request goes from the phone to the school; nothing passes through CoPlanly's servers.
 * The password is sent once, to [signIn], and returned nowhere. `https://` only: a Bakaláři
 * server is reached with the parent's credentials, and an address that is not encrypted is
 * refused before anything is sent to it.
 *
 * Stateless. Refreshing, the rotation of the refresh token and writing it down before it is used
 * are [BakalariAuthorizer]'s job.
 */
@Singleton
class BakalariClient internal constructor(
    private val http: OkHttpClient,
    private val requireHttps: Boolean,
    private val io: CoroutineDispatcher,
    private val now: () -> Long
) {

    /** The production client: https only, on the IO dispatcher, on the wall clock. */
    @Inject
    constructor(@SchoolHttpClient http: OkHttpClient) : this(http, true, Dispatchers.IO, System::currentTimeMillis)

    /**
     * Whether [baseUrl] answers as a Bakaláři server: `GET {base}/api`, which needs no sign-in and
     * returns the API version.
     *
     * @throws BakalariException.NotBakalari when it answers with anything else.
     */
    suspend fun checkServer(baseUrl: String) {
        val (status, body) = execute(Request.Builder().url(urlOf(baseUrl, "api")).get().build())
        val versioned = status == HTTP_OK && runCatching {
            BakalariJson.arrayOf(body).any { element ->
                element.isJsonObject && !element.asJsonObject.text("ApiVersion").isNullOrBlank()
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
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "password")
                .add("username", username)
                .add("password", password)
                .build(),
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
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build(),
            previousRefreshToken = refreshToken
        )

    /** Who the login belongs to: `GET /api/3/user`. */
    suspend fun user(baseUrl: String, accessToken: String): BakalariAccount =
        BakalariAccount.parse(get(urlOf(baseUrl, "api/3/user"), accessToken))

    /** The actual timetable of the week holding [date]: `GET /api/3/timetable/actual?date=`. */
    suspend fun timetable(baseUrl: String, accessToken: String, date: LocalDate): SchoolWeek {
        val url = urlOf(baseUrl, "api/3/timetable/actual").newBuilder()
            .addQueryParameter("date", date.toString())
            .build()
        return BakalariTimetableParser.parse(get(url, accessToken), date)
    }

    /** Every event visible to the account from [from] on: `GET /api/3/events?from=`. */
    suspend fun events(baseUrl: String, accessToken: String, from: LocalDate): List<SchoolEvent> {
        val url = urlOf(baseUrl, "api/3/events").newBuilder()
            .addQueryParameter("from", from.toString())
            .build()
        return BakalariEventsParser.parse(get(url, accessToken))
    }

    private suspend fun token(baseUrl: String, form: FormBody, previousRefreshToken: String?): BakalariTokens {
        val request = Request.Builder().url(urlOf(baseUrl, "api/login")).post(form).build()
        val (status, body) = execute(request)
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

    private suspend fun get(url: HttpUrl, accessToken: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()
        val (status, body) = execute(request)
        if (status == HTTP_OK) return body
        throw when (status) {
            HTTP_UNAUTHORIZED -> BakalariException.Unauthorized()
            HTTP_FORBIDDEN -> BakalariException.Forbidden()
            else -> BakalariException.Http(status)
        }
    }

    private suspend fun execute(request: Request): Pair<Int, String> = withContext(io) {
        try {
            http.newCall(request).execute().use { response -> response.code to response.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw BakalariException.Network(e)
        }
    }

    private fun urlOf(baseUrl: String, path: String): HttpUrl {
        val base = baseUrl.toHttpUrlOrNull() ?: throw BakalariException.NotBakalari("Unreadable address")
        if (requireHttps && !base.isHttps) throw BakalariException.InsecureUrl()
        return base.newBuilder().addPathSegments(path).build()
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
    }
}
