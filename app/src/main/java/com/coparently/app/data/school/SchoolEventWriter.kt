package com.coparently.app.data.school

import android.util.Log
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.school.ExistingImport
import com.coparently.app.domain.school.SchoolImportPlan
import com.coparently.app.domain.usecase.EventUseCases
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How many events an update changed.
 *
 * @property created Events created.
 * @property updated Events whose school data changed.
 * @property deleted School-hours events removed because the day has no school any more.
 * @property failedCreates Ids whose create failed; they are left out of the connection's record so
 *   the next update tries them again.
 */
data class SchoolImportCounts(
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val failedCreates: Set<String> = emptySet()
)

/**
 * Where a school import meets the calendar (MON-8): it reads what Room holds under the imported
 * ids and carries out a [SchoolImportPlan] through the **ordinary event use cases**.
 *
 * Nothing about an imported event is written by hand. Creating one goes through
 * `CreateEventUseCase` → `EventRepositoryImpl.insertEvent`, which stamps the creator, computes the
 * `sharedWith` audience from the event's own family, records a revision and uploads it; deleting
 * one goes through the ordinary delete path, which writes a tombstone and a revision. The one
 * difference is `announce = false`: an import of thirty events must not post thirty cards to the
 * co-parent's chat.
 */
@Singleton
class SchoolEventWriter @Inject constructor(
    private val eventDao: EventDao,
    private val eventRepository: EventRepository,
    private val eventUseCases: EventUseCases
) {

    /**
     * What Room holds under each of [ids]: a live event, a pending deletion, or nothing (absent
     * from the map). `EventDao.getEventById` is read directly because it is the one query that
     * still returns a tombstone, which is exactly the answer "never resurrect" needs.
     */
    suspend fun existing(ids: Set<String>): Map<String, ExistingImport> = ids.mapNotNull { id ->
        val row = eventDao.getEventById(id) ?: return@mapNotNull null
        if (row.deletedAtMillis != null) return@mapNotNull id to ExistingImport.Deleted
        eventRepository.getEventById(id)?.let { id to ExistingImport.Live(it) }
    }.toMap()

    /** Carries out [plan]. A failed write is logged and counted, never thrown. */
    suspend fun apply(plan: SchoolImportPlan): SchoolImportCounts {
        val failed = mutableSetOf<String>()
        plan.creates.forEach { event ->
            eventUseCases.createEvent(event, announce = false).onFailure { cause ->
                Log.w(TAG, "An imported school event could not be created", cause)
                failed += event.id
            }
        }
        val updated = plan.updates.count { event ->
            eventUseCases.updateEvent(event, announce = false).onFailure { cause ->
                Log.w(TAG, "An imported school event could not be updated", cause)
            }.isSuccess
        }
        val deleted = plan.deletes.count { event ->
            eventUseCases.deleteEvent(event, announce = false).onFailure { cause ->
                Log.w(TAG, "A school-hours event could not be deleted", cause)
            }.isSuccess
        }
        return SchoolImportCounts(plan.creates.size - failed.size, updated, deleted, failed)
    }

    private companion object {
        const val TAG = "SchoolEventWriter"
    }
}
