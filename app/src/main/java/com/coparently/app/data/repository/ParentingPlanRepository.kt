package com.coparently.app.data.repository

import android.util.Log
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.local.dao.ParentingPlanDao
import com.coparently.app.data.local.entity.ParentingPlanEntryEntity
import com.coparently.app.data.remote.firebase.FirestoreParentingPlanDataSource
import com.coparently.app.domain.parentingplan.ParentingPlanEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import javax.inject.Inject
import javax.inject.Singleton

/** Both halves of one family's plan, as a screen needs them. */
data class ParentingPlanPair(
    val yours: ParentingPlanEntry,
    val theirs: ParentingPlanEntry?
)

/**
 * This phone's Room copy of one family's plan.
 *
 * @property halves Each stored half, by author uid.
 * @property unsentAuthors The authors whose half is still in this phone's outbox.
 */
data class ParentingPlanLocalCopy(
    val halves: Map<String, ParentingPlanEntry>,
    val unsentAuthors: Set<String>
)

/**
 * Reads and writes the two halves of a family's parenting plan (MON-5).
 *
 * Room is the source of truth and Firestore is the mirror, the same shape `MessageRepositoryImpl`
 * uses: [observe] merges a remote branch that only *fills* Room with a local branch that is the
 * only one emitting. A failed listener therefore costs the co-parent's updates, not the screen.
 */
