package com.coparently.app.e2e

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.documents.FamilyDocumentIndex
import com.coparently.app.data.documents.FamilyDocumentIndexCache
import com.coparently.app.data.documents.FamilyDocumentRepositoryImpl
import com.coparently.app.data.export.ExportReceipts
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStager
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirestoreBudgetDataSource
import com.coparently.app.data.remote.firebase.FirestoreChangeRequestDataSource
import com.coparently.app.data.remote.firebase.FirestoreChildInfoDataSource
import com.coparently.app.data.remote.firebase.FirestoreCustodyDataSource
import com.coparently.app.data.remote.firebase.FirestoreEventDataSource
import com.coparently.app.data.remote.firebase.FirestoreEventVersionDataSource
import com.coparently.app.data.remote.firebase.FirestoreExpenseDataSource
import com.coparently.app.data.remote.firebase.FirestoreFamilyDataSource
import com.coparently.app.data.remote.firebase.FirestoreFamilySettingsDataSource
import com.coparently.app.data.remote.firebase.FirestoreMessageDataSource
import com.coparently.app.data.remote.firebase.FirestoreParentingPlanDataSource
import com.coparently.app.data.remote.firebase.FirestorePetDataSource
import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.repository.BudgetRepositoryImpl
import com.coparently.app.data.repository.CalendarFeedRepositoryImpl
import com.coparently.app.data.repository.ChangeRequestRepositoryImpl
import com.coparently.app.data.repository.ChildInfoRepositoryImpl
import com.coparently.app.data.repository.ConversationMigrator
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.EventRepositoryImpl
import com.coparently.app.data.repository.ExpenseRepositoryImpl
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.repository.FriendRepositoryImpl
import com.coparently.app.data.repository.GuestRepositoryImpl
import com.coparently.app.data.repository.MessageRepositoryImpl
import com.coparently.app.data.repository.PairingRepositoryImpl
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.repository.PostPairingConversationSetup
import com.coparently.app.data.repository.ProfessionalRepositoryImpl
import com.coparently.app.data.repository.UserRepositoryImpl
import com.coparently.app.data.security.EncryptionManager
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.versions.EventVersionRecorder
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.chat.AttachmentUploadGate
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.PairingState
import com.coparently.app.presentation.common.ParentsSource
import com.coparently.app.testing.NoFcm
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * One parent's phone, in one process: a Firebase app of its own, a Room database of its own, and
 * the production data layer wired over both.
 *
 * **Why not Hilt.** A process has one `SingletonComponent`, and the scenario needs two of
 * everything — two signed-in accounts, two Firestore clients with separate caches, two Room
 * files. So each parent is a *named* [FirebaseApp] (`FirebaseApp.initializeApp(context, options,
 * name)`), and the repositories are built by hand from the same constructors Hilt calls. That
 * leaves the ordinary instrumented job exactly as it was: nothing here is a Hilt module, so
 * `FakeFirebaseModule` still replaces `FirebaseModule` for every `@HiltAndroidTest`, and no
 * `google-services.json` is involved on either side.
 *
 * **What is real and what is not.** Every class that reads or writes Firestore, Auth, Functions or
 * Room is the production class: the data sources, `EventRepositoryImpl`, `ExpenseRepositoryImpl`,
 * `MessageRepositoryImpl`, `PairingRepositoryImpl` (which drives the real `acceptPairingInvitation`
 * callable), `UserRepositoryImpl`, `SelectedFamilySource`, `ParentsSource` and `ActivityAnnouncer`.
 * `SharedFileStorage`, `ChatAttachmentOutbox`, `SharedFileCache` and `FamilyDocumentRepositoryImpl`
 * are production too, over the Storage emulator, each phone with its own `files` and `cache`, and
 * so are the repositories for children, pets, budgets, change requests, the custody schedule, the
 * expense split, the parenting plan, calendar friends, professionals, guests, calendar feeds and
 * export receipts — every feature two phones share has its production writer here, so a
 * two-parent test of it exercises what ships (`tools/e2e/coverage.json` says which test
 * does).
 * [FcmService] is the real one too, over an SDK with no FCM ([NoFcm]): the `notification_queue`
 * document a push leaves behind is the production write and [queuedFor] reads it back; only the
 * delivery is missing, because FCM has no emulator. Two collaborators are stand-ins:
 * `CrashlyticsManager` (a relaxed mock — telemetry has no place in a test) and [SyncRequester]
 * (a no-op — it enqueues WorkManager, whose `SyncService` would need the whole graph). The Room
 * database is in memory and unencrypted, which is the one
 * thing the `instrumented` job's SQLCipher coverage already owns.
 *
 * @property name The display name this parent signs up with, which becomes their profile name.
 */
