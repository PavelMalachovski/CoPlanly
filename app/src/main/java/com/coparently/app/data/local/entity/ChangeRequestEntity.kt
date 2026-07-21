package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing an event change request in the local Room database.
 */
@Entity(
    tableName = "change_requests",
    indices = [Index("eventId"), Index("status")]
)
data class ChangeRequestEntity(
    @PrimaryKey
    val id: String,
    val eventId: String,
    val eventTitle: String,
    val requestedBy: String,
    val requestedTo: String,
    val currentStartDateTime: LocalDateTime,
    val currentEndDateTime: LocalDateTime? = null,
    val proposedStartDateTime: LocalDateTime,
    val proposedEndDateTime: LocalDateTime? = null,
    val note: String? = null,
    val status: String, // ChangeRequestStatus enum name
    val createdAt: LocalDateTime,
    val respondedAt: LocalDateTime? = null,
    val syncedToFirestore: Boolean = false
)
