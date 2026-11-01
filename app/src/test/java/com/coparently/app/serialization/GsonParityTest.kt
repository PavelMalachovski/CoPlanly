package com.coparently.app.serialization

import com.coparently.app.data.school.SchoolConnectionCodec
import com.coparently.app.data.school.StoredSchoolConnection
import com.coparently.app.data.school.bakalari.BakalariTokens
import com.coparently.app.domain.model.Activity
import com.coparently.app.domain.model.EmergencyContact
import com.coparently.app.domain.model.MedicalProfile
import com.coparently.app.domain.model.Medication
import com.coparently.app.domain.model.SchoolInfo
import com.coparently.app.domain.model.Vaccination
import com.coparently.app.domain.school.SchoolConnection
import com.coparently.app.domain.school.SchoolConnectionStatus
import com.coparently.app.domain.school.SchoolImportKind
import com.coparently.app.domain.school.SchoolImportRecord
import com.coparently.app.domain.school.SchoolStudent
import com.coparently.app.domain.serialization.StoredJson
import com.coparently.app.presentation.event.EventDraft
import com.coparently.app.wire.WireFixtures
import com.coparently.app.wire.WireTimestamp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.Strictness
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Test
import java.lang.reflect.Type
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * [StoredJson] against Gson itself for what the app — not the shared module — handed to Gson
 * before MON-24: the event draft, the school connection's hand-built tree, and every value in the
 * wire fixtures (`app/src/test/resources/wire/`) that went through Gson on its way into or out of
 * Room. The shared module's models have their own `GsonParityTest` there.
 *
 * A class's keys are compared in the order Gson wrote them on a phone, sorted by name
 * ([ArtFieldOrder]), not in this JVM's declaration order, which never reached a phone.
 *
 * Each Gson is configured as its call site was: `provideGson()`'s lenient instance for the draft,
 * a plain `Gson()`, the child and pet repositories' with the `LocalDate` adapter
 * ([LegacyLocalDateJsonAdapter], verbatim), and the revision outbox's (`serializeNulls`,
 * `ToNumberPolicy.LONG_OR_DOUBLE`).
 */
class GsonParityTest {

    private val plain = Gson()

    private val lenient: Gson = GsonBuilder().setStrictness(Strictness.LENIENT).create()

