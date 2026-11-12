@file:OptIn(InternalSharedApi::class)

package com.coparently.app.r8probe

import com.coparently.app.data.http.HttpTimeouts
import com.coparently.app.data.http.httpClient
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.data.remote.google.GoogleCredential
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.events
import com.coparently.app.data.school.edupage.EdupageChild
import com.coparently.app.data.school.edupage.EdupageClient
import com.coparently.app.data.school.edupage.EdupageTimelineParser
import com.coparently.app.data.school.edupage.EdupageTimetableParser
import com.coparently.app.domain.InternalSharedApi
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * The imports' HTTP clients in the minified build (MON-24 fix round): a recorded Google
 * `events.list` page through `GoogleCalendarApi`, a Bakaláři sign-in and events answer through
 * `BakalariClient` and the app's events parser, and an EduPage form sign-in (with its redirect to
 * the school's host), timetable and timeline through `EdupageClient` and its two parsers, each
 * checked value by value. Each client is built
 * by `httpClient(engine, timeouts)` with the timeouts its Koin module gives it, so the minified
 * build runs production's configuration: the `HttpTimeout` plugin, Ktor's redirects off, the
 * whole-call limit around the redirect loop (the school client's), `bodyAsText` and the parsers.
 *
 * The one part that is not production is the engine: [CannedEngine] answers in memory, because the
 * clients only speak https to fixed hosts, and the `r8Test` build may not open a cleartext socket.
 * So no timeout fires and nothing redirects here: what runs minified is the configuration and the
 * code paths, not the waiting.
 * google-api-client's `@Key` models had a case here until MON-24 phase 2 removed them; this replaced
 * it.
 */
@Suppress("MagicNumber") // Recorded answers and the dates and statuses they go with.
internal object HttpImportProbe {

    /** The labels the report uses; `tools/check-r8-probe.js` requires a passing case for each. */
    const val GOOGLE_PAGE = "Google Calendar events.list page through GoogleCalendarApi"
    const val BAKALARI_ANSWERS = "Bakalari sign-in and events through BakalariClient"

    const val EDUPAGE_ANSWERS = "EduPage sign-in, timetable and timeline through the minified Ktor pipeline"

    /** Every case; never throws. */
    fun run(): List<ProbeCase> = googleCases() + bakalariCases() + edupageCases()

    private fun googleCases(): List<ProbeCase> =
        guarded(listOf(GOOGLE_PAGE), "GoogleCalendarApi.listEvents -> GoogleCalendarJson.page") {
            val engine = CannedEngine { request ->
                val authorized = request.headers[HttpHeaders.Authorization] == "Bearer r8-probe"
                if (authorized) 200 to GOOGLE_PAGE_BODY else 401 to ""
            }
            val read = runBlocking {
                GoogleCalendarApi(httpClient(engine, HttpTimeouts.GOOGLE_CALENDAR)).listEvents(
                    GoogleCredential("r8-probe"),
                    timeMin = LocalDateTime(2026, 5, 1, 0, 0),
                    timeMax = LocalDateTime(2026, 6, 1, 0, 0)
                )
            }
            val event = read.events.single()
            val decoded = listOf(
                event.id,
                event.summary,
                event.start?.dateTime?.toEpochMilliseconds()?.toString(),
                event.end?.date?.toString(),
                event.recurrence?.joinToString()
            ).joinToString("|")
            value(GOOGLE_PAGE, GOOGLE_PAGE_BODY, GOOGLE_EXPECTED, decoded)
        }

    private fun bakalariCases(): List<ProbeCase> =
        guarded(listOf(BAKALARI_ANSWERS), "BakalariClient.signIn and .events -> BakalariEventsParser") {
            val engine = CannedEngine { request ->
                when (request.url.encodedPath) {
                    "/api/login" -> 200 to LOGIN_BODY
                    "/api/3/events" -> 200 to EVENTS_BODY
                    else -> 404 to ""
                }
            }
            val client = BakalariClient(httpClient(engine, HttpTimeouts.SCHOOL))
            val decoded = runBlocking {
                val tokens = client.signIn(SCHOOL, "r8-probe", "heslo")
                val event = client.events(SCHOOL, tokens.accessToken, LocalDate(2026, 10, 5)).single()
                val start = event.times.single().start.toString()
                listOf(tokens.accessToken, tokens.refreshToken, event.id, event.title, start).joinToString("|")
            }
            value(BAKALARI_ANSWERS, LOGIN_BODY + EVENTS_BODY, BAKALARI_EXPECTED, decoded)
        }

    private fun edupageCases(): List<ProbeCase> =
        guarded(listOf(EDUPAGE_ANSWERS), "EdupageClient.signIn, .timetableJson, .timelineJson -> the parsers") {
            val engine = CannedEngine(
                headers = { request ->
                    if (request.url.encodedPath == "/login/edubarLogin.php") {
                        Headers.build {
                            append(HttpHeaders.Location, "https://$EDUPAGE_SCHOOL/user/?login=1")
                            append(HttpHeaders.SetCookie, "edid=r8-edid; path=/; secure")
                        }
                    } else {
                        Headers.Empty
                    }
                }
            ) { request ->
                when (request.url.encodedPath) {
                    "/login/" -> 200 to EDUPAGE_LOGIN_PAGE
                    "/login/edubarLogin.php" -> 302 to ""
                    "/user/" -> 200 to EDUPAGE_USER_PAGE
                    "/timetable/server/currenttt.js" -> 200 to EDUPAGE_TIMETABLE
                    "/timeline/" -> 200 to EDUPAGE_TIMELINE
                    else -> 404 to ""
                }
            }
            val client = EdupageClient(httpClient(engine, HttpTimeouts.SCHOOL))
            val from = LocalDate(2026, 10, 5)
            val to = LocalDate(2026, 10, 9)
            val child = EdupageChild("4711", "Eva Nováková", "5.A")
            val decoded = runBlocking {
                val session = client.signIn("login1.edupage.org", "r8-probe", "heslo")
                val timetable = client.timetableJson(session, child.studentId, from, to)
                val day = EdupageTimetableParser.weeks(timetable, from, to).single().days.first()
                val timeline = EdupageTimelineParser.parse(client.timelineJson(session, from), child)
                val task = timeline.tasks.single()
                val event = timeline.events.single()
                listOf(
                    session.host,
                    session.gsecHash,
                    session.cookies.keys.sorted().joinToString(","),
                    day.date.toString(),
                    day.firstLessonStart.toString(),
                    day.lastLessonEnd.toString(),
                    event.id,
                    event.title,
                    task.id,
                    task.date.toString(),
                    task.title
                ).joinToString("|")
            }
            value(EDUPAGE_ANSWERS, EDUPAGE_USER_PAGE + EDUPAGE_TIMETABLE + EDUPAGE_TIMELINE, EDUPAGE_EXPECTED, decoded)
        }

    private const val SCHOOL = "https://skola.example.cz"

    private const val EDUPAGE_SCHOOL = "skola.edupage.org"
    private const val EDUPAGE_LOGIN_PAGE = """<html><script>ASC.req_props = {"csrftoken":"r8-csrf"};</script></html>"""
    private const val EDUPAGE_USER_PAGE =
        "<html><script>ASC.gsechash=\"r8-gsh\";\nuserhome({\n\t\"userid\":\"Rodic1\",\n" +
            "\t\"dp\":{\"year\":2026}\n});\n</script></html>"
    private const val EDUPAGE_TIMETABLE =
        """{"r":{"ttitems":[{"type":"lesson","date":"2026-10-05","starttime":"08:00","endtime":"08:45"},""" +
            """{"type":"lesson","date":"2026-10-05","starttime":"09:00","endtime":"09:45"}]}}"""
    private const val EDUPAGE_TIMELINE =
        """{"timelineItems":[{"timelineid":"R8TR","typ":"trip","text":"Zoo","user_meno":"5.A",""" +
            """"cas_udalosti":"2026-10-07 08:30:00"},{"timelineid":"R8HW","typ":"bexam","text":"Písomka",""" +
            """"user_meno":"Eva Nováková","cas_udalosti":"2026-10-08 00:00:00","data":{"predmet":"Matematika"}}]}"""
    private const val EDUPAGE_EXPECTED =
        "skola.edupage.org|r8-gsh|edid|2026-10-05|08:00|09:45|R8TR|Zoo|R8HW|2026-10-08|Matematika: Písomka"

    private const val GOOGLE_PAGE_BODY =
        """{"kind":"calendar#events","items":[{"id":"r8-probe-event","summary":"Swimming",""" +
            """"start":{"dateTime":"2026-05-04T16:00:00.000+02:00"},"end":{"date":"2026-05-05"},""" +
            """"recurrence":["RRULE:FREQ=WEEKLY"]}]}"""
    private const val GOOGLE_EXPECTED = "r8-probe-event|Swimming|1777903200000|2026-05-05|RRULE:FREQ=WEEKLY"

    private const val LOGIN_BODY =
        """{"access_token":"access-r8","refresh_token":"refresh-r8","token_type":"Bearer","expires_in":3599}"""
    private const val EVENTS_BODY =
        """{"Events":[{"Id":"R8EV","Title":"Výlet","Times":[{"WholeDay":false,""" +
            """"StartTime":"2026-10-07T08:00:00+02:00","EndTime":"2026-10-07T12:00:00+02:00"}]}]}"""
    private const val BAKALARI_EXPECTED = "access-r8|refresh-r8|R8EV|Výlet|2026-10-07T08:00"
}

/**
 * A Ktor engine that answers each request with [answer]'s status and body, in memory. Ktor's own
 * `MockEngine` is a test library, which the minified build does not carry.
 */
@OptIn(InternalAPI::class)
private class CannedEngine(
    private val headers: (HttpRequestData) -> Headers = { Headers.Empty },
    private val answer: (HttpRequestData) -> Pair<Int, String>
) : HttpClientEngineBase("r8-probe") {

    override val config: HttpClientEngineConfig = HttpClientEngineConfig()

    override val dispatcher = Dispatchers.Unconfined

    /** As Ktor's `MockEngine`: the `HttpTimeout` plugin refuses an engine that does not declare it. */
    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val (status, body) = answer(data)
        return HttpResponseData(
            HttpStatusCode.fromValue(status),
            GMTDate(),
            Headers.build {
                append(HttpHeaders.ContentType, "application/json; charset=utf-8")
                appendAll(headers(data))
            },
            HttpProtocolVersion.HTTP_1_1,
            ByteReadChannel(body.toByteArray()),
            callContext()
        )
    }
}
