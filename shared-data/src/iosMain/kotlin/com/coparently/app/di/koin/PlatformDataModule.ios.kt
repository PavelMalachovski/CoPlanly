package com.coparently.app.di.koin

import com.coparently.app.data.export.ExportVerifyAddress
import com.coparently.app.data.export.RecordFileWriter
import com.coparently.app.data.files.PickedFiles
import com.coparently.app.data.files.StorageBucket
import com.coparently.app.data.http.HttpTimeouts
import com.coparently.app.data.http.httpClient
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.buildCoPlanlyDatabase
import com.coparently.app.data.local.dao.BudgetDao
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.EventVersionOutboxDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.dao.ParentingPlanDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.platform.Analytics
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.Connectivity
import com.coparently.app.data.platform.CrashReporter
import com.coparently.app.data.platform.DeviceRegion
import com.coparently.app.data.platform.IosAnalytics
import com.coparently.app.data.platform.IosAppDirectories
import com.coparently.app.data.platform.IosConnectivity
import com.coparently.app.data.platform.IosCrashReporter
import com.coparently.app.data.platform.IosDeviceRegion
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.keychainSecurePreferences
import com.coparently.app.data.remote.firebase.AccountSignIn
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestorePushQueue
import com.coparently.app.data.remote.firebase.GitLiveFirestoreClient
import com.coparently.app.data.remote.firebase.IosCallableFunctions
import com.coparently.app.data.remote.firebase.IosFirebaseAuth
import com.coparently.app.data.remote.firebase.PushDevice
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.GoogleCalendarAccount
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.data.remote.google.GoogleCalendarConnectionSource
import com.coparently.app.data.requests.RequestClockNotices
import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariSchoolDirectory
import com.coparently.app.data.school.edupage.EdupageClient
import com.coparently.app.data.session.AccountConsistency
import com.coparently.app.data.session.AccountDeletion
import com.coparently.app.data.session.AccountTraces
import com.coparently.app.data.session.IosAccountDeletion
import com.coparently.app.data.session.IosAccountSwitch
import com.coparently.app.data.session.IosAccountTraces
import com.coparently.app.data.session.LastSignedInUid
import com.coparently.app.data.session.UserDefaultsLastSignedInUid
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.sync.ForegroundSyncRunner
import com.coparently.app.data.sync.GoogleCalendarAutoImport
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.sync.SyncService
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.receipts.ReceiptTextRecognizer
import com.coparently.app.domain.repository.RecordPhotoStorage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Everything [dataModules] takes from the platform, as the iPhone has it today (MON-24 phase 2; the
 * seams since Task 8j): GitLive's default Firestore and Functions instances, the app's own
 * directories, the Room database on the bundled driver with its DAOs, the Keychain's sealed
 * preference store, the no-op telemetry, the network monitor and the two HTTP clients; since phase
 * 3's Task 5, GitLive's Auth ([IosFirebaseAuth]: who is signed in, the session and the e-mail
 * sign-in), the account switch's wipe ([IosAccountSwitch]) and sign-out's traces
 * ([IosAccountTraces]) — since Task 6, the push queue ([FirestorePushQueue]) and the sync while the
 * app is open ([ForegroundSyncRunner]), since part B's Task 11 account deletion
 * ([IosAccountDeletion]) — and, for what the iPhone does not have yet (a push token, Cloud Storage,
 * a file picker, Google Calendar, background jobs and notifications), the stand-ins in
 * `IosUnavailable.kt`, which answer as a phone with nothing of the kind would. The two wordings the
 * data layer writes into stored data read the app's strings, so `:shared-ui`'s `platformUiModule`
 * binds them (`IosPairingWording`, `IosSchoolImportWordings`); the graph resolves with both
 * modules, as `IosApp.start` builds it.
 *
 * The iPhone app's `IosApp.start` builds this graph after `IosFirebase` has configured Firebase,
 * and phase 3 replaces each remaining stand-in with the real one.
 */
