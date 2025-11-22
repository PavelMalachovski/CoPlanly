package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coparently.app.data.local.entity.SchoolEventEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * DAO for school events table.
 */
@Dao
interface SchoolEventDao {

    @Query("SELECT * FROM school_events WHERE childId = :childId ORDER BY startDateTime ASC")
    fun getSchoolEventsForChild(childId: String): Flow<List<SchoolEventEntity>>

    @Query("""
        SELECT * FROM school_events
        WHERE childId = :childId
        AND startDateTime >= :now
        ORDER BY startDateTime ASC
    """)
    fun getUpcomingSchoolEvents(childId: String, now: LocalDateTime): Flow<List<SchoolEventEntity>>

    @Query("""
        SELECT * FROM school_events
        WHERE childId = :childId
        AND startDateTime < :now
        ORDER BY startDateTime DESC
    """)
    fun getPastSchoolEvents(childId: String, now: LocalDateTime): Flow<List<SchoolEventEntity>>

    @Query("SELECT * FROM school_events WHERE id = :id")
    suspend fun getSchoolEventById(id: String): SchoolEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchoolEvent(event: SchoolEventEntity)

    @Update
    suspend fun updateSchoolEvent(event: SchoolEventEntity)

    @Query("DELETE FROM school_events WHERE id = :id")
    suspend fun deleteSchoolEventById(id: String)

    @Delete
    suspend fun deleteSchoolEvent(event: SchoolEventEntity)

    @Query("SELECT * FROM school_events WHERE syncedToFirestore = 0")
    suspend fun getUnsyncedSchoolEvents(): List<SchoolEventEntity>

    @Query("UPDATE school_events SET syncedToFirestore = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}
