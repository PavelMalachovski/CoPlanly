package com.coparently.app.qa

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.e2e.EmulatorEnvironment
import com.coparently.app.e2e.UiTourVariant
import com.coparently.app.testing.settle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** A step's outcome, as the report prints it. */
enum class QaVerdict(val wire: String) {
    PASSED("passed"),
    FAILED("failed"),
    COULD_NOT_RUN("could_not_run")
}

/**
 * How a change made on the co-parent's phone reached the screen: by one of the app's live
 * listeners, or only after the tester ran the sync a push or the periodic tick would have started.
 */
enum class QaDelivery(val wire: String) {
    LISTENER("listener"),
    FORCED_SYNC("forced_sync")
}

/** Thrown by [QaStep.couldNotRun]: the step could not be attempted, which is not a failure of the app. */
class QaCouldNotRun(reason: String) : RuntimeException(reason)

/**
 * The step in progress: what it saw ([actual]), how a change reached the screen ([delivery]) and
 * what else a tester would flag ([findings]). A step fails by throwing — an `AssertionError` from
 * a matcher, a timeout — and says it could not run through [couldNotRun].
 */
class QaStep internal constructor() {
    /** What the step saw, in a sentence; the expected text when a passing step sets nothing. */
    var actual: String = ""

    /** How the change this step waited for arrived, when it waited for one. */
    var delivery: QaDelivery? = null

    /** Findings beside the verdict; [QaReport] adds the screen's visual findings after the step. */
    val findings: MutableList<String> = mutableListOf()

    /** Ends the step as could-not-run with [reason]. */
    fun couldNotRun(reason: String): Nothing = throw QaCouldNotRun(reason)
}

/**
 * The virtual tester's record: runs each step, gives it a verdict, photographs the screen after it
 * and appends one JSON line per step to `<files dir>/virtual-tester/<variant>/steps.jsonl`, which
 * `tools/virtual-tester/run-virtual-tester.sh` streams out and `tools/virtual-tester/report.js`
 * renders.
 *
 * Each line is written and flushed before the next step starts, so a run whose process dies still
 * leaves every finished step behind. After a step fails, the rest of the same test method is
 * recorded as could-not-run without being attempted — they build on it — unless a step is
 * declared `independent`.
 *
 * @param rule The compose rule whose (paused) clock the steps drive.
 * @param variant The run's variant; the directory the lines and screenshots go to.
 * @param scenario The scenario's name in the report, such as `Calendar`.
 */
class QaReport(
    private val rule: ComposeTestRule,
    private val variant: UiTourVariant,
    private val scenario: String
) {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.filesDir, "virtual-tester/${variant.name}").apply { mkdirs() }
    private val shots = File(directory, "shots").apply { mkdirs() }
    private val lines = File(directory, "steps.jsonl")
    private var method = ""
    private var blockedBy: String? = null
    private val failed = mutableListOf<String>()

    /** Starts a test method: steps after a failure in the previous method run again. */
    fun begin(method: String) {
        this.method = method
        blockedBy = null
        failed.clear()
    }

    /**
     * Runs [block] as the step [id] and records it.
     *
     * @param title What the step does, in a sentence the owner reads.
     * @param expected What a parent should see.
     * @param independent Whether the step stands on its own (such as one field of a recognised
     *   receipt): it runs after an earlier failure, and its own failure blocks nothing.
     */
    @Suppress("TooGenericExceptionCaught") // a step's failure is recorded, whatever threw it
    fun step(id: String, title: String, expected: String, independent: Boolean = false, block: QaStep.() -> Unit) {
        val started = System.currentTimeMillis()
        val step = QaStep()
        val blocker = blockedBy
        val verdict = if (blocker != null && !independent) {
            step.actual = "an earlier step failed: $blocker"
            QaVerdict.COULD_NOT_RUN
        } else {
            try {
                step.block()
                if (step.actual.isBlank()) step.actual = expected
                QaVerdict.PASSED
            } catch (e: QaCouldNotRun) {
                step.actual = e.message.orEmpty()
                QaVerdict.COULD_NOT_RUN
            } catch (e: Throwable) {
                step.actual = (e.message ?: e::class.java.simpleName).take(MAX_ACTUAL)
                if (!independent) blockedBy = id
                failed += id
                QaVerdict.FAILED
            }
        }
        val shot = screenshot(id, step)
        if (verdict != QaVerdict.COULD_NOT_RUN || blocker == null) {
            runCatching { step.findings += rule.visualFindings() }
                .onFailure { step.findings += "visual checks did not run: ${it.message}" }
        }
        write(id, title, expected, step, verdict, shot, started)
        EmulatorEnvironment.step("qa: $scenario/$id ${verdict.wire}")
    }

    /** Fails the test method, naming its failed steps, when any failed; the report already holds them. */
    fun finish() {
        if (failed.isNotEmpty()) throw AssertionError("$scenario.$method: failed steps ${failed.joinToString()}")
    }

    @Suppress("TooGenericExceptionCaught") // a missing screenshot is a finding, never a verdict
    private fun screenshot(id: String, step: QaStep): String? = try {
        rule.settle()
        // The render thread draws in real time, which the paused Compose clock does not move.
        SystemClock.sleep(RENDER_PAUSE_MS)
        val name = String.format(Locale.ROOT, "%03d_%s_%s.png", nextNumber(), scenario, id)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("UiAutomation returned no screenshot")
        FileOutputStream(File(shots, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        "virtual-tester/${variant.name}/shots/$name"
    } catch (e: Throwable) {
        step.findings += "no screenshot: ${e.message}"
        null
    }

    private fun nextNumber(): Int = (shots.list()?.size ?: 0) + 1

    private fun write(
        id: String,
        title: String,
        expected: String,
        step: QaStep,
        verdict: QaVerdict,
        shot: String?,
        started: Long
    ) {
        val line = JSONObject()
            .put("variant", variant.name)
            .put("scenario", scenario)
            .put("method", method)
            .put("id", id)
            .put("title", title)
            .put("expected", expected)
            .put("actual", step.actual)
            .put("verdict", verdict.wire)
            .put("delivery", step.delivery?.wire ?: JSONObject.NULL)
            .put("screenshot", shot ?: JSONObject.NULL)
            .put("findings", JSONArray(step.findings.distinct()))
            .put("startedAtMillis", started)
            .put("durationMs", System.currentTimeMillis() - started)
        FileOutputStream(lines, true).use { out ->
            out.write((line.toString() + "\n").toByteArray())
            out.fd.sync()
        }
    }

    private companion object {
        const val RENDER_PAUSE_MS = 600L
        const val PNG_QUALITY = 100
        const val MAX_ACTUAL = 600
    }
}