actual val platformDataModule: Module = module {
    single<FirestoreClient> { GitLiveFirestoreClient(Firebase.firestore) }
    single<CallableFunctions> { IosCallableFunctions(Firebase.functions(FUNCTIONS_REGION)) }

    single<AppDirectories> { IosAppDirectories() }
    single<SecurePreferences> { keychainSecurePreferences() }
    single<CrashReporter> { IosCrashReporter }
    single<Analytics> { IosAnalytics }
    single<Connectivity> { IosConnectivity() }
    single<DeviceRegion> { IosDeviceRegion }

    single<CoPlanlyDatabase> { buildCoPlanlyDatabase(get()) }
    factory<EventDao> { get<CoPlanlyDatabase>().eventDao() }
    factory<UserDao> { get<CoPlanlyDatabase>().userDao() }
    factory<CustodyScheduleDao> { get<CoPlanlyDatabase>().custodyScheduleDao() }
    factory<CustodyModelDao> { get<CoPlanlyDatabase>().custodyModelDao() }
    factory<ChildInfoDao> { get<CoPlanlyDatabase>().childInfoDao() }
    factory<PetDao> { get<CoPlanlyDatabase>().petDao() }
    factory<MessageDao> { get<CoPlanlyDatabase>().messageDao() }
    factory<ExpenseDao> { get<CoPlanlyDatabase>().expenseDao() }
    factory<BudgetDao> { get<CoPlanlyDatabase>().budgetDao() }
    factory<ChangeRequestDao> { get<CoPlanlyDatabase>().changeRequestDao() }
    factory<ParentingPlanDao> { get<CoPlanlyDatabase>().parentingPlanDao() }
    factory<EventVersionOutboxDao> { get<CoPlanlyDatabase>().eventVersionOutboxDao() }
    factory<JournalDao> { get<CoPlanlyDatabase>().journalDao() }
    factory<FamilyDocumentCacheDao> { get<CoPlanlyDatabase>().familyDocumentCacheDao() }
    factory<KnownFamilyDao> { get<CoPlanlyDatabase>().knownFamilyDao() }

    single { GoogleCalendarApi(httpClient(HttpTimeouts.GOOGLE_CALENDAR)) }
    single { BakalariClient(httpClient(HttpTimeouts.SCHOOL)) }
    single { EdupageClient(httpClient(HttpTimeouts.SCHOOL)) }
    // The school directory the connect screen's ViewModel searches (slice 10f), on a client of its
    // own with the same timeouts (Android shares one named client; nothing on the iPhone needs that).
    single { BakalariSchoolDirectory(httpClient(HttpTimeouts.SCHOOL)) }

    // One object under the three types, as `FirebaseAuthService` is on Android.
    single { IosFirebaseAuth(Firebase.auth) }
    single<SignedInUser> { get<IosFirebaseAuth>() }
    single<AuthSession> { get<IosFirebaseAuth>() }
    single<AccountSignIn> { get<IosFirebaseAuth>() }
    single { IosAccountTraces(get()) }
    single<AccountTraces> { get<IosAccountTraces>() }
    single<LastSignedInUid> { UserDefaultsLastSignedInUid }
    single<AccountConsistency> {
        IosAccountSwitch(
            database = get(),
            auth = get(),
            preferences = get(),
            reminders = get(),
            clocks = get(),
            traces = get(),
            lastUid = get(),
        )
    }
    // The co-parent is told through the queue, as `FcmService` tells them on Android; this phone
    // holds no push token yet, so the switch and sign-out's detach are still `NoPush` (phase 4
    // replaces it with the token's registration and unregistration together).
    single { FirestorePushQueue(get(), get()) }
    single<PushDevice> { get<FirestorePushQueue>() }
    single<PushQueue> { get<PushDevice>() }
    single<PushRegistration> { NoPush }
    // The `deleteAccount` callable, then this phone's wipe and the session's end (part B, Task 11).
    single<AccountDeletion> {
        IosAccountDeletion(
            functions = get(),
            database = get(),
            preferences = get(),
            push = get(),
            reminders = get(),
            clocks = get(),
            traces = get(),
            authSession = get(),
        )
    }
    single<ReminderScheduler> { NoEventReminders }
    single<RequestClockScheduler> { NoRequestClockJobs }
    single<RequestClockNotices> { NoRequestClockNotices }
    single<SchoolImportScheduler> { NoSchoolImportJob }
    single<SupportReminderScheduler> { NoSupportReminderJob }
    // A sync while the app is open, one at a time, with the Google import after each (Task 6;
    // the iPhone has no background job until phase 4). The lambdas resolve at run time, so the
    // sync's own need of a `SyncRequester` (the pairing transition asks for one) is no cycle.
    single<SyncRequester> {
        ForegroundSyncRunner(
            sync = { get<SyncService>().performFullSync() },
            afterSync = { get<GoogleCalendarAutoImport>().importIfDue() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single<GoogleCalendarAccount> { NoGoogleCalendar }
    single<GoogleCalendarConnectionSource> { NoGoogleCalendar }
    single<CredentialProvider> { NoGoogleCalendar }
    single<StorageBucket> { NoStorageBucket }
    single<RecordPhotoStorage> { NoRecordPhotoStorage }
    single<ReceiptTextRecognizer> { NoReceiptText }
    single<PickedFiles> { NoPickedFiles }
    single<ExportVerifyAddress> { NoExportVerifyAddress }
    single<RecordFileWriter> { NoRecordFiles }
}
