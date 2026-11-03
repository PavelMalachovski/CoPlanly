package com.coparently.app.wire

import com.coparently.app.data.repository.toChildInfo
import com.coparently.app.data.repository.toDomain
import com.coparently.app.data.repository.toEntity
import com.coparently.app.data.repository.toFirestoreMap
import com.coparently.app.data.sync.Tombstone
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.events.EventTimestamp
import com.coparently.app.domain.guests.GuestGrant
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.time.IsoText
import kotlinx.datetime.LocalDateTime

/**
 * `child_info`: read by `toChildInfo` (the pull), stored through Room (`toEntity`/`toDomain`, JSON
 * columns included) and written back by `toFirestoreMap` — a `set()`, so a newer build's key is at
 * the mercy of this one. The four are `ChildInfoRepositoryImpl`'s mappers, top-level in
 * `:shared-data` since MON-24 phase 2 (Task 8e); the contract stays in the app's list, since
 * `SyncService` writes `child_info` too.
 */
@OptIn(InternalSharedApi::class)
internal object ChildInfoWireContract : WireContract {

    override val collection = "child_info"

    override val alwaysWrites = setOf("id", "childName", "createdAt", "updatedAt", "sharedWith", "familyId")

    override val blankMeansAbsent = setOf("familyId")

    override fun read(document: Map<String, Any?>): Map<String, Any?>? {
        if (Tombstone.isDeleted(document)) return mapOf("id" to document["id"], "deleted" to true)
        val child = runCatching { document.toChildInfo() }.getOrNull() ?: return null
        return mapOf(
            "id" to child.id,
            "childName" to child.childName,
            "dateOfBirth" to child.dateOfBirth?.let(IsoText::dateTime),
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
        return document.toChildInfo().toEntity().toDomain().toFirestoreMap(audience)
    }

    override fun currentWrites(): List<CurrentWrite> = listOf(
        CurrentWrite(
            case = "repository-save",
            about = "ChildInfoRepositoryImpl.upsertChildInfo's document: medical profile, a guest, a school.",
            document = child().toFirestoreMap(listOf("uidA", "uidB", "guest-1"))
        )
    )

    private fun child() = ChildInfo(
        id = "child-current-1",
        childName = "Anya",
        dateOfBirth = LocalDateTime(2019, 4, 2, 0, 0),
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
        createdAt = LocalDateTime(2026, 5, 1, 8, 0),
        updatedAt = LocalDateTime(2026, 5, 2, 18, 45, 10),
        createdByFirebaseUid = "uidA",
        lastModifiedBy = "uidA",
        familyId = "uidA__uidB"
    )
}
