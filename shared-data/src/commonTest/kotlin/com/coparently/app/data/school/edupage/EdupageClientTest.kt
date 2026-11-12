package com.coparently.app.data.school.edupage

import com.coparently.app.data.http.RecordingServer
import com.coparently.app.data.http.answer
import com.coparently.app.data.school.edupage.EdupageFixtures.CSRF
import com.coparently.app.data.school.edupage.EdupageFixtures.GSEC_HASH
import com.coparently.app.data.school.edupage.EdupageFixtures.LOGIN_HOST
import com.coparently.app.data.school.edupage.EdupageFixtures.LOGIN_PAGE
import com.coparently.app.data.school.edupage.EdupageFixtures.NOT_EDUPAGE_PAGE
import com.coparently.app.data.school.edupage.EdupageFixtures.PASSWORD
import com.coparently.app.data.school.edupage.EdupageFixtures.SCHOOL_HOST
import com.coparently.app.data.school.edupage.EdupageFixtures.SESSION_ID
import com.coparently.app.data.school.edupage.EdupageFixtures.USERNAME
import com.coparently.app.data.school.edupage.EdupageFixtures.USER_PAGE
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The EduPage client against an in-memory EduPage: the form sign-in from `login1.edupage.org` to
 * the school's host, the session cookie carried across, EduPage's refusals read from the address
 * the sign-in ends on, and the shape of the data requests. The answers are inferred from the
 * edupage-api 0.13.3 protocol description ([EdupageFixtures]), not recorded live.
 */
class EdupageClientTest {

    /**
     * EduPage as the sign-in meets it: `login1` serves the sign-in page with a session cookie and
     * answers the form with a redirect to [afterForm] (setting a second cookie), which serves
     * [landing].
     */
    private fun edupage(afterForm: String, landing: String = USER_PAGE) = RecordingServer { request ->
        when {
            request.url.host == LOGIN_HOST && request.url.encodedPath == "/login/" ->
                answer(LOGIN_PAGE, headers = cookie("PHPSESSID=$SESSION_ID; path=/; domain=.edupage.org"))
            request.url.encodedPath == "/login/edubarLogin.php" ->
                redirect(afterForm, "edid=e-2; path=/; secure")
            request.url.toString() == afterForm -> answer(landing)
            else -> answer("", status = 404)
        }
    }

    private fun cookie(vararg setCookies: String) = headersOf(HttpHeaders.SetCookie to setCookies.toList())

    private fun MockRequestHandleScope.redirect(location: String, setCookie: String): HttpResponseData =
        answer(
            "",
            status = 302,
            headers = headersOf(HttpHeaders.Location to listOf(location), HttpHeaders.SetCookie to listOf(setCookie))
        )

    private suspend fun signIn(server: RecordingServer): EdupageSession =
        EdupageClient(server.client).signIn(LOGIN_HOST, USERNAME, PASSWORD)

    @Test
    fun `sign-in follows the redirect to the school and reads userhome and the gsec hash`() = runTest {
        val server = edupage(afterForm = "https://$SCHOOL_HOST/user/?login=1")

        val session = signIn(server)

        assertEquals(SCHOOL_HOST, session.host)
        assertEquals(GSEC_HASH, session.gsecHash)
        assertEquals("Rodic12345", session.userhome["userid"]?.jsonPrimitive?.content)
        assertEquals("2025", session.userhome["dp"]?.jsonObject?.get("year")?.jsonPrimitive?.content)
        assertEquals("a } brace and a \"quote\"", session.userhome["note"]?.jsonPrimitive?.content)

        assertEquals(3, server.requests.size)
        assertEquals("https://$LOGIN_HOST/login/?cmd=MainLogin", server.requests[0].url.toString())
        val form = server.requests[1]
        assertEquals("POST", form.method.value)
        assertEquals("https://$LOGIN_HOST/login/edubarLogin.php", form.url.toString())
        assertEquals("application/x-www-form-urlencoded", form.body.contentType.toString())
        assertEquals("csrfauth=$CSRF&username=rodic%40example.cz&password=$PASSWORD", server.bodyOf(1))
        assertEquals("GET", server.requests[2].method.value)
        assertEquals("https://$SCHOOL_HOST/user/?login=1", server.requests[2].url.toString())
    }

    @Test
    fun `sign-in carries the session cookie across hosts`() = runTest {
        val server = edupage(afterForm = "https://$SCHOOL_HOST/user/?login=1")

        val session = signIn(server)

        assertNull(server.requests[0].headers[HttpHeaders.Cookie])
        assertEquals("PHPSESSID=$SESSION_ID", server.requests[1].headers[HttpHeaders.Cookie])
        assertEquals(SCHOOL_HOST, server.requests[2].url.host)
        assertEquals("PHPSESSID=$SESSION_ID; edid=e-2", server.requests[2].headers[HttpHeaders.Cookie])
        assertEquals(mapOf("PHPSESSID" to SESSION_ID, "edid" to "e-2"), session.cookies)
    }

