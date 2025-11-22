package com.coparently.app.data.repository

import com.coparently.app.data.local.dao.GradeDao
import com.coparently.app.data.local.dao.SchoolEventDao
import com.coparently.app.data.local.entity.GradeEntity
import com.coparently.app.data.local.entity.SchoolEventEntity
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreEducationDataSource
import com.coparently.app.domain.model.Grade
import com.coparently.app.domain.model.GradeScale
import com.coparently.app.domain.model.RSVPStatus
import com.coparently.app.domain.model.SchoolEvent
import com.coparently.app.domain.model.SchoolEventType
import com.coparently.app.domain.repository.EducationRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [EducationRepository].
 */
@Singleton
class EducationRepositoryImpl @Inject constructor(
    private val gradeDao: GradeDao,
    private val schoolEventDao: SchoolEventDao,
    private val firebaseAuthService: FirebaseAuthService,
    private val firestoreEducationDataSource: FirestoreEducationDataSource
) : EducationRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // Grades

    override fun getGradesForChild(childId: String): Flow<List<Grade>> {
        return gradeDao.getGradesForChild(childId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getGradesForSubject(childId: String, subject: String): Flow<List<Grade>> {
        return gradeDao.getGradesForSubject(childId, subject).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getGradesForPeriod(periodId: String): Flow<List<Grade>> {
        return gradeDao.getGradesForPeriod(periodId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getGradeById(id: String): Grade? {
        return gradeDao.getGradeById(id)?.toDomain()
    }

    override suspend fun upsertGrade(grade: Grade) {
        val entity = grade.toEntity()
        gradeDao.insertGrade(entity)

        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            val data = grade.toFirestoreMap()
            firestoreEducationDataSource.upsertGrade(grade.id, data)
            val syncedEntity = entity.copy(syncedToFirestore = true)
            gradeDao.updateGrade(syncedEntity)
        }
    }

    override suspend fun deleteGrade(grade: Grade) {
        gradeDao.deleteGradeById(grade.id)
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            firestoreEducationDataSource.deleteGrade(grade.id)
        }
    }

    // School Events

    override fun getSchoolEventsForChild(childId: String): Flow<List<SchoolEvent>> {
        return schoolEventDao.getSchoolEventsForChild(childId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUpcomingSchoolEvents(childId: String): Flow<List<SchoolEvent>> {
        return schoolEventDao.getUpcomingSchoolEvents(childId, LocalDateTime.now()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getPastSchoolEvents(childId: String): Flow<List<SchoolEvent>> {
        return schoolEventDao.getPastSchoolEvents(childId, LocalDateTime.now()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSchoolEventById(id: String): SchoolEvent? {
        return schoolEventDao.getSchoolEventById(id)?.toDomain()
    }

    override suspend fun upsertSchoolEvent(event: SchoolEvent) {
        val entity = event.toEntity()
        schoolEventDao.insertSchoolEvent(entity)

        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            val data = event.toFirestoreMap()
            firestoreEducationDataSource.upsertSchoolEvent(event.id, data)
            val syncedEntity = entity.copy(syncedToFirestore = true)
            schoolEventDao.updateSchoolEvent(syncedEntity)
        }
    }

    override suspend fun deleteSchoolEvent(event: SchoolEvent) {
        schoolEventDao.deleteSchoolEventById(event.id)
        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            firestoreEducationDataSource.deleteSchoolEvent(event.id)
        }
    }

    override suspend fun updateRSVPStatus(eventId: String, status: RSVPStatus) {
        val event = schoolEventDao.getSchoolEventById(eventId) ?: return
        val updatedEvent = event.copy(rsvpStatus = status.name)
        schoolEventDao.updateSchoolEvent(updatedEvent)

        val firebaseUser = firebaseAuthService.getCurrentUser()
        if (firebaseUser != null) {
            val data = updatedEvent.toDomain().toFirestoreMap()
            firestoreEducationDataSource.upsertSchoolEvent(eventId, data)
        }
    }

    override suspend fun syncWithFirestore() {
        val firebaseUser = firebaseAuthService.getCurrentUser() ?: return

        // Sync grades
        val unsyncedGrades = gradeDao.getUnsyncedGrades()
        for (entity in unsyncedGrades) {
            val grade = entity.toDomain()
            val data = grade.toFirestoreMap()
            val result = firestoreEducationDataSource.upsertGrade(entity.id, data)
            if (result.isSuccess) {
                gradeDao.markAsSynced(entity.id)
            }
        }

        // Sync school events
        val unsyncedEvents = schoolEventDao.getUnsyncedSchoolEvents()
        for (entity in unsyncedEvents) {
            val event = entity.toDomain()
            val data = event.toFirestoreMap()
            val result = firestoreEducationDataSource.upsertSchoolEvent(entity.id, data)
            if (result.isSuccess) {
                schoolEventDao.markAsSynced(entity.id)
            }
        }
    }

    // Conversion methods - Grade

    private fun GradeEntity.toDomain(): Grade {
        return Grade(
            id = id,
            childId = childId,
            subject = subject,
            grade = grade,
            gradeScale = GradeScale.valueOf(gradeScale),
            date = date,
            teacher = teacher,
            assignment = assignment,
            comments = comments,
            weight = weight,
            academicPeriodId = academicPeriodId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun Grade.toEntity(): GradeEntity {
        return GradeEntity(
            id = id,
            childId = childId,
            subject = subject,
            grade = grade,
            gradeScale = gradeScale.name,
            date = date,
            teacher = teacher,
            assignment = assignment,
            comments = comments,
            weight = weight,
            academicPeriodId = academicPeriodId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun Grade.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "childId" to childId,
            "subject" to subject,
            "grade" to grade,
            "gradeScale" to gradeScale.name,
            "date" to date.format(dateFormatter),
            "teacher" to teacher,
            "assignment" to assignment,
            "comments" to comments,
            "weight" to weight,
            "academicPeriodId" to academicPeriodId,
            "createdAt" to createdAt.format(dateTimeFormatter),
            "updatedAt" to updatedAt.format(dateTimeFormatter),
            "createdByFirebaseUid" to createdByFirebaseUid
        )
    }

    // Conversion methods - SchoolEvent

    private fun SchoolEventEntity.toDomain(): SchoolEvent {
        return SchoolEvent(
            id = id,
            childId = childId,
            title = title,
            description = description,
            eventType = SchoolEventType.valueOf(eventType),
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            location = location,
            teacher = teacher,
            isRequired = isRequired,
            rsvpRequired = rsvpRequired,
            rsvpStatus = rsvpStatus?.let { RSVPStatus.valueOf(it) },
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun SchoolEvent.toEntity(): SchoolEventEntity {
        return SchoolEventEntity(
            id = id,
            childId = childId,
            title = title,
            description = description,
            eventType = eventType.name,
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            location = location,
            teacher = teacher,
            isRequired = isRequired,
            rsvpRequired = rsvpRequired,
            rsvpStatus = rsvpStatus?.name,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            createdByFirebaseUid = createdByFirebaseUid,
            syncedToFirestore = syncedToFirestore
        )
    }

    private fun SchoolEvent.toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "childId" to childId,
            "title" to title,
            "description" to description,
            "eventType" to eventType.name,
            "startDateTime" to startDateTime.format(dateTimeFormatter),
            "endDateTime" to endDateTime?.format(dateTimeFormatter),
            "location" to location,
            "teacher" to teacher,
            "isRequired" to isRequired,
            "rsvpRequired" to rsvpRequired,
            "rsvpStatus" to rsvpStatus?.name,
            "notes" to notes,
            "createdAt" to createdAt.format(dateTimeFormatter),
            "updatedAt" to updatedAt.format(dateTimeFormatter),
            "createdByFirebaseUid" to createdByFirebaseUid
        )
    }
}
