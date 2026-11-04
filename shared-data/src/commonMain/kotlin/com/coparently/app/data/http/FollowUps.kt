package com.coparently.app.data.http

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException

/**
 * One request of the app's two outside clients (MON-24).
 *
 * @property method GET, or POST for the Bakaláři sign-in.
 * @property url The address, exactly as it is sent.
 * @property headers The headers the client sets itself.
 * @property form An `application/x-www-form-urlencoded` body (already encoded), or null for none.
 */
internal class Outgoing(
    val method: HttpMethod,
    val url: String,
    val headers: List<Pair<String, String>> = emptyList(),
    val form: String? = null
)

/**
 * The last answer a request got, after the redirects [Redirects] says to follow.
 *
 * @property status The HTTP status.
 * @property body The body as text.
 * @property headers The response headers.
 */
internal class Answer(val status: Int, val body: String, val headers: Headers)

/**
 * Whose redirect rules a client follows: the library it replaced (MON-24). Ktor's own redirect
 * plugin is off in every client (`httpClient`), because it follows GET and HEAD only and drops the
 * bearer token across hosts, and neither old library did that.
 */
internal enum class Redirects {
    /**
     * OkHttp 4.12's `RetryAndFollowUpInterceptor`, which the school import used: 300, 301, 302, 303,
     * 307 and 308 with a `Location` are followed; a POST becomes a GET without its body except on
     * 307 and 308, which re-send the method and the body; `Authorization` is dropped when the
     * scheme, host or port changes; past 20 follow-ups the call fails as an `IOException`.
     */
    OKHTTP_4,

    /**
     * google-http-client 1.42.3's `HttpRequest.handleRedirect`, which the Calendar import used: 301,
     * 302, 303, 307 and 308 with a `Location` are followed, a 303 as a GET; the bearer token is sent
     * again wherever it leads (the credential re-applied it on every attempt); after 10 redirects the
     * last redirect is the answer.
     */
    GOOGLE_HTTP_CLIENT;

    /** Whether this library followed [status]. */
    fun follows(status: Int): Boolean = when (this) {
        OKHTTP_4 -> status in MULTIPLE_CHOICES..SEE_OTHER || status.keepsMethod()
        GOOGLE_HTTP_CLIENT -> status in MOVED_PERMANENTLY..SEE_OTHER || status.keepsMethod()
    }

    /** [current] sent again to [target] after a [status] redirect, as this library sent it. */
    fun next(current: Outgoing, target: WebAddress, status: Int): Outgoing {
        val keepsMethod = when (this) {
            OKHTTP_4 -> current.method != HttpMethod.Post || status.keepsMethod()
            GOOGLE_HTTP_CLIENT -> status != SEE_OTHER
        }
        val sameOrigin = WebAddress.parse(current.url)?.sameOrigin(target) == true
        val headers = if (this == OKHTTP_4 && !sameOrigin) {
            current.headers.filterNot { (name, _) -> name.equals(HttpHeaders.Authorization, true) }
        } else {
            current.headers
        }
        return Outgoing(
            method = if (keepsMethod) current.method else HttpMethod.Get,
            url = target.toString(),
            headers = headers,
            form = current.form.takeIf { keepsMethod }
        )
    }

    /**
     * Whether a redirect that comes after [followed] followed ones is followed too. OkHttp failed the
     * call on its 21st (`ProtocolException`, an `IOException`); google-http-client stopped after 10
     * and answered with the last redirect.
     */
    fun allows(followed: Int): Boolean = when (this) {
        OKHTTP_4 -> {
            if (followed >= MAX_OKHTTP_FOLLOW_UPS) throw IOException("Too many follow-up requests: ${followed + 1}")
            true
        }
        GOOGLE_HTTP_CLIENT -> followed < MAX_GOOGLE_REDIRECTS
    }

    private companion object {
        /** 307 and 308, the two redirects that keep the method and the body. */
        fun Int.keepsMethod(): Boolean = this == TEMPORARY_REDIRECT || this == PERMANENT_REDIRECT

        const val MULTIPLE_CHOICES = 300
        const val MOVED_PERMANENTLY = 301
        const val SEE_OTHER = 303
        const val TEMPORARY_REDIRECT = 307
        const val PERMANENT_REDIRECT = 308
        const val MAX_OKHTTP_FOLLOW_UPS = 20
        const val MAX_GOOGLE_REDIRECTS = 10
    }
}

/**
 * Sends [outgoing], following its redirects by [redirects]' rules, and returns the last answer.
 * A redirect whose `Location` is missing or not an http(s) address is the answer itself, as it was.
 *
 * The client's whole-call limit (`HttpTimeouts.callMillis`) covers the whole chain, every redirect
 * and body included, as OkHttp's `callTimeout` did. It is measured on the engine's dispatcher, as
 * OkHttp measured it on its own watchdog thread rather than the caller's: on a phone that is a real
 * clock either way, and a test that puts the engine on virtual time puts the limit there too, where a
 * caller's virtual clock alone would end a call still being answered on a real one.
 *
 * @throws IOException when the server cannot be reached, the whole-call limit passes ("timeout",
 *   as OkHttp's `InterruptedIOException` said), or OkHttp's follow-up limit is passed.
 */
internal suspend fun HttpClient.send(outgoing: Outgoing, redirects: Redirects): Answer {
    val callMillis = attributes.getOrNull(CALL_TIMEOUT_MILLIS) ?: return followingRedirects(outgoing, redirects)
    return withContext(engine.dispatcher) {
        withTimeoutOrNull(callMillis) { followingRedirects(outgoing, redirects) }
    } ?: throw IOException("timeout")
}

private suspend fun HttpClient.followingRedirects(outgoing: Outgoing, redirects: Redirects): Answer {
    var current = outgoing
    var followUps = 0
    while (true) {
        val response = request {
            method = current.method
            url(current.url)
            current.headers.forEach { (name, value) -> header(name, value) }
            current.form?.let { setBody(TextContent(it, ContentType.Application.FormUrlEncoded)) }
        }
        val status = response.status.value
        val target = response.headers[HttpHeaders.Location]
            ?.takeIf { redirects.follows(status) }
            ?.let { WebAddress.parse(current.url)?.resolve(it) }
        if (target == null || !redirects.allows(followUps)) {
            return Answer(status, response.bodyAsText(), response.headers)
        }
        followUps++
        current = redirects.next(current, target, status)
    }
}
