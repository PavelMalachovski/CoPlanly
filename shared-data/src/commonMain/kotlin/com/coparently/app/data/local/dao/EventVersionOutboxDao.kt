package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.coparently.app.data.local.entity.EventVersionOutboxEntity

/**
 * The event revisions this device has recorded and the server does not have yet (MON-4).
 *
 * Every query is scoped to one editor. A device that has been signed into two accounts may hold
 * rows written under the first; the rule refuses a revision whose author is not the caller, so
 * uploading them under the second would only burn their attempts.
 */
@Dao
interface EventVersionOutboxDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: EventVersionOutboxEntity)

    /** Rows still worth sending, oldest first, so a create reaches the server before its edits. */
    @Query(
        "SELECT * FROM event_version_outbox WHERE editorUid = :editorUid AND attempts < :maxAttempts " +
            "ORDER BY deviceTimeMillis ASC"
    )
    suspend fun pending(editorUid: String, maxAttempts: Int): List<EventVersionOutboxEntity>

    /** Everything this editor has not delivered, including rows the server kept refusing. */
    @Query("SELECT * FROM event_version_outbox WHERE editorUid = :editorUid ORDER BY deviceTimeMillis ASC")
    suspend fun undelivered(editorUid: String): List<EventVersionOutboxEntity>

    @Query("DELETE FROM event_version_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE event_version_outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun recordRefusal(id: String)
}