    @Test
    fun `bad=1 is BadCredentials`() = runTest {
        val server = edupage(afterForm = "https://$LOGIN_HOST/login/?cmd=MainLogin&bad=1", landing = LOGIN_PAGE)

        assertFailsWith<EdupageException.BadCredentials> { signIn(server) }
    }

    @Test
    fun `cap=1 is Captcha`() = runTest {
        val server = edupage(afterForm = "https://$LOGIN_HOST/login/?cmd=MainLogin&cap=1", landing = LOGIN_PAGE)

        assertFailsWith<EdupageException.Captcha> { signIn(server) }
    }

    @Test
    fun `the lerr code b43b43 is Captcha too`() = runTest {
        val server = edupage(afterForm = "https://$LOGIN_HOST/login/?lerr=b43b43", landing = LOGIN_PAGE)

        assertFailsWith<EdupageException.Captcha> { signIn(server) }
    }

    @Test
    fun `a twofactor redirect is SecondFactor`() = runTest {
        val server = edupage(afterForm = "https://$SCHOOL_HOST/login/twofactor?sn=1", landing = "<html></html>")

        val refusal = assertFailsWith<EdupageException.SecondFactor> { signIn(server) }

        assertEquals(SCHOOL_HOST, refusal.host)
        assertEquals(SESSION_ID, refusal.session["PHPSESSID"])
        assertFalse(SESSION_ID in refusal.toString())
    }

    @Test
    fun `a page without a csrf token is NotEdupage`() = runTest {
        val server = RecordingServer { answer(NOT_EDUPAGE_PAGE) }

        assertFailsWith<EdupageException.NotEdupage> { signIn(server) }

        assertEquals(1, server.requests.size)
    }

