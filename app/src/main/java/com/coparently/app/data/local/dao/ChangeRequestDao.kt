package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.coparently.app.data.local.entity.ChangeRequestEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for event change requests.
 */
@Dao
interface ChangeRequestDao {

    /**
     * All change requests, newest first.
     */
    @Query("SELECT * FROM change_requests ORDER BY createdAt DESC")
    fun getAllChangeRequests(): Flow<List<ChangeRequestEntity>>

    /**
     * Number of pending requests addressed to [userId] — drives the inbox badge.
     */
    @Query("SELECT COUNT(*) FROM change_requests WHERE status = 'PENDING' AND requestedTo = :userId")
    fun getPendingIncomingCount(userId: String): Flow<Int>

    /**
     * Change requests attached to a specific event, newest first.
     */
    @Query("SELECT * FROM change_requests WHERE eventId = :eventId ORDER BY createdAt DESC")
    fun getChangeRequestsForEvent(eventId: String): Flow<List<ChangeRequestEntity>>

    @Query("SELECT * FROM change_requests WHERE id = :id")
    suspend fun getChangeRequestById(id: String): ChangeRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChangeRequest(changeRequest: ChangeRequestEntity)

    @Query("DELETE FROM change_requests WHERE id = :id")
    suspend fun deleteChangeRequest(id: String)
}
