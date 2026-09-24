package com.coparently.app.e2e

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import com.coparently.app.R
import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.sync.SyncService
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.MainActivity
import com.coparently.app.presentation.navigation.BOTTOM_BAR_TEST_TAG
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.testing.exists
import com.coparently.app.testing.pumpUntil
import com.coparently.app.testing.settle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import java.util.UUID
import javax.inject.Inject

/**
 * Alice's **real app**, on screen, against the emulators, with Bob on the data layer of a second
 * phone in the same process — the setup every on-screen e2e test shares.
 *
 * Alice is the Hilt graph `MainActivity` builds. `FakeFirebaseModule` gives it the real Auth,
 * Firestore, Storage and Functions SDKs of [EmulatorEnvironment.appUnderTest] when the run has an
 * emulator host, and the Room database is the real SQLCipher one. She signs up on the Auth
 * emulator, `ensureProfile` writes her profile, and she is paired with Bob through the real
 * callable — the same steps as a phone, minus the onboarding screens, which are marked done so the
 * app opens on Home.
 *
 * Two things the app does from `CoPlanlyApplication.onCreate`, which `HiltTestApplication` never
 * runs, are started here by hand: the process-wide chat mirror, and — where a test needs it, in
 * place of the fifteen-minute `SyncWorker` tick — `SyncService.performFullSync()`, the call that
 * worker makes.
 *
 * **The Compose clock is paused** (`testing/PausedClock.kt`): the skeletons animate for as long
 * as a screen waits, so every wait here moves Compose time itself and is bounded in
 * real time. Every text a test looks for is read from the app's own string resources, so a test
 * does not depend on the emulator's language.
 *
 * A subclass is a `@HiltAndroidTest`; the fields below are injected into it with its own.
 * Without the emulator host argument every test skips itself in [startAliceOnScreenAndBobBesideHer].
 */
