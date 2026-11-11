@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.gate

import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStager
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.buildCoPlanlyDatabase
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.MemoryTextStore
import com.coparently.app.data.platform.SealedSecurePreferences
import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.data.remote.firebase.FirestoreChildInfoDataSource
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreEventDataSource
import com.coparently.app.data.remote.firebase.FirestoreEventVersionDataSource
import com.coparently.app.data.remote.firebase.FirestoreExpenseDataSource
import com.coparently.app.data.remote.firebase.FirestoreFamilyDataSource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.data.remote.firebase.FirestorePetDataSource
import com.coparently.app.data.remote.firebase.FirestorePushQueue
import com.coparently.app.data.remote.firebase.FirestoreRequestDeadlineDataSource
import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.data.remote.firebase.GitLiveFirestoreClient
import com.coparently.app.data.remote.firebase.IosCallableFunctions
import com.coparently.app.data.remote.firebase.IosFirebaseAuth
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.repository.ChangeRequestRepositoryImpl
import com.coparently.app.data.repository.ChildInfoRepositoryImpl
import com.coparently.app.data.repository.ConversationMigrator
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.EventRepositoryImpl
import com.coparently.app.data.repository.ExpenseRepositoryImpl
import com.coparently.app.data.repository.MessageRepositoryImpl
import com.coparently.app.data.repository.PairingRepositoryImpl
import com.coparently.app.data.repository.PairingWording
import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.repository.PostPairingConversationSetup
import com.coparently.app.data.repository.RequestDeadlineRepositoryImpl
import com.coparently.app.data.repository.UserRepositoryImpl
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.versions.EventVersionRecorder
import com.coparently.app.di.koin.FUNCTIONS_REGION
import com.coparently.app.di.koin.NoPickedFiles
import com.coparently.app.di.koin.NoRequestClockJobs
import com.coparently.app.di.koin.NoStorageBucket
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.model.PairingState
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.initialize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * One parent's iPhone in the write gate's test process (MON-24 phase 3, Task 7): a named Firebase
 * app of its own on the emulators, a Room database of its own on the bundled driver, and the
 * production data layer wired over both as the iPhone's `platformDataModule` wires it — GitLive's
 * Firestore, Functions and Auth ([IosFirebaseAuth]), the push queue ([FirestorePushQueue]) and the
 * stand-ins for what the iPhone does not have yet (`NoStorageBucket`, `NoPickedFiles`,
 * `NoRequestClockJobs`). Every class that writes to Firestore is the production class.
 *
 * Built by hand, as the Android e2e job's `EmulatorParent` is, because one process has one Koin
 * graph and the gate needs two of everything. Its Firestore client is wrapped by
 * [RecordingFirestoreClient], so each document it writes reaches [written].
 *
 * The Firebase app is named and **never deleted**, as on Android: the SDKs keep emulator settings
 * per app for the life of the process. Each phone signs up a fresh account, so nothing is shared
 * with another run.
 *
 * @property name The display name the parent signs up with.
 */
