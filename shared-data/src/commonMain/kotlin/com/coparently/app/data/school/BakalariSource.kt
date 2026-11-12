package com.coparently.app.data.school

import com.coparently.app.data.platform.logger
import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariException
import com.coparently.app.data.school.bakalari.events
import com.coparently.app.data.school.bakalari.timetable
import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolImportPlanner
import com.coparently.app.domain.school.SchoolTask
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Bakaláři as a [SchoolSource]: the importer's former timetable and events reads, unchanged, with
 * [BakalariException] worded as a [SchoolSourceFailure] the way the importer used to word it.
 */
class BakalariSource(
    private val authorizer: BakalariAuthorizer,
    private val client: BakalariClient
) : SchoolSource {

    override suspend fun weeks(uid: String, connectionId: String, monday: LocalDate): List<SchoolWeek> =
        translated {
            try {
                (0 until SchoolImportPlanner.TIMETABLE_WEEKS).map { week ->
                    authorizer.withAccess(uid, connectionId) { baseUrl, token ->
                        client.timetable(baseUrl, token, monday.plus(week, DateTimeUnit.WEEK))
                    }
                }
            } catch (e: BakalariException.Forbidden) {
                // The summer holidays can take the right away; nothing is imported, nothing deleted.
                logger.i(TAG, "No timetable right on this account right now", e)
                emptyList()
            }
        }

    override suspend fun events(uid: String, connectionId: String, today: LocalDate): List<SchoolEvent> =
        translated {
            try {
                authorizer.withAccess(uid, connectionId) { baseUrl, token -> client.events(baseUrl, token, today) }
            } catch (e: BakalariException.Forbidden) {
                logger.i(TAG, "No events right on this account right now", e)
                emptyList()
            }
        }

    /** Bakaláři has no homework list. */
    override suspend fun tasks(uid: String, connectionId: String, today: LocalDate): List<SchoolTask> = emptyList()

    private suspend fun <T> translated(read: suspend () -> T): T = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: BakalariException) {
        throw when (e) {
            is BakalariException.NeedsPassword, is BakalariException.InvalidGrant ->
                SchoolSourceFailure.NeedsPassword(e)
            is BakalariException.ConnectionGone -> SchoolSourceFailure.Gone(e)
            is BakalariException.Network -> SchoolSourceFailure.Network(e)
            else -> SchoolSourceFailure.Server(e)
        }
    }

    private companion object {
        const val TAG = "BakalariSource"
    }
}