abstract class AliceOnScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    /** Every thread's stack in logcat when the test fails; outside [timeout], so it sees a hang. */
    @get:Rule(order = 2)
    val threadDump: TestWatcher = EmulatorEnvironment.threadDumpOnFailure()

    /** Innermost, so it bounds `@Before` and `@After` as well as the test body. */
    @get:Rule(order = 3)
    val timeout: Timeout = EmulatorEnvironment.testTimeout()

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userDao: UserDao

    @Inject
    lateinit var pairingRepository: PairingRepository

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var encryptedPreferences: EncryptedPreferences

    @Inject
    lateinit var chatMirror: ChatMirror

    @Inject
    lateinit var syncService: SyncService

    @Inject
    lateinit var database: CoPlanlyDatabase

    protected val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The bottom navigation bar; its presence is what says a tab is on screen. */
    protected val bottomBar: SemanticsMatcher = hasTestTag(BOTTOM_BAR_TEST_TAG)

    private var coParent: EmulatorParent? = null
    private val otherPhones = mutableListOf<EmulatorParent>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var savedConsent: String? = null

    /**
     * Whether `@Before` marks Alice's onboarding done and waits for Home. `UiTourOnboardingTest`
     * turns it off to open the app on the first-run wizard instead.
     */
    protected open val opensOnHome: Boolean = true

    /**
     * Where [string] reads the app's text from. The application context by default; a test that
     * switches the app's language (the UI tour) points it at that language, because on API 32 and
     * below AppCompat's per-app locale reaches activities only, not the application context.
     */
    protected open val strings: Context
        get() = context

    /** Alice's uid, once `@Before` has signed her up. */
    protected lateinit var aliceUid: String

    /** Bob's phone: the data layer of a second parent, paired with Alice before the test. */
    protected val bob: EmulatorParent
        get() = checkNotNull(coParent) { "Bob's phone was not started" }

    @Before
    fun startAliceOnScreenAndBobBesideHer() {
        assumeRunnable()
        initializeWorkManager()
        hiltRule.inject()
        savedConsent = encryptedPreferences.getString(PreferenceKeys.TELEMETRY_CONSENT, null)
        step("before: sign up Alice")
        runBlocking {
            aliceUid = signUpAlice()
            val phone = EmulatorParent.create(context, "Bob")
            coParent = phone
            step("before: pair")
            val invite = pairingRepository.createOrReuseInviteCode().getOrThrow()
            phone.pairingRepository.redeem(invite.code).getOrThrow()
            phone.awaitPairedWith(aliceUid)
            withTimeout(EmulatorParent.WAIT_MS) {
                pairingRepository.observePairingState()
                    .filterIsInstance<PairingState.Paired>()
                    .first { it.partner.id == phone.uid }
            }
            val row = checkNotNull(userDao.getUserById(aliceUid)) { "ensureProfile wrote no Room row" }
            if (opensOnHome) userDao.updateUser(row.copy(onboardingCompletedAt = "2026-09-01T00:00:00"))
            // "No": nothing in a test should switch collection on.
            preferencesRepository.setTelemetryConsent(TelemetryConsent.DENIED)
        }
        chatMirror.start()
        beforeLaunch()
        step("before: launch MainActivity")

        composeTestRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        if (opensOnHome) {
            composeTestRule.pumpUntil("Home with the bottom bar", HOME_TIMEOUT_MS) { exists(bottomBar) }
        }
        composeTestRule.settle()
        step("before: the app is up")
    }

    /**
     * Skips the test unless this run can do it: the emulator host by default, and the UI tour's own
     * switch on top of it for the tour. Called first in `@Before`, before anything is signed up.
     */
    protected open fun assumeRunnable() {
        EmulatorEnvironment.assumeEmulators()
    }

    /** Runs after Alice is signed up and paired, just before `MainActivity` starts: theme, language. */
    protected open fun beforeLaunch() = Unit

    @After
    fun tearDown() {
        // Skipped (no emulator host): nothing was injected, so there is nothing to undo.
        if (!::encryptedPreferences.isInitialized) return
        step("after: close")
        scenario?.close()
        otherPhones.forEach { it.close() }
        coParent?.close()
        runCatching { firebaseAuth.signOut() }
        // The database is a file on the emulator; nothing Alice's account wrote may outlive the test.
        runCatching { database.clearAllTables() }
        encryptedPreferences.putString(PreferenceKeys.TELEMETRY_CONSENT, savedConsent.orEmpty())
        step("after: done")
    }

    /** Starts a third phone for a brand-new account; closed after the test like Bob's. */
    protected suspend fun newParent(name: String): EmulatorParent =
        EmulatorParent.create(context, name).also { otherPhones += it }

    /** Moves Compose time until [matcher] is on screen, failing after [timeoutMillis] of real time. */
    protected fun waitFor(
        description: String,
        matcher: SemanticsMatcher,
        timeoutMillis: Long = CROSS_DEVICE_TIMEOUT_MS
    ) {
        step("wait: $description")
        composeTestRule.pumpUntil(description, timeoutMillis) { exists(matcher) }
    }

    /** Taps the first node matching [matcher] and lets what it started settle. */
    protected fun tap(matcher: SemanticsMatcher) {
        composeTestRule.onAllNodes(matcher).onFirst().performClick()
        composeTestRule.settle()
    }

    /** A button labelled by [labelRes] inside the dialog on screen. */
    protected fun dialogButton(labelRes: Int): SemanticsMatcher =
        hasAnyAncestor(isDialog()) and hasText(string(labelRes)) and hasClickAction()

    /** Opens one of the four tabs through the bottom bar. */
    protected fun openTab(destination: BottomNavDestination) {
        tap(hasAnyAncestor(bottomBar) and hasText(string(destination.labelRes)) and hasClickAction())
    }

    /** Types [text] into the chat composer on screen and presses Send. */
    protected fun sendInChat(text: String) {
        composeTestRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        composeTestRule.settle()
        tap(hasContentDescription(string(R.string.chat_send)) and hasClickAction())
    }

    /** The app's own string, in the language [strings] reads — the emulator's, unless a test switched it. */
    protected fun string(id: Int, vararg args: Any): String =
        if (args.isEmpty()) strings.getString(id) else strings.getString(id, *args)

    /** A few characters that make a title or a message unique to this run. */
    protected fun shortId(): String = UUID.randomUUID().toString().take(SHORT_ID_LENGTH)

    /**
     * WorkManager as the app would have it. The manifest removes WorkManager's own initializer
     * because `CoPlanlyApplication` is its `Configuration.Provider` — and `HiltTestApplication`
     * is not, so the first `WorkManager.getInstance` threw. The pairing path reaches it
     * (`SyncRequester` on the Paired transition), the throw landed in `observePairingState`'s
     * `catch`, and the flow ended before Alice ever saw herself paired. Nothing here runs the
     * enqueued work; the tests drive `SyncService` themselves.
     */
    private fun initializeWorkManager() {
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
        } catch (e: IllegalStateException) {
            // Already initialised in this process — the state this needs.
            Log.i(TAG, "WorkManager was already initialised", e)
        }
    }

    /** Signs Alice up on the app's own Auth and writes her profile the way the app does. */
    private suspend fun signUpAlice(): String {
        firebaseAuth.signOut()
        val email = "alice-${UUID.randomUUID()}@e2e.coplanly.test"
        val user = checkNotNull(firebaseAuth.createUserWithEmailAndPassword(email, PASSWORD).await().user)
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName("Alice").build()).await()
        userRepository.ensureProfile()
        return user.uid
    }

    companion object {
        private const val TAG = "AliceOnScreenTest"
        private const val PASSWORD = "e2e-password-1"
        private const val HOME_TIMEOUT_MS = 60_000L
        private const val SHORT_ID_LENGTH = 6

        /** How long something the other phone did may take to be drawn on Alice's screen. */
        const val CROSS_DEVICE_TIMEOUT_MS = 45_000L
    }
}
