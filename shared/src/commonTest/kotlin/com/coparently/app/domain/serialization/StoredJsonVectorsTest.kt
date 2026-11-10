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
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The text [StoredJson] writes, pinned as literals on every platform it runs on (MON-24).
 *
 * Every expected string here is **Gson 2.11.0's own output** for the same value, obtained by
 * running Gson on the JVM over a twin of each class (same field names) and pasting the result —
 * never written by hand — with each class's keys sorted by name, as Gson wrote them on a phone:
 * ART returns a class's fields in dex order, which is by name (`GsonFieldOrder`; the JVM's
 * declaration order never reached a phone). `GsonDeviceParityTest` compares with Gson on ART, and
 * `GsonParityTest` makes the same comparison live on the JVM; this file exists so the iPhone's
 * simulator is held to the same bytes, since the iPhone has no Gson to compare with. Each vector
 * is also read back, to the value it was written from.
 */
class StoredJsonVectorsTest {

    @Test
    fun aClassWritesEveryPropertyByNameDefaultsIncludedAndANullLeftOut() {
        vector(Shape("plain"), shapeText("plain"))
    }

    @Test
    fun htmlCharactersAreEscapedAsGsonEscapedThem() {
        vector(
            Shape("<a href='x'>&b=c</a>", optional = "x"),
            shapeText(
                "\\u003ca href\\u003d\\u0027x\\u0027\\u003e\\u0026b\\u003dc\\u003c/a\\u003e",
                optional = "\"optional\":\"x\","
            )
        )
    }

    @Test
    fun quotesBackslashesAndControlCharactersAreEscapedAndDeleteIsNot() {
        vector(
            Shape("\"\\/ \u0000\u0001\b\t\n\u000b\u000c\r\u001f\u007f"),
            shapeText("\\\"\\\\/ \\u0000\\u0001\\b\\t\\n\\u000b\\f\\r\\u001f\u007f")
        )
    }

    @Test
    fun nonAsciiIsWrittenAsItIsExceptTheTwoLineSeparators() {
        vector(
            Shape("Жёлтый ї é 😀 \u2028\u2029 \u00a0"),
            shapeText("Жёлтый ї é 😀 \\u2028\\u2029 \u00a0")
        )
    }

    @Test
    fun aDoubleIsLaidOutAsTheJvmPrintsIt() {
        // Pairs, not a map: 0.0 and -0.0 must both be written.
        val expected = listOf(
            0.0 to "0.0",
            -0.0 to "-0.0",
            1.0 to "1.0",
            2.675 to "2.675",
            1.0E10 to "1.0E10",
            1.0E-5 to "1.0E-5",
            1.0E7 to "1.0E7",
            0.001 to "0.001",
            123456.789 to "123456.789"
        )
        expected.forEach { (ratio, text) ->
            vector(
                Shape("d", ratio = ratio),
                shapeText("d", ratio = text)
            )
        }
    }

    @Test
    fun wholeNumbersBooleansAndEnumsAreWrittenPlainly() {
        vector(
            Shape("n", count = Int.MIN_VALUE, big = Long.MAX_VALUE, flag = true, level = Level.HIGH),
            """{"big":9223372036854775807,"byName":{},"count":-2147483648,"flag":true,"level":"HIGH",""" +
                """"nested":[],"ratio":0.0,"tags":[],"text":"n"}"""
        )
    }

    @Test
    fun emptyAndNestedListsAndAMapInItsOrder() {
        vector(
            Shape(
                "l",
                tags = listOf("", "a"),
                nested = listOf(emptyList(), listOf("a"), listOf("b", "c")),
                byName = linkedMapOf("k<" to 1, "z" to -2)
            ),
            "{\"big\":0,\"byName\":{\"k\\u003c\":1,\"z\":-2},\"count\":0,\"flag\":false,\"level\":\"LOW\"," +
                "\"nested\":[[],[\"a\"],[\"b\",\"c\"]],\"ratio\":0.0,\"tags\":[\"\",\"a\"],\"text\":\"l\"}"
        )
    }