internal class GatePhone private constructor(
    val name: String,
    val auth: FirebaseAuth,
    firestore: FirestoreClient,
    functions: IosCallableFunctions,
    written: WrittenDocuments
) {
    private val root = NSTemporaryDirectory() + "coplanly-gate-" + NSUUID().UUIDString

    private val directories = object : AppDirectories {
        override val files: Path = Path(root, "files")
        override val cache: Path = Path(root, "cache")
        override val noBackup: Path = Path(root, "no_backup")
    }

    /** Where repositories run their background work; cancelled by [close]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The phone's Firestore client, every write recorded. */
    val documents: FirestoreClient = RecordingFirestoreClient(firestore, written)

    /** The phone's Auth, as the iPhone's data layer reads it. */
    val signedIn = IosFirebaseAuth(auth)

    /** The phone's rows. */
    val database: CoPlanlyDatabase = buildCoPlanlyDatabase(directories)

    private val preferences = SealedSecurePreferences(MemoryTextStore())
    private val pushQueue = FirestorePushQueue(documents, signedIn)
    private val userDataSource = FirestoreUserDataSource(documents)
    private val messageDataSource = FirestoreMessageDataSource(documents)
    private val sharedFileStorage = SharedFileStorage(NoStorageBucket)

    /** The event writer and reader's data source. */
    val eventDataSource = FirestoreEventDataSource(documents)

    /** Messages, gated by the chat outbox the iPhone binds (no Cloud Storage yet). */
    val messageRepository = MessageRepositoryImpl(
        messageDao = database.messageDao(),
        signedInUser = signedIn,
        firestoreMessageDataSource = messageDataSource,
        attachmentGate = ChatAttachmentOutbox(
            directories = directories,
            stager = SharedFileStager(NoPickedFiles),
            storage = sharedFileStorage,
            cache = SharedFileCache(directories, sharedFileStorage),
            authService = signedIn
        )
    )

    /** The profile and the co-parent's. */
    val userRepository = UserRepositoryImpl(
        userDao = database.userDao(),
        firebaseAuthService = signedIn,
        firestoreUserDataSource = userDataSource,
        firestoreFamilyDataSource = FirestoreFamilyDataSource(documents),
        fcmService = pushQueue
    )

    private val announcer = ActivityAnnouncer(messageRepository, userRepository)

    /** The revisions' reader, as the export reads them. */
    val eventVersionDataSource = FirestoreEventVersionDataSource(documents)

    /** The revision outbox, which a save fills and uploads in the background. */
    val eventVersionRecorder = EventVersionRecorder(
        outboxDao = database.eventVersionOutboxDao(),
        userDao = database.userDao(),
        remote = eventVersionDataSource
    )

    /** Events, with their revisions and the important event's push. */
    val eventRepository = EventRepositoryImpl(
        eventDao = database.eventDao(),
        userDao = database.userDao(),
        firebaseAuthService = signedIn,
        firestoreEventDataSource = eventDataSource,
        activityAnnouncer = announcer,
        eventVersionRecorder = eventVersionRecorder,
        fcmService = pushQueue
    )

    /** Expenses. */
    val expenseRepository = ExpenseRepositoryImpl(
        expenseDao = database.expenseDao(),
        userDao = database.userDao(),
        firebaseAuthService = signedIn,
        firestoreExpenseDataSource = FirestoreExpenseDataSource(documents),
        activityAnnouncer = announcer,
        fcmService = pushQueue
    )

    private val selectedFamilySource = SelectedFamilySource(
        userDao = database.userDao(),
        firebaseAuthService = signedIn,
        firestoreUserDataSource = userDataSource,
        encryptedPreferences = preferences
    )

    /** Pairing, through the real `acceptPairingInvitation` on the Functions emulator. */
    val pairingRepository = PairingRepositoryImpl(
        firestoreClient = documents,
        authService = signedIn,
        pairingFunctions = PairingFunctions(functions),
        postPairingConversationSetup = PostPairingConversationSetup(
            messageRepository,
            ConversationMigrator(database.messageDao(), messageDataSource)
        ),
        userDao = database.userDao(),
        selectedFamilySource = selectedFamilySource,
        syncRequester = NoSync,
        wording = GateWording
    )

    /** Children. */
    val childInfoRepository = ChildInfoRepositoryImpl(
        childInfoDao = database.childInfoDao(),
        userDao = database.userDao(),
        firebaseAuthService = signedIn,
        firestoreChildInfoDataSource = FirestoreChildInfoDataSource(documents)
    )

    /** Pets. */
    val petRepository = PetRepositoryImpl(
        petDao = database.petDao(),
        userDao = database.userDao(),
        firebaseAuthService = signedIn,
        firestorePetDataSource = FirestorePetDataSource(documents)
    )

    /** Request clocks, their jobs the iPhone's stand-in. */
    val requestDeadlineRepository = RequestDeadlineRepositoryImpl(
        dataSource = FirestoreRequestDeadlineDataSource(documents),
        authService = signedIn,
        scheduler = NoRequestClockJobs
    )

    /** Change requests. */
    val changeRequestRepository = ChangeRequestRepositoryImpl(
        changeRequestDao = database.changeRequestDao(),
        firestoreDataSource = FirestoreChangeRequestDataSource(documents),
        firebaseAuthService = signedIn,
        fcmService = pushQueue,
        requestDeadlines = requestDeadlineRepository
    )

    /** The custody schedule, its swaps and proposals. */
    val custodyRepository = CustodyModelRepository(
        custodyModelDao = database.custodyModelDao(),
        userRepository = userRepository,
        firestoreCustodyDataSource = FirestoreCustodyDataSource(documents),
        activityAnnouncer = announcer,
        fcmService = pushQueue,
        requestDeadlines = requestDeadlineRepository,
        scope = scope
    )

    /** This parent's uid; valid once [create] has returned. */
    val uid: String get() = checkNotNull(signedIn.getCurrentUid()) { "$name is not signed in" }

    /**
     * Waits until this phone observes itself paired with [partnerUid], as the app does: the
     * listener's `onEach` mirrors the pairing into Room and creates the conversation before a
     * `Paired` value reaches here.
     */
    suspend fun awaitPairedWith(partnerUid: String): PairingState.Paired = withTimeout(WAIT_MS) {
        pairingRepository.observePairingState()
            .filterIsInstance<PairingState.Paired>()
            .first { it.partner.id == partnerUid }
    }

    /** Stops the background work, closes Room and removes the phone's folders; the app stays. */
    fun close() {
        scope.cancel()
        database.close()
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    /** Asks for no sync: the gate writes and reads each record itself. */
    private object NoSync : SyncRequester {
        override fun requestSyncNow() = Unit
    }

    /**
     * The thread title fallback, which stays on the phone (`ensureConversation` writes no title);
     * the iPhone's own wording reads `:shared-ui`'s strings, which this module cannot.
     */
    private object GateWording : PairingWording {
        override suspend fun defaultPartnerName(): String = "Co-parent"
    }

    companion object {
        /** How long one cross-phone wait may take. */
        const val WAIT_MS = 30_000L

        private const val PASSWORD = "gate-password-1"
        private const val FIRESTORE_PORT = 8080
        private const val FUNCTIONS_PORT = 5001
        private const val AUTH_PORT = 9099

        /** The emulators' fake options, which no production service accepts (`IosFirebase`'s). */
        private val OPTIONS = FirebaseOptions(
            applicationId = "1:000000000000:ios:0000000000000000",
            apiKey = "A-fake-key-for-the-firebase-emulators-x",
            projectId = "demo-coplanly",
            storageBucket = "demo-coplanly.appspot.com",
            gcmSenderId = "000000000000",
        )

        /**
         * Starts a phone named [name] on the emulators at [host] — a Firebase app called [appName],
         * once per process — and signs a fresh account up on it, with the profile
         * `UserRepositoryImpl.ensureProfile` writes, as the app does after sign-in.
         */
        suspend fun create(name: String, appName: String, host: String, written: WrittenDocuments): GatePhone {
            val app = Firebase.initialize(context = null, options = OPTIONS, name = appName)
            val auth = Firebase.auth(app).also { it.useEmulator(host, AUTH_PORT) }
            val firestore = Firebase.firestore(app).also { it.useEmulator(host, FIRESTORE_PORT) }
            val functions = Firebase.functions(app, FUNCTIONS_REGION).also { it.useEmulator(host, FUNCTIONS_PORT) }
            val client = GitLiveFirestoreClient(firestore)
            val phone = GatePhone(name, auth, client, IosCallableFunctions(functions), written)
            val email = "gate-${name.lowercase()}-${NSUUID().UUIDString.lowercase()}@example.test"
            phone.signedIn.createAccountWithEmail(email, PASSWORD).getOrThrow()
            checkNotNull(auth.currentUser) { "$name did not sign up" }.updateProfile(displayName = name)
            phone.userRepository.ensureProfile()
            return phone
        }
    }
}
