package com.coparently.app.domain.serialization

import com.coparently.app.domain.custody.DayOverride
import com.coparently.app.domain.custody.DayOverrideStatus
import com.coparently.app.domain.model.Activity
import com.coparently.app.domain.model.BloodType
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.lang.reflect.Type
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * [StoredJson] against Gson itself, for every model of the shared module Gson reflected over before
 * MON-24: the child record's lists, the medical profile, and the day swaps (`DayOverride`).
 *
 * Each Gson here is configured exactly as a call site was: a plain `Gson()` (`DayOverrideJson`, the
 * string lists), one with the `kotlinx.datetime.LocalDate` adapter (`ChildInfoRepositoryImpl`,
 * `PetRepositoryImpl`, `SyncService`, [LegacyLocalDateJsonAdapter] verbatim), and the revision
 * outbox's (`serializeNulls`, `ToNumberPolicy.LONG_OR_DOUBLE`). On a seeded corpus — every
 * character class Gson escapes or does not, nulls and defaults, empty and nested lists, the
 * doubles the brief names — both must write the same text, and [StoredJson] must read Gson's text
 * back to the value. The app's own models are covered by the app's `GsonParityTest`.
 */
class GsonParityTest {

    private val plain = Gson()

    private val dated: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LegacyLocalDateJsonAdapter())
        .create()

    private val outbox: Gson = GsonBuilder()
        .serializeNulls()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .create()

    @Test
    fun theChildRecordsModelsWriteAndReadAsGsonDid() {
        val random = Random(SEED)
        repeat(CASES) {
            same(MEDICATIONS, List(random.nextInt(3)) { medication(random) }, dated)
            same(ListSerializer(Activity.serializer()), List(random.nextInt(3)) { activity(random) }, dated)
            same(ListSerializer(EmergencyContact.serializer()), List(random.nextInt(3)) { contact(random) }, dated)
            same(SchoolInfo.serializer(), school(random), dated)
            same(MedicalProfile.serializer(), profile(random), dated)
            same(ListSerializer(Vaccination.serializer()), List(random.nextInt(3)) { vaccination(random) }, dated)
            same(StoredJson.strings, List(random.nextInt(4)) { text(random) }, plain)
        }
    }

    @Test
    fun dayOverridesWriteAndReadAsGsonDid() {
        val random = Random(SEED)
        val swaps = MapSerializer(String.serializer(), DayOverride.serializer())
        repeat(CASES) {
            same(swaps, List(random.nextInt(4)) { text(random) to dayOverride(random) }.toMap(), plain)
        }
    }

    @Test
    fun stringMapsAndMarkMapsWriteAndReadAsGsonDid() {
        val random = Random(SEED)
        repeat(CASES) {
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

    @Test
    fun untypedValuesWriteAndReadAsGsonDid() {
        val random = Random(SEED)
        val mapType: Type = object : TypeToken<Map<String, Any?>>() {}.type
        repeat(CASES) {
            val value = LinkedHashMap<String, Any?>()
            repeat(random.nextInt(5)) { value[text(random)] = any(random, 0) }
            val written = plain.toJson(value)
            assertEquals(written, StoredJson.encodeValue(value))
            assertEquals(outbox.toJson(value), StoredJson.encodeValue(value, serializeNulls = true))
            // `Map::class.java`, `List::class.java` and `TypeToken<Map<String, Any?>>`: Doubles.
            assertEquals(plain.fromJson(written, Map::class.java), StoredJson.decodeObject(written))
            assertEquals(plain.fromJson<Map<String, Any?>>(written, mapType), StoredJson.decodeValue(written))
            // What `SyncService` uploads: Room's text read back as Doubles, written again.
            assertEquals(
                plain.toJson(plain.fromJson(written, Map::class.java)),
                StoredJson.encodeValue(StoredJson.decodeObject(written))
            )
            val withNulls = outbox.toJson(value)
            assertEquals(
                outbox.fromJson<Map<String, Any?>>(withNulls, mapType),
                StoredJson.decodeObject(withNulls, StoredJson.Numbers.LONG_OR_DOUBLE)
            )
            val list = List(random.nextInt(4)) { any(random, 1) }
            assertEquals(plain.toJson(list), StoredJson.encodeValue(list))
            val listText = plain.toJson(list)
            assertEquals(plain.fromJson(listText, List::class.java), StoredJson.decodeArray(listText))
        }
    }

    @Test
    fun aTreeIsPrintedAsGsonsToStringPrintedIt() {
        val random = Random(SEED)
        repeat(CASES) {
            val name = text(random)
            val value = text(random)
            val number = random.nextLong()
            val gsonTree = com.google.gson.JsonObject().apply {
                addProperty("v", 1)
                addProperty(name, value)
                addProperty("none", null as String?)
                addProperty("n", number)
                add(
                    "list",
                    com.google.gson.JsonArray().apply {
                        add(value)
                        add(number)
                    }
                )
            }
            val tree = buildJsonObject {
                put("v", 1)
                put(name, value)
                put("none", null as String?)
                put("n", number)
                put(
                    "list",
                    buildJsonArray {
                        add(value)
                        add(number)
                    }
                )
            }
            assertEquals(gsonTree.toString(), StoredJson.printTree(tree))
        }
    }

    @Test
    fun aFloatingPointNumberIsLaidOutAsTheJvmLaysItOut() {
        val random = Random(SEED)
        (SPECIAL_DOUBLES + List(LAYOUT_CASES) { Double.fromBits(random.nextLong()) }.filter { it.isFinite() })
            .forEach { assertEquals(it.toString(), JvmNumberLayout.of(it.toString())) }
        List(LAYOUT_CASES) { Float.fromBits(random.nextInt()) }.filter { it.isFinite() }
            .forEach { assertEquals(it.toString(), JvmNumberLayout.of(it.toString())) }
        // Another platform's spelling of the same digits.
        assertEquals("1.0E10", JvmNumberLayout.of("1e+10"))
        assertEquals("1.0E-5", JvmNumberLayout.of("1e-05"))
        assertEquals("1.0E-5", JvmNumberLayout.of("0.00001"))
        assertEquals("1.0E10", JvmNumberLayout.of("10000000000.0"))
        assertEquals("-0.0", JvmNumberLayout.of("-0e0"))
    }

    private fun <T> same(serializer: KSerializer<T>, value: T, gson: Gson) {
        val written = gson.toJson(value)
        assertEquals(written, StoredJson.encode(serializer, value), "$value")
        assertEquals(value, StoredJson.decode(serializer, written), written)
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

    /** A value as a Firestore document holds one, nested at most three deep. */
    private fun any(random: Random, depth: Int): Any? = when (random.nextInt(if (depth > 2) 7 else 9)) {
        0 -> null
        1 -> text(random)
        2 -> random.nextBoolean()
        3 -> random.nextLong()
        4 -> random.nextInt()
        5 -> DOUBLES.random(random).takeIf { random.nextBoolean() } ?: (random.nextDouble() * 1e30)
        6 -> random.nextLong(0, 2_000_000_000_000L)
        7 -> LinkedHashMap<String, Any?>().also { map ->
            repeat(random.nextInt(4)) { map[text(random)] = any(random, depth + 1) }
        }
        else -> List(random.nextInt(4)) { any(random, depth + 1) }
    }

    private fun maybe(random: Random): String? = if (random.nextInt(4) == 0) null else text(random)

    private fun text(random: Random): String =
        String(CharArray(random.nextInt(0, 8)) { CHARS[random.nextInt(CHARS.length)] })

    /** `LocalDateJsonAdapter` as the app had it before MON-24, verbatim: the oracle for dates. */
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
        const val CASES = 3_000
        const val LAYOUT_CASES = 100_000

        /** Every class of character Gson's writer treats differently, and some it does not. */
        const val CHARS = "aZ09 <>&='\"\\/\t\n\r\b\u000c\u0000\u001f\u007f\u2028\u2029éčЖїґ😀\u00a0{}[],:|"

        val DOUBLES = listOf(0.0, -0.0, 1.0, 2.675, 1.0E10, 1.0E-5, 15.0, 1.7E12)

        val SPECIAL_DOUBLES = DOUBLES + listOf(1.0E7, 9_999_999.0, 0.001, 1.0E-4, 1.0E21, 1.0E22, 1.0E23, 100.0) +
            listOf(Double.MAX_VALUE, Double.MIN_VALUE, -Double.MIN_VALUE, 123456.789)

        val MEDICATIONS = ListSerializer(Medication.serializer())
    }
}
