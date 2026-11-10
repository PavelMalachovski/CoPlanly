@file:OptIn(InternalSharedApi::class)

package com.coparently.app.data.http

import com.coparently.app.domain.InternalSharedApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A server for the HTTP clients' tests, in memory (MON-24): it answers each request with what
 * [respond] returns and keeps every request it saw, for the JVM and the iOS simulator alike. It
 * replaced OkHttp's MockWebServer, which runs on the JVM only.
 *
 * The client it hands out is configured as production's (`httpClient(engine, timeouts)`), with
 * Ktor's mock engine in place of OkHttp or NSURLSession. A [dispatcher] (a test dispatcher) puts the
 * engine on virtual time, for the timeout tests.
 */
internal class RecordingServer(
    timeouts: HttpTimeouts = HttpTimeouts.SCHOOL,
    dispatcher: CoroutineDispatcher? = null,
    private val respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { answer("") }
) {
    /** Every request the client made, in order. */
    val requests = mutableListOf<HttpRequestData>()

    /** A client whose requests reach this server. */
    val client: HttpClient = httpClient(
        MockEngine(
            MockEngineConfig().apply {
                dispatcher?.let { this.dispatcher = it }
                addHandler { request ->
                    requests += request
                    respond(request)
                }
            }
        ),
        timeouts
    )

    /** The [index]th request's body as text. */
    suspend fun bodyOf(index: Int): String = requests[index].body.toByteArray().decodeToString()
}

/** An answer with [body], [status] and [headers]. */
internal fun MockRequestHandleScope.answer(
    body: String,
    status: Int = 200,
    headers: Headers = headersOf()
): HttpResponseData = respond(body, HttpStatusCode.fromValue(status), headers)
