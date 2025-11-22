package com.coparently.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model representing an allergy for a child.
 * Stores critical allergy information with severity levels.
 *
 * @property id Unique identifier for the allergy
 * @property childId ID of the child this allergy belongs to
 * @property allergen Name of the allergen (e.g., "Peanuts", "Penicillin")
 * @property severity Severity level of the allergy
 * @property symptoms Symptoms that occur during allergic reaction
 * @property firstReactionDate Date of first allergic reaction
 * @property treatment Treatment or medication for allergic reactions
 * @property emergencyContacts Emergency contact phone numbers
 * @property notes Additional notes
 * @property createdAt Timestamp when the allergy was recorded
 * @property updatedAt Timestamp when the allergy was last updated
 * @property createdByFirebaseUid Firebase UID of the user who created this record
 * @property syncedToFirestore Whether the allergy has been synced to Firestore
 */
data class Allergy(
    val id: String,
    val childId: String,
    val allergen: String,
    val severity: AllergySeverity,
    val symptoms: String,
    val firstReactionDate: LocalDate? = null,
    val treatment: String? = null,
    val emergencyContacts: List<String> = emptyList(),
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdByFirebaseUid: String? = null,
    val syncedToFirestore: Boolean = false
)

/**
 * Severity levels for allergies.
 * Used for color coding and prioritization in UI.
 */
enum class AllergySeverity {
    MILD,
    MODERATE,
    SEVERE,
    LIFE_THREATENING
}
