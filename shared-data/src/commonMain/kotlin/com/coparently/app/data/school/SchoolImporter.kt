package com.coparently.app.data.school

import com.coparently.app.data.platform.logger
import com.coparently.app.data.platform.newMonitor
import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.withStatus
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.family.FamilyAudience
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.family.FamilySlotsSource
import com.coparently.app.domain.holidays.HolidayLocation
import com.coparently.app.domain.model.User
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportInput
import com.coparently.app.domain.school.SchoolImportPlanner
import com.coparently.app.domain.school.SchoolImportTarget
import com.coparently.app.domain.school.SchoolSyncFailure
import com.coparently.app.domain.school.SchoolSyncResult
import com.coparently.app.domain.school.SchoolSystem
import com.coparently.app.domain.school.SchoolWeek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * One school update (MON-8): fetch, plan, write.
 *
 * The fetching and the writing live here; every decision is [SchoolImportPlanner]'s, a pure
 * function the JVM tests reach. Updates of one connection run one at a time — the daily job and
 * "Update now" can meet — so two passes never both create the same event.
 */
@Suppress("LongParameterList") // a clock and its zone, which one `java.time.Clock` carried (MON-24)
class SchoolImporter internal constructor(
    private val sources: Map<SchoolSystem, SchoolSource>,
    private val store: SchoolConnectionStore,
    private val userRepository: UserRepository,
    private val collaborators: Collaborators,
    private val clock: Clock,
    private val zone: TimeZone
) {

    /**
     * What an update writes with and words with, kept together so the constructor stays short.
     *
     * @property writer Reads and writes the calendar through the ordinary event use cases.
     * @property wording Resolves the titles in the parent's language.
     * @property children The child records, read by id in every family, for the family of a
     *   connection that names none ([familyOf]).
     * @property familySlots The importer's slot in the connection's family, which may differ from
     *   the profile's (per-family slots, 25 October 2026).
     */
    class Collaborators(
        val writer: SchoolEventWriter,
        val wording: SchoolImportWordings,
        val children: ChildInfoRepository,
        val familySlots: FamilySlotsSource = FamilySlotsSource.None
    )

    /**
     * The production importer, on the system clock in the zone the phone had when it was built —
     * what `java.time.Clock.systemDefaultZone()` gave it before MON-24. Bakaláři is read through
     * [BakalariSource]; [edupage] is the EduPage source, or null while the build has none.
     */
    constructor(
        authorizer: BakalariAuthorizer,
        client: BakalariClient,
        store: SchoolConnectionStore,
        userRepository: UserRepository,
        collaborators: Collaborators,
        edupage: SchoolSource? = null
    ) : this(
        sourcesOf(authorizer, client, edupage),
        store,
        userRepository,
        collaborators,
        Clock.System,
        TimeZone.currentSystemDefault()
    )

    /** One lock per connection, made on first use; [locksMonitor] guards the map, as a concurrent map did. */
    private val locks = HashMap<String, Mutex>()
    private val locksMonitor = newMonitor()

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
        return locksMonitor.locked { locks.getOrPut(connectionId) { Mutex() } }.withLock {
            try {
                syncLocked(uid, connectionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SchoolSourceFailure) {
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
            logger.i(TAG, "A connection that names no family imports nothing while the parent has two")
            return SchoolSyncResult.NeedsFamily
        }

        val today = clock.todayIn(zone)
        val source = sources[stored.connection.system] ?: throw SchoolSourceFailure.Gone()
        // The timetable first: a source opens its session there and reuses it for the rest.
        val weeks = source.weeks(uid, connectionId, SchoolWeek.mondayOf(today))
        val events = source.events(uid, connectionId, today)
        val tasks = source.tasks(uid, connectionId, today)
        // Re-read: the fetch may have rotated the tokens, and those must not be written back stale.
        val latest = store.get(uid, connectionId) ?: throw SchoolSourceFailure.Gone()
        val connection = latest.connection
        val draft = SchoolImportInput(
            target = SchoolImportTarget(
                baseUrl = connection.baseUrl,
                username = connection.username,
                childId = connection.childId,
                familyId = family.familyId,
                ownerUid = uid,
                ownerSlot = family.familyId?.let { collaborators.familySlots.get(it)?.of(uid) } ?: user.role,
                system = connection.system,
                studentKey = latest.edupage?.studentId.orEmpty()
            ),
            today = today,
            now = clock.now().toLocalDateTime(zone),
            student = latest.student,
            events = events,
            tasks = tasks,
            weeks = weeks,
            drawnHolidays = drawnHolidays(user, today),
            existing = emptyMap(),
            ledger = latest.ledger,
            wording = collaborators.wording.forChild(connection.childId, latest.student.name)
        )
        val input = draft.copy(existing = collaborators.writer.existing(SchoolImportPlanner.idsToLookUp(draft)))
        val plan = SchoolImportPlanner.plan(input)
        val counts = collaborators.writer.apply(plan)
        val finishedAt = clock.now().toEpochMilliseconds()
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

    /**
     * The days the calendar already draws as a public holiday or a school vacation for [user]'s
     * country and region — the same `HolidayLocation` the calendar reads.
     */
    private fun drawnHolidays(user: User, today: LocalDate): Set<LocalDate> {
        val provider = HolidayLocation.of(user.countryCode, user.regionCode).provider ?: return emptySet()
        val from = SchoolWeek.mondayOf(today)
        val to = today.plus(SchoolImportPlanner.EVENT_WINDOW_DAYS, DateTimeUnit.DAY)
        return (provider.holidaysInRange(from, to).keys + provider.schoolVacationDaysInRange(from, to)).toSet()
    }

    private fun failure(uid: String, connectionId: String, error: SchoolSourceFailure): SchoolSyncResult {
        logger.w(TAG, "School import failed", error)
        return when (error) {
            // The source has already marked the connection.
            is SchoolSourceFailure.NeedsPassword -> SchoolSyncResult.NeedsPassword
            is SchoolSourceFailure.Gone -> SchoolSyncResult.NotFound
            else -> {
                store.update(uid, connectionId) { it.withStatus(SchoolConnectionStatus.ERROR) }
                SchoolSyncResult.Failed(
                    if (error is SchoolSourceFailure.Network) SchoolSyncFailure.NETWORK else SchoolSyncFailure.SERVER
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

    /** Builders for the app's tests, which live in another module. */
    companion object {
        private const val TAG = "SchoolImporter"

        /** An importer on [clock] in [zone], for a test; [edupage] is the EduPage source, if the test has one. */
        @InternalSharedApi
        @Suppress("LongParameterList") // the constructor's, one by one
        fun on(
            authorizer: BakalariAuthorizer,
            client: BakalariClient,
            store: SchoolConnectionStore,
            userRepository: UserRepository,
            collaborators: Collaborators,
            clock: Clock,
            zone: TimeZone,
            edupage: SchoolSource? = null
        ): SchoolImporter =
            SchoolImporter(sourcesOf(authorizer, client, edupage), store, userRepository, collaborators, clock, zone)

        private fun sourcesOf(
            authorizer: BakalariAuthorizer,
            client: BakalariClient,
            edupage: SchoolSource?
        ): Map<SchoolSystem, SchoolSource> = buildMap {
            put(SchoolSystem.BAKALARI, BakalariSource(authorizer, client))
            edupage?.let { put(SchoolSystem.EDUPAGE, it) }
        }
    }
}
