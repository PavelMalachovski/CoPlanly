package com.coparently.app.data.school.bakalari

import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.datetime.LocalDate

/*
 * What a school's server answers, read into the school domain (MON-8). The client that fetches it
 * moved to `:shared-data` in MON-24 and returns the server's text; these three read it with the
 * parsers.
 */

/** Who the login belongs to: `GET /api/3/user`. */
suspend fun BakalariClient.user(baseUrl: String, accessToken: String): BakalariAccount =
    BakalariAccount.parse(userJson(baseUrl, accessToken))

/** The actual timetable of the week holding [date]: `GET /api/3/timetable/actual?date=`. */
suspend fun BakalariClient.timetable(baseUrl: String, accessToken: String, date: LocalDate): SchoolWeek =
    BakalariTimetableParser.parse(timetableJson(baseUrl, accessToken, date), date)

/** Every event visible to the account from [from] on: `GET /api/3/events?from=`. */
suspend fun BakalariClient.events(baseUrl: String, accessToken: String, from: LocalDate): List<SchoolEvent> =
    BakalariEventsParser.parse(eventsJson(baseUrl, accessToken, from))
