package com.coparently.app.r8probe

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.model.Activity
import com.coparently.app.domain.model.BloodType
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.Conversation
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.Pet
import com.coparently.app.domain.model.PetSpecies
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.presentation.event.EventDraft
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The values the probe writes: every field set, so every key has to appear in the JSON.
 *
 * A field left at its default would be a field the probe cannot see renamed — Gson omits a null,
 * and an empty list proves nothing about the element type. Dates are fixed so a failure reads the
 * same on every run. ASCII only, so `am instrument`'s output survives any shell encoding.
 */
@Suppress("MagicNumber")
internal object ProbeFixtures {

    private val created = LocalDateTime.of(2026, 5, 4, 9, 30)
    private val updated = LocalDateTime.of(2026, 5, 4, 9, 45)

    val medication = Medication(
        name = "Ibuprofen",
        dosage = "100 mg",
        frequency = "as needed",
        notes = "after food"
    )

    val vaccination = Vaccination(name = "MMR", date = LocalDate.of(2019, 5, 1))

    /** What the probe expects [vaccination]'s date to be written as (`LocalDateJsonAdapter`). */
    const val VACCINATION_DATE = "2019-05-01"

    val medicalProfile = MedicalProfile(
        bloodType = BloodType.A_POSITIVE,
        intolerances = listOf("lactose"),
        hereditaryConditions = listOf("asthma"),
        vaccinations = listOf(vaccination)
    )

    val child = ChildInfo(
        id = "r8-probe-child",
        childName = "Ema",
        dateOfBirth = LocalDateTime.of(2018, 3, 14, 0, 0),
        medications = listOf(medication),
        activities = listOf(
            Activity(
                name = "Swimming",
                schedule = "Tue 16:00",
                location = "City pool",
                contactPerson = "Coach Novak",
                contactPhone = "+420600000001"
            )
        ),
        allergies = listOf("penicillin", "peanuts"),
        medicalNotes = "Inhaler in the school bag",
        emergencyContacts = listOf(
            EmergencyContact(
                name = "Grandma",
                relationship = "grandmother",
                phone = "+420600000002",
                alternatePhone = "+420600000003"
            )
        ),
        schoolInfo = SchoolInfo(
            name = "Primary School Example",
            address = "Main Street 1",
            phone = "+420600000004",
            teacherName = "Mrs Svoboda",
            teacherEmail = "teacher@example.org",
            grade = "3"
        ),
        medicalProfile = medicalProfile,
        createdAt = created,
        updatedAt = updated
    )

    val pet = Pet(
        id = "r8-probe-pet",
        name = "Rex",
        species = PetSpecies.DOG,
        breed = "Beagle",
        medications = listOf(medication),
        vaccinations = listOf(Vaccination(name = "Rabies", date = LocalDate.of(2025, 9, 1))),
        createdAt = created,
        updatedAt = updated
    )

    /** The day key [dayOverride] is stored under, as `DayOverrideJson`'s map carries it. */
    const val DAY_OVERRIDE_DATE = "2026-05-09"

    val dayOverride = DayOverride(
        toParent = "dad",
        requestedBy = "uid-a",
        requestedAt = "2026-05-01T10:00:00",
        status = DayOverrideStatus.ACCEPTED,
        decidedBy = "uid-b",
        decidedAt = "2026-05-02T08:00:00",
        note = "Swap for the school trip",
        groupId = "group-1"
    )

    val eventDraft = EventDraft(
        title = "Dentist",
        description = "Bring the insurance card",
        parentOwner = "mom",
        eventType = "APPOINTMENT",
        startDate = "2026-05-12",
        startTime = "14:30",
        endTime = "15:15"
    )

    val conversation = Conversation(
        id = "uid-a_uid-b",
        participants = listOf("uid-a", "uid-b"),
        title = "Chat",
        lastReadAt = mapOf("uid-a" to 1_777_000_000_000L),
        lastDeliveredAt = mapOf("uid-b" to 1_777_000_100_000L),
        lastMessageAtMillis = 1_777_000_200_000L,
        createdAt = created
    )

    val message = Message(
        id = "r8-probe-message",
        conversationId = "uid-a_uid-b",
        senderId = "uid-a",
        senderName = "Alice",
        content = "See you at five",
        sentAtMillis = 1_777_000_200_000L,
        attachments = listOf("att1|chat_attachments/c/m/a.pdf|application/pdf|10|abc|a.pdf"),
        replyToMessageId = "r8-probe-earlier"
    )

    val eventSnapshot: Map<String, Any?> = mapOf(
        "title" to "Dentist",
        "reminderMinutes" to 15L,
        "sharedWith" to listOf("uid-a", "uid-b"),
        "isPrivate" to false
    )

    /** A Google Calendar API page, as `Calendar.Events.list` returns it (the `@Key` models). */
    val calendarPage = """
        {"items":[{"id":"r8-probe-event","summary":"Swimming",
        "start":{"dateTime":"2026-05-04T16:00:00.000+02:00"},
        "end":{"dateTime":"2026-05-04T17:00:00.000+02:00"}}]}
    """.trimIndent()

    /** [calendarPage]'s start, as `DateTime.toStringRfc3339` prints it back. */
    const val CALENDAR_START = "2026-05-04T16:00:00.000+02:00"
}
