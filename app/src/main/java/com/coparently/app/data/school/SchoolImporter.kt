package com.coparently.app.data.school

import android.util.Log
import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariException
import com.coparently.app.data.school.bakalari.withStatus
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.holidays.HolidayLocation
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolEvent
import com.coparently.app.domain.school.SchoolImportInput
import com.coparently.app.domain.school.SchoolImportPlanner
import com.coparently.app.domain.school.SchoolImportTarget
import com.coparently.app.domain.school.SchoolSyncFailure
import com.coparently.app.domain.school.SchoolSyncResult
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One school update (MON-8): fetch, plan, write.
 *
 * The fetching and the writing live here; every decision is [SchoolImportPlanner]'s, a pure
 * function the JVM tests reach. Updates of one connection run one at a time — the daily job and
 * "Update now" can meet — so two passes never both create the same event.
 */
@Singleton
class SchoolImporter internal constructor(
    private val authorizer: BakalariAuthorizer,
    private val client: BakalariClient,
    private val store: SchoolConnectionStore,
    private val userRepository: UserRepository,
    private val collaborators: Collaborators,
    private val clock: Clock
) {

    /**
     * What an update writes with and words with, kept together so the constructor stays short.
     *
     * @property writer Reads and writes the calendar through the ordinary event use cases.
     * @property wording Resolves the titles in the parent's language.
     * @property children The child records, read by id in every family, for the family of a
     *   connection that names none ([familyOf]).
     */
    class Collaborators @Inject constructor(
        val writer: SchoolEventWriter,
        val wording: SchoolImportWordingSource,
        val children: ChildInfoRepository
    )

    /** The production importer, on the system clock. */
    @Inject
    constructor(
        authorizer: BakalariAuthorizer,
        client: BakalariClient,
        store: SchoolConnectionStore,
        userRepository: UserRepository,
        collaborators: Collaborators
    ) : this(authorizer, client, store, userRepository, collaborators, Clock.systemDefaultZone())

    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Updates every connection of the signed-in account.
     *
     * @return How many connections there were — zero when nobody is signed in.
     */
    suspend fun syncAll(): Int {
        val uid = userRepository.getCurrentUserId() ?: return 0
        val connections = store.all(uid)
        connections.forEach { sync(it.id) }
        return connections.size
    }

    /** Updates the connection [connectionId] of the signed-in account. Never throws. */
    suspend fun sync(connectionId: String): SchoolSyncResult {
        val uid = userRepository.getCurrentUserId() ?: return SchoolSyncResult.NotFound
        return locks.getOrPut(connectionId) { Mutex() }.withLock {
            try {
                syncLocked(uid, connectionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BakalariException) {
                failure(uid, connectionId, e)
            }
        }
    }

    private suspend fun syncLocked(uid: String, connectionId: String): SchoolSyncResult {
        val user = userRepository.getUserById(uid)
        val stored = store.get(uid, connectionId)
        // A connection waiting for its password is refused by the authorizer before any request.
        if (user == null || stored == null) return SchoolSyncResult.NotFound
        // Decided before anything is fetched, and passed to the planner explicitly: an event of no
        // family is stamped with the family on screen when it is saved, whichever that happens to
        // be when the daily job runs (isolation audit, October 2026).
        val family = familyOf(uid, user, stored.connection)
        if (family !is ConnectionFamily.Known) {
            Log.i(TAG, "A connection that names no family imports nothing while the parent has two")
            return SchoolSyncResult.NeedsFamily
        }

        val today = LocalDate.now(clock)
        val weeks = timetableWeeks(uid, connectionId, today)
        val events = schoolEvents(uid, connectionId, today)
        // Re-read: the fetch may have rotated the tokens, and those must not be written back stale.
        val latest = store.get(uid, connectionId) ?: throw BakalariException.ConnectionGone()
        val connection = latest.connection
        val draft = SchoolImportInput(
            target = SchoolImportTarget(
                baseUrl = connection.baseUrl,
                username = connection.username,
                childId = connection.childId,
                familyId = family.familyId,
                ownerUid = uid,
                ownerSlot = user.role
            ),
            today = today,
            now = LocalDateTime.now(clock),
            student = latest.student,
            events = events,
            weeks = weeks,
            drawnHolidays = drawnHolidays(user, today),
            existing = emptyMap(),
            ledger = latest.ledger,
            wording = collaborators.wording.forChild(connection.childId, latest.student.name)
        )
        val input = draft.copy(existing = collaborators.writer.existing(SchoolImportPlanner.idsToLookUp(draft)))
        val plan = SchoolImportPlanner.plan(input)
        val counts = collaborators.writer.apply(plan)
        val finishedAt = clock.millis()
        store.update(uid, connectionId) { current ->
            current.copy(
                connection = current.connection.copy(
                    status = SchoolConnectionStatus.OK,
                    lastSuccessAtMillis = finishedAt
                ),
                // A create that failed is not recorded, so the next update tries it again.
                ledger = plan.ledger - counts.failedCreates
            )
        }
        return SchoolSyncResult.Success(counts.created, counts.updated, counts.deleted)
    }

    /**
     * The family [connection]'s events belong to (MON-8: "one connection is one child in one family").
     *
     * A connection made while the parent had no co-parent names none, and nothing used to resolve
     * it: its events were stamped with whichever family was on screen when the daily job ran, and
     * shared with that family's co-parent — another family's child's school calendar included. So a
     * connection that names no family takes, in this order:
     *
     * - **the chosen child's own family**, when their record is stamped: the events are about that
     *   child, and a stamp naming an earlier family is evidence the ledger rule says not to guess
     *   past — such events stay that family's history, shared with nobody;
     * - **the parent's only family**, while they have exactly one co-parent;
     * - and otherwise nothing: with no co-parent its events belong to nobody yet (known, and null),
     *   with two or more nothing is imported until a family is chosen ([ConnectionFamily.Unknown]).
     *   Never the family on screen.
     *
     * A family found here is written onto the connection, so the next update need not ask again.
     */
    private suspend fun familyOf(uid: String, user: User, connection: SchoolConnection): ConnectionFamily {
        connection.familyId?.takeIf { it.isNotBlank() }?.let { return ConnectionFamily.Known(it) }
        val coParents = FamilyAudience.coParents(uid, user.partnerIds, user.partnerId)
        val childFamily = collaborators.children.getChildInfoById(connection.childId)?.familyId
        val resolved = childFamily?.takeIf { it.isNotBlank() }
            ?: coParents.singleOrNull()?.let { FamilyKey.orNull(uid, it) }
        if (resolved != null) {
            store.update(uid, connection.id) { current ->
                current.copy(connection = current.connection.copy(familyId = resolved))
            }
        }
        return if (resolved != null || coParents.isEmpty()) {
            ConnectionFamily.Known(resolved)
        } else {
            ConnectionFamily.Unknown
        }
    }

    /** [SchoolImportPlanner.TIMETABLE_WEEKS] weeks from this one; none when the right is missing. */
    private suspend fun timetableWeeks(uid: String, connectionId: String, today: LocalDate): List<SchoolWeek> {
        val monday = SchoolWeek.mondayOf(today)
        return try {
            (0 until SchoolImportPlanner.TIMETABLE_WEEKS).map { week ->
                authorizer.withAccess(uid, connectionId) { baseUrl, token ->
                    client.timetable(baseUrl, token, monday.plusWeeks(week.toLong()))
                }
            }
        } catch (e: BakalariException.Forbidden) {
            // The summer holidays can take the right away; nothing is imported, nothing deleted.
            Log.i(TAG, "No timetable right on this account right now", e)
            emptyList()
        }
    }

    private suspend fun schoolEvents(uid: String, connectionId: String, today: LocalDate): List<SchoolEvent> =
        try {
            authorizer.withAccess(uid, connectionId) { baseUrl, token -> client.events(baseUrl, token, today) }
        } catch (e: BakalariException.Forbidden) {
            Log.i(TAG, "No events right on this account right now", e)
            emptyList()
        }

    /**
     * The days the calendar already draws as a public holiday or a school vacation for [user]'s
     * country and region — the same `HolidayLocation` the calendar reads.
     */
    private fun drawnHolidays(user: User, today: LocalDate): Set<LocalDate> {
        val provider = HolidayLocation.of(user.countryCode, user.regionCode).provider ?: return emptySet()
        val from = SchoolWeek.mondayOf(today)
        val to = today.plusDays(SchoolImportPlanner.EVENT_WINDOW_DAYS)
        return provider.holidaysInRange(from, to).keys + provider.schoolVacationDaysInRange(from, to)
    }

    private fun failure(uid: String, connectionId: String, error: BakalariException): SchoolSyncResult {
        Log.w(TAG, "School import failed", error)
        return when (error) {
            // The authorizer has already marked the connection.
            is BakalariException.NeedsPassword, is BakalariException.InvalidGrant -> SchoolSyncResult.NeedsPassword
            is BakalariException.ConnectionGone -> SchoolSyncResult.NotFound
            else -> {
                store.update(uid, connectionId) { it.withStatus(SchoolConnectionStatus.ERROR) }
                SchoolSyncResult.Failed(
                    if (error is BakalariException.Network) SchoolSyncFailure.NETWORK else SchoolSyncFailure.SERVER
                )
            }
        }
    }

    /** The family a connection's events belong to, as [familyOf] resolves it before an update. */
    private sealed interface ConnectionFamily {

        /** The events go to [familyId], or to no family while the parent has no co-parent. */
        data class Known(val familyId: String?) : ConnectionFamily

        /** The parent has two families or more and nothing names one: nothing is imported. */
        data object Unknown : ConnectionFamily
    }

    private companion object {
        const val TAG = "SchoolImporter"
    }
}
