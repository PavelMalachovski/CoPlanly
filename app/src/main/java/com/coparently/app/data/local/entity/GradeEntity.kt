package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Room entity for grades table.
 */
@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey
    val id: String,
    val childId: String,
    val subject: String,
    val grade: String,
    val gradeScale: String, // GradeScale enum as string
    val date: LocalDate,
    val teacher: String?,
    val assignment: String?,
    val comments: String?,
    val weight: Double,
    val academicPeriodId: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val createdByFirebaseUid: String?,
    val syncedToFirestore: Boolean = false
)
