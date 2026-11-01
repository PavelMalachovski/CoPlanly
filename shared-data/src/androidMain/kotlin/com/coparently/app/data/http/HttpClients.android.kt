package com.coparently.app.data.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

/** OkHttp, as the school import used before MON-24; see the common declaration. */
actual fun httpClient(timeouts: HttpTimeouts): HttpClient = HttpClient(OkHttp) {
    sharedConfiguration(timeouts)
}.withCallTimeout(timeouts)
