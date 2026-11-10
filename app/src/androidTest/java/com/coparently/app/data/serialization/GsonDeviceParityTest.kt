package com.coparently.app.data.serialization

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.model.Activity
import com.coparently.app.domain.model.BloodType
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.presentation.event.EventDraft
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.Strictness
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Type
import kotlin.random.Random

/**
 * [StoredJson] against Gson **on this device's ART**, byte for byte (MON-24).
 *
 * The bytes already stored on phones are what Gson wrote on ART, not on a JVM: Gson writes a
 * class's fields in `Class.getDeclaredFields()` order, and ART returns them in dex order, which is
 * sorted by name, where HotSpot returns declaration order. The JVM tests (`GsonParityTest`,
 * `StoredJsonVectorsTest`) can only re-sort the JVM's output by that rule; this is the test that
 * settles it, for every model Gson wrote into a Room column, the event draft and the string lists
 * and maps beside them, each with the Gson its call site used.
 *
 * Deliberately no Koin, no MockK and no `@UsesFakeFirebase`, so it runs on every emulator leg,
 * the 16 KB one included. A debug build: field names are the source's, as the release build's
 * keep rules kept them when Gson wrote them.
 */
@RunWith(AndroidJUnit4::class)
class GsonDeviceParityTest {

    private val plain = Gson()

    /** `provideGson()` as it was: the event draft's instance. */
    private val lenient: Gson = GsonBuilder().setStrictness(Strictness.LENIENT).create()

    /** The child, pet and user repositories' instance, with the date adapter they registered. */
    private val dated: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LegacyLocalDateJsonAdapter())
        .create()

    @Test
    fun theChildRecordsModelsAreWrittenAsGsonWroteThemHere() {
        val random = Random(SEED)
        repeat(CASES) {
            same(ListSerializer(Medication.serializer()), List(random.nextInt(3)) { medication(random) }, dated)
            same(ListSerializer(Activity.serializer()), List(random.nextInt(3)) { activity(random) }, dated)
            same(ListSerializer(EmergencyContact.serializer()), List(random.nextInt(3)) { contact(random) }, dated)
            same(SchoolInfo.serializer(), school(random), dated)
            same(MedicalProfile.serializer(), profile(random), dated)
            same(ListSerializer(Vaccination.serializer()), List(random.nextInt(3)) { vaccination(random) }, dated)
        }
    }

    @Test
    fun daySwapsAreWrittenAsGsonWroteThemHere() {
        val random = Random(SEED)
        val swaps = MapSerializer(String.serializer(), DayOverride.serializer())
        repeat(CASES) {
            same(swaps, List(random.nextInt(4)) { text(random) to dayOverride(random) }.toMap(), plain)
        }
    }

    @Test
    fun theEventDraftIsWrittenAsGsonWroteItHere() {
        val random = Random(SEED)
        repeat(CASES) {
            val draft = EventDraft(
                title = text(random),
                description = text(random),
                parentOwner = text(random),
                eventType = text(random),
                startDate = text(random),
                startTime = text(random),
                endTime = text(random),
                endDaySpan = random.nextInt(-2, 400),
                allDay = random.nextBoolean()
            )
            same(EventDraft.serializer(), draft, lenient)
        }
    }

    @Test
    fun stringListsAndMapsAreWrittenAsGsonWroteThemHere() {
        val random = Random(SEED)
        repeat(CASES) {
            same(StoredJson.strings, List(random.nextInt(4)) { text(random) }, plain)
            same(
                MapSerializer(String.serializer(), String.serializer()),
                List(random.nextInt(4)) { text(random) to text(random) }.toMap(),
                plain
            )
            same(
                MapSerializer(String.serializer(), Long.serializer()),
                List(random.nextInt(4)) { text(random) to random.nextLong() }.toMap(),
                plain
            )
        }
    }

    private fun <T> same(serializer: KSerializer<T>, value: T, gson: Gson) {
        val written = gson.toJson(value)
        assertEquals("$value", written, StoredJson.encode(serializer, value))
        assertEquals(written, value, StoredJson.decode(serializer, written))
    }

    private fun medication(random: Random) = Medication(text(random), text(random), text(random), maybe(random))

    private fun activity(random: Random) =
        Activity(text(random), text(random), maybe(random), maybe(random), maybe(random))

    private fun contact(random: Random) = EmergencyContact(text(random), text(random), text(random), maybe(random))

    private fun school(random: Random) =
        SchoolInfo(text(random), maybe(random), maybe(random), maybe(random), maybe(random), maybe(random))

    private fun vaccination(random: Random) = Vaccination(
        text(random),
        if (random.nextBoolean()) {
            null
        } else {
            LocalDate(random.nextInt(1900, 2100), random.nextInt(1, 13), random.nextInt(1, 29))
        }
    )

    private fun profile(random: Random) = MedicalProfile(
        bloodType = if (random.nextBoolean()) null else BloodType.entries.random(random),
        intolerances = List(random.nextInt(3)) { text(random) },
        hereditaryConditions = List(random.nextInt(3)) { text(random) },
        vaccinations = List(random.nextInt(3)) { vaccination(random) }
    )

    private fun dayOverride(random: Random) = DayOverride(
        toParent = text(random),
        requestedBy = text(random),
        requestedAt = text(random),
        status = DayOverrideStatus.entries.random(random),
        decidedBy = maybe(random),
        decidedAt = maybe(random),
        note = maybe(random),
        groupId = maybe(random),
        earlier = if (random.nextBoolean()) null else List(random.nextInt(3)) { text(random) }
    )

    private fun maybe(random: Random): String? = if (random.nextInt(4) == 0) null else text(random)

    private fun text(random: Random): String =
        String(CharArray(random.nextInt(0, 8)) { CHARS[random.nextInt(CHARS.length)] })

    /** `LocalDateJsonAdapter` as the app had it before MON-24, verbatim. */
    private class LegacyLocalDateJsonAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {

        override fun serialize(src: LocalDate?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement =
            JsonPrimitive(src?.toJavaLocalDate()?.toString())

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDate? =
            json?.asString?.takeIf { it.isNotBlank() }
                ?.let(java.time.LocalDate::parse)
                ?.toKotlinLocalDate()
    }

    private companion object {
        const val SEED = 20261101
        const val CASES = 300

        /** Every class of character Gson's writer treats differently, and some it does not. */
        const val CHARS = "aZ09 <>&='\"\\/\t\n\r\b\u000c\u0000\u001f\u007f\u2028\u2029\u00a0éčЖїґ😀{}[],:|"
    }
}
