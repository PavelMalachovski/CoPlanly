package com.coparently.app.qa

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.coparently.app.R
import com.coparently.app.e2e.AliceOnScreenTest
import com.coparently.app.e2e.EmulatorEnvironment
import com.coparently.app.e2e.EmulatorParent
import com.coparently.app.e2e.UiTourDriver
import com.coparently.app.e2e.UiTourVariant
import com.coparently.app.testing.exists
import com.coparently.app.testing.pumpUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName

/**
 * The virtual tester's base: one parent on screen driving `MainActivity` through the real UI, the
 * co-parent beside them as the production data layer (`AliceOnScreenTest`), every step recorded
 * by [report] with a verdict and a screenshot.
 *
 * **The family.** The parents are named after the testers' own: Zuzana on screen and Igor beside
 * her — or, for a method whose name ends in `AsIgor`, the other way round, so the screen that
 * answers is exercised as well as the screen that proposes. Their daughter Lena and the second
 * family (Zuzana and Carol, Carol's son Max) are seeded by [QaFamily].
 *
 * **How a change reaches the screen** ([awaitDelivered]). The app has live listeners for the chat,
 * the custody document (its swaps and proposals included), change requests, request clocks and
 * the pairing (the family list); a change there must be drawn without anything else happening.
 * Events, expenses, children and pets arrive in production by sync — started by a push or the
 * periodic tick, neither of which exists on the emulator — so for them the tester runs the sync
 * itself, as `SyncWorker` would, and the report says so.
 *
 * Runs only with `-e coplanlyEmulatorHost` and `-e coplanlyVirtualTester true`
 * (`tools/virtual-tester/run-virtual-tester.sh`); the theme and language follow
 * `-e coplanlyUiTourVariant`, as in the UI tour.
 */
abstract class QaOnScreenTest : AliceOnScreenTest() {

    @get:Rule
    val testName = TestName()

    /** Whether this method puts Igor on screen. */
    private val igorOnScreen: Boolean
        get() = testName.methodName.orEmpty().endsWith(IGOR_SUFFIX)

    override val onScreenName: String
        get() = if (igorOnScreen) IGOR else ZUZANA

    override val coParentName: String
        get() = if (igorOnScreen) ZUZANA else IGOR

    /** The run's theme, language and font scale. */
    protected val variant: UiTourVariant by lazy { UiTourVariant.current() }

    private val localized: Context by lazy { variant.localized(context) }

    override val strings: Context
        get() = localized

    /** Navigation helpers shared with the UI tour. */
    protected val driver: UiTourDriver by lazy { UiTourDriver(composeTestRule, tabBar) }

    /** This method's record. */
    protected lateinit var report: QaReport

    override fun assumeRunnable() = EmulatorEnvironment.assumeVirtualTester()

    override fun beforeLaunch() = runBlocking { variant.applyTo(preferencesRepository) }

    /** Opens the record once the app is up (the base class's `@Before` has run by now). */
    @Before
    fun openReport() {
        report = QaReport(composeTestRule, variant, this::class.java.simpleName.removeSuffix("ScenarioTest"))
        report.begin(testName.methodName)
    }

    /** Fails the method when a step failed; runs before the base class closes the activity. */
    @After
    fun closeReport() {
        if (::report.isInitialized) report.finish()
    }

    /**
     * Waits until [matcher] is on screen and records in [step] how it got there.
     *
     * @param expectsListener Whether a live listener should draw it with nothing else happening.
     *   When it should and does not within [LISTENER_WAIT_MS], the sync is run anyway, so the steps
     *   after this one still mean something, and the step carries the finding.
     */
    protected fun awaitDelivered(step: QaStep, description: String, matcher: SemanticsMatcher, expectsListener: Boolean) {
        if (expectsListener && appeared(matcher, LISTENER_WAIT_MS)) {
            step.delivery = QaDelivery.LISTENER
            return
        }
        runBlocking { syncService.performFullSync().getOrThrow() }
        if (!appeared(matcher, SYNC_WAIT_MS)) throw AssertionError("$description did not appear")
        step.delivery = QaDelivery.FORCED_SYNC
        if (expectsListener) {
            step.findings += "needed a forced sync where a live listener should have delivered: $description"
        }
    }

    /** A clickable node whose text is the app's string [label]. */
    protected fun textButton(label: Int): SemanticsMatcher = hasText(string(label)) and hasClickAction()

    /** Switches the calendar through its title menu, which is also the Month/Week/Day picker. */
    protected fun viewMode(label: Int) {
        tap(hasContentDescription(string(R.string.calendar_change_view_mode)) and hasClickAction())
        tap(textButton(label))
    }

    /**
     * A third phone, [name], paired with the parent on screen through the real callable — the
     * second family. Nothing runs on the screen's side: the open app learns of the family from its
     * own pairing listener (item 39), and a scenario waits for that where it matters.
     */
    protected suspend fun pairThirdParent(name: String): EmulatorParent {
        val parent = newParent(name)
        val invite = pairingRepository.createOrReuseInviteCode().getOrThrow()
        parent.pairingRepository.redeem(invite.code).getOrThrow()
        parent.awaitPairedWith(aliceUid)
        return parent
    }

    /** Runs the co-parent's sync: their side is the data layer, so how it arrived is not reported. */
    protected fun coParentSync() {
        runBlocking { bob.syncService.performFullSync().getOrThrow() }
    }

    private fun appeared(matcher: SemanticsMatcher, timeoutMillis: Long): Boolean = try {
        composeTestRule.pumpUntil("delivery", timeoutMillis) { exists(matcher) }
        true
    } catch (e: AssertionError) {
        EmulatorEnvironment.step("qa: not drawn within ${timeoutMillis}ms: ${e.message}")
        false
    }

    companion object {
        const val ZUZANA = "Zuzana"
        const val IGOR = "Igor"
        private const val IGOR_SUFFIX = "AsIgor"

        /** How long a live listener may take to draw the other phone's change. */
        const val LISTENER_WAIT_MS = 30_000L

        /** How long a change may take to be drawn after a sync. */
        const val SYNC_WAIT_MS = 30_000L

        /** Puts the app back on the device's language, so nothing after the run inherits the tester's. */
        @JvmStatic
        @AfterClass
        fun resetLanguage() {
            if (EmulatorEnvironment.virtualTester && EmulatorEnvironment.host != null) UiTourVariant.resetLanguage()
        }
    }
}
