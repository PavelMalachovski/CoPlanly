package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.coparently.app.data.local.entity.KnownFamilyEntity
import kotlinx.coroutines.flow.Flow

/** The families this phone has seen ([KnownFamilyEntity], schema 46). */
@Dao
interface KnownFamilyDao {

    /** Every family this phone has seen, live or ended. */
    @Query("SELECT * FROM known_families")
    fun observeAll(): Flow<List<KnownFamilyEntity>>

    /** One family, or null when this phone never saw it. */
    @Query("SELECT * FROM known_families WHERE familyId = :familyId")
    suspend fun get(familyId: String): KnownFamilyEntity?

    /** One family, followed. */
    @Query("SELECT * FROM known_families WHERE familyId = :familyId")
    fun observe(familyId: String): Flow<KnownFamilyEntity?>

    /** Inserts or replaces [row]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: KnownFamilyEntity)

    /**
     * Writes [familyId]'s co-parent name, creating the row when there is none, and keeping the
     * slots already known.
     */
    @Transaction
    suspend fun rememberName(familyId: String, coParentUid: String, name: String, atMillis: Long) {
        val row = get(familyId)
        upsert(
            row?.copy(coParentUid = coParentUid, coParentName = name, updatedAtMillis = atMillis)
                ?: KnownFamilyEntity(familyId, coParentUid, coParentName = name, updatedAtMillis = atMillis)
        )
    }

    /**
     * Writes [familyId]'s two slots, creating the row when there is none, and keeping the name
     * already known.
     */
    @Transaction
    suspend fun rememberSlots(
        familyId: String,
        coParentUid: String,
        mySlot: String,
        coParentSlot: String,
        atMillis: Long
    ) {
        val row = get(familyId)
        upsert(
            row?.copy(coParentUid = coParentUid, mySlot = mySlot, coParentSlot = coParentSlot, updatedAtMillis = atMillis)
                ?: KnownFamilyEntity(familyId, coParentUid, mySlot = mySlot, coParentSlot = coParentSlot, updatedAtMillis = atMillis)
        )
    }
}
