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
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.GitLiveFirestoreClient
import com.coparently.app.data.remote.firebase.IosCallableFunctions
import com.coparently.app.data.remote.firebase.PushDevice
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.data.remote.google.GoogleCalendarConnectionSource
import com.coparently.app.data.repository.PairingWording
import com.coparently.app.data.requests.RequestClockNotices
import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.SchoolImportWordings
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariSchoolDirectory
import com.coparently.app.data.session.AccountConsistency
import com.coparently.app.data.session.AccountDeletion
import com.coparently.app.data.session.AccountTraces
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.receipts.ReceiptTextRecognizer
import com.coparently.app.domain.repository.RecordPhotoStorage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Everything [dataModules] takes from the platform, as the iPhone has it today (MON-24 phase 2;
 * the seams since Task 8j): GitLive's default Firestore and Functions instances, the app's own
 * directories, the Room database on the bundled driver with its DAOs, the Keychain's sealed
 * preference store, the no-op telemetry, the network monitor and the two HTTP clients — and, for
 * what the iPhone does not have yet (sign-in, push, Cloud Storage, a file picker, Google Calendar,
 * background jobs, notifications and its own wording), the stand-ins in `IosUnavailable.kt`, which
 * answer as a phone with nothing of the kind would. So the whole graph resolves.
 *
 * Nothing builds this graph on the iPhone yet: the app that configures Firebase first
 * (`FirebaseApp.configure()`) comes in phase 3, and replaces each stand-in with the real one.
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
    // The school directory the connect screen's ViewModel searches (slice 10f), on a client of its
    // own with the same timeouts (Android shares one named client; nothing on the iPhone needs that).
    single { BakalariSchoolDirectory(httpClient(HttpTimeouts.SCHOOL)) }

    single<SignedInUser> { NoSignedInUser }
    single<AuthSession> { NoAuthSession }
    // One stand-in under all three types, as `FcmService` is on Android: replaced together.
    single<PushDevice> { NoPush }
    single<PushQueue> { get<PushDevice>() }
    single<PushRegistration> { NoPush }
    single<AccountConsistency> { NoAccountSwitch }
    single<AccountDeletion> { NoAccountDeletion }
    single<AccountTraces> { NoAccountTraces }
    single<ReminderScheduler> { NoEventReminders }
    single<RequestClockScheduler> { NoRequestClockJobs }
    single<RequestClockNotices> { NoRequestClockNotices }
    single<SchoolImportScheduler> { NoSchoolImportJob }
    single<SupportReminderScheduler> { NoSupportReminderJob }
    single<SyncRequester> { NoSyncRequests }
    single<GoogleCalendarConnectionSource> { NoGoogleCalendar }
    single<CredentialProvider> { NoGoogleCalendar }
    single<PairingWording> { NoWording }
    single<SchoolImportWordings> { NoWording }
    single<StorageBucket> { NoStorageBucket }
    single<RecordPhotoStorage> { NoRecordPhotoStorage }
    single<ReceiptTextRecognizer> { NoReceiptText }
    single<PickedFiles> { NoPickedFiles }
    single<ExportVerifyAddress> { NoExportVerifyAddress }
    single<RecordFileWriter> { NoRecordFiles }
}
