@file:OptIn(InternalSharedApi::class)

package com.coparently.app.e2e

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
import com.coparently.app.data.session.AccountSwitchGuard
import com.coparently.app.data.stickers.ParentRoles
import com.coparently.app.data.sync.SyncService
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.model.PairingState
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.stickers.ParentRole
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.e2e.EmulatorEnvironment.step
import com.coparently.app.presentation.MainActivity
import com.coparently.app.presentation.navigation.BOTTOM_BAR_TEST_TAG
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.presentation.navigation.NAVIGATION_RAIL_TEST_TAG
import com.coparently.app.testing.KoinTestRule
import com.coparently.app.testing.UsesFakeFirebase
import com.coparently.app.testing.exists
import com.coparently.app.testing.pumpUntil
import com.coparently.app.testing.settle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import java.util.UUID

/**
 * Alice's **real app**, on screen, against the emulators, with Bob on the data layer of a second
 * phone in the same process — the setup every on-screen e2e test shares.
 *
 * Alice is the Koin graph `MainActivity` runs on. `fakeFirebaseModule` gives it the real Auth,
 * Firestore, Storage and Functions SDKs of [EmulatorEnvironment.appUnderTest] when the run has an
 * emulator host, and the Room database is the real SQLCipher one. She signs up on the Auth
 * emulator, `ensureProfile` writes her profile, and she is paired with Bob through the real
 * callable — the same steps as a phone, minus the onboarding screens, which are marked done so the
 * app opens on Home.
 *
 * Two things the app does from `CoPlanlyApplication.onCreate`, which `KoinTestApplication` never
 * runs, are started here by hand: the process-wide chat mirror, and — where a test needs it, in
 * place of the fifteen-minute `SyncWorker` tick — `SyncService.performFullSync()`, the call that
 * worker makes.
 *
 * **The Compose clock is paused** (`testing/PausedClock.kt`): the skeletons animate for as long
 * as a screen waits, so every wait here moves Compose time itself and is bounded in
 * real time. Every text a test looks for is read from the app's own string resources, so a test
 * does not depend on the emulator's language.
 *
 * A subclass carries `@UsesFakeFirebase`, as this class does; [koinRule] starts the graph the
 * fields below are resolved from.
 * Without the emulator host argument every test skips itself in [startAliceOnScreenAndBobBesideHer].
 */
@UsesFakeFirebase
abstract class AliceOnScreenTest {

    @get:Rule(order = 0)
    val koinRule = KoinTestRule()

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    /** Every thread's stack in logcat when the test fails; outside [timeout], so it sees a hang. */
    @get:Rule(order = 2)
    val threadDump: TestWatcher = EmulatorEnvironment.threadDumpOnFailure()

    /** Innermost, so it bounds `@Before` and `@After` as well as the test body. */
    @get:Rule(order = 3)
    val timeout: Timeout = EmulatorEnvironment.testTimeout()

    lateinit var firebaseAuth: FirebaseAuth

    lateinit var userRepository: UserRepository

    lateinit var userDao: UserDao

    lateinit var pairingRepository: PairingRepository

    lateinit var preferencesRepository: PreferencesRepository

    lateinit var encryptedPreferences: EncryptedPreferences

    lateinit var chatMirror: ChatMirror

    lateinit var syncService: SyncService

    lateinit var database: CoPlanlyDatabase

    lateinit var accountSwitchGuard: AccountSwitchGuard

    lateinit var parentRoles: ParentRoles

    protected val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The tab bar — the bottom bar on a phone, the rail on a wide window (the UI tour's `-wide`
     * variant); its presence is what says a tab is on screen. Both carry the same four tabs, so a
     * tab is tapped by its label on either.
     */
    protected val tabBar: SemanticsMatcher = hasTestTag(BOTTOM_BAR_TEST_TAG) or hasTestTag(NAVIGATION_RAIL_TEST_TAG)

    private var coParent: EmulatorParent? = null
    private val otherPhones = mutableListOf<EmulatorParent>()
    private var scenario: ActivityScenario<MainActivity>? = null

    /**
     * The chat mirror, run in a scope this test owns rather than through `ChatMirror.start()`,
     * whose process-lifetime scope nothing cancels: every test's mirror used to live on into the
     * next, following the process-wide FirebaseAuth to the next Alice and writing her rows through
     * a database instance the next test's Room observers never hear from.
     */
    private var chatMirrorScope: CoroutineScope? = null
    private var savedConsent: String? = null
    private var savedPause: Boolean = true

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

