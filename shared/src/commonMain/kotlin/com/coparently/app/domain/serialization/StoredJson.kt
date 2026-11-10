package com.coparently.app.domain.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * The one JSON configuration for everything the app stores or sends as JSON text: Room columns,
 * the event draft, the event-revision outbox, the school connection, and the medical profile's
 * map in `ChildInfoRepositoryImpl.toFirestoreMap()` (MON-24, iPhone phase 2).
 *
 * It replaced Gson, and **every byte it writes is what Gson wrote**, because those bytes are
 * already on phones: Room rows an update must read, rows `CustodyModelRepository.mirrorIntoRoom`
 * compares whole to decide whether to re-insert, and a co-parent's device that reads the same
 * document. So the text is not kotlinx's own; it is printed by [GsonShapedText] from the tree
 * kotlinx builds, the way Gson's `JsonWriter` printed it:
 * - compact, a class's fields sorted by name — the order Gson wrote them in on Android, where
 *   ART returns a class's fields in dex order, which is by name ([GsonFieldOrder]) — and map
 *   entries in iteration order;
 * - every property written, defaults included, and a null property left out (a null in a list is
 *   written), unless [encodeValue] is asked to `serializeNulls` as the revision outbox's Gson was;
 * - Gson's HTML escaping (`<`, `>`, `&`, `=`, `'` as `\u003c` …), U+2028 and U+2029 escaped,
 *   control characters as `\n`, `\t` … or `\u00XX`, and every other character as it is;
 * - a floating-point number laid out as `Double.toString` lays it out on the JVM (`1.0`, `1.0E10`,
 *   `1.0E-5`) on every platform, so the iPhone writes what an Android phone writes.
 *
 * Reading accepts everything Gson read (`StoredJsonTolerance`): unknown keys are ignored, a number
 * may be quoted and a string unquoted, and a field Gson would have left at the JVM's default —
 * missing from the text, or `null` there — reads as that default (0, false, an empty text, list or
 * map) rather than throwing, since Gson's `null` in a non-null Kotlin property was a crash waiting
 * for the first caller. A property with a Kotlin default takes it.
 *
 * `StoredJsonVectorsTest` (commonTest, so the iOS simulator runs it) pins the text with Gson's own
 * output as literals, and `GsonParityTest` (the app's and the shared module's JVM tests) compares
 * every model with Gson itself, which stays a test-only dependency for exactly that.
 */
object StoredJson {

    /**
     * What a JSON number becomes when [decodeValue] has no model to say: Gson's `ToNumberPolicy`s,
     * as far as the app used them.
     */
    enum class Numbers {
        /** Every number as a `Double` — what a plain `Gson()` reads into a `Map` or a `List`. */
        DOUBLE,

        /** A whole number as a `Long`, any other as a `Double` — `ToNumberPolicy.LONG_OR_DOUBLE`. */
        LONG_OR_DOUBLE
    }

    /** A list of texts — uids, references, photos — the shape most stored columns hold. */
    val strings: KSerializer<List<String>> = ListSerializer(String.serializer())

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** [value] as the JSON text Gson wrote for it. */
    fun <T> encode(serializer: KSerializer<T>, value: T): String {
        val tree = GsonFieldOrder.apply(
            serializersOwnFailure { json.encodeToJsonElement(serializer, value) },
            serializer.descriptor
        )
        return GsonShapedText.print(tree, htmlSafe = true, serializeNulls = false)
    }

    /**
     * [text] read as [serializer]'s type, as Gson read it.
     *
     * @throws SerializationException or [IllegalArgumentException] when [text] is not JSON or not
     *   that shape at all — where Gson threw too.
     */
    fun <T> decode(serializer: KSerializer<T>, text: String): T = decodeElement(serializer, parse(text))

    /**
     * [text] read as [serializer]'s type, or null for a null, blank or `null` text — the three a
     * Gson `fromJson` answered with null for.
     */
    fun <T : Any> decodeOrNull(serializer: KSerializer<T>, text: String?): T? {
        if (text.isNullOrBlank()) return null
        val element = parse(text)
        return if (element === JsonNull) null else decodeElement(serializer, element)
    }

    /** [element], already parsed, read as [serializer]'s type with the same tolerance as [decode]. */
    fun <T> decodeElement(serializer: KSerializer<T>, element: JsonElement): T = serializersOwnFailure {
        json.decodeFromJsonElement(serializer, StoredJsonTolerance.fill(element, serializer.descriptor))
    }

    /** [text] as a tree, read as leniently as Gson's `JsonParser` read it. */
    fun parse(text: String): JsonElement = json.parseToJsonElement(text)

    /**
     * [element] printed as Gson printed a tree it built by hand (`JsonElement.toString()`): no HTML
     * escaping, and a null member written as `null`.
     */
    fun printTree(element: JsonElement): String =
        GsonShapedText.print(element, htmlSafe = false, serializeNulls = true)

    /**
     * An untyped value — the maps, lists, strings, numbers and booleans a Firestore document is
     * made of — as the JSON text Gson wrote for it by its runtime type.
     *
     * @param serializeNulls Whether a null map value is written (`"key":null`) rather than left
     *   out, as a `GsonBuilder().serializeNulls()` instance did.
     * @throws IllegalArgumentException for a non-finite number, as Gson threw, and for a value of
     *   any other type: Gson would have reflected over its fields, and nothing the app stores is one.
     */
    fun encodeValue(value: Any?, serializeNulls: Boolean = false): String =
        GsonShapedText.print(StoredJsonValues.elementOf(value), htmlSafe = true, serializeNulls = serializeNulls)

    /**
     * [text] as maps (in the text's order), lists, strings, booleans and numbers per [numbers] —
     * what Gson read into `Map::class.java`, `List::class.java` or a `TypeToken<Map<String, Any?>>`.
     * Null for a null, blank or `null` text.
     */
    fun decodeValue(text: String?, numbers: Numbers = Numbers.DOUBLE): Any? {
        if (text.isNullOrBlank()) return null
        return StoredJsonValues.valueOf(parse(text), numbers)
    }

    /**
     * [text] as a map, as [decodeValue] reads it; null for a null, blank or `null` text.
     *
     * @throws SerializationException when [text] holds something other than an object, as Gson
     *   threw reading one into a `Map`.
     */
    @Suppress("UNCHECKED_CAST")
    fun decodeObject(text: String?, numbers: Numbers = Numbers.DOUBLE): Map<String, Any?>? =
        when (val value = decodeValue(text, numbers)) {
            null -> null
            // Every key [StoredJsonValues.valueOf] puts in a map is a string.
            is Map<*, *> -> value as Map<String, Any?>
            else -> throw SerializationException("Expected a JSON object")
        }

    /**
     * [text] as a list, as [decodeValue] reads it; null for a null, blank or `null` text.
     *
     * @throws SerializationException when [text] holds something other than an array, as Gson
     *   threw reading one into a `List`.
     */
    fun decodeArray(text: String?, numbers: Numbers = Numbers.DOUBLE): List<Any?>? =
        when (val value = decodeValue(text, numbers)) {
            null -> null
            is List<*> -> value
            else -> throw SerializationException("Expected a JSON array")
        }
}

/**
 * [block], with an exception a serializer threw passed on as the serializer threw it.
 *
 * kotlinx-serialization 1.12 wraps an exception from inside a serializer that is not its own
 * (the `DateTimeParseException` of a stored date that does not parse, an `IllegalStateException`
 * from a Gson-shaped reader) in a `JsonDecodingException` or `JsonEncodingException`, with the
 * original as its cause. 1.11 let it through, and so did Gson, so a caller catching what the
 * stored text used to throw would now catch something else. Its own failures carry no such
 * cause and pass unchanged.
 */
private inline fun <R> serializersOwnFailure(block: () -> R): R = try {
    block()
} catch (e: SerializationException) {
    val cause = e.cause
    throw if (cause != null && cause !is SerializationException) cause else e
}
