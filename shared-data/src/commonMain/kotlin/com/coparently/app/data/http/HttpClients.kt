package com.coparently.app.data.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.util.AttributeKey

/**
 * A new HTTP client for one of the app's two outside services (MON-24): a school's Bakaláři
 * server and Bakaláři's school directory, or Google Calendar. OkHttp on Android, NSURLSession on
 * iOS.
 *
 * Each caller owns the client it is handed, so the school's requests, which carry a child's school
 * credentials, never share a client with anything else. It logs nothing — no `Logging` plugin is
 * installed — because a request body carries the school password and the headers carry the OAuth
 * and Bakaláři tokens. It does not throw on an HTTP error status, and it does not follow a
 * redirect: each client reads the status itself and follows redirects as the library it replaced
 * did ([send], [Redirects]).
 *
 * @param timeouts The timeouts of the library the client replaced.
 */
expect fun httpClient(timeouts: HttpTimeouts): HttpClient

/**
 * A client over [engine] configured exactly as [httpClient] configures OkHttp and NSURLSession:
 * for the R8 runtime probe and the tests, which answer from memory but must run the production
 * configuration (the timeouts, the redirect switch).
 */
fun httpClient(engine: HttpClientEngine, timeouts: HttpTimeouts): HttpClient =
    HttpClient(engine) { sharedConfiguration(timeouts) }.withCallTimeout(timeouts)

/**
 * How long a client waits, as the library it replaced waited (MON-24).
 *
 * @property connectMillis To open a connection.
 * @property socketMillis Between two reads or two writes.
 * @property callMillis For the whole call, every redirect and every body included, or null for no
 *   limit. Enforced around the redirect loop ([send]), as OkHttp's `callTimeout` covered the whole
 *   chain of follow-ups; past it the call fails as an `IOException`, as OkHttp's did.
 *   Ktor's own request timeout would restart on each redirect, so it is not used.
 */
class HttpTimeouts private constructor(
    val connectMillis: Long,
    val socketMillis: Long,
    val callMillis: Long?
) {
    companion object {
        private const val TWENTY_SECONDS = 20_000L
        private const val FORTY_SECONDS = 40_000L

        /**
         * The school import's OkHttp client before MON-24 (`SchoolModule`): 20 seconds to connect,
         * to read and to write, and 40 for the whole call (`callTimeout`).
         */
        val SCHOOL = HttpTimeouts(TWENTY_SECONDS, TWENTY_SECONDS, FORTY_SECONDS)

        /**
         * google-http-client 1.42.3's `HttpRequest` defaults, which the Calendar import never
         * changed: 20 seconds to connect and to read, no limit on writing (the GET writes no body)
         * and none on the whole call.
         */
        val GOOGLE_CALENDAR = HttpTimeouts(TWENTY_SECONDS, TWENTY_SECONDS, null)
    }
}

/** What every platform's client is configured with, for [timeouts]; see [withCallTimeout] too. */
internal fun HttpClientConfig<*>.sharedConfiguration(timeouts: HttpTimeouts) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = timeouts.connectMillis
        socketTimeoutMillis = timeouts.socketMillis
    }
}

/** Where a client keeps its whole-call limit, which [send] applies around the redirect loop. */
internal val CALL_TIMEOUT_MILLIS = AttributeKey<Long>("CoPlanlyCallTimeoutMillis")

/** This client, with [timeouts]' whole-call limit recorded for [send]. */
internal fun HttpClient.withCallTimeout(timeouts: HttpTimeouts): HttpClient = apply {
    timeouts.callMillis?.let { attributes.put(CALL_TIMEOUT_MILLIS, it) }
}
