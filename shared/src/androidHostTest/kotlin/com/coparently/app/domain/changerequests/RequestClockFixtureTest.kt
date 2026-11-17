package com.coparently.app.domain.changerequests

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [RequestClock] against `functions/test/fixtures/request-clock-cases.json`, the file the server's
 * `functions/request-clocks.js` is tested against too (MON-25), so the two implementations of the
 * clock's arithmetic cannot drift apart. Only `stateCases` are read here: `nextStepCases` are the
 * server's schedule and have no Kotlin counterpart.
 *
 * The file is found from the module's working directory, which Gradle sets to `shared/` for a JVM
 * test, so the path is `../functions/...`.
 */
class RequestClockFixtureTest {

    private val fixture: JsonObject = run {
        val file = File("../functions/test/fixtures/request-clock-cases.json")
        assertTrue(file.isFile, "the shared fixture is missing: ${file.absolutePath}")
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    private fun clockOf(json: JsonObject): RequestDeadline = RequestDeadline(
        id = "fixture",
        kind = RequestKind.SWAP,
        ref = "fixture",
        familyId = "family",
        proposerUid = "proposer",
        responderUid = "responder",
        answerByMillis = json.getValue("answerByMillis").jsonPrimitive.long(),
        capMillis = json["capMillis"]?.jsonPrimitive?.longOrNull,
        lastChanceUntilMillis = json["lastChanceUntilMillis"]?.jsonPrimitive?.longOrNull,
        outcome = RequestClockOutcome.fromWire(json["outcome"]?.jsonPrimitive?.contentOrNull)
    )

    private fun JsonPrimitive.long(): Long = checkNotNull(longOrNull) { "not a number: $this" }

    @Test
    fun fixtureIsWideEnough() {
        assertTrue(cases().size >= 30)
    }

    @Test
    fun everyCaseMatchesTheFixture() {
        for (case in cases()) {
            val name = case.getValue("name").jsonPrimitive.content
            val clock = clockOf(case.getValue("clock").jsonObject)
            val now = case.getValue("nowMillis").jsonPrimitive.long()
            val expectedState = case.getValue("state").jsonPrimitive.content
            val expectedWindow = case.getValue("lastChanceWindowMillis").jsonPrimitive.longOrNull
            val expectedEnd = case.getValue("endMillis").jsonPrimitive.long()

            assertEquals(expectedState, RequestClock.stateOf(clock, now).name, "$name: state")
            assertEquals(expectedWindow, RequestClock.lastChanceWindowMillis(clock, now), "$name: window")
            assertEquals(expectedEnd, RequestClock.endMillis(clock), "$name: end")
        }
    }

    private fun cases(): List<JsonObject> =
        (fixture.getValue("stateCases") as JsonArray).jsonArray.map { it.jsonObject }
}