    @Test
    fun `an address that is not a host alone is NotEdupage and nothing is sent`() = runTest {
        val server = RecordingServer { answer(LOGIN_PAGE) }

        for (host in listOf("", "evil.example/zsx.edupage.org", "user@evil.example", "zsx.edupage.org:8443")) {
            assertFailsWith<EdupageException.NotEdupage>(host) {
                EdupageClient(server.client).signIn(host, USERNAME, PASSWORD)
            }
        }
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `resume with an expired cookie is SessionExpired`() = runTest {
        val server = RecordingServer { request ->
            if (request.url.encodedPath == "/user") {
                answer("", status = 302, headers = headersOf(HttpHeaders.Location, "/login/?msg=expired"))
            } else {
                answer(LOGIN_PAGE)
            }
        }

        assertFailsWith<EdupageException.SessionExpired> {
            EdupageClient(server.client).resume(SCHOOL_HOST, mapOf("PHPSESSID" to "old"))
        }

        assertEquals("https://$SCHOOL_HOST/user", server.requests[0].url.toString())
        assertEquals("PHPSESSID=old", server.requests[0].headers[HttpHeaders.Cookie])
        assertEquals("https://$SCHOOL_HOST/login/?msg=expired", server.requests[1].url.toString())
    }

    @Test
    fun `resume with a live cookie reads the session again`() = runTest {
        val server = RecordingServer { answer(USER_PAGE) }

        val session = EdupageClient(server.client).resume(SCHOOL_HOST, mapOf("PHPSESSID" to SESSION_ID))

        assertEquals(SCHOOL_HOST, session.host)
        assertEquals(GSEC_HASH, session.gsecHash)
        assertEquals(mapOf("PHPSESSID" to SESSION_ID), session.cookies)
    }

    @Test
    fun `the password is sent only in the edubarLogin form`() = runTest {
        val server = edupage(afterForm = "https://$SCHOOL_HOST/user/?login=1")

        val session = signIn(server)

        server.requests.forEachIndexed { index, request ->
            assertFalse(PASSWORD in request.url.toString(), "address of request $index")
            assertFalse(request.headers.entries().any { (_, values) -> values.any { PASSWORD in it } })
            val inBody = PASSWORD in server.bodyOf(index)
            assertEquals(request.url.encodedPath == "/login/edubarLogin.php", inBody, "body of request $index")
        }
        assertFalse(SESSION_ID in session.toString())
        assertFalse(GSEC_HASH in session.toString())
    }

    @Test
    fun `the timetable request carries the year the student and the gsec hash`() = runTest {
        val server = RecordingServer { answer("""{"r":{"ttitems":[]}}""") }
        val session = sessionOn(SCHOOL_HOST)

        val json = EdupageClient(server.client)
            .timetableJson(session, "4711", LocalDate(2026, 11, 9), LocalDate(2026, 11, 15))

        assertEquals("""{"r":{"ttitems":[]}}""", json)
        val request = server.requests.single()
        assertEquals("POST", request.method.value)
        assertEquals(
            "https://$SCHOOL_HOST/timetable/server/currenttt.js?__func=curentttGetData",
            request.url.toString()
        )
        assertEquals("application/json", request.body.contentType.toString())
        assertEquals("PHPSESSID=$SESSION_ID", request.headers[HttpHeaders.Cookie])
        assertEquals(
            """{"__args":[null,{"year":2025,"datefrom":"2026-11-09","dateto":"2026-11-15",""" +
                """"table":"students","id":"4711","showColors":true,"showIgroupsInClasses":true,""" +
                """"showOrig":true,"log_module":"CurrentTTView"}],"__gsh":"$GSEC_HASH"}""",
            server.bodyOf(0)
        )
    }

    @Test
    fun `the timeline request posts the start date as a form`() = runTest {
        val server = RecordingServer { answer("""{"timelineItems":[]}""") }

        val json = EdupageClient(server.client).timelineJson(sessionOn(SCHOOL_HOST), LocalDate(2026, 9, 1))

        assertEquals("""{"timelineItems":[]}""", json)
        val request = server.requests.single()
        assertEquals("POST", request.method.value)
        assertEquals(
            "https://$SCHOOL_HOST/timeline/?module=todo&filterTab=&akcia=getData&filterTab=messages",
            request.url.toString()
        )
        assertEquals("application/x-www-form-urlencoded", request.body.contentType.toString())
        assertEquals("datefrom=2026-09-01", server.bodyOf(0))
    }

    @Test
    fun `a data request sent to the sign-in page is SessionExpired`() = runTest {
        val server = RecordingServer { request ->
            if (request.url.encodedPath.startsWith("/timeline")) {
                answer("", status = 302, headers = headersOf(HttpHeaders.Location, "https://$SCHOOL_HOST/login/"))
            } else {
                answer(LOGIN_PAGE)
            }
        }

        assertFailsWith<EdupageException.SessionExpired> {
            EdupageClient(server.client).timelineJson(sessionOn(SCHOOL_HOST), LocalDate(2026, 9, 1))
        }
    }

    @Test
    fun `switching the child keeps the cookies it sets and refuses anything but OK`() = runTest {
        val switched = RecordingServer { answer("OK", headers = cookie("edid=child-4711; path=/")) }

        val session = EdupageClient(switched.client).switchChild(sessionOn(SCHOOL_HOST), "4711")

        assertEquals("https://$SCHOOL_HOST/login/switchchild?studentid=4711", switched.requests.single().url.toString())
        assertEquals(mapOf("PHPSESSID" to SESSION_ID, "edid" to "child-4711"), session.cookies)

        val refused = RecordingServer { answer("Not your child") }
        val failure = assertFailsWith<EdupageException.Server> {
            EdupageClient(refused.client).switchChild(sessionOn(SCHOOL_HOST), "9999")
        }
        assertEquals(200, failure.status)
    }

    @Test
    fun `a redirect out of edupage is not followed`() = runTest {
        val server = RecordingServer {
            answer("", status = 302, headers = headersOf(HttpHeaders.Location, "https://evil.example/user"))
        }

        val failure = assertFailsWith<EdupageException.Server> {
            EdupageClient(server.client).resume(SCHOOL_HOST, mapOf("PHPSESSID" to SESSION_ID))
        }

        assertEquals(302, failure.status)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun `the eleventh redirect is not followed`() = runTest {
        val server = RecordingServer {
            answer("", status = 302, headers = headersOf(HttpHeaders.Location, "/user"))
        }

        assertFailsWith<EdupageException.Server> {
            EdupageClient(server.client).resume(SCHOOL_HOST, mapOf("PHPSESSID" to SESSION_ID))
        }

        assertEquals(11, server.requests.size)
    }

    @Test
    fun `a server error is Server`() = runTest {
        val server = RecordingServer { answer("", status = 503) }

        val failure = assertFailsWith<EdupageException.Server> {
            EdupageClient(server.client).timelineJson(sessionOn(SCHOOL_HOST), LocalDate(2026, 9, 1))
        }

        assertEquals(503, failure.status)
    }

    @Test
    fun `cookies joined by NSURLSession are told apart and a deleted one is dropped`() {
        val jar = EdupageCookieJar(mapOf("PHPSESSID" to "old", "edid" to "e-1"))

        jar.absorb(
            listOf(
                "PHPSESSID=deleted; expires=Thu, 01-Jan-1970 00:00:01 GMT; path=/, " +
                    "PHPSESSID2=new; expires=Fri, 13-Nov-2026 10:00:00 GMT; path=/",
                "edid=; Max-Age=0",
                "lang=sk; Max-Age=3600"
            )
        )

        assertEquals(mapOf("PHPSESSID2" to "new", "lang" to "sk"), jar.snapshot())
        assertEquals("PHPSESSID2=new; lang=sk", jar.header())
    }

    private fun sessionOn(host: String): EdupageSession = EdupageSession(
        host = host,
        cookies = mapOf("PHPSESSID" to SESSION_ID),
        gsecHash = GSEC_HASH,
        userhome = Json.parseToJsonElement("""{"userid":"Rodic12345","dp":{"year":2025}}""") as JsonObject
    )
}
