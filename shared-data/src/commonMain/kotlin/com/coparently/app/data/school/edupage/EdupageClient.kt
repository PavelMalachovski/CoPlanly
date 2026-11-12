package com.coparently.app.data.school.edupage

import com.coparently.app.data.http.Answer
import com.coparently.app.data.http.Outgoing
import com.coparently.app.data.http.PercentEncoding
import com.coparently.app.data.http.WebAddress
import com.coparently.app.data.http.sendOnce
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The phone's direct line to a school's EduPage (`*.edupage.org`), on Ktor.
 *
 * Every request goes from the phone to EduPage; nothing passes through CoPlanly's servers. The
 * password is sent in one place only, the body of the `/login/edubarLogin.php` form, and returned
 * nowhere. `https://` only.
 *
 * EduPage keeps a signed-in account in a session cookie and moves a sign-in from
 * `login1.edupage.org` to the school's own host, so the client follows redirects itself, one
 * request at a time ([sendOnce]), carrying every cookie an answer sets to the next host. It follows
 * at most [MAX_REDIRECTS] redirects, only to `https` hosts of the `edupage.org` family (or the host
 * it started on): 301, 302 and 303 as a GET without a body, 307 and 308 with the method and body.
 *
 * Stateless, and it only fetches: storing the session and reading the timetable and the timeline
 * out of what EduPage answers are the import's job. The requests follow the open-source
 * `edupage-api` 0.13.3's description of the protocol; they have not been recorded live yet.
 */
class EdupageClient(private val http: HttpClient) {

    /**
     * Signs in with a password, through EduPage's sign-in form: the sign-in page's CSRF token, then
     * the form, then its redirects to the school's host.
     *
     * @param host Where to sign in; `login1.edupage.org` when the school is not known.
     * @throws EdupageException.BadCredentials on a wrong username or password.
     * @throws EdupageException.Captcha when EduPage asks for a CAPTCHA.
     * @throws EdupageException.SecondFactor when the account asks for a second factor.
     * @throws EdupageException.NotEdupage when [host] does not answer as EduPage.
     */
    suspend fun signIn(host: String, username: String, password: String): EdupageSession {
        val origin = originOf(host)
        val jar = EdupageCookieJar(emptyMap())
        val loginPage = exchange(HttpMethod.Get, "$origin/login/?cmd=MainLogin", jar)
        val csrf = csrfOf(loginPage)
        val form = formOf("csrfauth" to csrf, "username" to username, "password" to password)
        val signedIn = exchange(HttpMethod.Post, "$origin/login/edubarLogin.php", jar, form)
        refusalOf(signedIn, jar)?.let { throw it }
        return sessionOf(signedIn, jar) ?: throw EdupageException.BadCredentials()
    }

    /**
     * The session [cookies] keep on [host], read again from `GET /user`.
     *
     * @throws EdupageException.SessionExpired when EduPage answers with its sign-in page.
     */
    suspend fun resume(host: String, cookies: Map<String, String>): EdupageSession {
        val jar = EdupageCookieJar(cookies)
        val page = exchange(HttpMethod.Get, "${originOf(host)}/user", jar)
        return sessionOf(page, jar) ?: throw EdupageException.SessionExpired()
    }

    /**
     * Makes [studentId] the child a parent's session shows: `GET /login/switchchild`, which answers
     * `OK`.
     *
     * @return The session with the cookies the switch set.
     * @throws EdupageException.Server when EduPage answers anything but `OK`.
     */
    suspend fun switchChild(session: EdupageSession, studentId: String): EdupageSession {
        val jar = EdupageCookieJar(session.cookies)
        val url = "${originOf(session.host)}/login/switchchild?studentid=${PercentEncoding.formField(studentId)}"
        val answer = exchange(HttpMethod.Get, url, jar)
        if (okBody(answer).trim() != SWITCHED) throw EdupageException.Server(answer.answer.status)
        return session.copy(cookies = jar.snapshot())
    }

