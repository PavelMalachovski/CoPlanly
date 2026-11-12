package com.coparently.app.data.school.edupage

import com.coparently.app.data.platform.logger
import com.coparently.app.data.platform.newMonitor
import com.coparently.app.data.school.EdupageSecrets
import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.school.SchoolSource
import com.coparently.app.data.school.SchoolSourceFailure
import com.coparently.app.data.school.StoredSchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolDay
import com.coparently.app.domain.school.SchoolDayType
import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolImportPlanner
import com.coparently.app.domain.school.SchoolTask
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * EduPage as a [SchoolSource].
 *
 * One update is one **pass**: [weeks], which the importer calls first, opens a session — the stored
 * cookies through [EdupageClient.resume], or, when EduPage answers with its sign-in page, a new
 * sign-in with the stored password — and the timeline is read once; [events] and [tasks] reuse both.
 * Three rules, each one a way a connection dies:
 * - **Fresh cookies are stored before anything is read with them**, so a process that dies mid-update
 *   keeps the session that works.
 * - **A sign-in EduPage refuses in the background** (wrong password, CAPTCHA, a second factor) marks
 *   the connection `NEEDS_PASSWORD` and throws [SchoolSourceFailure.NeedsPassword]. The password
 *   stays stored: the parent corrects it from the connection's own screen, and nothing is deleted.
 * - **A parent account switches to the connection's child** before the timetable is read.
 */
