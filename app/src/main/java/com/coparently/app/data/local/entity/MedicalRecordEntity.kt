package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Room entity for medical records table.
 * Stores medical records in the local database.
 */
@Entity(tableName = "medical_records")
data class MedicalRecordEntity(
    @PrimaryKey
    val id: String,
    val childId: String,
    val recordType: String, // MedicalRecordType enum as string
    val title: String,
    val description: String?,
    val date: LocalDate,
    val doctorName: String?,
    val clinicName: String?,
    val diagnosis: String?,
    val treatment: String?,
    val medicationsJson: String, // JSON array of MedicalMedication objects
    val attachmentsJson: String, // JSON array of attachment URLs
    val followUpDate: LocalDate?,
    val notes: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val createdByFirebaseUid: String?,
    val syncedToFirestore: Boolean = false
)