    @Test
    fun theChildRecordsModelsWriteWhatGsonWrote() {
        vector(
            Medication.serializer(),
            Medication("Ventolin", "2 puffs", "as needed", null),
            """{"dosage":"2 puffs","frequency":"as needed","name":"Ventolin"}"""
        )
        vector(
            MedicalProfile.serializer(),
            MedicalProfile(
                BloodType.AB_NEGATIVE,
                listOf("lactose"),
                emptyList(),
                listOf(Vaccination("MMR", LocalDate(2024, 3, 12)), Vaccination("Tetanus", null))
            ),
            """{"bloodType":"AB_NEGATIVE","hereditaryConditions":[],"intolerances":["lactose"],""" +
                """"vaccinations":[{"date":"2024-03-12","name":"MMR"},{"name":"Tetanus"}]}"""
        )
        vector(
            MedicalProfile.serializer(),
            MedicalProfile(),
            """{"hereditaryConditions":[],"intolerances":[],"vaccinations":[]}"""
        )
        vector(
            ListSerializer(Activity.serializer()),
            listOf(Activity("Swim", "Mon 4-5 pm", "Pool & Gym", null, "+420 123")),
            "[{\"contactPhone\":\"+420 123\",\"location\":\"Pool \\u0026 Gym\",\"name\":\"Swim\"," +
                "\"schedule\":\"Mon 4-5 pm\"}]"
        )
        vector(
            EmergencyContact.serializer(),
            EmergencyContact("Babička", "grandmother", "+420 777", null),
            """{"name":"Babička","phone":"+420 777","relationship":"grandmother"}"""
        )
        vector(
            SchoolInfo.serializer(),
            SchoolInfo("ZŠ <Lipová>", grade = "3.A"),
            "{\"grade\":\"3.A\",\"name\":\"ZŠ \\u003cLipová\\u003e\"}"
        )
    }

    @Test
    fun dayOverridesWriteWhatGsonWrote() {
        val swaps = MapSerializer(String.serializer(), DayOverride.serializer())
        vector(
            swaps,
            mapOf(
                "2026-05-01" to DayOverride(
                    "dad",
                    "uidA",
                    "2026-04-20T10:00",
                    DayOverrideStatus.ACCEPTED,
                    "uidB",
                    "2026-04-21T08:00",
                    "Ill — can't 'swap'",
                    "g1",
                    listOf("d1|mom|uidA|2026-04-01T09:00|DECLINED|uidB|2026-04-02T09:00|")
                )
            ),
            "{\"2026-05-01\":{\"decidedAt\":\"2026-04-21T08:00\",\"decidedBy\":\"uidB\"," +
                "\"earlier\":[\"d1|mom|uidA|2026-04-01T09:00|DECLINED|uidB|2026-04-02T09:00|\"],\"groupId\":\"g1\"," +
                "\"note\":\"Ill — can\\u0027t \\u0027swap\\u0027\",\"requestedAt\":\"2026-04-20T10:00\"," +
                "\"requestedBy\":\"uidA\",\"status\":\"ACCEPTED\",\"toParent\":\"dad\"}}"
        )
        vector(
            swaps,
            mapOf("2026-05-02" to DayOverride("mom", "uidB", "2026-04-20T10:00")),
            """{"2026-05-02":{"requestedAt":"2026-04-20T10:00","requestedBy":"uidB","status":"PENDING",""" +
                """"toParent":"mom"}}"""
        )
    }

    @Test
    fun anUntypedValueIsWrittenByItsRuntimeType() {
        val value = linkedMapOf<String, Any?>(
            "s" to "a=b",
            "n" to null,
            "l" to 15L,
            "i" to 3,
            "d" to 15.0,
            "e" to 1.0E10,
            "f" to 1.0E-5,
            "z" to -0.0,
            "b" to false,
            "list" to listOf(1L, null, "x", 2.675),
            "m" to linkedMapOf("k" to emptyList<Any>(), "e" to emptyMap<String, Any>())
        )
        assertEquals(
            "{\"s\":\"a\\u003db\",\"l\":15,\"i\":3,\"d\":15.0,\"e\":1.0E10,\"f\":1.0E-5,\"z\":-0.0,\"b\":false," +
                "\"list\":[1,null,\"x\",2.675],\"m\":{\"k\":[],\"e\":{}}}",
            StoredJson.encodeValue(value)
        )
        assertEquals(
            "{\"s\":\"a\\u003db\",\"n\":null,\"l\":15,\"i\":3,\"d\":15.0,\"e\":1.0E10,\"f\":1.0E-5,\"z\":-0.0," +
                "\"b\":false,\"list\":[1,null,\"x\",2.675],\"m\":{\"k\":[],\"e\":{}}}",
            StoredJson.encodeValue(value, serializeNulls = true)
        )
        assertFailsWith<IllegalArgumentException> { StoredJson.encodeValue(listOf(Double.NaN)) }
    }

