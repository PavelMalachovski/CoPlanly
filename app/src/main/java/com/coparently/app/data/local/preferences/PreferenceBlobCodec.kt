package com.coparently.app.data.local.preferences

import java.util.Base64

/**
 * The plaintext form of the whole preference store, before [EncryptedPreferences] seals it (SEC-5).
 *
 * One line per entry — `<type>:<key>:<value>` — under a version header, with the key and every
 * string Base64-encoded so no separator can appear inside one. The types are the ones a
 * [android.content.SharedPreferences] can hold: `s` string, `i` int, `l` long, `f` float,
 * `b` boolean, `S` string set (its members Base64-encoded and joined by commas).
 *
 * Deliberately not Gson and not `org.json`: Gson over a model is what R8 corrupted once already,
 * and `org.json` is a stub in JVM unit tests, where this format is proved. The whole store is
 * a few kilobytes — tokens, a handful of settings, chat drafts — so a full rewrite per edit is
 * cheap, and one sealed blob also hides the key names, which the old per-entry scheme did too.
 */
object PreferenceBlobCodec {

    /** The first line of every blob; a different one is a format this build cannot read. */
    const val HEADER = "coplanly-prefs-1"

    private const val SEPARATOR = ':'
    private const val SET_SEPARATOR = ','
    private const val FIELD_COUNT = 3

    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    /** The text for [values]. Null values are left out, as a `SharedPreferences` would. */
    fun encode(values: Map<String, Any?>): String = buildString {
        append(HEADER)
        values.toSortedMap().forEach { (key, value) ->
            val line = entry(key, value) ?: return@forEach
            append('\n').append(line)
        }
    }

    /**
     * The entries in [text].
     *
     * @throws IllegalArgumentException for a blob this build cannot read — a caller treats that as
     *   a store it has to start again, never as an empty one it may silently overwrite without
     *   saying so.
     */
    fun decode(text: String): Map<String, Any> {
        val lines = text.split('\n')
        require(lines.firstOrNull() == HEADER) { "Not a preference blob this build can read" }
        return lines.drop(1).filter { it.isNotEmpty() }.associate(::parseLine)
    }

    private fun entry(key: String, value: Any?): String? {
        val (type, payload) = when (value) {
            null -> return null
            is String -> "s" to b64(value)
            is Int -> "i" to value.toString()
            is Long -> "l" to value.toString()
            is Float -> "f" to value.toString()
            is Boolean -> "b" to value.toString()
            is Set<*> -> "S" to value.filterIsInstance<String>().sorted()
                .joinToString(SET_SEPARATOR.toString()) { b64(it) }
            else -> throw IllegalArgumentException("A preference cannot hold ${value::class.java.name}")
        }
        return "$type$SEPARATOR${b64(key)}$SEPARATOR$payload"
    }

    private fun parseLine(line: String): Pair<String, Any> {
        val parts = line.split(SEPARATOR, limit = FIELD_COUNT)
        require(parts.size == FIELD_COUNT) { "Malformed preference entry" }
        val (type, encodedKey, payload) = parts
        val value: Any = when (type) {
            "s" -> unb64(payload)
            "i" -> payload.toInt()
            "l" -> payload.toLong()
            "f" -> payload.toFloat()
            "b" -> payload.toBooleanStrict()
            "S" -> if (payload.isEmpty()) emptySet() else payload.split(SET_SEPARATOR).map(::unb64).toSet()
            else -> throw IllegalArgumentException("Unknown preference type '$type'")
        }
        return unb64(encodedKey) to value
    }

    private fun b64(text: String): String = encoder.encodeToString(text.toByteArray(Charsets.UTF_8))

    private fun unb64(text: String): String = String(decoder.decode(text), Charsets.UTF_8)
}
