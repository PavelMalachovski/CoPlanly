package com.coparently.app.presentation.childinfo

import com.coparently.app.domain.model.Activity
import com.coparently.app.domain.model.ChildInfo
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.SchoolInfo
import java.time.LocalDateTime

/**
 * The values the child form edits, held in `ChildInfoViewModel.childForm` so a rotation keeps
 * them, and compared with what they were seeded with to tell whether Back would drop an edit
 * (D-11). A photo picked or marked for removal counts as an edit.
 */
internal data class ChildFields(
    val childName: String,
    val dateOfBirth: LocalDateTime?,
    val medications: List<Medication>,
    val activities: List<Activity>,
    val allergies: List<String>,
    val medicalNotes: String,
    val emergencyContacts: List<EmergencyContact>,
    val schoolInfo: SchoolInfo?,
    val medicalProfile: MedicalProfile,
    val pickedPhotos: List<String>,
    val removedPhotos: List<String>
) {
    /** A blank form, and the form seeded from a stored record. */
    companion object {
        val EMPTY = ChildFields(
            childName = "",
            dateOfBirth = null,
            medications = emptyList(),
            activities = emptyList(),
            allergies = emptyList(),
            medicalNotes = "",
            emergencyContacts = emptyList(),
            schoolInfo = null,
            medicalProfile = MedicalProfile(),
            pickedPhotos = emptyList(),
            removedPhotos = emptyList()
        )

        fun of(info: ChildInfo) = EMPTY.copy(
            childName = info.childName,
            dateOfBirth = info.dateOfBirth,
            medications = info.medications,
            activities = info.activities,
            allergies = info.allergies,
            medicalNotes = info.medicalNotes ?: "",
            emergencyContacts = info.emergencyContacts,
            schoolInfo = info.schoolInfo,
            medicalProfile = info.medicalProfile
        )
    }
}
