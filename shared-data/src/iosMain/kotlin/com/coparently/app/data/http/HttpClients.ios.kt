package com.coparently.app.data.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** NSURLSession; see the common declaration. */
actual fun httpClient(timeouts: HttpTimeouts): HttpClient =
    httpClient(Darwin, timeouts)
