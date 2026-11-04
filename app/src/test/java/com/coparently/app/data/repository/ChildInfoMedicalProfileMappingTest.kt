package com.coparently.app.data.repository

import com.coparently.app.domain.model.BloodType
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.domain.serialization.StoredJson
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A medical profile must survive the trip through `StoredJson` unchanged.
 *
 * `ChildInfoRepositoryImpl` stores this type as a JSON string in Room and sends it to Firestore
 * as a nested map, so a `LocalDate` inside `Vaccination` has to be written as ISO text — as Gson's
 * `LocalDateJsonAdapter` wrote it before MON-24, and `StoredLocalDateSerializer` writes it now —
 * never as the date's internal structure (`{"year":2024,"month":3,"day":12}`). This pins the
 * representation.
 */
class ChildInfoMedicalProfileMappingTest {

    @Test
    fun `a full profile round-trips through JSON`() {
        val profile = MedicalProfile(
            bloodType = BloodType.O_NEGATIVE,
            intolerances = listOf("lactose"),
            hereditaryConditions = listOf("asthma"),
            vaccinations = listOf(
                Vaccination("MMR", LocalDate(2024, 3, 12)),
                Vaccination("Tetanus", null)
            )
        )

        val restored = StoredJson.decode(
            MedicalProfile.serializer(),
            StoredJson.encode(MedicalProfile.serializer(), profile)
        )

        assertEquals(profile, restored)
    }

    @Test
    fun `a vaccination date is written as an ISO string, not as an object`() {
        val json = StoredJson.encode(Vaccination.serializer(), Vaccination("MMR", LocalDate(2024, 3, 12)))

        // The keys in the device's order, by field name (`GsonFieldOrder`): what Gson wrote on ART.
        assertEquals("""{"date":"2024-03-12","name":"MMR"}""", json)
    }

    @Test
    fun `an untouched profile is the empty default rather than null`() {
        val restored = StoredJson.decode(MedicalProfile.serializer(), "{}")

        assertEquals(null, restored.bloodType)
        assertEquals(emptyList(), restored.vaccinations)
    }
}
