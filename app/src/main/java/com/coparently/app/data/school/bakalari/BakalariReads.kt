package com.coparently.app.data.school.bakalari

import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate

/*
 * What a school's server answers, read into the school domain (MON-8). The client that fetches it
 * moved to `:shared-data` in MON-24 and returns the server's text; these three read it here, with
 * the parsers, because the school domain they build is still `java.time` (MON-24's stay list).
 */

/** Who the login belongs to: `GET /api/3/user`. */
suspend fun BakalariClient.user(baseUrl: String, accessToken: String): BakalariAccount =
    BakalariAccount.parse(userJson(baseUrl, accessToken))

/** The actual timetable of the week holding [date]: `GET /api/3/timetable/actual?date=`. */
suspend fun BakalariClient.timetable(baseUrl: String, accessToken: String, date: LocalDate): SchoolWeek =
    BakalariTimetableParser.parse(timetableJson(baseUrl, accessToken, date.toKotlinLocalDate()), date)

/** Every event visible to the account from [from] on: `GET /api/3/events?from=`. */
suspend fun BakalariClient.events(baseUrl: String, accessToken: String, from: LocalDate): List<SchoolEvent> =
    BakalariEventsParser.parse(eventsJson(baseUrl, accessToken, from.toKotlinLocalDate()))
