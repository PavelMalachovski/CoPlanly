package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.AllergyDao
import com.coparently.app.data.local.dao.MedicalRecordDao
import com.coparently.app.data.local.entity.AllergyEntity
import com.coparently.app.data.local.entity.MedicalRecordEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreMedicalDataSource
import com.coparently.app.domain.model.Allergy
import com.coparently.app.domain.model.AllergySeverity
import com.coparently.app.domain.model.MedicalMedication
import com.coparently.app.domain.model.MedicalRecord
import com.coparently.app.domain.model.MedicalRecordType
import com.coparently.app.domain.repository.MedicalRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [MedicalRepository].
 * Coordinates between local database and Firestore for medical data.
 */
@Singleton
class MedicalRepositoryImpl @Inject constructor(
    private val medicalRecordDao: MedicalRecordDao,
    private val allergyDao: AllergyDao,
    private val firebaseAuthService: FirebaseAuthService,
    private val firestoreMedicalDataSource: FirestoreMedicalDataSource
) : MedicalRepository {

    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // Medical Records

    override fun getMedicalRecordsForChild(childId: String): Flow<List<MedicalRecord>> {
        return medicalRecordDao.getMedicalRecordsForChild(childId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMedicalRecordsForPeriod(
        childId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<MedicalRecord>> {
        return medicalRecordDao.getMedicalRecordsForPeriod(childId, startDate, endDate).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMedicalRecordById(id: String): MedicalRecord? {
        return medicalRecordDao.getMedicalRecordById(id)?.toDomain()
    }

    override suspend fun upsertMedicalRecord(medicalRecord: MedicalRecord) {
        val entity = medicalRecord.toEntity()
        medicalRecordDao.insertMedicalRecord(entity)

        // Sync to Firestore if authenticated
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            val data = medicalRecord.toFirestoreMap()
            firestoreMedicalDataSource.upsertMedicalRecord(medicalRecord.id, data)

            // Mark as synced
            val syncedEntity = entity.copy(syncedToFirestore = true)
            medicalRecordDao.updateMedicalRecord(syncedEntity)
        }
    }

    override suspend fun deleteMedicalRecord(medicalRecord: MedicalRecord) {
        medicalRecordDao.deleteMedicalRecordById(medicalRecord.id)

        // Delete from Firestore if authenticated
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            firestoreMedicalDataSource.deleteMedicalRecord(medicalRecord.id)
        }
    }

    // Allergies

    override fun getAllergiesForChild(childId: String): Flow<List<Allergy>> {
        return allergyDao.getAllergiesForChild(childId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllergyById(id: String): Allergy? {
        return allergyDao.getAllergyById(id)?.toDomain()
    }

    override suspend fun upsertAllergy(allergy: Allergy) {
        val entity = allergy.toEntity()
        allergyDao.insertAllergy(entity)

        // Sync to Firestore if authenticated
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            val data = allergy.toFirestoreMap()
            firestoreMedicalDataSource.upsertAllergy(allergy.id, data)

            // Mark as synced
            val syncedEntity = entity.copy(syncedToFirestore = true)
            allergyDao.updateAllergy(syncedEntity)
        }
    }

    override suspend fun deleteAllergy(allergy: Allergy) {
        allergyDao.deleteAllergyById(allergy.id)

        // Delete from Firestore if authenticated
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            firestoreMedicalDataSource.deleteAllergy(allergy.id)
        }
    }

    override suspend fun syncWithFirestore() {
        val firebaseUser = firebaseAuthService.getCurrentUser() ?: return

        // Sync medical records
        val unsyncedRecords = medicalRecordDao.getUnsyncedMedicalRecords()
        for (entity in unsyncedRecords) {
            val record = entity.toDomain()
            val data = record.toFirestoreMap()
            val result = firestoreMedicalDataSource.upsertMedicalRecord(entity.id, data)

            if (result.isSuccess) {
                medicalRecordDao.markAsSynced(entity.id)
            }
        }

        // Sync allergies
        val unsyncedAllergies = allergyDao.getUnsyncedAllergies()
        for (entity in unsyncedAllergies) {
            val allergy = entity.toDomain()
            val data = allergy.toFirestoreMap()
            val result = firestoreMedicalDataSource.upsertAllergy(entity.id, data)

            if (result.isSuccess) {
                allergyDao.markAsSynced(entity.id)
            }
        }
    }

    // Conversion methods - MedicalRecord

    private fun MedicalRecordEntity.toDomain(): MedicalRecord {
        return MedicalRecord(
            id = id,
            childId = childId,
            recordType = MedicalRecordType.valueOf(recordType),
            title = title,
            description = description,
            date = date,
            doctorName = doctorName,
            clinicName = clinicName,
            diagnosis = diagnosis,
            treatment = treatment,
            medications = gson.fromJson(medicationsJson, Array<MedicalMedication>::class.java).toList(),
            attachments = gson.fromJson(attachmentsJson, Array<String>::class.java).toList(),
            followUpDate = followUpDate,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun MedicalRecord.toEntity(): MedicalRecordEntity {
        return MedicalRecordEntity(
            id = id,
            childId = childId,
            recordType = recordType.name,
            title = title,
            description = description,
            date = date,
            doctorName = doctorName,
            clinicName = clinicName,
            diagnosis = diagnosis,
            treatment = treatment,
            medicationsJson = gson.toJson(medications),
            attachmentsJson = gson.toJson(attachments),
            followUpDate = followUpDate,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun MedicalRecord.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "childId" to childId,
            "recordType" to recordType.name,
            "title" to title,
            "description" to description,
            "date" to date.format(dateFormatter),
            "doctorName" to doctorName,
            "clinicName" to clinicName,
            "diagnosis" to diagnosis,
            "treatment" to treatment,
            "medications" to medications.map { mapOf(
                "name" to it.name,
                "dosage" to it.dosage,
                "frequency" to it.frequency,
                "startDate" to it.startDate.format(dateFormatter),
                "endDate" to it.endDate?.format(dateFormatter),
                "notes" to it.notes
            )},
            "attachments" to attachments,
            "followUpDate" to followUpDate?.format(dateFormatter),
            "notes" to notes,
            "createdAt" to createdAt.format(dateTimeFormatter),
            "updatedAt" to updatedAt.format(dateTimeFormatter),
            "createdByFirebaseUid" to createdByFirebaseUid
        )
    }

    // Conversion methods - Allergy

    private fun AllergyEntity.toDomain(): Allergy {
        return Allergy(
            id = id,
            childId = childId,
            allergen = allergen,
            severity = AllergySeverity.valueOf(severity),
            symptoms = symptoms,
            firstReactionDate = firstReactionDate,
            treatment = treatment,
            emergencyContacts = gson.fromJson(emergencyContactsJson, Array<String>::class.java).toList(),
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun Allergy.toEntity(): AllergyEntity {
        return AllergyEntity(
            id = id,
            childId = childId,
            allergen = allergen,
            severity = severity.name,
            symptoms = symptoms,
            firstReactionDate = firstReactionDate,
            treatment = treatment,
            emergencyContactsJson = gson.toJson(emergencyContacts),
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun Allergy.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "childId" to childId,
            "allergen" to allergen,
            "severity" to severity.name,
            "symptoms" to symptoms,
            "firstReactionDate" to firstReactionDate?.format(dateFormatter),
            "treatment" to treatment,
            "emergencyContacts" to emergencyContacts,
            "notes" to notes,
            "createdAt" to createdAt.format(dateTimeFormatter),
            "updatedAt" to updatedAt.format(dateTimeFormatter),
            "createdByFirebaseUid" to createdByFirebaseUid
        )
    }
}