    /**
     * The display name of the parent on screen. The virtual tester (`com.coparently.app.qa`) names
     * the parents after the testers' own family; every other test keeps Alice.
     */
    protected open val onScreenName: String = "Alice"

    /** The display name of the co-parent beside her, Bob unless a subclass names them. */
    protected open val coParentName: String = "Bob"

    /** Alice's uid, once `@Before` has signed her up. */
    protected lateinit var aliceUid: String

    /** Bob's phone: the data layer of a second parent, paired with Alice before the test. */
    protected val bob: EmulatorParent
        get() = checkNotNull(coParent) { "Bob's phone was not started" }

    @Before
    fun startAliceOnScreenAndBobBesideHer() {
        assumeRunnable()
        initializeWorkManager()
        injectFromGraph()
        savedConsent = encryptedPreferences.getString(PreferenceKeys.TELEMETRY_CONSENT, null)
        savedPause = encryptedPreferences.getBoolean(PreferenceKeys.CHAT_PAUSE_BEFORE_SENDING, true)
        step("before: sign up Alice")
        runBlocking {
            aliceUid = signUpAlice()
            val phone = EmulatorParent.create(context, coParentName)
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
            // Off, so a message typed into the composer leaves at once: these tests are about what
            // reaches the other phone, and the pause (on by default) is ChatViewModel's own test's.
            preferencesRepository.setPauseBeforeSending(false)
            // Answered, so Home's one question about how stickers address her — which only an answer
            // closes — does not stand over every screen the tests walk.
            check(parentRoles.setMine(ParentRole.MOM)) { "Alice's sticker role was not saved" }
        }
        chatMirrorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope ->
            scope.launch { chatMirror.mirror() }
        }
        beforeLaunch()
        step("before: launch MainActivity")

        composeTestRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        if (opensOnHome) {
            composeTestRule.pumpUntil("Home with the tab bar", HOME_TIMEOUT_MS) { exists(tabBar) }
        }
        composeTestRule.settle()
        step("before: the app is up")
    }

    /**
     * Resolves the fields above from the test's Koin graph, at the point Hilt's field injection
     * used to run: after [assumeRunnable], so a skipped test resolves nothing. A subclass's own
     * fields are resolved lazily, on first use.
     */
    private fun injectFromGraph() {
        firebaseAuth = koinRule.get()
        userRepository = koinRule.get()
        userDao = koinRule.get()
        pairingRepository = koinRule.get()
        preferencesRepository = koinRule.get()
        encryptedPreferences = koinRule.get()
        chatMirror = koinRule.get()
        syncService = koinRule.get()
        database = koinRule.get()
        accountSwitchGuard = koinRule.get()
        parentRoles = koinRule.get()
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
        // Skipped (no emulator host): nothing was resolved, so there is nothing to undo.
        if (!::encryptedPreferences.isInitialized) return
        step("after: close")
        scenario?.close()
        chatMirrorScope?.cancel()
        otherPhones.forEach { it.close() }
        coParent?.close()
        runCatching { firebaseAuth.signOut() }
        // The database is a file on the emulator; nothing Alice's account wrote may outlive the test.
        runCatching { database.clearAllTables() }
        encryptedPreferences.putString(PreferenceKeys.TELEMETRY_CONSENT, savedConsent.orEmpty())
        encryptedPreferences.putBoolean(PreferenceKeys.CHAT_PAUSE_BEFORE_SENDING, savedPause)
        // Each test builds a new Koin graph (a new Room instance on the same file) but shares the
        // process-wide FirebaseAuth and Firestore. This graph's shared streams (the custody mirror,
        // ParentsSource, FamilyKindSource) stay up for their WhileSubscribed(5 s) after the
        // activity closes, and in that window they followed the *next* test's account: the stale
        // custody mirror wrote the next pair's day swap into the file first, the current mirror's
        // equality guard then skipped its own write, and the current instance's observers were
        // never invalidated — Home never showed Bob's second swap. Let them lapse first.
        SystemClock.sleep(STALE_GRAPH_LINGER_MS)
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

    /** Opens one of the four tabs through the tab bar. */
    protected fun openTab(destination: BottomNavDestination) {
        tap(hasAnyAncestor(tabBar) and hasText(string(destination.labelRes)) and hasClickAction())
    }

    /**
     * Opens Expenses the way a parent does since the Family tab replaced its tab (new-user review,
     * October 2026): the tab, then its money card.
     */
    protected fun openExpenses() {
        openTab(BottomNavDestination.FAMILY)
        tap(hasText(string(R.string.family_money_this_month), substring = true) and hasClickAction())
        waitFor("Expenses", hasText(string(R.string.expenses_title)))
    }

    /**
     * Opens a row of the Family tab by its title, through the row's click action: the row may sit
     * below the fold, and `performScrollTo` waits on a scroll the paused clock never advances.
     */
    protected fun openFamilyRow(titleRes: Int) {
        openTab(BottomNavDestination.FAMILY)
        val row = hasText(string(titleRes)) and hasClickAction()
        waitFor("the ${string(titleRes)} row on the Family tab", row)
        composeTestRule.onAllNodes(row).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.settle()
    }

    /**
     * Types [text] into the chat composer on screen and presses Send.
     *
     * It also logs what the composer and the send button held around the tap. A message typed here
     * once never reached the other phone, and the run's log could not say whether it was never
     * sent (the field lost the text, or the button was disabled) or sent and lost on the way
     * ([logLocalMessages] answers that half).
     */
    protected fun sendInChat(text: String) {
        composeTestRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text)
        composeTestRule.settle()
        val send = hasContentDescription(string(R.string.chat_send)) and hasClickAction()
        val typed = hasSetTextAction() and hasText(text)
        step(
            "chat: before the tap, the composer holds the text: ${composeTestRule.exists(typed)}, " +
                "send is enabled: ${composeTestRule.exists(send and isEnabled())}"
        )
        tap(send)
        step("chat: after the tap, the composer still holds the text: ${composeTestRule.exists(typed)}")
    }

    /**
     * Logs every row Alice's Room holds in [conversationId] — content, status and whether it
     * reached the server — so that a message which never arrived on the other phone shows where it
     * stopped. At info level under the e2e tag, beside the timed steps the CI job prints.
     */
    protected fun logLocalMessages(conversationId: String) {
        val rows = runBlocking { database.messageDao().getMessagesOnce(conversationId) }
        Log.i(EmulatorEnvironment.LOG_TAG, "Alice's Room holds ${rows.size} message(s) in $conversationId")
        rows.forEach { row ->
            Log.i(
                EmulatorEnvironment.LOG_TAG,
                "  from ${row.senderId}: \"${row.content}\" status=${row.status} synced=${row.syncedToFirestore}"
            )
        }
    }

    /** The app's own string, in the language [strings] reads — the emulator's, unless a test switched it. */
    protected fun string(id: Int, vararg args: Any): String =
        if (args.isEmpty()) strings.getString(id) else strings.getString(id, *args)

    /** A few characters that make a title or a message unique to this run. */
    protected fun shortId(): String = UUID.randomUUID().toString().take(SHORT_ID_LENGTH)

    /**
     * WorkManager as the app would have it. The manifest removes WorkManager's own initializer
     * because `CoPlanlyApplication` is its `Configuration.Provider` — and `KoinTestApplication`
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
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(onScreenName).build()).await()
        // What signing in does first (`SessionProfileSynchronizer`): this emulator remembers the
        // previous test's Alice, and the guard would otherwise wipe Room at her first sync,
        // mid-test — taking her profile row, and with it the family switcher, off the screen.
        accountSwitchGuard.ensureAccountConsistency()
        userRepository.ensureProfile()
        return user.uid
    }

    companion object {
        private const val TAG = "AliceOnScreenTest"
        private const val PASSWORD = "e2e-password-1"
        private const val HOME_TIMEOUT_MS = 60_000L
        private const val SHORT_ID_LENGTH = 6

        /** Past every `WhileSubscribed(5_000)` in the app's process-lifetime singletons, with margin. */
        private const val STALE_GRAPH_LINGER_MS = 6_000L

        /** How long something the other phone did may take to be drawn on Alice's screen. */
        const val CROSS_DEVICE_TIMEOUT_MS = 45_000L
    }
}
