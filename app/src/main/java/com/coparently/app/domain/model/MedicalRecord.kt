package com.coparently.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model representing a medical record for a child.
 * Stores information about doctor visits, vaccinations, illnesses, and treatments.
 *
 * @property id Unique identifier for the medical record
 * @property childId ID of the child this record belongs to
 * @property recordType Type of medical record
 * @property title Brief title of the medical record
 * @property description Detailed description
 * @property date Date of the medical event
 * @property doctorName Name of the doctor
 * @property clinicName Name of the clinic or hospital
 * @property diagnosis Medical diagnosis
 * @property treatment Treatment prescribed or administered
 * @property medications List of medications prescribed
 * @property attachments List of attachment URLs (photos, documents)
 * @property followUpDate Date for follow-up appointment
 * @property notes Additional notes
 * @property createdAt Timestamp when the record was created
 * @property updatedAt Timestamp when the record was last updated
 * @property createdByFirebaseUid Firebase UID of the user who created this record
 * @property syncedToFirestore Whether the record has been synced to Firestore
 */
data class MedicalRecord(
    val id: String,
    val childId: String,
    val recordType: MedicalRecordType,
    val title: String,
    val description: String? = null,
    val date: LocalDate,
    val doctorName: String? = null,
    val clinicName: String? = null,
    val diagnosis: String? = null,
    val treatment: String? = null,
    val medications: List<MedicalMedication> = emptyList(),
    val attachments: List<String> = emptyList(),
    val followUpDate: LocalDate? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdByFirebaseUid: String? = null,
    val syncedToFirestore: Boolean = false
)

/**
 * Types of medical records.
 */
enum class MedicalRecordType {
    VISIT,
    VACCINATION,
    ILLNESS,
    ALLERGY_UPDATE,
    MEDICATION_CHANGE,
    EMERGENCY,
    CHECKUP
}

/**
 * Enhanced medication model for medical records with date tracking.
 * This extends the basic Medication model from ChildInfo.
 *
 * @property name Name of the medication
 * @property dosage Dosage information
 * @property frequency How often to take
 * @property startDate Date when medication started
 * @property endDate Date when medication ends (null if ongoing)
 * @property notes Additional notes about the medication
 */
data class MedicalMedication(
    val name: String,
    val dosage: String,
    val frequency: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val notes: String? = null
)
