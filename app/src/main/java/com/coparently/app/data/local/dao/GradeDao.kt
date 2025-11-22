package com.coparently.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coparently.app.data.local.entity.GradeEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for grades table.
 */
@Dao
interface GradeDao {

    @Query("SELECT * FROM grades WHERE childId = :childId ORDER BY date DESC")
    fun getGradesForChild(childId: String): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades WHERE childId = :childId AND subject = :subject ORDER BY date DESC")
    fun getGradesForSubject(childId: String, subject: String): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades WHERE academicPeriodId = :periodId ORDER BY subject, date DESC")
    fun getGradesForPeriod(periodId: String): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades WHERE id = :id")
    suspend fun getGradeById(id: String): GradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrade(grade: GradeEntity)

    @Update
    suspend fun updateGrade(grade: GradeEntity)

    @Query("DELETE FROM grades WHERE id = :id")
    suspend fun deleteGradeById(id: String)

    @Delete
    suspend fun deleteGrade(grade: GradeEntity)

    @Query("SELECT * FROM grades WHERE syncedToFirestore = 0")
    suspend fun getUnsyncedGrades(): List<GradeEntity>

    @Query("UPDATE grades SET syncedToFirestore = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)
}
