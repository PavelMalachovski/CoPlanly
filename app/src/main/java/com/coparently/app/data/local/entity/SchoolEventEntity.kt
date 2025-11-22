package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Room entity for school events table.
 */
@Entity(tableName = "school_events")
data class SchoolEventEntity(
    @PrimaryKey
    val id: String,
    val childId: String,
    val title: String,
    val description: String?,
    val eventType: String, // SchoolEventType enum as string
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime?,
    val location: String?,
    val teacher: String?,
    val isRequired: Boolean,
    val rsvpRequired: Boolean,
    val rsvpStatus: String?, // RSVPStatus enum as string
    val notes: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val createdByFirebaseUid: String?,
    val syncedToFirestore: Boolean = false
)
