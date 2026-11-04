package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.coparently.app.data.local.entity.FamilyDocumentCacheEntity

/**
 * The Room cache of the vault index (MON-23, schema 43). Every query is scoped to one family.
 */
@Dao
interface FamilyDocumentCacheDao {

    /** [familyId]'s cached documents that were live when the server last answered. */
    @Query("SELECT * FROM family_documents_cache WHERE familyId = :familyId AND deletedAtMillis IS NULL")
    suspend fun liveIn(familyId: String): List<FamilyDocumentCacheEntity>

    /** Removes every cached row of [familyId]. */
    @Query("DELETE FROM family_documents_cache WHERE familyId = :familyId")
    suspend fun clearFamily(familyId: String)

    /** Inserts or replaces [rows]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<FamilyDocumentCacheEntity>)

    /**
     * Makes [rows] the whole of [familyId]'s cache, in one transaction.
     *
     * A server snapshot is the complete answer to the vault query, so a row it no longer carries
     * is one this parent may no longer see (an unpair narrows the audience, the 90-day sweep
     * removes a tombstone) and must not be listed from the cache either.
     */
    @Transaction
    suspend fun replaceFamily(familyId: String, rows: List<FamilyDocumentCacheEntity>) {
        clearFamily(familyId)
        upsertAll(rows)
    }
}
