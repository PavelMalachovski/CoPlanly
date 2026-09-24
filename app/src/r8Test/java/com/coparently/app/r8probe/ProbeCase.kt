package com.coparently.app.r8probe

import org.json.JSONArray
import org.json.JSONObject

/**
 * One thing the probe checked, as `tools/check-r8-probe.js` reads it.
 *
 * [type] is a **string literal** naming the source type, never `X::class.java.name`: in this build
 * R8 has renamed most classes, and the report has to say which model failed in the words the
 * source uses. `tools/check-invariants.js` greps these literals to prove every Gson-reflected type
 * has a case, and the CI check requires each of them to pass.
 *
 * @property type The fully-qualified source name of the type checked, or a label for a call site
 * @property via The production path the value went through
 * @property ok Whether every check on it held
 * @property detail "ok", or every problem found, on one line
 * @property json The JSON the production code wrote, for the log
 */
internal data class ProbeCase(
    val type: String,
    val via: String,
    val ok: Boolean,
    val detail: String,
    val json: String
)

/**
 * Collects the cases of one production path ([via]).
 *
 * Each check compares what the production code *wrote* with what the source declares, and what it
 * read back with what went in. The cases are only built here; nothing is thrown, so one failing
 * type never hides the others.
 */
internal class Cases(private val via: String) {

    private val recorded = mutableListOf<ProbeCase>()

    /** Everything recorded so far. */
    val all: List<ProbeCase> get() = recorded.toList()

    /**
     * The keys of [json] (or of its first element, when it is an array) must be exactly
     * [expectedKeys] — the source field names — and the value must have read back equal.
     *
     * A renamed field shows up twice: its real name missing, and an `a` or `b` unexpected. A field
     * added to the model with a value in the fixture fails here as unexpected, which is the prompt
     * to name it; one left null is omitted by Gson and cannot be seen at all, which is why the
     * fixtures set every field.
     */
    fun keys(type: String, json: String, expectedKeys: Set<String>, readBackEqual: Boolean) {
        val actualKeys = keysOf(json)
        val problems = buildList {
            val missing = expectedKeys - actualKeys
            val unexpected = actualKeys - expectedKeys
            if (missing.isNotEmpty()) add("keys missing from the JSON: ${missing.sorted()}")
            if (unexpected.isNotEmpty()) add("keys the source does not declare: ${unexpected.sorted()}")
            if (!readBackEqual) add(NOT_EQUAL)
        }
        record(type, json, problems)
    }

    /** One written value, such as an enum constant's name, must be the source's [expected]. */
    fun value(type: String, json: String, expected: String, actual: String?) {
        record(type, json, if (actual == expected) emptyList() else listOf("wrote \"$actual\", not \"$expected\""))
    }

    /** The value must have survived the round trip; the JSON is logged, not inspected. */
    fun roundTrip(type: String, json: String, readBackEqual: Boolean) {
        record(type, json, if (readBackEqual) emptyList() else listOf(NOT_EQUAL))
    }

    private fun record(type: String, json: String, problems: List<String>) {
        recorded += ProbeCase(
            type = type,
            via = via,
            ok = problems.isEmpty(),
            detail = problems.joinToString("; ").ifEmpty { "ok" },
            json = json
        )
    }

    private fun keysOf(json: String): Set<String> {
        val trimmed = json.trimStart()
        val obj = if (trimmed.startsWith("[")) JSONArray(trimmed).getJSONObject(0) else JSONObject(trimmed)
        return obj.keys().asSequence().toSet()
    }

    private companion object {
        const val NOT_EQUAL = "did not read back equal to what was written"
    }
}

/**
 * Runs [block] over a fresh [Cases] for [via], turning anything it throws into a failed case for
 * each of [types].
 *
 * Throwable, not Exception, on purpose: the failures this probe exists to see include an
 * `ExceptionInInitializerError` from a `TypeToken` whose generic signature R8 stripped, raised
 * while an `object` initialises, and a `NoSuchMethodError`/`NoSuchFieldError` from a member R8
 * removed. Each is reported against the types it prevented from being checked, and the other
 * groups still run.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun guarded(types: List<String>, via: String, block: Cases.() -> Unit): List<ProbeCase> {
    val cases = Cases(via)
    return try {
        cases.block()
        cases.all
    } catch (failure: Throwable) {
        val detail = describe(failure)
        cases.all + types.map { ProbeCase(type = it, via = via, ok = false, detail = detail, json = "") }
    }
}

/** A throwable and its causes on one line; class names may be R8's, messages are the library's. */
internal fun describe(failure: Throwable): String =
    generateSequence(failure) { it.cause }
        .take(MAX_CAUSES)
        .joinToString(" <- ") { "threw ${it.javaClass.name}: ${it.message}" }
        .replace('\n', ' ')

private const val MAX_CAUSES = 4