    /**
     * The timetable of [studentId] from [from] to [to], as EduPage writes it:
     * `POST /timetable/server/currenttt.js?__func=curentttGetData` with the school year of the
     * session (`dp.year`) and its `gsechash`.
     *
     * @throws EdupageException.NotEdupage when the session's user data names no school year.
     */
    suspend fun timetableJson(session: EdupageSession, studentId: String, from: LocalDate, to: LocalDate): String {
        val year = (session.userhome["dp"] as? JsonObject)?.get("year") as? JsonPrimitive
            ?: throw EdupageException.NotEdupage("No school year")
        val body = buildJsonObject {
            putJsonArray("__args") {
                add(JsonNull)
                add(
                    buildJsonObject {
                        put("year", year)
                        put("datefrom", from.toString())
                        put("dateto", to.toString())
                        put("table", "students")
                        put("id", studentId)
                        put("showColors", true)
                        put("showIgroupsInClasses", true)
                        put("showOrig", true)
                        put("log_module", "CurrentTTView")
                    }
                )
            }
            put("__gsh", session.gsecHash)
        }
        val url = "${originOf(session.host)}/timetable/server/currenttt.js?__func=curentttGetData"
        val answer = exchange(HttpMethod.Post, url, EdupageCookieJar(session.cookies), body.toString(), JSON)
        return okBody(answer)
    }

    /**
     * The timeline (school events, homework, tests and messages) from [from] on, as EduPage writes
     * it: `POST /timeline/` with the form `datefrom`.
     */
    suspend fun timelineJson(session: EdupageSession, from: LocalDate): String {
        val url = "${originOf(session.host)}/timeline/?module=todo&filterTab=&akcia=getData&filterTab=messages"
        val answer = exchange(HttpMethod.Post, url, EdupageCookieJar(session.cookies), formOf("datefrom" to "$from"))
        return okBody(answer)
    }

    /** Where an exchange ended: the last answer, the address that gave it, and whether it moved. */
    private class Reached(val answer: Answer, val address: WebAddress, val redirected: Boolean)

    /**
     * Sends a request with [jar]'s cookies and follows its redirects, taking in every cookie each
     * answer sets. A redirect it may not follow is the answer itself.
     */
    private suspend fun exchange(
        method: HttpMethod,
        url: String,
        jar: EdupageCookieJar,
        body: String? = null,
        bodyType: ContentType = ContentType.Application.FormUrlEncoded
    ): Reached {
        val start = WebAddress.parse(url) ?: throw EdupageException.NotEdupage("Unreadable address")
        var address = start
        var current = Outgoing(method, url, form = body, formType = bodyType)
        var followed = 0
        while (true) {
            val headers = listOfNotNull(jar.header()?.let { HttpHeaders.Cookie to it })
            val answer = try {
                http.sendOnce(Outgoing(current.method, current.url, headers, current.form, current.formType))
            } catch (e: IOException) {
                throw EdupageException.Network(e)
            }
            jar.absorb(answer.headers.getAll(HttpHeaders.SetCookie).orEmpty())
            val target = redirectTarget(answer, address, start)
            if (target == null || followed >= MAX_REDIRECTS) return Reached(answer, address, followed > 0)
            followed++
            address = target
            val keepsMethod = answer.status == TEMPORARY_REDIRECT || answer.status == PERMANENT_REDIRECT
            current = if (keepsMethod) {
                Outgoing(current.method, target.toString(), form = current.form, formType = current.formType)
            } else {
                Outgoing(HttpMethod.Get, target.toString())
            }
        }
    }

    /** Where [answer] redirects to, when it is a redirect the client follows; otherwise null. */
    private fun redirectTarget(answer: Answer, from: WebAddress, start: WebAddress): WebAddress? {
        if (answer.status !in FOLLOWED) return null
        val target = answer.headers[HttpHeaders.Location]?.let(from::resolve) ?: return null
        val inFamily = target.host == start.host || target.host.endsWith(EDUPAGE_FAMILY)
        return target.takeIf { it.isHttps && inFamily }
    }

