package com.coparently.app.domain.repository

import com.coparently.app.domain.model.Grade
import com.coparently.app.domain.model.SchoolEvent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Repository interface for managing education-related data.
 */
interface EducationRepository {

    // Grades
    fun getGradesForChild(childId: String): Flow<List<Grade>>
    fun getGradesForSubject(childId: String, subject: String): Flow<List<Grade>>
    fun getGradesForPeriod(periodId: String): Flow<List<Grade>>
    suspend fun getGradeById(id: String): Grade?
    suspend fun upsertGrade(grade: Grade)
    suspend fun deleteGrade(grade: Grade)

    // School Events
    fun getSchoolEventsForChild(childId: String): Flow<List<SchoolEvent>>
    fun getUpcomingSchoolEvents(childId: String): Flow<List<SchoolEvent>>
    fun getPastSchoolEvents(childId: String): Flow<List<SchoolEvent>>
    suspend fun getSchoolEventById(id: String): SchoolEvent?
    suspend fun upsertSchoolEvent(event: SchoolEvent)
    suspend fun deleteSchoolEvent(event: SchoolEvent)
    suspend fun updateRSVPStatus(eventId: String, status: com.coparently.app.domain.model.RSVPStatus)

    // Sync
    suspend fun syncWithFirestore()
}
