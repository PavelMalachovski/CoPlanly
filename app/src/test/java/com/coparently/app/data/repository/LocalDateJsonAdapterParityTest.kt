package com.coparently.app.data.repository

import com.coparently.app.domain.model.BloodType
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Vaccination
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Test
import java.lang.reflect.Type
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The stored medical profile and pet vaccination list keep their bytes now that `Vaccination.date`
 * is a kotlinx-datetime date (MON-24).
 *
 * The oracle is the `java.time` adapter this build replaced, verbatim ([LegacyLocalDateJsonAdapter]),
 * over twins of the two models that still hold a `java.time.LocalDate` ([LegacyVaccination],
 * [LegacyMedicalProfile]): the same field names, so Gson writes the same keys. On seeded random
 * input both must write the same JSON, and both must read the same text to the same date or fail
 * with the same exception — a Room column or a Firestore map written by an older build reads exactly
 * as it did, and one this build writes reads on an older build.
 */
class LocalDateJsonAdapterParityTest {

    private val current: Gson = GsonBuilder()
        .registerTypeAdapter(kotlinx.datetime.LocalDate::class.java, LocalDateJsonAdapter())
        .create()

    private val legacy: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LegacyLocalDateJsonAdapter())
        .create()

    @Test
    fun aProfileIsWrittenAsTheJavaTimeAdapterWroteIt() {
        val random = Random(SEED)
        repeat(CASES) {
            val dates = List(random.nextInt(0, 4)) { randomDate(random).takeIf { random.nextInt(5) > 0 } }
            val profile = MedicalProfile(
                bloodType = BloodType.entries[random.nextInt(BloodType.entries.size)],
                intolerances = listOf("lactose"),
                vaccinations = dates.mapIndexed { index, date ->
                    Vaccination("v$index", date?.toKotlinLocalDate())
                }
            )
            val twin = LegacyMedicalProfile(
                bloodType = profile.bloodType,
                intolerances = profile.intolerances,
                hereditaryConditions = profile.hereditaryConditions,
                vaccinations = dates.mapIndexed { index, date -> LegacyVaccination("v$index", date) }
            )
            assertEquals(legacy.toJson(twin), current.toJson(profile), "$dates")
            assertEquals(legacy.toJson(twin.vaccinations), current.toJson(profile.vaccinations), "$dates")
        }
    }

    @Test
    fun aStoredDateIsReadAsTheJavaTimeAdapterReadIt() {
        val random = Random(SEED)
        repeat(CASES) {
            val json = """{"name":"MMR","date":${randomDateJson(random)}}"""
            val old = runCatching { legacy.fromJson(json, LegacyVaccination::class.java) }
            val new = runCatching { current.fromJson(json, Vaccination::class.java) }
            when {
                old.isSuccess && new.isSuccess ->
                    assertEquals(old.getOrThrow().date, new.getOrThrow().date?.toJavaLocalDate(), json)
                old.isFailure && new.isFailure ->
                    assertEquals(
                        old.exceptionOrNull()!!::class,
                        new.exceptionOrNull()!!::class,
                        "the same failure for $json"
                    )
                else -> fail(
                    "$json: java.time ${old.exceptionOrNull() ?: old.getOrNull()}, " +
                        "now ${new.exceptionOrNull() ?: new.getOrNull()}"
                )
            }
        }
    }

    private fun randomDate(random: Random): LocalDate {
        val year = when (random.nextInt(4)) {
            0 -> random.nextInt(-999_999_999, 1_000_000_000)
            1 -> random.nextInt(-10_000, 20_000)
            else -> random.nextInt(1900, 2200)
        }
        val month = random.nextInt(1, 13)
        return LocalDate.of(year, month, random.nextInt(1, LocalDate.of(year, month, 1).lengthOfMonth() + 1))
    }

    /** A date's JSON as a stored column might hold it: valid, mutated, blank, null or not a string. */
    private fun randomDateJson(random: Random): String = when (random.nextInt(10)) {
        0 -> "null"
        1 -> listOf("\"\"", "\"  \"", "\"\\t\"").random(random)
        2 -> listOf("20240312", "2024", "true", "{\"year\":2024,\"month\":3,\"day\":12}", "[\"2024-03-12\"]")
            .random(random)
        3, 4 -> "\"${mutate(randomDate(random).toString(), random)}\""
        else -> "\"${randomDate(random)}\""
    }

    private fun mutate(text: String, random: Random): String {
        val chars = text.toMutableList()
        when (random.nextInt(4)) {
            0 -> chars.removeAt(random.nextInt(chars.size))
            1 -> chars.add(random.nextInt(chars.size + 1), MUTATIONS.random(random))
            2 -> chars[random.nextInt(chars.size)] = MUTATIONS.random(random)
            else -> return " $text "
        }
        return chars.joinToString("")
    }

    /** `Vaccination` as it was before MON-24, field for field. */
    private data class LegacyVaccination(val name: String, val date: LocalDate? = null)

    /** `MedicalProfile` as it was before MON-24, field for field. */
    private data class LegacyMedicalProfile(
        val bloodType: BloodType? = null,
        val intolerances: List<String> = emptyList(),
        val hereditaryConditions: List<String> = emptyList(),
        val vaccinations: List<LegacyVaccination> = emptyList()
    )

    /** `LocalDateJsonAdapter` as it was before MON-24, verbatim. */
    private class LegacyLocalDateJsonAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

        override fun serialize(
            src: LocalDate?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement = JsonPrimitive(src?.toString())

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): LocalDate? = json?.asString?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    }

    private companion object {
        const val SEED = 20261104
        const val CASES = 5_000
        const val MUTATIONS = "0123456789-+T: x"
    }
}