    @Test
    fun anUntypedValueReadsBackAsGsonReadIt() {
        val text = """{"a":15,"b":1.5,"c":[1,null,true,"x"],"d":{"e":-0.0,"f":1e3,"g":12345678901234}}"""
        assertEquals(
            mapOf(
                "a" to 15.0,
                "b" to 1.5,
                "c" to listOf(1.0, null, true, "x"),
                "d" to mapOf("e" to -0.0, "f" to 1000.0, "g" to 1.2345678901234E13)
            ),
            StoredJson.decodeValue(text)
        )
        assertEquals(
            mapOf(
                "a" to 15L,
                "b" to 1.5,
                "c" to listOf(1L, null, true, "x"),
                "d" to mapOf("e" to -0.0, "f" to 1000.0, "g" to 12345678901234L)
            ),
            StoredJson.decodeObject(text, StoredJson.Numbers.LONG_OR_DOUBLE)
        )
        for (nothing in listOf(null, "", "  ", "null", " null ")) {
            assertNull(StoredJson.decodeValue(nothing), "'$nothing'")
            assertNull(StoredJson.decodeOrNull(StoredJson.strings, nothing), "'$nothing'")
        }
    }

    @Test
    fun aTreeIsPrintedAsGsonsJsonElementToStringPrintedIt() {
        val tree = buildJsonObject {
            put("v", 1)
            put("url", "https://a.cz/?a=1&b=<2>")
            put("none", JsonNull)
            put("t", 1700000000000L)
            putJsonObject("ledger") { put("school-1", "event|2026-05-01|it's") }
        }
        assertEquals(
            """{"v":1,"url":"https://a.cz/?a=1&b=<2>","none":null,"t":1700000000000,""" +
                """"ledger":{"school-1":"event|2026-05-01|it's"}}""",
            StoredJson.printTree(tree)
        )
    }

    @Test
    fun whatGsonLeftAtTheJvmDefaultReadsAsThatDefaultOrEmpty() {
        assertEquals(
            Medication("Ventolin", "", ""),
            StoredJson.decode(Medication.serializer(), """{"name":"Ventolin","dosage":null,"extra":[1]}""")
        )
        // A number where text is expected reads as its text, as Gson read it.
        assertEquals(
            Medication("Ventolin", "5", "true"),
            StoredJson.decode(Medication.serializer(), """{"name":"Ventolin","dosage":5,"frequency":true}""")
        )
        assertEquals(
            MedicalProfile(vaccinations = listOf(Vaccination("", LocalDate(2024, 3, 12)), Vaccination("b"))),
            StoredJson.decode(
                MedicalProfile.serializer(),
                """{"bloodType":"Z_POSITIVE","intolerances":null,""" +
                    """"vaccinations":[{"date":"2024-03-12"},null,{"name":"b","date":"  "}]}"""
            )
        )
        assertEquals(
            Shape("", count = 7),
            StoredJson.decode(Shape.serializer(), """{"count":"7","tags":null}""")
        )
    }

    private fun vector(value: Shape, expected: String) = vector(Shape.serializer(), value, expected)

    private fun <T> vector(serializer: KSerializer<T>, value: T, expected: String) {
        assertEquals(expected, StoredJson.encode(serializer, value))
        assertEquals(value, StoredJson.decode(serializer, expected))
    }

    /** One of every shape a stored model can hold; its Gson twin has the same fields in this order. */
    @Serializable
    private data class Shape(
        val text: String,
        val optional: String? = null,
        val count: Int = 0,
        val big: Long = 0,
        val ratio: Double = 0.0,
        val flag: Boolean = false,
        val tags: List<String> = emptyList(),
        val nested: List<List<String>> = emptyList(),
        val byName: Map<String, Int> = emptyMap(),
        val level: Level = Level.LOW
    )

    @Serializable
    private enum class Level { LOW, HIGH }

    /**
     * A [Shape] left at its defaults but for [text] (already escaped as JSON), [ratio] and
     * [optional] (a whole member, `"optional":"…",`), in the order Gson wrote a class's fields on a
     * phone: sorted by name.
     */
    private fun shapeText(text: String, ratio: String = "0.0", optional: String = ""): String =
        """{"big":0,"byName":{},"count":0,"flag":false,"level":"LOW","nested":[],""" + optional +
            """"ratio":""" + ratio + ""","tags":[],"text":"""" + text + """"}"""
}
