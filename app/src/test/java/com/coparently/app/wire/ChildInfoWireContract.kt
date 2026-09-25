package com.coparently.app.wire

import com.coparently.app.data.repository.ChildInfoRepositoryImpl
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.guests.GuestGrant
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.SchoolInfo
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `child_info`: read by `ChildInfoRepositoryImpl.toChildInfo` (the pull), stored through Room
 * (`toEntity`/`toDomain`, JSON columns included) and written back by `toFirestoreMap` — a
 * `set()`, so a newer build's key is at the mercy of this one.
 */
internal object ChildInfoWireContract : WireContract {

    private val repository by lazy { ChildInfoRepositoryImpl(mockk(), mockk(), mockk(), mockk()) }
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override val collection = "child_info"

    override val alwaysWrites = setOf("id", "childName", "createdAt", "updatedAt", "sharedWith", "familyId")

    override val blankMeansAbsent = setOf("familyId")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return mapOf("id" to document["id"], "deleted" to true)
        val child = runCatching { with(repository) { document.toChildInfo() } }.getOrNull() ?: return null
        return mapOf(
            "id" to child.id,
            "childName" to child.childName,
            "dateOfBirth" to child.dateOfBirth?.format(formatter),
            "allergies" to child.allergies,
            "medications" to child.medications.map { it.name },
            "intolerances" to child.medicalProfile.intolerances,
            "school" to child.schoolInfo?.name,
            "guests" to child.guests.keys.sorted(),
            "updatedAtMillis" to EventTimestamp.ofWallClock(child.updatedAt),
            "createdByFirebaseUid" to child.createdByFirebaseUid,
            "familyId" to child.familyId,
            "deleted" to false
        )
    }

    override fun roundTrip(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return null
        val audience = (document["sharedWith"] as? List<*>).orEmpty().filterIsInstance<String>()
        return with(repository) { document.toChildInfo().toEntity().toDomain().toFirestoreMap(audience) }
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "ChildInfoRepositoryImpl.upsertChildInfo's document: medical profile, a guest, a school.",
            document = with(repository) { child().toFirestoreMap(listOf("uidA", "uidB", "guest-1")) }
        )
    )

    private fun child() = ChildInfo(
        id = "child-current-1",
        childName = "Anya",
        dateOfBirth = LocalDateTime.of(2019, 4, 2, 0, 0),
        medications = listOf(Medication(name = "Ventolin", dosage = "2 puffs", frequency = "as needed")),
        allergies = listOf("peanuts"),
        medicalNotes = "Asthma",
        schoolInfo = SchoolInfo(name = "ZS Vinohrady", grade = "2"),
        medicalProfile = MedicalProfile(intolerances = listOf("lactose")),
        medicalPhotos = listOf(
            "ph1|medical_photos/uidA__uidB/child-current-1/0b6c4a9e.jpg|image/jpeg|48213|" +
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        ),
        guests = mapOf(
            "guest-1" to GuestGrant(
                uid = "guest-1",
                name = "Grandma",
                grantedBy = "uidA",
                grantedAtMillis = 1_778_000_000_000L,
                expiresAtMillis = 1_780_000_000_000L
            )
        ),
        createdAt = LocalDateTime.of(2026, 5, 1, 8, 0),
        updatedAt = LocalDateTime.of(2026, 5, 2, 18, 45, 10),
        createdByFirebaseUid = "uidA",
        lastModifiedBy = "uidA",
        familyId = "uidA__uidB"
    )
}
