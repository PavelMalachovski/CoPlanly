package com.coparently.app.wire

/**
 * How two wire documents are compared: by what a reader can tell apart, not by Kotlin type.
 *
 * Two rules, both what every mapper in the app already does when it reads:
 * - **Numbers compare by value.** Firestore stores one integer type, and a mapper that writes an
 *   `Int` has it read back as a `Long`; a whole `Double` (`40.0`) is the same amount as `40`.
 * - **A null is an absent key.** Every reader uses `as?`, so a stored null and a missing field
 *   read the same.
 *
 * A contract may add a third for its own keys ([WireContract.blankMeansAbsent]): `""` read the
 * same as absent, because its reader does `ifBlank { null }` and its two writers disagree on
 * which of the two they write.
 */
internal object WireCompare {

    private val LONG_RANGE = Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()

    /** [value] with its numbers widened and its nulls dropped, so `==` means "reads the same". */
    fun normalize(value: Any?): Any? = when (value) {
        null -> null
        is Map<*, *> ->
            value.entries
                .filter { it.value != null }
                .associate { (key, entry) -> key.toString() to normalize(entry) }
                .toSortedMap()
        is Iterable<*> -> value.map(::normalize)
        is Array<*> -> value.map(::normalize)
        is Double -> if (value % 1.0 == 0.0 && value in LONG_RANGE) value.toLong() else value
        is Float -> normalize(value.toDouble())
        is Number -> value.toLong()
        is Enum<*> -> value.name
        else -> value
    }

    /**
     * Every path in [before] whose value [after] does not carry, dotted (`medicalProfile.bloodType`).
     *
     * A map is kept when each of its keys is kept, recursively — so a writer that *adds* a key to
     * a sub-map (a default it fills in) has lost nothing — while a list or a scalar must be equal.
     * [blankMeansAbsent] names top-level keys where `""` and absent are one value.
     */
    fun lostPaths(
        before: Map<String, Any?>,
        after: Map<String, Any?>,
        blankMeansAbsent: Set<String> = emptySet()
    ): Set<String> = buildSet {
        before.forEach { (key, value) ->
            val blank = key in blankMeansAbsent
            val was = if (blank) value.orBlankAsNull() else value
            val now = if (blank) after[key].orBlankAsNull() else after[key]
            addAll(lost(key, was, now))
        }
    }

    private fun lost(path: String, was: Any?, now: Any?): Set<String> {
        if (was == null) return emptySet()
        if (was is Map<*, *> && now is Map<*, *>) {
            return was.entries.flatMapTo(mutableSetOf()) { (key, value) -> lost("$path.$key", value, now[key]) }
        }
        return if (normalize(was) == normalize(now)) emptySet() else setOf(path)
    }

    private fun Any?.orBlankAsNull(): Any? = if (this is String && isEmpty()) null else this
}