@Singleton
class ParentingPlanRepository @Inject constructor(
    private val dao: ParentingPlanDao,
    private val remote: FirestoreParentingPlanDataSource,
    private val gson: Gson,
    private val crashlyticsManager: CrashlyticsManager
) {

    private val textMapType = object : TypeToken<Map<String, String>>() {}.type

    /**
     * Both halves of [familyId]'s plan, live.
     *
     * @param myUid Which of the two rows is the signed-in parent's; the other is the mirror.
     */
    fun observe(familyId: String, myUid: String): Flow<ParentingPlanPair> {
        val mirror = remote.observePlan(familyId)
            .onEach { halves -> adopt(familyId, myUid, halves) }
            .catch { e ->
                // Ending the mirror leaves the local branch running, which is the whole point of
                // Room being the source of truth. An uncaught failure here would instead reach
                // the collector's `viewModelScope.launch` and take the process.
                Log.w(TAG, "Parenting plan mirror ended", e)
                crashlyticsManager.recordException(e)
            }
            .mirrorOnly<ParentingPlanPair>()

        val local = dao.observeEntries(familyId).map { rows ->
            ParentingPlanPair(
                yours = rows.firstOrNull { it.authorUid == myUid }?.toDomain() ?: ParentingPlanEntry(),
                theirs = rows.firstOrNull { it.authorUid != myUid }?.toDomain()
            )
        }

        return merge(mirror, local)
    }

    /**
     * Both halves of [familyId]'s plan as the server holds them, by author uid (MON-3's export).
     *
     * Read once, from the server and never the cache, and not written into Room: the export
     * prints what the server has, and a read made for a document is not a sync. Throws when the
     * server cannot be reached.
     */
    suspend fun serverHalves(familyId: String): Map<String, ParentingPlanEntry> = remote.fetchFromServer(familyId)

    /**
     * This phone's copy of both halves of [familyId]'s plan, and which of them have not been sent.
     *
     * What the export prints when the server cannot be reached — labelled as this phone's copy —
     * and how it knows the signed-in parent has edits the server's copy does not show yet.
     */
    suspend fun localCopy(familyId: String): ParentingPlanLocalCopy {
        val rows = dao.observeEntries(familyId).first()
        return ParentingPlanLocalCopy(
            halves = rows.associate { it.authorUid to it.toDomain() },
            unsentAuthors = rows.filterNot { it.syncedToFirestore }.map { it.authorUid }.toSet()
        )
    }

    /**
     * Records [entry] as this parent's half and tries to send it.
     *
     * Written to Room first and marked unsent, so the answer survives a failed upload and the
     * outbox picks it up on the next pass — the shape `data/sync/Tombstone.kt` establishes for
     * every other table.
     */
    suspend fun save(familyId: String, myUid: String, entry: ParentingPlanEntry) {
        dao.upsert(entry.toEntity(familyId, myUid, synced = false))
        upload(familyId, myUid, entry)
    }

    /**
     * Sends whatever this account has written and not yet uploaded, across every family.
     *
     * Called from the sync pass rather than only from [save], so a half written while offline
     * leaves the device as soon as anything else does.
     */
    suspend fun flushOutbox(myUid: String) {
        dao.getUnsynced(myUid).forEach { row ->
            upload(row.familyId, myUid, row.toDomain())
        }
    }

    /** Uploads one half and marks it sent, or leaves it in the outbox. */
    private suspend fun upload(familyId: String, myUid: String, entry: ParentingPlanEntry) {
        try {
            remote.uploadHalf(familyId, myUid, entry)
            dao.markSynced(familyId, myUid)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Not a failure the parent needs to see: the row stays in the outbox and the next
            // sync sends it. An uncaught one would crash the screen that called `save`.
            Log.w(TAG, "Parenting plan half not uploaded", e)
            crashlyticsManager.recordException(e)
        }
    }

    /** Writes the halves a snapshot carried into Room, subject to [adopts]. */
    private suspend fun adopt(
        familyId: String,
        myUid: String,
        halves: Map<String, ParentingPlanEntry>
    ) {
        halves.forEach { (authorUid, entry) ->
            val local = dao.getEntry(familyId, authorUid)
            if (adopts(local, entry.updatedAtMillis, isMine = authorUid == myUid)) {
                dao.upsert(entry.toEntity(familyId, authorUid, synced = true))
            }
        }
    }

    /**
     * Turns a mirroring flow into one that never emits.
     *
     * The same device `MessageRepositoryImpl` uses: the remote branch is merged in purely for its
     * side effect, so exactly one of the two — Room — decides what the caller sees.
     */
    private fun <T> Flow<*>.mirrorOnly(): Flow<T> = transform { }

    private fun ParentingPlanEntryEntity.toDomain() = ParentingPlanEntry(
        answers = gson.fromJson(answersJson, textMapType) ?: emptyMap(),
        agreedTo = gson.fromJson(agreedToJson, textMapType) ?: emptyMap(),
        catalogueVersion = catalogueVersion,
        updatedAtMillis = updatedAtMillis
    )

    private fun ParentingPlanEntry.toEntity(
        familyId: String,
        authorUid: String,
        synced: Boolean
    ) = ParentingPlanEntryEntity(
        familyId = familyId,
        authorUid = authorUid,
        catalogueVersion = catalogueVersion,
        // Gson over a plain `Map<String, String>`, never over a model class: R8 rewrote a
        // Gson-mapped model's field names once already and it shipped (see `FamilyMemberRef`).
        answersJson = gson.toJson(answers),
        agreedToJson = gson.toJson(agreedTo),
        updatedAtMillis = updatedAtMillis,
        syncedToFirestore = synced
    )

    companion object {
        private const val TAG = "ParentingPlanRepo"

        /**
         * Whether a downloaded half should replace what Room holds.
         *
         * Pure, and separate, because it is the one place a parent's own words can be lost. The
         * co-parent's half is always theirs to say. This parent's own is not: a half edited on
         * this device and not yet sent outranks whatever the server still has, or an answer typed
         * on a train is overwritten by its own older copy the moment the listener reconnects.
         *
         * @param local What Room holds, or null when this author has no row yet.
         * @param remoteUpdatedAtMillis When the downloaded half was last edited.
         * @param isMine Whether the half belongs to the signed-in parent.
         */
        fun adopts(
            local: ParentingPlanEntryEntity?,
            remoteUpdatedAtMillis: Long,
            isMine: Boolean
        ): Boolean = when {
            local == null -> true
            !isMine -> true
            !local.syncedToFirestore -> false
            else -> remoteUpdatedAtMillis > local.updatedAtMillis
        }
    }
}