class EdupageSource(
    private val client: EdupageClient,
    private val store: SchoolConnectionStore
) : SchoolSource {

    /** The session and the timeline of the update in progress, by connection id. */
    private class Pass(
        val session: EdupageSession,
        val child: EdupageChild,
        val studentId: String,
        var timeline: Timeline? = null
    )

    private class Timeline(val from: LocalDate, val result: EdupageTimelineParser.Result)

    private val passes = HashMap<String, Pass>()
    private val passesMonitor = newMonitor()
    private val locks = HashMap<String, Mutex>()

    override suspend fun weeks(uid: String, connectionId: String, monday: LocalDate): List<SchoolWeek> =
        translated {
            // A new update: the session is opened again, never carried over from the last one.
            passesMonitor.locked { passes.remove(connectionId) }
            val first = open(uid, connectionId, signInFirst = false)
            passesMonitor.locked { passes[connectionId] = first }
            val to = monday.plus(SchoolImportPlanner.TIMETABLE_WEEKS, DateTimeUnit.WEEK)
                .minus(1, DateTimeUnit.DAY)
            reading(uid, connectionId, first) { pass ->
                val json = client.timetableJson(pass.session, pass.studentId, monday, to)
                val weeks = EdupageTimetableParser.weeks(json, monday, to)
                // `today` is not known here, only its Monday: reading from a week earlier covers the
                // `today - 7 days` the events and tasks want, so the timeline is still read once.
                mergeDaysOff(weeks, timeline(pass, lookbackFrom(monday)).daysOff)
            }
        }

    override suspend fun events(uid: String, connectionId: String, today: LocalDate): List<SchoolEvent> =
        translated {
            reading(uid, connectionId, passFor(uid, connectionId)) { timeline(it, lookbackFrom(today)).events }
        }

    override suspend fun tasks(uid: String, connectionId: String, today: LocalDate): List<SchoolTask> =
        translated {
            reading(uid, connectionId, passFor(uid, connectionId)) { timeline(it, lookbackFrom(today)).tasks }
        }

    /** The update is over: the session, its cookies and the timeline are dropped from memory. */
    override fun finish(connectionId: String) {
        passesMonitor.locked {
            passes.remove(connectionId)
            locks.remove(connectionId)
        }
    }

    private fun lookbackFrom(today: LocalDate): LocalDate = today.minus(TIMELINE_LOOKBACK_DAYS, DateTimeUnit.DAY)

    private suspend fun passFor(uid: String, connectionId: String): Pass =
        passesMonitor.locked { passes[connectionId] }
            ?: open(uid, connectionId, signInFirst = false).also { pass ->
                passesMonitor.locked { passes[connectionId] = pass }
            }

    /** The timeline from [from] or earlier, read at most once per pass. */
    private suspend fun timeline(pass: Pass, from: LocalDate): EdupageTimelineParser.Result {
        pass.timeline?.takeIf { it.from <= from }?.let { return it.result }
        val json = client.timelineJson(pass.session, from)
        val result = EdupageTimelineParser.parse(json, pass.child)
        pass.timeline = Timeline(from, result)
        return result
    }

    /**
     * Runs [read] on [first]. When EduPage answers a data call with its sign-in page, the stored
     * cookies have lapsed after all: sign in once with the stored password and run [read] again on the
     * new pass. A second failure is the caller's.
     */
    private suspend fun <T> reading(uid: String, connectionId: String, first: Pass, read: suspend (Pass) -> T): T =
        try {
            read(first)
        } catch (e: EdupageException.SessionExpired) {
            logger.i(TAG, "EduPage refused the session mid-update; signing in once more", e)
            val renewed = open(uid, connectionId, signInFirst = true)
            passesMonitor.locked { passes[connectionId] = renewed }
            read(renewed)
        }

    /**
     * The session for the connection, signed in again when its cookies have lapsed, or at once when
     * [signInFirst] says they are known to have.
     */
    private suspend fun open(uid: String, connectionId: String, signInFirst: Boolean): Pass =
        lockFor(connectionId).withLock { openLocked(uid, connectionId, signInFirst) }

    private suspend fun openLocked(uid: String, connectionId: String, signInFirst: Boolean): Pass {
        val stored = store.get(uid, connectionId) ?: throw SchoolSourceFailure.Gone()
        val secrets = stored.edupage ?: throw SchoolSourceFailure.Gone()
        if (stored.connection.status == SchoolConnectionStatus.NEEDS_PASSWORD) {
            throw SchoolSourceFailure.NeedsPassword()
        }
        val host = hostOf(stored.connection.baseUrl)
        var session = if (signInFirst) {
            signInAgain(uid, stored, secrets, host)
        } else {
            try {
                client.resume(host, secrets.cookies)
            } catch (e: EdupageException.SessionExpired) {
                logger.i(TAG, "The EduPage session lapsed; signing in again", e)
                signInAgain(uid, stored, secrets, host)
            }
        }
        keepCookies(uid, connectionId, session)
        val account = EdupageAccountParser.parse(session.userhome)
        if (account.isParent) {
            session = client.switchChild(session, secrets.studentId)
            keepCookies(uid, connectionId, session)
        }
        val child = account.children.firstOrNull { it.studentId == secrets.studentId }
            ?: EdupageChild(secrets.studentId, stored.student.name, stored.student.classId)
        return Pass(session, child, secrets.studentId)
    }

    private suspend fun signInAgain(
        uid: String,
        stored: StoredSchoolConnection,
        secrets: EdupageSecrets,
        host: String
    ): EdupageSession = try {
        client.signIn(host, stored.connection.username, secrets.password)
    } catch (e: EdupageException.BadCredentials) {
        throw needsPassword(uid, stored.id, e)
    } catch (e: EdupageException.Captcha) {
        throw needsPassword(uid, stored.id, e)
    } catch (e: EdupageException.SecondFactor) {
        throw needsPassword(uid, stored.id, e)
    }

    /** Marks the connection as waiting for the password, keeping the password, and answers the failure. */
    private fun needsPassword(uid: String, connectionId: String, cause: EdupageException): SchoolSourceFailure {
        store.update(uid, connectionId) { current ->
            current.copy(connection = current.connection.copy(status = SchoolConnectionStatus.NEEDS_PASSWORD))
        }
        return SchoolSourceFailure.NeedsPassword(cause)
    }

    /** Writes the session's cookies down when they differ from the stored ones. */
    private fun keepCookies(uid: String, connectionId: String, session: EdupageSession) {
        store.update(uid, connectionId) { current ->
            val secrets = current.edupage
            if (secrets == null || secrets.cookies == session.cookies) {
                current
            } else {
                current.copy(edupage = secrets.copy(cookies = session.cookies))
            }
        }
    }

    /**
     * [weeks] with each of [daysOff] that falls in one of them: a day off wins over a work day with no
     * lessons, and a day with lessons keeps them, because the hours still matter.
     */
    private fun mergeDaysOff(weeks: List<SchoolWeek>, daysOff: List<SchoolDay>): List<SchoolWeek> {
        if (daysOff.isEmpty()) return weeks
        return weeks.map { week ->
            val offs = daysOff.filter { SchoolWeek.mondayOf(it.date) == week.monday }
            if (offs.isEmpty()) {
                week
            } else {
                val merged = week.days.toMutableList()
                for (off in offs) {
                    val at = merged.indexOfFirst { it.date == off.date }
                    when {
                        at < 0 -> merged += off.copy(type = SchoolDayType.HOLIDAY)
                        !merged[at].hasLessons -> merged[at] = off.copy(type = SchoolDayType.HOLIDAY)
                    }
                }
                week.copy(days = merged.sortedBy { it.date })
            }
        }
    }

    private suspend fun <T> translated(read: suspend () -> T): T = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SchoolSourceFailure) {
        throw e
    } catch (e: EdupageException.Network) {
        throw SchoolSourceFailure.Network(e)
    } catch (e: EdupageException) {
        throw SchoolSourceFailure.Server(e)
    }

    private fun lockFor(connectionId: String): Mutex =
        passesMonitor.locked { locks.getOrPut(connectionId) { Mutex() } }

    /** The host of a connection's address (`https://<host>`). */
    private fun hostOf(baseUrl: String): String =
        baseUrl.substringAfter("://").substringBefore('/').substringBefore('?').lowercase()

    private companion object {
        const val TAG = "EdupageSource"
        const val TIMELINE_LOOKBACK_DAYS = 7
    }
}