    /**
     * The body of a successful answer.
     *
     * @throws EdupageException.SessionExpired when EduPage sent the request to its sign-in page.
     * @throws EdupageException.Server on any other answer that is not a success (a redirect the
     *   client did not follow included).
     */
    private fun okBody(reached: Reached): String {
        if (reached.redirected && reached.address.encodedPathSegments.firstOrNull() == LOGIN_SEGMENT) {
            throw EdupageException.SessionExpired()
        }
        if (reached.answer.status !in SUCCESS) throw EdupageException.Server(reached.answer.status)
        return reached.answer.body
    }

    /**
     * The CSRF token of EduPage's sign-in page.
     *
     * @throws EdupageException.Server when the page is not a success.
     * @throws EdupageException.NotEdupage when the page has no token.
     */
    private fun csrfOf(loginPage: Reached): String {
        if (loginPage.answer.status !in SUCCESS) throw EdupageException.Server(loginPage.answer.status)
        return EdupagePages.csrfToken(loginPage.answer.body) ?: throw EdupageException.NotEdupage("No sign-in token")
    }

    /** Why EduPage refused a sign-in, read from the address the form ended on, or null for none. */
    private fun refusalOf(reached: Reached, jar: EdupageCookieJar): EdupageException? {
        val url = reached.address.toString()
        return when {
            CAPTCHA_MARKS.any { it in url } -> EdupageException.Captcha()
            BAD_CREDENTIALS_MARK in url -> EdupageException.BadCredentials()
            SECOND_FACTOR_MARK in url -> EdupageException.SecondFactor(jar.snapshot(), reached.address.host)
            else -> null
        }
    }

    /**
     * The session a page holds, or null when it is EduPage's sign-in page (no `userhome(`).
     *
     * @throws EdupageException.Server when the answer is not a success.
     */
    private fun sessionOf(reached: Reached, jar: EdupageCookieJar): EdupageSession? {
        if (reached.answer.status !in SUCCESS) throw EdupageException.Server(reached.answer.status)
        val page = reached.answer.body
        val userhome = EdupagePages.userhome(page) ?: return null
        return EdupageSession(reached.address.host, jar.snapshot(), EdupagePages.gsecHash(page), userhome)
    }

    private companion object {
        /** At most this many redirects are followed for one request. */
        const val MAX_REDIRECTS = 10
        const val TEMPORARY_REDIRECT = 307
        const val PERMANENT_REDIRECT = 308
        val FOLLOWED = setOf(301, 302, 303, TEMPORARY_REDIRECT, PERMANENT_REDIRECT)
        val SUCCESS = 200..299
        const val EDUPAGE_FAMILY = ".edupage.org"
        const val LOGIN_SEGMENT = "login"
        const val SWITCHED = "OK"
        const val BAD_CREDENTIALS_MARK = "bad=1"
        const val SECOND_FACTOR_MARK = "twofactor"
        val CAPTCHA_MARKS = listOf("cap=1", "lerr=b43b43")
        val JSON = ContentType.Application.Json

        /** An `application/x-www-form-urlencoded` body of [fields], encoded as the Bakaláři form is. */
        fun formOf(vararg fields: Pair<String, String>): String = fields.joinToString("&") { (name, value) ->
            PercentEncoding.formField(name) + "=" + PercentEncoding.formField(value)
        }

        /**
         * `https://{host}`, after checking that [host] is a host alone (no scheme, port, path or
         * account), so nothing typed can send the password elsewhere.
         */
        fun originOf(host: String): String {
            val clean = host.trim().lowercase()
            val address = clean.takeIf { it.isNotEmpty() }?.let { WebAddress.parse("https://$it/") }
            if (address == null || address.host != clean || address.port != HTTPS_PORT) {
                throw EdupageException.NotEdupage("Not a host")
            }
            return "https://${address.host}"
        }

        const val HTTPS_PORT = 443
    }
}
