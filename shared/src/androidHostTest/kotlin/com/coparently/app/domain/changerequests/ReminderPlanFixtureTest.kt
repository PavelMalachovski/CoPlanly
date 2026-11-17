package com.coparently.app.domain.changerequests

import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ReminderPlan] against `functions/test/fixtures/reminder-plan-cases.json`, the file the server's
 * `functions/reminder-plan.js` is tested against too (iPhone phase 4 part C, spec §7.3): the server
 * plans the reminders of a clock no phone delivered, so its arithmetic must be the phone's.
 *
 * The file is found from the module's working directory, which Gradle sets to `shared/` for a JVM
 * test, so the path is `../functions/...`.
 */
class ReminderPlanFixtureTest {

    private val fixture: JsonObject = run {
        val file = File("../functions/test/fixtures/reminder-plan-cases.json")
        assertTrue(file.isFile, "the shared fixture is missing: ${file.absolutePath}")
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test
    fun fixtureIsWideEnough() {
        assertTrue(cases("planCases").size >= MIN_PLAN_CASES)
        assertTrue(cases("quietCases").size >= MIN_QUIET_CASES)
    }

    @Test
    fun everyPlanMatchesTheFixture() {
        for (case in cases("planCases")) {
            val name = case.getValue("name").jsonPrimitive.content
            val plan = ReminderPlan.plan(
                sentAtMillis = case.getValue("sentAtMillis").jsonPrimitive.long,
                answerByMillis = case.getValue("answerByMillis").jsonPrimitive.long,
                notBeforeMillis = case.getValue("notBeforeMillis").jsonPrimitive.long,
                zone = TimeZone.of(case.getValue("zone").jsonPrimitive.content)
            )
            val expected = case.getValue("plan").jsonArray.map { it.jsonPrimitive.long }
            assertEquals(expected, plan, name)
        }
    }

    @Test
    fun everyQuietAnswerMatchesTheFixture() {
        for (case in cases("quietCases")) {
            val name = case.getValue("name").jsonPrimitive.content
            val quiet = ReminderPlan.isQuietAt(
                case.getValue("millis").jsonPrimitive.long,
                TimeZone.of(case.getValue("zone").jsonPrimitive.content)
            )
            assertEquals(case.getValue("quiet").jsonPrimitive.boolean, quiet, name)
        }
    }

    private fun cases(key: String): List<JsonObject> = fixture.getValue(key).jsonArray.map { it.jsonObject }

    private companion object {
        const val MIN_PLAN_CASES = 25
        const val MIN_QUIET_CASES = 5
    }
}
