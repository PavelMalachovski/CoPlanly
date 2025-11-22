package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Room entity for allergies table.
 * Stores allergy information in the local database.
 */
@Entity(tableName = "allergies")
data class AllergyEntity(
    @PrimaryKey
    val id: String,
    val childId: String,
    val allergen: String,
    val severity: String, // AllergySeverity enum as string
    val symptoms: String,
    val firstReactionDate: LocalDate?,
    val treatment: String?,
    val emergencyContactsJson: String, // JSON array of phone numbers
    val notes: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val createdByFirebaseUid: String?,
    val syncedToFirestore: Boolean = false
)
