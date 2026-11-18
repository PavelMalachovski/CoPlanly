@file:OptIn(InternalSharedApi::class)

package com.coparently.app.di.koin

import com.coparently.app.data.export.ExportVerifyAddress
import com.coparently.app.data.export.InfoPlistExportVerifyAddress
import com.coparently.app.data.export.IosRecordFileWriter
import com.coparently.app.data.export.RecordFileWriter
import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.files.IosRecordPhotoStorage
import com.coparently.app.data.files.IosStorageBucket
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
import com.coparently.app.data.notification.EventReminderPlanner
import com.coparently.app.data.notification.IosEventReminders
import com.coparently.app.data.notification.IosLocalReminders
import com.coparently.app.data.notification.IosSupportReminders
import com.coparently.app.data.notification.LocalNotificationCenter
import com.coparently.app.data.notification.SupportReminderPlanner
import com.coparently.app.data.notification.UserNotificationCenter
import com.coparently.app.data.notification.appReminderWording
import com.coparently.app.data.notification.checkedAccount
import com.coparently.app.data.notification.toReminderEvent
import com.coparently.app.data.payment.PaymentStore
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
import com.coparently.app.data.platform.logger
import com.coparently.app.data.receipts.VisionReceiptText
import com.coparently.app.data.remote.firebase.AccountLinking
import com.coparently.app.data.remote.firebase.AccountSignIn
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestorePushQueue
import com.coparently.app.data.remote.firebase.GitLiveFirestoreClient
import com.coparently.app.data.remote.firebase.IosAccountLinking
import com.coparently.app.data.remote.firebase.IosCallableFunctions
import com.coparently.app.data.remote.firebase.IosFirebaseAuth
import com.coparently.app.data.remote.firebase.IosPushRegistration
import com.coparently.app.data.remote.firebase.NotificationPermission
import com.coparently.app.data.remote.firebase.PushDevice
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.firebase.UserNotificationsPermission
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.GoogleCalendarAccount
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.data.remote.google.GoogleCalendarConnectionSource
import com.coparently.app.data.remote.google.GoogleRevokeEndpoint
import com.coparently.app.data.remote.google.IosGoogleCalendarAccount
import com.coparently.app.data.school.DailySchoolImport
import com.coparently.app.data.school.IosSchoolImportJob
import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.SchoolImporter
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
import com.coparently.app.data.session.SessionEndingHook
import com.coparently.app.data.session.UserDefaultsLastSignedInUid
import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.support.SupportReminders
import com.coparently.app.data.sync.ForegroundSyncRunner
import com.coparently.app.data.sync.GoogleCalendarAutoImport
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.sync.SyncService
import com.coparently.app.domain.InternalSharedApi
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.receipts.ReceiptTextRecognizer
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
 * ([IosAccountDeletion]), since part C's Task 10 Cloud Storage ([IosStorageBucket]), since Task 11
 * a record's photos ([IosRecordPhotoStorage]) and the pickers' copies ([IosPickedFiles]), since
 * Task 12 a receipt read by Vision ([VisionReceiptText]), the record's file ([IosRecordFileWriter])
 * and its verification address ([InfoPlistExportVerifyAddress]), since phase 4's Task 7 Google
 * Calendar ([IosGoogleCalendarAccount]), since phase 4 part C's Task 5 the push token
 * ([IosPushRegistration]), since its Task 7 the event and child support reminders as local
 * notifications ([IosLocalReminders]) — and, for what the iPhone does not have yet (background
 * jobs), the stand-ins in
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
    // The account's sign-in methods (phase 4, part A), Apple's unlink through `dataModules`' AppleTokens.
    single<AccountLinking> { IosAccountLinking(Firebase.auth, appleTokens = get()) }
    // The provider session is the app's Google bridge, which `IosApp.start` binds (`iosAppModule`).
    // The app's own module binds the hook (the Today widget's signed-out snapshot, phase 4 part D);
    // a graph without it, as a data-layer test's, runs none.
    single { IosAccountTraces(get(), get(), getOrNull<SessionEndingHook>() ?: SessionEndingHook.None) }
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
    // The co-parent is told through the queue, as `FcmService` tells them on Android, and this
    // phone's own token (phase 4 part C) is registered and detached by `IosPushRegistration`, which
    // the queue reads it through: the switch, the sync's write and sign-out's detach in one place
    // (CLAUDE.md item 23). The token is the app's Swift side's (`IosPushToken`, which `IosApp.start`
    // binds in `iosAppModule`), the permission the system's.
    single<NotificationPermission> { UserNotificationsPermission }
    single {
        IosPushRegistration(
            firestore = get(),
            signedInUser = get(),
            preferences = get(),
            token = get(),
            permission = get(),
        )
    }
    single<PushRegistration> { get<IosPushRegistration>() }
    single { FirestorePushQueue(get(), get(), tokens = get<IosPushRegistration>()) }
    single<PushDevice> { get<FirestorePushQueue>() }
    single<PushQueue> { get<PushDevice>() }
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
    // The event and child support reminders as local notifications (phase 4 part C, Task 7): one
    // book for both, because iOS keeps at most 64 pending and the nearest 60 are armed, a support
    // reminder first. Each plan reads at run time, so the stores' own graph is no cycle here, and
    // only for an account the account check has run for: sign-out keeps Room, and a new account's
    // first re-arm must not read the previous one's events before `IosAccountSwitch` wipes them.
    single<LocalNotificationCenter> { UserNotificationCenter }
    single {
        EventReminderPlanner(
            events = {
                get<EventDao>().getAllEvents().first()
                    .filter { it.reminderMinutes != null }
                    .map { it.toReminderEvent().copy(familyId = it.familyId) }
            },
            uid = { checkedAccount(get<SignedInUser>().getCurrentUid(), get<LastSignedInUid>().get()) },
            wording = ::appReminderWording,
        )
    }
    single {
        SupportReminderPlanner(
            reminders = get(),
            uid = { checkedAccount(get<SignedInUser>().getCurrentUid(), get<LastSignedInUid>().get()) },
            agreement = { familyId -> get<SupportAgreementStore>().readFromServer(familyId) },
            payment = { id -> get<PaymentStore>().readFromServer(id) },
            payeeName = { uid -> get<PairingRepository>().observeCoParentProfile(uid).first().name },
            wording = ::appReminderWording,
        )
    }
    single {
        IosLocalReminders(
            center = get(),
            events = { now -> get<EventReminderPlanner>().plan(now) },
            support = { now -> get<SupportReminderPlanner>().plan(now) },
            onCancelAll = { get<SupportReminders>().forgetArmed() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single<ReminderScheduler> { IosEventReminders(get()) }
    single<RequestClockScheduler> { NoRequestClockJobs }
    single<SchoolImportScheduler> { IosSchoolImportJob }
    single { DailySchoolImport(get<SchoolImporter>(), get(), get(), get()) }
    single<SupportReminderScheduler> { IosSupportReminders(get()) }
    // A sync while the app is open, one at a time, with the Google import after each (Task 6;
    // the iPhone has no background job until phase 4). The lambdas resolve at run time, so the
    // sync's own need of a `SyncRequester` (the pairing transition asks for one) is no cycle.
    single<SyncRequester> {
        ForegroundSyncRunner(
            sync = { get<SyncService>().performFullSync() },
            // Then the local reminders are planned again from what the sync brought (design §7.4):
            // an event deleted on the other phone loses its reminder, a month paid there its own.
            // The school import rides the same syncs (phase 4 part D): at most once a day per account,
            // after the Google import however that went, and its own failure is logged
            // (`runDailySchoolImport`), so it stops neither the reminders nor the next run.
            afterSync = {
                try {
                    try {
                        get<GoogleCalendarAutoImport>().importIfDue()
                    } finally {
                        runDailySchoolImport(get())
                    }
                } finally {
                    get<IosLocalReminders>().requestRearm()
                }
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    // Google Calendar (phase 4, part A, Task 7): one object under the three types, its tokens in the
    // sealed store and renewed through the token function; Google's sheet is the shell's.
    // The grant is revoked on disconnect and sign-out through Google's revoke endpoint, as Android's
    // `revokeAccess()` ends it, so the next connect carries a refresh token.
    single {
        IosGoogleCalendarAccount(
            preferences = get(),
            oauth = get(),
            revocation = GoogleRevokeEndpoint(httpClient(HttpTimeouts.GOOGLE_CALENDAR)),
            providerSession = get(),
        )
    }
    single<GoogleCalendarAccount> { get<IosGoogleCalendarAccount>() }
    single<GoogleCalendarConnectionSource> { get<IosGoogleCalendarAccount>() }
    single<CredentialProvider> { get<IosGoogleCalendarAccount>() }
    // Cloud Storage by path, never by download URL (part C, Task 10).
    single<StorageBucket> { IosStorageBucket(Firebase.storage) }
    // A record's photos re-encoded and stored under its family's path, and the copies the pickers
    // keep of what was picked (part C, Task 11).
    single<RecordPhotoStorage> {
        IosRecordPhotoStorage(
            authService = get(),
            userDao = get(),
            storage = get(),
            cache = get(),
            storedRecords = get(),
        )
    }
    // A receipt read on the phone by Vision, and the record drawn and saved on the phone (Task 12).
    single<ReceiptTextRecognizer> { VisionReceiptText() }
    single { IosPickedFiles(get<AppDirectories>().cache.toString()) }
    single<PickedFiles> { get<IosPickedFiles>() }
    single<ExportVerifyAddress> { InfoPlistExportVerifyAddress }
    single<RecordFileWriter> { IosRecordFileWriter(get<AppDirectories>().cache.toString()) }
}

/**
 * Today's school import when it is due ([DailySchoolImport]), with a failure logged rather than
 * thrown: it runs inside the sync's `afterSync`, where a throw would skip what follows.
 */
private suspend fun runDailySchoolImport(daily: DailySchoolImport) {
    runCatching { daily.runIfDue() }.onFailure { e ->
        if (e is CancellationException) throw e
        logger.w("DailySchoolImport", "The daily school import threw.", e)
    }
}
