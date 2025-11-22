package com.coparently.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model representing an academic grade for a child.
 *
 * @property id Unique identifier for the grade
 * @property childId ID of the child this grade belongs to
 * @property subject Subject name (e.g., "Mathematics", "English")
 * @property grade Grade value (e.g., "A", "95", "4.0")
 * @property gradeScale Scale used for the grade
 * @property date Date the grade was received
 * @property teacher Teacher name
 * @property assignment Assignment or test name
 * @property comments Teacher comments
 * @property weight Weight for weighted average calculation
 * @property academicPeriodId ID of the academic period this grade belongs to
 * @property createdAt Timestamp when the grade was recorded
 * @property updatedAt Timestamp when the grade was last updated
 * @property createdByFirebaseUid Firebase UID of the user who created this record
 * @property syncedToFirestore Whether the grade has been synced to Firestore
 */
data class Grade(
    val id: String,
    val childId: String,
    val subject: String,
    val grade: String,
    val gradeScale: GradeScale,
    val date: LocalDate,
    val teacher: String? = null,
    val assignment: String? = null,
    val comments: String? = null,
    val weight: Double = 1.0,
    val academicPeriodId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdByFirebaseUid: String? = null,
    val syncedToFirestore: Boolean = false
)

/**
 * Grading scales used in different educational systems.
 */
enum class GradeScale {
    LETTER,      // A, B, C, D, F
    PERCENTAGE,  // 0-100
    GPA_4,       // 0.0-4.0
    GPA_5        // 0.0-5.0
}

/**
 * Domain model representing an academic period (semester, quarter, etc.).
 *
 * @property id Unique identifier for the academic period
 * @property childId ID of the child this period belongs to
 * @property name Name of the period (e.g., "Fall 2024", "Q1 2024")
 * @property startDate Start date of the period
 * @property endDate End date of the period
 * @property isCurrent Whether this is the current active period
 * @property createdAt Timestamp when the period was created
 * @property syncedToFirestore Whether the period has been synced to Firestore
 */
data class AcademicPeriod(
    val id: String,
    val childId: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isCurrent: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val syncedToFirestore: Boolean = false
)
