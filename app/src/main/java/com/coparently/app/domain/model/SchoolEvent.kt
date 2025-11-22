package com.coparently.app.domain.model

import java.time.LocalDateTime

/**
 * Domain model representing a school event.
 *
 * @property id Unique identifier for the school event
 * @property childId ID of the child this event belongs to
 * @property title Event title
 * @property description Event description
 * @property eventType Type of school event
 * @property startDateTime Start date and time
 * @property endDateTime End date and time
 * @property location Event location
 * @property teacher Teacher or organizer name
 * @property isRequired Whether attendance is required
 * @property rsvpRequired Whether RSVP is required
 * @property rsvpStatus RSVP status
 * @property notes Additional notes
 * @property createdAt Timestamp when the event was created
 * @property updatedAt Timestamp when the event was last updated
 * @property createdByFirebaseUid Firebase UID of the user who created this event
 * @property syncedToFirestore Whether the event has been synced to Firestore
 */
data class SchoolEvent(
    val id: String,
    val childId: String,
    val title: String,
    val description: String? = null,
    val eventType: SchoolEventType,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime? = null,
    val location: String? = null,
    val teacher: String? = null,
    val isRequired: Boolean = false,
    val rsvpRequired: Boolean = false,
    val rsvpStatus: RSVPStatus? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val createdByFirebaseUid: String? = null,
    val syncedToFirestore: Boolean = false
)

/**
 * Types of school events.
 */
enum class SchoolEventType {
    PARENT_TEACHER_CONFERENCE,
    SCHOOL_PLAY,
    FIELD_TRIP,
    SPORTS_EVENT,
    SCHOOL_BOARD_MEETING,
    HOLIDAY_PROGRAM,
    CLASS_PARTY,
    OTHER
}

/**
 * RSVP status for school events.
 */
enum class RSVPStatus {
    ATTENDING,
    NOT_ATTENDING,
    MAYBE,
    PENDING
}
