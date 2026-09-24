package com.coparently.app.e2e

import android.util.Log
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import com.coparently.app.R
import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.sync.SyncService
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.model.Message
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
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * Alice's **real app**, on screen, against the emulators; Bob on the data layer of a second phone
 * in the same process. The part of the two-phone round the other e2e tests could not reach: they
 * prove the data arrives, and this proves it is *drawn* — and that what Alice types into the real
 * composer reaches Bob.
 *
 * Alice is the Hilt graph `MainActivity` builds. `FakeFirebaseModule` gives it the real Auth,
 * Firestore, Storage and Functions SDKs of [EmulatorEnvironment.appUnderTest] when the run has an
 * emulator host, and the Room database is the real SQLCipher one. She signs up on the Auth
 * emulator, `ensureProfile` writes her profile, and she is paired with Bob through the real
 * callable — the same steps as a phone, minus the onboarding screens, which are marked done so the
 * app opens on Home.
 *
 * Two things the app does from `CoPlanlyApplication.onCreate`, which `HiltTestApplication` never
 * runs, are started here by hand: the process-wide chat mirror, and — in place of the fifteen-
 * minute `SyncWorker` tick — one `SyncService.performFullSync()`, the call that worker makes.
 *
 * Still not covered, and kept on `docs/DEVICE-CHECKLIST.md`: the push that wakes a phone (FCM has
 * no emulator), and anything that needs two *screens* at once.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OneParentOnScreenTest {

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

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bottomBar = hasTestTag(BOTTOM_BAR_TEST_TAG)

    private var bob: EmulatorParent? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private var savedConsent: String? = null
    private lateinit var aliceUid: String

    @Before
    fun startAliceOnScreenAndBobBesideHer() {
        EmulatorEnvironment.assumeEmulators()
        initializeWorkManager()
        hiltRule.inject()
        savedConsent = encryptedPreferences.getString(PreferenceKeys.TELEMETRY_CONSENT, null)
        step("before: sign up Alice")
        runBlocking {
            aliceUid = signUpAlice()
            val coParent = EmulatorParent.create(context, "Bob")
            bob = coParent
            step("before: pair")
            val invite = pairingRepository.createOrReuseInviteCode().getOrThrow()
            coParent.pairingRepository.redeem(invite.code).getOrThrow()
            coParent.awaitPairedWith(aliceUid)
            withTimeout(EmulatorParent.WAIT_MS) {
                pairingRepository.observePairingState()
                    .filterIsInstance<PairingState.Paired>()
                    .first { it.partner.id == coParent.uid }
            }
            val row = checkNotNull(userDao.getUserById(aliceUid)) { "ensureProfile wrote no Room row" }
            userDao.updateUser(row.copy(onboardingCompletedAt = "2026-09-01T00:00:00"))
            // "No": nothing in a test should switch collection on.
            preferencesRepository.setTelemetryConsent(TelemetryConsent.DENIED)
        }
        chatMirror.start()
        step("before: launch MainActivity")

        composeTestRule.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.pumpUntil("Home with the bottom bar", HOME_TIMEOUT_MS) { exists(bottomBar) }
        composeTestRule.settle()
        step("before: Home is up")
    }

    @After
    fun tearDown() {
        // Skipped (no emulator host): nothing was injected, so there is nothing to undo.
        if (!::encryptedPreferences.isInitialized) return
        step("after: close")
        scenario?.close()
        bob?.close()
        runCatching { firebaseAuth.signOut() }
        // The database is a file on the emulator; nothing Alice's account wrote may outlive the test.
        runCatching { database.clearAllTables() }
        encryptedPreferences.putString(PreferenceKeys.TELEMETRY_CONSENT, savedConsent.orEmpty())
        step("after: done")
    }

    @Test
    fun whatBobSendsIsDrawnOnAlicesScreens_andWhatSheTypesReachesBob() {
        val bob = checkNotNull(this.bob)
        val conversationId = ConversationKey.of(aliceUid, bob.uid)

        // An event Bob saves for today appears on Alice's Home, once her sync has run.
        val eventTitle = "Dentist ${shortId()}"
        runBlocking {
            bob.eventRepository.insertEvent(eventForToday(bob, eventTitle))
            step("event: sync")
            syncService.performFullSync().getOrThrow()
        }
        step("event: wait for it on Home")
        composeTestRule.pumpUntil("Bob's event on Alice's Home", CROSS_DEVICE_TIMEOUT_MS) {
            exists(hasText(eventTitle, substring = true))
        }

        // A message Bob sends is drawn in Alice's thread.
        val fromBob = "Pickup moved to 5 pm ${shortId()}"
        runBlocking {
            bob.messageRepository.sendMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    senderId = bob.uid,
                    senderName = bob.name,
                    content = fromBob
                )
            )
        }
        step("chat: open the tab")
        openTab(BottomNavDestination.CHAT)
        step("chat: wait for Bob's message")
        composeTestRule.pumpUntil("Bob's message in Alice's thread", CROSS_DEVICE_TIMEOUT_MS) {
            exists(hasText(fromBob))
        }

        // What Alice types into the real composer arrives on Bob's phone.
        val fromAlice = "See you there ${shortId()}"
        step("chat: type")
        composeTestRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput(fromAlice)
        composeTestRule.settle()
        step("chat: send")
        composeTestRule.onAllNodes(hasContentDescription(string(R.string.chat_send)) and hasClickAction())
            .onFirst()
            .performClick()
        composeTestRule.settle()
        step("chat: wait for the reply on Bob's phone")
        runBlocking {
            withTimeout(EmulatorParent.WAIT_MS) {
                bob.messageRepository.observeMessages(conversationId).first { list ->
                    list.any { it.content == fromAlice && it.senderId == aliceUid }
                }
            }
        }
    }

    /**
     * WorkManager as the app would have it. The manifest removes WorkManager's own initializer
     * because `CoPlanlyApplication` is its `Configuration.Provider` — and `HiltTestApplication`
     * is not, so the first `WorkManager.getInstance` threw. The pairing path reaches it
     * (`SyncRequester` on the Paired transition), the throw landed in `observePairingState`'s
     * `catch`, and the flow ended before Alice ever saw herself paired. Nothing here runs the
     * enqueued work; the test drives `SyncService` itself.
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

    /** An event on today's date, owned by Bob's slot, starting soon enough to still be today. */
    private suspend fun eventForToday(bob: EmulatorParent, title: String): Event {
        val today = LocalDate.now()
        val start = minOf(LocalDateTime.now().plusMinutes(2), today.atTime(LATEST_START))
            .withSecond(0).withNano(0)
        return Event(
            id = UUID.randomUUID().toString(),
            title = title,
            startDateTime = start,
            endDateTime = start.plusMinutes(1),
            eventType = "appointment",
            parentOwner = bob.database.userDao().getUserById(bob.uid)?.role ?: "dad",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun openTab(destination: BottomNavDestination) {
        composeTestRule.onNode(
            hasAnyAncestor(bottomBar) and hasText(string(destination.labelRes)) and hasClickAction()
        ).performClick()
        composeTestRule.settle()
    }

    private fun string(id: Int): String = context.getString(id)

    private fun shortId(): String = UUID.randomUUID().toString().take(SHORT_ID_LENGTH)

    private companion object {
        const val TAG = "OneParentOnScreenTest"
        const val PASSWORD = "e2e-password-1"
        const val HOME_TIMEOUT_MS = 60_000L
        const val CROSS_DEVICE_TIMEOUT_MS = 45_000L
        const val SHORT_ID_LENGTH = 6
        val LATEST_START: LocalTime = LocalTime.of(23, 58)
    }
}
