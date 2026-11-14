package com.coparently.app.data.session

import androidx.room.useReaderConnection
import com.coparently.app.data.local.ROOM_TABLES
import com.coparently.app.data.local.entity.EventEntity
import com.coparently.app.data.remote.firebase.AuthEmulator
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.FirestoreFamilyDataSource
import com.coparently.app.data.remote.firebase.FirestorePushQueue
import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.data.remote.firebase.GitLiveFirestoreClient
import com.coparently.app.data.remote.firebase.IosCallableFunctions
import com.coparently.app.data.remote.firebase.IosFirebaseAuth
import com.coparently.app.data.remote.firebase.runOnMainRunLoop
import com.coparently.app.data.repository.UserRepositoryImpl
import com.coparently.app.data.session.AccountSwitchFixture.Companion.EDUPAGE_KEY
import com.coparently.app.data.session.AccountSwitchFixture.Companion.ORDINARY_KEY
import com.coparently.app.data.session.AccountSwitchFixture.Companion.SCHOOL_KEY
import com.coparently.app.data.session.AccountSwitchFixture.Companion.SLOT_MARKER_KEY
import com.coparently.app.di.koin.FUNCTIONS_REGION
import com.coparently.app.di.koin.NoPush
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.initialize
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * [IosAccountDeletion] on the Firebase emulators (MON-24 phase 3, part B, Task 11): the real
 * `deleteAccount` callable on the Functions emulator, the real Auth emulator, and this phone's real
 * database on the bundled driver, sealed store and files ([AccountSwitchFixture]).
 *
 * - With the server reached, the account is gone from the Auth emulator, every Room table is empty,
 *   the sealed store holds nothing but the per-uid slot markers `clear()` keeps by design on both
 *   platforms (both school connections included in what goes), the account's files are removed, the
 *   reminders and request clocks are cancelled and nobody is signed in on the phone.
 * - With the Functions emulator unreachable (the same production [IosCallableFunctions], pointed at
 *   a port nothing listens on), the deletion fails and nothing on the phone is deleted: the event row,
 *   the preferences, the files and the session are all still there, to be tried again.
 *
 * Each case signs a fresh account up on a Firebase app of its own (named, never deleted, as the write
 * gate's), so neither depends on the other or on any other test. It runs where [AuthEmulator]'s tests
 * run, under the same rule: without an emulator host it prints [AuthEmulator.SKIPPED] and returns,
 * and fails instead under `COPLANLY_REQUIRE_EMULATORS=1`; the `ios-app` job runs it with one.
 */
class IosAccountDeletionTest {

    @Test
    fun `the deleted account leaves nothing on the phone or on the Auth emulator`() =
        onEmulators("account deletion") { host ->
            val phone = DeletionPhone.create("deletion_reached", host, FUNCTIONS_PORT)
            try {
                phone.fill()

                val result = phone.deletion.deleteAccount()

                assertTrue(result.isSuccess, "The deletion failed: ${result.exceptionOrNull()}")
                assertEquals(emptyMap(), phone.rowCounts().filterValues { it > 0 }, "Rows are left in Room")
                assertEquals(setOf(SLOT_MARKER_KEY), phone.fixture.preferences.keysWithPrefix(""))
                phone.fixture.traceFiles.forEach { assertFalse(phone.fixture.exists(it), "$it is still there") }
                assertEquals(1, phone.fixture.reminders.cancelledAll)
                assertEquals(1, phone.fixture.clocks.cancelledAll)
                assertNull(phone.auth.getCurrentUid(), "The phone is still signed in")
                assertTrue(
                    phone.auth.signInWithEmail(phone.email, PASSWORD).isFailure,
                    "The Auth emulator still has the account"
                )
            } finally {
                phone.close()
            }
        }

    @Test
    fun `a deletion the server never heard of deletes nothing on the phone`() =
        onEmulators("account deletion without the server") { host ->
            val phone = DeletionPhone.create("deletion_unreachable", host, UNREACHABLE_PORT)
            try {
                phone.fill()
                val uid = assertNotNull(phone.auth.getCurrentUid())

                val result = phone.deletion.deleteAccount()

                assertTrue(result.isFailure, "A deletion that never reached the server succeeded")
                assertNotNull(phone.fixture.database.eventDao().getEventById(EVENT_ID), "The event was deleted")
                assertEquals("the previous account's", phone.fixture.preferences.getString(ORDINARY_KEY))
                assertNotNull(phone.fixture.preferences.getString(SCHOOL_KEY))
                assertNotNull(phone.fixture.preferences.getString(EDUPAGE_KEY))
                phone.fixture.traceFiles.forEach { assertTrue(phone.fixture.exists(it), "$it was removed") }
                assertEquals(0, phone.fixture.reminders.cancelledAll)
                assertEquals(0, phone.fixture.clocks.cancelledAll)
                assertEquals(uid, phone.auth.getCurrentUid(), "The phone was signed out")
            } finally {
                phone.auth.signOutCompletely()
                phone.close()
            }
        }

    /** Runs [test] on the emulators, or skips (or fails, when they are required) without them. */
    private fun onEmulators(name: String, test: suspend (host: String) -> Unit) {
        val host = AuthEmulator.host
        if (host == null) {
            if (AuthEmulator.required) {
                fail("COPLANLY_REQUIRE_EMULATORS=1 but no COPLANLY_EMULATOR_HOST reached the test")
            }
            println("${AuthEmulator.SKIPPED} $name")
            return
        }
        runOnMainRunLoop(TIMEOUT) { test(host) }
        println("${AuthEmulator.RAN} $name")
    }

    /**
     * One phone: a named Firebase app on the emulators with Functions at [functionsPort], a freshly
     * signed-up account with its profile, the fixture's database, store and files, and the deletion
     * under test wired as `platformDataModule` wires it (the push switch the iPhone's `NoPush`).
     */
    private class DeletionPhone(
        val auth: IosFirebaseAuth,
        val email: String,
        val fixture: AccountSwitchFixture,
        val profiles: UserRepositoryImpl,
        functions: CallableFunctions
    ) {
        val deletion = IosAccountDeletion(
            functions = functions,
            database = fixture.database,
            preferences = fixture.preferences,
            push = NoPush,
            reminders = fixture.reminders,
            clocks = fixture.clocks,
            traces = fixture.traces,
            authSession = auth,
        )

        /** The profile on the server and in Room, one event row, both school connections and files. */
        suspend fun fill() {
            profiles.ensureProfile()
            fixture.fillWithThePreviousAccount()
            val uid = checkNotNull(auth.getCurrentUid())
            val now = LocalDateTime(YEAR, MONTH, DAY, HOUR, 0)
            fixture.database.eventDao().insertEvent(
                EventEntity(
                    id = EVENT_ID,
                    title = "Dentist",
                    startDateTime = now,
                    eventType = "medical",
                    parentOwner = "mom",
                    createdAt = now,
                    updatedAt = now,
                    updatedAtMillis = 0L,
                    createdByFirebaseUid = uid
                )
            )
            assertNotNull(fixture.database.eventDao().getEventById(EVENT_ID))
        }

        /** Each Room table's row count, by name. */
        suspend fun rowCounts(): Map<String, Long> = fixture.database.useReaderConnection { connection ->
            val tables = connection.usePrepared(ROOM_TABLES) { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
            tables.associateWith { table ->
                connection.usePrepared("SELECT COUNT(*) FROM `$table`") { statement ->
                    statement.step()
                    statement.getLong(0)
                }
            }
        }

        fun close() = fixture.close()

        companion object {
            /** Starts the Firebase app [appName] (once per process) and signs a fresh account up on it. */
            suspend fun create(appName: String, host: String, functionsPort: Int): DeletionPhone {
                val app = Firebase.initialize(context = null, options = OPTIONS, name = appName)
                val firebaseAuth = Firebase.auth(app).also { it.useEmulator(host, AUTH_PORT) }
                val firestore = Firebase.firestore(app).also { it.useEmulator(host, FIRESTORE_PORT) }
                val functions = Firebase.functions(app, FUNCTIONS_REGION).also { it.useEmulator(host, functionsPort) }
                val auth = IosFirebaseAuth(firebaseAuth)
                val email = "deletion-${NSUUID().UUIDString.lowercase()}@example.test"
                auth.createAccountWithEmail(email, PASSWORD).getOrThrow()
                val client = GitLiveFirestoreClient(firestore)
                val fixture = AccountSwitchFixture(auth, lastUid = null)
                val profiles = UserRepositoryImpl(
                    userDao = fixture.database.userDao(),
                    firebaseAuthService = auth,
                    firestoreUserDataSource = FirestoreUserDataSource(client),
                    firestoreFamilyDataSource = FirestoreFamilyDataSource(client),
                    fcmService = FirestorePushQueue(client, auth)
                )
                return DeletionPhone(auth, email, fixture, profiles, IosCallableFunctions(functions))
            }
        }
    }

    private companion object {
        const val PASSWORD = "deletion-password-1"
        const val EVENT_ID = "deletion-test-event"
        const val YEAR = 2026
        const val MONTH = 11
        const val DAY = 20
        const val HOUR = 10
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
        const val FUNCTIONS_PORT = 5001

        /** A port nothing listens on, so the callable never reaches a server. */
        const val UNREACHABLE_PORT = 1

        /** A sign-up, a profile and the callable, which erases the account on the emulators. */
        val TIMEOUT = 3.minutes

        /** The emulators' fake options, which no production service accepts (`IosFirebase`'s). */
        val OPTIONS = FirebaseOptions(
            applicationId = "1:000000000000:ios:0000000000000000",
            apiKey = "A-fake-key-for-the-firebase-emulators-x",
            projectId = "demo-coplanly",
            storageBucket = "demo-coplanly.appspot.com",
            gcmSenderId = "000000000000",
        )
    }
}