    private val dated: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LegacyLocalDateJsonAdapter())
        .create()

    private val outbox: Gson = GsonBuilder()
        .serializeNulls()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .create()

    @Test
    fun anEventDraftWritesAndReadsAsTheInjectedGsonDid() {
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
            val written = lenient.toJson(draft)
            assertEquals(plain.toJson(draft), written)
            // As a phone wrote it: the fields sorted by name (ArtFieldOrder).
            val onPhones = ArtFieldOrder.of(written, draft)
            assertEquals(onPhones, StoredJson.encode(EventDraft.serializer(), draft))
            assertEquals(draft, StoredJson.decodeOrNull(EventDraft.serializer(), onPhones))
            assertEquals(draft, StoredJson.decodeOrNull(EventDraft.serializer(), written))
        }
    }

    @Test
    fun aDraftSavedBeforeTheEndDateReadsAsAOneDayTimedEvent() {
        val older = """{"title":"Swim","description":"","parentOwner":"mom","eventType":"general",""" +
            """"startDate":"2026-05-01","startTime":"16:00","endTime":"17:00"}"""
        val expected = lenient.fromJson(older, EventDraft::class.java)
        assertEquals(expected, StoredJson.decodeOrNull(EventDraft.serializer(), older))
        assertEquals(0, expected.endDaySpan)
    }

    @Test
    fun aSchoolConnectionIsWrittenAsGsonsTreeWroteIt() {
        val random = Random(SEED)
        repeat(CASES) {
            val stored = StoredSchoolConnection(
                connection = SchoolConnection(
                    id = text(random),
                    baseUrl = "https://${text(random)}/?a=1&b=<2>",
                    username = text(random),
                    schoolName = text(random),
                    studentName = text(random),
                    childId = text(random),
                    familyId = if (random.nextBoolean()) null else text(random),
                    status = SchoolConnectionStatus.entries.random(random),
                    lastSuccessAtMillis = if (random.nextBoolean()) null else random.nextLong()
                ),
                ownerUid = text(random),
                student = SchoolStudent(text(random), text(random), text(random)),
                tokens = BakalariTokens(text(random), text(random), random.nextLong()),
                ledger = List(random.nextInt(3)) {
                    text(random) to SchoolImportRecord(
                        SchoolImportKind.entries.random(random),
                        java.time.LocalDate.of(
                            random.nextInt(2000, 2100),
                            random.nextInt(1, 13),
                            random.nextInt(1, 29)
                        ),
                        text(random).replace("|", "")
                    )
                }.toMap()
            )
            val written = SchoolConnectionCodec.encode(stored)
            assertEquals(gsonTreeOf(stored), written)
            assertEquals(stored, SchoolConnectionCodec.decode(written))
        }
    }

    @Test
    fun everyFixtureValueThatWentThroughGsonWritesAndReadsAsGsonDid() {
        val fixtures = WireFixtures.all()
        check(fixtures.isNotEmpty()) { "no wire fixtures found" }
        for (fixture in fixtures) {
            for ((key, value) in fixture.document) {
                if ((value !is Map<*, *> && value !is List<*>) || value.holdsTimestamp()) continue
                val where = "${fixture.name}: $key"
                val written = plain.toJson(value)
                assertEquals(written, StoredJson.encodeValue(value), where)
                assertEquals(outbox.toJson(value), StoredJson.encodeValue(value, serializeNulls = true), where)
                assertEquals(plain.fromJson(written, Any::class.java), StoredJson.decodeValue(written), where)
                val withNulls = outbox.toJson(value)
                assertEquals(
                    outbox.fromJson(withNulls, Any::class.java),
                    StoredJson.decodeValue(withNulls, StoredJson.Numbers.LONG_OR_DOUBLE),
                    where
                )
                typed(fixture.collection, key, written)?.let { (gson, mine) -> assertEquals(gson, mine, where) }
            }
        }
    }

    /**
     * What the repository read out of the Room column [written] — the text Gson wrote for a
     * downloaded field — through Gson then and through [StoredJson] now; null for a field no
     * repository reads into a model.
     */
    private fun typed(collection: String, key: String, written: String): Pair<Any?, Any?>? = when (collection to key) {
        "child_info" to "medications", "pets" to "medications" -> both(written, Medication.serializer())
        "child_info" to "activities" -> both(written, Activity.serializer())
        "child_info" to "emergencyContacts" -> both(written, EmergencyContact.serializer())
        "pets" to "vaccinations" -> both(written, Vaccination.serializer())
        "child_info" to "schoolInfo" ->
            dated.fromJson(written, SchoolInfo::class.java) to StoredJson.decodeOrNull(SchoolInfo.serializer(), written)
        "child_info" to "medicalProfile" ->
            dated.fromJson(written, MedicalProfile::class.java) to
                StoredJson.decodeOrNull(MedicalProfile.serializer(), written)
        else -> if (key in STRING_LISTS) {
            dated.fromJson(written, Array<String>::class.java)?.toList() to
                StoredJson.decodeOrNull(StoredJson.strings, written)
        } else {
            null
        }
    }

    private fun <T : Any> both(written: String, item: KSerializer<T>): Pair<Any?, Any?> {
        val listType = TypeToken.getParameterized(List::class.java, itemClass(item)).type
        val gson = dated.fromJson<List<Any?>>(written, listType)
        return gson to StoredJson.decodeOrNull(ListSerializer(item), written)
    }

    private fun itemClass(item: KSerializer<*>): Class<*> = when (item) {
        Medication.serializer() -> Medication::class.java
        Activity.serializer() -> Activity::class.java
        EmergencyContact.serializer() -> EmergencyContact::class.java
        else -> Vaccination::class.java
    }

    private fun Any?.holdsTimestamp(): Boolean = when (this) {
        is WireTimestamp -> true
        is Map<*, *> -> values.any { it.holdsTimestamp() }
        is List<*> -> any { it.holdsTimestamp() }
        else -> false
    }

    /** `SchoolConnectionCodec.encode` as it was before MON-24, verbatim: Gson's tree. */
    private fun gsonTreeOf(stored: StoredSchoolConnection): String {
        val connection = stored.connection
        val root = JsonObject()
        root.addProperty("v", 1)
        root.addProperty("id", connection.id)
        root.addProperty("owner", stored.ownerUid)
        root.addProperty("system", "bakalari")
        root.addProperty("baseUrl", connection.baseUrl)
        root.addProperty("username", connection.username)
        root.addProperty("schoolName", connection.schoolName)
        root.addProperty("studentName", connection.studentName)
        root.addProperty("childId", connection.childId)
        connection.familyId?.let { root.addProperty("familyId", it) }
        root.addProperty("status", connection.status.stored)
        connection.lastSuccessAtMillis?.let { root.addProperty("lastSuccessAt", it) }
        root.addProperty("userUid", stored.student.userUid)
        root.addProperty("fullName", stored.student.fullName)
        root.addProperty("classId", stored.student.classId)
        root.addProperty("accessToken", stored.tokens.accessToken)
        root.addProperty("refreshToken", stored.tokens.refreshToken)
        root.addProperty("expiresAt", stored.tokens.expiresAtMillis)
        val ledger = JsonObject()
        stored.ledger.forEach { (eventId, record) ->
            ledger.addProperty(eventId, "${record.kind.stored}|${record.lastDate}|${record.fingerprint}")
        }
        root.add("ledger", ledger)
        return root.toString()
    }

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
        const val CASES = 2_000

        /** Every class of character Gson's writer treats differently, and some it does not. */
        const val CHARS = "aZ09 <>&='\"\\/\t\n\r\b\u000c\u0000\u001f\u007f\u2028\u2029éčЖїґ😀\u00a0{}[],:|"

        /** The fields every collection stores as a JSON array of texts. */
        val STRING_LISTS = setOf("allergies", "medicalPhotos", "photos", "sharedWith", "forMembers", "attachments")
    }
}
