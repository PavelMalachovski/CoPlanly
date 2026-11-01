package com.coparently.app.data.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout

/**
 * A new HTTP client for one of the app's two outside services (MON-24): a school's Bakaláři
 * server and Bakaláři's school directory, or Google Calendar. OkHttp on Android, NSURLSession on
 * iOS.
 *
 * Each caller owns the client it is handed, so the school's requests, which carry a child's school
 * credentials, never share a client with anything else. It logs nothing — no `Logging` plugin is
 * installed — because a request body carries the school password and the headers carry the OAuth
 * and Bakaláři tokens. It does not throw on an HTTP error status: each client reads the status
 * itself, as it did on OkHttp.
 */
expect fun httpClient(): HttpClient

/**
 * What every platform's client is configured with: the timeouts the school import's OkHttp client
 * had (20 seconds to connect and between two reads or writes, 40 for the whole call), which are
 * also google-http-client's 20-second connect and read defaults the Calendar import ran with.
 */
internal fun HttpClientConfig<*>.sharedConfiguration() {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = TIMEOUT_MILLIS
        socketTimeoutMillis = TIMEOUT_MILLIS
        requestTimeoutMillis = TIMEOUT_MILLIS * 2
    }
}

/** A server that has not answered in this long is treated as unreachable. */
private const val TIMEOUT_MILLIS = 20_000L