class EmulatorParent private constructor(
    val name: String,
    private val context: Context,
    val app: FirebaseApp
) : Closeable {

    val auth: FirebaseAuth = FirebaseAuth.getInstance(app)
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(app)
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(app)
    val storage: FirebaseStorage = FirebaseStorage.getInstance(app)

    /**
     * This phone's own `files` and `cache` directories. Both parents run in one app process, and
     * the chat outbox and the verified-download cache live under those two directories: shared,
     * Bob would "open" Alice's attachment from the copy her upload left behind and never download
     * it. Everything that touches files is given this context; nothing else needs it.
     */
    private val fileContext: Context = PhoneDirectories(context, name)

    /** This phone's Room database. In memory: each test starts from an empty install. */
    val database: CoPlanlyDatabase =
        Room.inMemoryDatabaseBuilder(context, CoPlanlyDatabase::class.java).build()

    val authService = FirebaseAuthService(auth)
    val eventDataSource = FirestoreEventDataSource(firestore)
    private val userDataSource = FirestoreUserDataSource(firestore)
    private val messageDataSource = FirestoreMessageDataSource(firestore)

    val sharedFileStorage = SharedFileStorage(storage)
    val sharedFileCache = SharedFileCache(fileContext, sharedFileStorage)
    private val stager = SharedFileStager(fileContext)

    /** The production chat outbox: stages a file, uploads it, then lets the message be written. */
    val attachmentOutbox = ChatAttachmentOutbox(fileContext, stager, sharedFileStorage, sharedFileCache, authService)

    /** Sits in front of [attachmentOutbox] so a test can make this phone's uploads fail. */
    val uploads = SwitchableUploads(attachmentOutbox)

    val messageRepository = MessageRepositoryImpl(database.messageDao(), authService, messageDataSource, uploads)

    /** The production vault: Firestore index, Storage bytes, Room cache of the index. */
    val documentRepository = FamilyDocumentRepositoryImpl(
        context = fileContext,
        index = FamilyDocumentIndex(firestore, FamilyDocumentIndexCache(database.familyDocumentCacheDao())),
        authService = authService,
        stager = stager,
        storage = sharedFileStorage,
        cache = sharedFileCache
    )

    /**
     * A store of this phone's own: two instances over one file would each keep their own map and
     * overwrite each other's writes (SEC-5's store is a sealed snapshot, not a shared file).
     */
    val encryptedPreferences = EncryptedPreferences(
        context = fileContext,
        encryptionManager = EncryptionManager(context)
    )

    /**
     * The production push writer over an SDK with no FCM ([NoFcm]): what a repository queues in
     * `notification_queue` is the real document the rules check; only its delivery, which has no
     * emulator, is missing. Read back with [queuedFor].
     */
    val fcmService = FcmService(NoFcm.messaging(), firestore, authService, encryptedPreferences)

    val userRepository = UserRepositoryImpl(
        userDao = database.userDao(),
        firebaseAuthService = authService,
        firestoreUserDataSource = userDataSource,
        firestoreFamilyDataSource = FirestoreFamilyDataSource(firestore),
        fcmService = fcmService
    )

    private val announcer = ActivityAnnouncer(messageRepository, userRepository)

    val eventRepository = EventRepositoryImpl(
        eventDao = database.eventDao(),
        userDao = database.userDao(),
        firebaseAuthService = authService,
        firestoreEventDataSource = eventDataSource,
        activityAnnouncer = announcer,
        eventVersionRecorder = EventVersionRecorder(
            outboxDao = database.eventVersionOutboxDao(),
            userDao = database.userDao(),
            remote = FirestoreEventVersionDataSource(firestore)
        )
    )

    val expenseRepository = ExpenseRepositoryImpl(
        expenseDao = database.expenseDao(),
        userDao = database.userDao(),
        firebaseAuthService = authService,
        firestoreExpenseDataSource = FirestoreExpenseDataSource(firestore),
        activityAnnouncer = announcer
    )

    val selectedFamilySource = SelectedFamilySource(
        userDao = database.userDao(),
        firebaseAuthService = authService,
        firestoreUserDataSource = userDataSource,
        encryptedPreferences = encryptedPreferences
    )

    val pairingRepository = PairingRepositoryImpl(
        firestore = firestore,
        authService = authService,
        pairingFunctions = PairingFunctions(functions),
        postPairingConversationSetup = PostPairingConversationSetup(
            messageRepository,
            ConversationMigrator(database.messageDao(), messageDataSource)
        ),
        userDao = database.userDao(),
        selectedFamilySource = selectedFamilySource,
        syncRequester = NoSyncRequester,
        context = context
    )

    val parentsSource = ParentsSource(userRepository, pairingRepository)

    private val custodyDataSource = FirestoreCustodyDataSource(firestore)
    private val planDataSource = FirestoreParentingPlanDataSource(firestore)
    private val pairingFunctions = PairingFunctions(functions)

    /** Where a repository's background work runs; cancelled in [close]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val childInfoRepository = ChildInfoRepositoryImpl(
        childInfoDao = database.childInfoDao(),
        userDao = database.userDao(),
        firebaseAuthService = authService,
        firestoreChildInfoDataSource = FirestoreChildInfoDataSource(firestore)
    )

    val petRepository = PetRepositoryImpl(
        petDao = database.petDao(),
        userDao = database.userDao(),
        firebaseAuthService = authService,
        firestorePetDataSource = FirestorePetDataSource(firestore)
    )

    val budgetRepository = BudgetRepositoryImpl(
        budgetDao = database.budgetDao(),
        expenseDao = database.expenseDao(),
        userDao = database.userDao(),
        firebaseAuthService = authService,
        firestoreBudgetDataSource = FirestoreBudgetDataSource(firestore)
    )

    val changeRequestRepository = ChangeRequestRepositoryImpl(
        changeRequestDao = database.changeRequestDao(),
        firestoreDataSource = FirestoreChangeRequestDataSource(firestore),
        firebaseAuthService = authService,
        fcmService = fcmService
    )

    val custodyRepository = CustodyModelRepository(
        custodyModelDao = database.custodyModelDao(),
        userRepository = userRepository,
        firestoreCustodyDataSource = custodyDataSource,
        activityAnnouncer = announcer,
        fcmService = fcmService,
        scope = scope
    )

    val familySettingsRepository = FamilySettingsRepository(
        dataSource = FirestoreFamilySettingsDataSource(firestore),
        userRepository = userRepository,
        preferences = encryptedPreferences,
        fcmService = fcmService
    )

    val parentingPlanRepository = ParentingPlanRepository(
        dao = database.parentingPlanDao(),
        remote = planDataSource,
        gson = GsonBuilder().setStrictness(Strictness.LENIENT).create(),
        crashlyticsManager = mockk<CrashlyticsManager>(relaxed = true)
    )

    val friendRepository = FriendRepositoryImpl(
        firestore = firestore,
        authService = authService,
        pairingFunctions = pairingFunctions,
        selectedFamilySource = selectedFamilySource
    )

    val professionalRepository = ProfessionalRepositoryImpl(
        firestore = firestore,
        authService = authService,
        pairingFunctions = pairingFunctions,
        selectedFamilySource = selectedFamilySource,
        custodyDataSource = custodyDataSource,
        planDataSource = planDataSource
    )

    val guestRepository = GuestRepositoryImpl(firestore, authService, pairingFunctions)

    val calendarFeedRepository = CalendarFeedRepositoryImpl(functions)

    val exportReceipts = ExportReceipts(functions)

    /**
     * The pushes queued for [targetUid] from this phone, as their payload maps — read as the
     * server would see them, around the rules, because `notification_queue` is not readable by
     * the addressee's client either: only Cloud Functions read it to send.
     */
    fun queuedFor(targetUid: String): List<Map<String, Any?>> =
        EmulatorEnvironment.queryAsAdmin("notification_queue", "targetUserId", targetUid)

    /** This parent's Firebase uid. Valid once [create] has returned. */
    val uid: String
        get() = checkNotNull(auth.currentUser) { "$name is not signed in" }.uid

    /**
     * Waits until this phone observes itself paired with [partnerUid], exactly as the app does.
     *
     * Collecting `observePairingState` is not a shortcut around the app: its `onEach` is where the
     * pairing is mirrored into Room, the family projection is applied and the conversation is
     * created. By the time a `Paired` value reaches this collector, that work has finished — the
     * same state the app is in once the pairing screen shows the co-parent.
     */
    suspend fun awaitPairedWith(partnerUid: String): PairingState.Paired = withTimeout(WAIT_MS) {
        pairingRepository.observePairingState()
            .filterIsInstance<PairingState.Paired>()
            .first { it.partner.id == partnerUid }
    }

    /** Signs out, closes Room, deletes the named Firebase app and this phone's directories. */
    override fun close() {
        scope.cancel()
        runCatching { auth.signOut() }
        runCatching { database.close() }
        runCatching { app.delete() }
        runCatching { fileContext.filesDir.parentFile?.deleteRecursively() }
    }

    /**
     * The chat outbox as this phone's `MessageRepositoryImpl` sees it, with a switch that makes
     * every upload fail the way one does with no network — an `IOException` from the gate, before
     * anything is written. What a test proves with it is the ordering the gate exists for: the
     * message stays off the server while its file is not there, and goes once it is.
     */
    class SwitchableUploads(private val delegate: AttachmentUploadGate) : AttachmentUploadGate {

        /** While true, every upload fails and no message with a file can be written. */
        @Volatile
        var failing: Boolean = false

        override suspend fun ensureUploaded(message: Message) {
            if (failing) throw IOException("Uploads are switched off for this phone")
            delegate.ensureUploaded(message)
        }
    }

    /**
     * [base] with `files`, `cache` and `no_backup` directories, and preference names, of this
     * phone's own — so neither phone reads, writes or deletes the app's real stores.
     */
    private class PhoneDirectories(base: Context, phone: String) : ContextWrapper(base) {
        private val id = "$phone-${UUID.randomUUID()}"
        private val root = File(base.cacheDir, "e2e-phones/$id")
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            super.getSharedPreferences("e2e-$id-$name", mode)
        override fun deleteSharedPreferences(name: String?): Boolean =
            super.deleteSharedPreferences("e2e-$id-$name")
        override fun getApplicationContext(): Context = this
    }

    private object NoSyncRequester : SyncRequester {
        override fun requestSyncNow() = Unit
    }

    companion object {

        /** How long any single cross-device wait may take before the test fails. */
        const val WAIT_MS = 30_000L

        private const val PASSWORD = "e2e-password-1"

        /**
         * Starts a phone for a brand-new account named [name] and signs it up.
         *
         * Emulator redirection happens before the first call on each SDK, which is the only time
         * it is allowed. The profile is then written by `UserRepositoryImpl.ensureProfile` — the
         * same call the app makes after sign-in — so the `users/{uid}` document the pairing
         * callable requires is the one production writes, not a fixture.
         */
        suspend fun create(context: Context, name: String): EmulatorParent {
            val app = EmulatorEnvironment.startFirebaseApp(context, "e2e-$name-${UUID.randomUUID()}")
            val parent = EmulatorParent(name, context, app)
            val email = "${name.lowercase()}-${UUID.randomUUID()}@e2e.coplanly.test"
            val user = checkNotNull(
                parent.auth.createUserWithEmailAndPassword(email, PASSWORD).await().user
            )
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name).build()
            ).await()
            parent.userRepository.ensureProfile()
            return parent
        }
    }
}
