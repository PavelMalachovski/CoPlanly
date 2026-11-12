package com.coparently.app.data.school

import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolTask
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.datetime.LocalDate

/**
 * What [SchoolImporter] reads from one school system (Bakaláři or EduPage): the timetable, the
 * school's events and its homework, each for the connection [connectionId] of the account [uid].
 *
 * A source keeps the connection signed in itself and reports a failure as a [SchoolSourceFailure],
 * never as the system's own exception, so the importer decides nothing by system. One update calls
 * [weeks] first, then [events] and [tasks].
 */
interface SchoolSource {

    /**
     * The timetable weeks (`SchoolImportPlanner.TIMETABLE_WEEKS` of them) from [monday]; empty when
     * the account may not read them.
     */
    suspend fun weeks(uid: String, connectionId: String, monday: LocalDate): List<SchoolWeek>

    /** The school's events from [today] on; empty when the account may not read them. */
    suspend fun events(uid: String, connectionId: String, today: LocalDate): List<SchoolEvent>

    /** The school's homework and assignments from [today] on; none for a system that has no such list. */
    suspend fun tasks(uid: String, connectionId: String, today: LocalDate): List<SchoolTask>
}

/** Why a [SchoolSource] could not read: facts for the importer, never sentences (CQ-14). */
sealed class SchoolSourceFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * The school refused the stored sign-in and only the password can recover it. The source has
     * already marked the connection.
     */
    class NeedsPassword(cause: Throwable? = null) :
        SchoolSourceFailure("The connection needs the password again", cause)

    /** The connection is not stored for the signed-in account any more. */
    class Gone(cause: Throwable? = null) : SchoolSourceFailure("No such connection", cause)

    /** No network, a timeout or a TLS failure. */
    class Network(cause: Throwable? = null) : SchoolSourceFailure("Network failure", cause)

    /** Any other failure: a status, a page that is not what the school documents. */
    class Server(cause: Throwable? = null) : SchoolSourceFailure("Server failure", cause)
}
