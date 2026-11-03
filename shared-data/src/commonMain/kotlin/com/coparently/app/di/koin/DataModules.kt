package com.coparently.app.di.koin

import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.chat.DepartedThreadSource
import com.coparently.app.data.documents.FamilyDocumentIndex
import com.coparently.app.data.documents.FamilyDocumentIndexCache
import com.coparently.app.data.documents.FamilyDocumentRepositoryImpl
import com.coparently.app.data.export.CommunicationRecordSource
import com.coparently.app.data.export.OptionalRecordSections
import com.coparently.app.data.export.ParentingPlanRecordSource
import com.coparently.app.data.family.AgreementSignalSources
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.family.KnownFamilies
import com.coparently.app.data.family.KnownFamilySlots
import com.coparently.app.data.family.OtherFamiliesSignals
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.family.UnassignedRecords
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStager
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.files.StoredRecordFamilies
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.money.CurrencyHints
import com.coparently.app.data.payment.PaymentAccounts
import com.coparently.app.data.payment.PaymentStore
import com.coparently.app.data.payment.SettleUpMessages
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
import com.coparently.app.data.remote.firebase.FirestoreRequestDeadlineDataSource
import com.coparently.app.data.remote.firebase.FirestoreUserDataSource
import com.coparently.app.data.remote.firebase.GoogleOAuthFunctions
import com.coparently.app.data.remote.firebase.PairingFunctions
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.repository.CalendarFeedRepositoryImpl
import com.coparently.app.data.repository.ChangeRequestRepositoryImpl
import com.coparently.app.data.repository.ChatSearchRepositoryImpl
import com.coparently.app.data.repository.ChildInfoRepositoryImpl
import com.coparently.app.data.repository.ConversationMigrator
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.EventRepositoryImpl
import com.coparently.app.data.repository.ExpenseRepositoryImpl
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.repository.FriendRepositoryImpl
import com.coparently.app.data.repository.GuestRepositoryImpl
import com.coparently.app.data.repository.JournalRepositoryImpl
import com.coparently.app.data.repository.MessageRepositoryImpl
import com.coparently.app.data.repository.PairingRepositoryImpl
import com.coparently.app.data.repository.ParentSlotMigrator
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.repository.PostPairingConversationSetup
import com.coparently.app.data.repository.PreferencesRepositoryImpl
import com.coparently.app.data.repository.ProfessionalRepositoryImpl
import com.coparently.app.data.repository.RequestDeadlineRepositoryImpl
import com.coparently.app.data.repository.UserRepositoryImpl
import com.coparently.app.data.requests.AgreementWithdrawals
import com.coparently.app.data.requests.FamilyAgreementsSource
import com.coparently.app.data.requests.RequestClockActions
import com.coparently.app.data.requests.RequestClockKeeper
import com.coparently.app.data.requests.RequestClockSteps
import com.coparently.app.data.requests.RequestClockSweep
import com.coparently.app.data.requests.RequestClocks
import com.coparently.app.data.requests.RequestRecords
import com.coparently.app.data.requests.RequestTelling
import com.coparently.app.data.requests.RequestTime
import com.coparently.app.data.school.SchoolAccounts
import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.school.SchoolEventWriter
import com.coparently.app.data.school.SchoolImporter
import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.session.SessionProfileSynchronizer
import com.coparently.app.data.session.SignedInAccountSource
import com.coparently.app.data.stickers.ParentRoles
import com.coparently.app.data.stickers.StickerStore
import com.coparently.app.data.support.SupportAgreementStore
import com.coparently.app.data.support.SupportReminders
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.ConflictResolver
import com.coparently.app.data.sync.FamilyIdBackfill
import com.coparently.app.data.sync.GoogleCalendarAutoImport
import com.coparently.app.data.sync.SyncService
import com.coparently.app.data.versions.EventVersionRecorder
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.chat.AttachmentUploadGate
import com.coparently.app.domain.error.ErrorHandler
import com.coparently.app.domain.family.FamilySlotsSource
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.CalendarFeedRepository
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.ChatSearchRepository
import com.coparently.app.domain.repository.ChildInfoRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.ExpenseRepository
import com.coparently.app.domain.repository.FamilyDocumentRepository
import com.coparently.app.domain.repository.FriendRepository
import com.coparently.app.domain.repository.GuestRepository
import com.coparently.app.domain.repository.JournalRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.PreferencesRepository
import com.coparently.app.domain.repository.ProfessionalRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.domain.usecase.CreateEventUseCase
import com.coparently.app.domain.usecase.DeleteEventUseCase
import com.coparently.app.domain.usecase.EventUseCases
import com.coparently.app.domain.usecase.EventValidator
import com.coparently.app.domain.usecase.GetEventsUseCase
import com.coparently.app.domain.usecase.UpdateEventUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The Firestore data sources and the callables' wrappers, one each for the process (MON-24 phase
 * 2, Task 8a). Each takes the [com.coparently.app.data.remote.firebase.FirestoreClient] or the
 * [com.coparently.app.data.remote.firebase.CallableFunctions] [platformDataModule] binds.
 */
val remoteDataModule: Module = module {
    singleOf(::FirestoreChangeRequestDataSource)
    singleOf(::FirestoreChildInfoDataSource)
    singleOf(::FirestoreCustodyDataSource)
    singleOf(::FirestoreEventDataSource)
    singleOf(::FirestoreEventVersionDataSource)
    singleOf(::FirestoreExpenseDataSource)
    singleOf(::FirestoreFamilyDataSource)
    singleOf(::FirestoreFamilySettingsDataSource)
    singleOf(::FirestoreMessageDataSource)
    singleOf(::FirestoreParentingPlanDataSource)
    singleOf(::FirestorePetDataSource)
    singleOf(::FirestoreRequestDeadlineDataSource)
    singleOf(::FirestoreUserDataSource)
    singleOf(::GoogleOAuthFunctions)
    singleOf(::PairingFunctions)
}

/**
 * The sync (MON-24 phase 2: its pure parts since Task 8b, `SyncService` itself since Task 8g).
 * Their Room DAOs and the sealed preference store are the platform's database and store, bound
 * where each platform builds them, and so are the seams the sync asks the platform through: the
 * push token (`PushDevice`), the account-switch check (`AccountConsistency`) and the reminders
 * (`ReminderScheduler`); the request clocks' sweep (`RequestClockSweep`) is [requestsDataModule]'s
 * keeper since Task 8h. `SyncWorker`, which runs it
 * on WorkManager, stays in the app. Beside it, the Google Calendar import, read-only and private
 * (Legal item 10), and its automatic run after every sync (Task 8g), which reads the connection
 * through `GoogleCalendarConnectionSource`.
 */
val syncDataModule: Module = module {
    singleOf(::CalendarSyncRepository)
    singleOf(::ConflictResolver)
    singleOf(::FamilyIdBackfill)
    singleOf(::GoogleCalendarAutoImport)
    // Twenty-four collaborators, past what a constructor reference takes (`singleOf` stops at
    // twenty-two), so each one by its type.
    single {
        SyncService(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
}

/** The revision outbox of shared events (MON-4; CLAUDE.md item 25). */
val versionsDataModule: Module = module {
    singleOf(::EventVersionRecorder)
}

/**
 * The repositories that have moved here (MON-24 phase 2, Task 8b), with the interfaces they answer;
 * the rest are still bound in the app's `dataModule` and `repositoryModule`. What they take from
 * the platform — the signed-in account (`SignedInUser`), the push queue (`PushQueue`), the
 * attachment gate, the request clocks' scheduler — each platform binds.
 */
val repositoryDataModule: Module = module {
    // How a repository tells the co-parent what changed (MON-24 phase 2, Task 8c).
    singleOf(::ActivityAnnouncer)
    singleOf(::ChangeRequestRepositoryImpl)
    single<ChangeRequestRepository> { get<ChangeRequestRepositoryImpl>() }
    single { MessageRepositoryImpl(get(), get(), get(), get()) }
    single<MessageRepository> { get<MessageRepositoryImpl>() }
    // The Room-only chat search (MON-15).
    singleOf(::ChatSearchRepositoryImpl)
    single<ChatSearchRepository> { get<ChatSearchRepositoryImpl>() }
    singleOf(::ConversationMigrator)
    // The secondary constructor, which starts the process-lifetime scope the shared custody
    // stream runs in; the primary one takes a test's scope.
    single {
        CustodyModelRepository(
            get<CustodyModelDao>(),
            get<UserRepository>(),
            get<FirestoreCustodyDataSource>(),
            get<ActivityAnnouncer>(),
            get<PushQueue>(),
            get<RequestDeadlineRepository>()
        )
    }
    singleOf(::FamilySettingsRepository)
    // All eight arguments from the graph, the family slots' source included, rather than its
    // default (`FamilySlotsSource.None`), which only a test relies on.
    single { EventRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<EventRepository> { get<EventRepositoryImpl>() }
    // The expenses (Task 8f), with the family slots' source from the graph as the events', not its
    // default.
    single { ExpenseRepositoryImpl(get(), get(), get(), get(), get(), get(), get()) }
    single<ExpenseRepository> { get<ExpenseRepositoryImpl>() }
    // The private journal (MON-22; Task 8f): Room, and nothing that leaves the phone.
    singleOf(::JournalRepositoryImpl)
    single<JournalRepository> { get<JournalRepositoryImpl>() }
    singleOf(::PetRepositoryImpl)
    single<PetRepository> { get<PetRepositoryImpl>() }
    // The children's records (Task 8e): the same family-bound audience as a pet's (item 38).
    singleOf(::ChildInfoRepositoryImpl)
    single<ChildInfoRepository> { get<ChildInfoRepositoryImpl>() }
    // The read-only calendar links (MON-17) and a child's guest invitations (item 16), on the
    // callables and the Firestore seam since Task 8d.
    singleOf(::CalendarFeedRepositoryImpl)
    single<CalendarFeedRepository> { get<CalendarFeedRepositoryImpl>() }
    singleOf(::GuestRepositoryImpl)
    single<GuestRepository> { get<GuestRepositoryImpl>() }
    // The trusted third person (item 16), bound apart from GuestRepository because a friend and a
    // guest open different things; and MON-18's two-consent, expiring professional access, bound
    // apart from FriendRepository because the two are admitted by different rules (Task 8e).
    singleOf(::FriendRepositoryImpl)
    single<FriendRepository> { get<FriendRepositoryImpl>() }
    singleOf(::ProfessionalRepositoryImpl)
    single<ProfessionalRepository> { get<ProfessionalRepositoryImpl>() }
    singleOf(::PostPairingConversationSetup)
    singleOf(::ParentingPlanRepository)
    singleOf(::RequestDeadlineRepositoryImpl)
    single<RequestDeadlineRepository> { get<RequestDeadlineRepositoryImpl>() }
    // The signed-in parent's profile, in Room and in `users/{uid}` (Task 8f): what the announcer,
    // the family selection, the chat sources and the custody and split repositories read.
    singleOf(::UserRepositoryImpl)
    single<UserRepository> { get<UserRepositoryImpl>() }
    // The co-parent link as the listeners report it, mirrored into Room (items 37 and 39; Task
    // 8f). Its thread-title wording and the sync request are the platform's.
    singleOf(::PairingRepositoryImpl)
    single<PairingRepository> { get<PairingRepositoryImpl>() }
    // Moves this phone's records between the two parent slots when a pairing or a server backfill
    // moves this parent's (Task 8g).
    singleOf(::ParentSlotMigrator)
    // This phone's settings in the sealed store, and the facts a new expense's default currency is
    // guessed from (Task 8g). The hints take their two repositories lazily: the preferences are
    // below both in the graph.
    single { CurrencyHints(inject(), inject()) }
    singleOf(::PreferencesRepositoryImpl)
    single<PreferencesRepository> { get<PreferencesRepositoryImpl>() }
}

/**
 * Which family is on screen and what it may show (MON-24 phase 2, Task 8c; CLAUDE.md items 43, 46,
 * 50): the switcher's selection, the view the repositories filter by, each family's names and
 * slots as this phone last read them, and (Task 8h) what waits in the families not on screen.
 */
val familyDataModule: Module = module {
    // The three agreements' listeners the other families' signals add per family (board SW-7).
    factoryOf(::AgreementSignalSources)
    singleOf(::FamilyViewSource)
    singleOf(::KnownFamilies)
    singleOf(::KnownFamilySlots)
    factory<FamilySlotsSource> { get<KnownFamilySlots>() }
    singleOf(::SelectedFamilySource)
    // The switcher's dot: what waits in each family not on screen (design item 13; Task 8h).
    singleOf(::OtherFamiliesSignals)
    // The parent's own records of no family, and where they belong (CLAUDE.md item 46; Task 8i).
    singleOf(::UnassignedRecords)
}

/**
 * The chat thread's process-wide mirror, the co-parent it follows and the threads kept after a
 * co-parent's departure (MON-24 phase 2, Task 8c; CLAUDE.md "Known issues", CQ-8 and M-8).
 */
val chatDataModule: Module = module {
    singleOf(::ChatMirror)
    singleOf(::ChatPartnerSource)
    singleOf(::DepartedThreadSource)
}

/**
 * The request clocks (phase 3a, CLAUDE.md item 42): what their background steps say to the
 * answering parent (MON-24 phase 2, Task 8c), and since Task 8h their time, the clocks a screen
 * reads, the keeper every sync runs, the steps, the records they read and what a parent does from a
 * card. The jobs (`RequestClockScheduler`) and the notices (`RequestClockNotices`) are the
 * platform's, bound where it builds them.
 */
val requestsDataModule: Module = module {
    singleOf(::RequestTelling)
    single { RequestTime() }
    singleOf(::RequestClocks)
    singleOf(::RequestRecords)
    singleOf(::RequestClockSteps)
    singleOf(::RequestClockActions)
    // The secondary constructor, whose deliveries run on the keeper's own scope for the process.
    single {
        RequestClockKeeper(
            get<RequestDeadlineRepository>(),
            get<RequestClockSteps>(),
            get<RequestClockScheduler>(),
            get<RequestTime>()
        )
    }
    // The clocks' pass every full sync runs.
    single<RequestClockSweep> { get<RequestClockKeeper>() }
    // The three agreements beside the requests, read and withdrawn for the family on screen
    // (board SW-7; Task 8e).
    factoryOf(::FamilyAgreementsSource)
    factoryOf(::AgreementWithdrawals)
}

/**
 * Stickers between the parents and how each parent's stickers address them (CLAUDE.md item 47;
 * MON-24 phase 2, Task 8d).
 */
val stickersDataModule: Module = module {
    singleOf(::ParentRoles)
    singleOf(::StickerStore)
}

/**
 * The vault's index in Firestore and its read-through Room cache (MON-23, CLAUDE.md item 31;
 * MON-24 phase 2, Task 8d), and since Task 8j the repository over them, which stages, uploads and
 * opens the files through [filesDataModule].
 */
val documentsDataModule: Module = module {
    singleOf(::FamilyDocumentIndex)
    singleOf(::FamilyDocumentIndexCache)
    singleOf(::FamilyDocumentRepositoryImpl)
    single<FamilyDocumentRepository> { get<FamilyDocumentRepositoryImpl>() }
}

/**
 * The files the parents share and the photographs on their records (MON-23 and L-4, CLAUDE.md item
 * 31 and Legal item 8; MON-24 phase 2, Task 8j): Storage by path and never by download URL, the
 * digest-checked view cache, the staging of a picked file, which family a stored record names, and
 * the chat outbox's gate before a message's Firestore write. The bucket (`StorageBucket`) and the
 * picker's files (`PickedFiles`) are the platform's, bound where it builds them.
 */
val filesDataModule: Module = module {
    singleOf(::SharedFileStorage)
    singleOf(::SharedFileCache)
    singleOf(::SharedFileStager)
    factoryOf(::StoredRecordFamilies)
    singleOf(::ChatAttachmentOutbox)
    // The gate `MessageRepositoryImpl` passes every message through before writing it.
    single<AttachmentUploadGate> { get<ChatAttachmentOutbox>() }
}

/**
 * Money between the parents (CLAUDE.md items 45, 48 and 49; MON-24 phase 2, Task 8e): the account
 * each is paid on, the payments each says they made and received, the settle-up card, child
 * support's agreement and this phone's reminder settings. The reminder's job and its scheduler are
 * still the app's (WorkManager).
 */
val moneyDataModule: Module = module {
    singleOf(::PaymentAccounts)
    singleOf(::PaymentStore)
    factoryOf(::SettleUpMessages)
    singleOf(::SupportAgreementStore)
    singleOf(::SupportReminders)
}

/**
 * What a communication record is gathered from (MON-3, CLAUDE.md item 26; MON-24 phase 2, Task
 * 8i): the server's revisions and messages and this phone's copies, the parenting plan and the
 * private journal. The file writer (`PdfDocument`) and the record ids' callables are the app's.
 */
val exportDataModule: Module = module {
    singleOf(::CommunicationRecordSource)
    singleOf(::ParentingPlanRecordSource)
    singleOf(::OptionalRecordSections)
}

/**
 * The school import (MON-8, CLAUDE.md item 35; MON-24 phase 2, Task 8i): the sealed connection
 * store, the connect flow, the sign-in keeper, the calendar writer and the importer. The Bakaláři
 * client and directory, the titles' wording and the daily job's scheduler are the app's (its named
 * HTTP client, string resources and WorkManager).
 */
val schoolDataModule: Module = module {
    singleOf(::SchoolConnectionStore)
    singleOf(::SchoolAccounts)
    singleOf(::SchoolEventWriter)
    single { BakalariAuthorizer(get<BakalariClient>(), get<SchoolConnectionStore>()) }
    factory { SchoolImporter.Collaborators(get(), get(), get(), get()) }
    single {
        SchoolImporter(
            get<BakalariAuthorizer>(),
            get<BakalariClient>(),
            get<SchoolConnectionStore>(),
            get<UserRepository>(),
            get<SchoolImporter.Collaborators>()
        )
    }
}

/**
 * The session (MON-24 phase 2, Task 8j): the profile kept in existence for every signed-in account,
 * after the platform's account-switch check (`AccountConsistency`), and "who am I signed in as" for
 * the pairing and Settings screens. Account deletion, the switch's wipe and the device's traces are
 * still the app's (the Functions SDK, a `Context` and the notification manager).
 */
val sessionDataModule: Module = module {
    singleOf(::SessionProfileSynchronizer)
    singleOf(::SignedInAccountSource)
}

/**
 * The event use cases and the error boundary of the screen that runs them (MON-24 phase 2, Task
 * 8f; the app's `useCaseModule` and `domainModule` before). Each use case is a `factory`: under
 * Hilt they were `@ViewModelScoped`, one instance per ViewModel, and every one of them is
 * stateless, so a new instance per resolution behaves the same and needs no ViewModel scope (the
 * school import's writer builds them outside any ViewModel, through their constructors).
 */
val useCaseDataModule: Module = module {
    factoryOf(::EventValidator)
    factoryOf(::CreateEventUseCase)
    factoryOf(::UpdateEventUseCase)
    factoryOf(::DeleteEventUseCase)
    factoryOf(::GetEventsUseCase)
    factoryOf(::EventUseCases)
    singleOf(::ErrorHandler)
}

/**
 * Every module of this layer both apps use. The app's `appModules` includes them, with
 * [platformDataModule]; the iPhone app's will.
 */
val dataModules: List<Module> = listOf(
    remoteDataModule,
    syncDataModule,
    versionsDataModule,
    repositoryDataModule,
    familyDataModule,
    chatDataModule,
    requestsDataModule,
    stickersDataModule,
    documentsDataModule,
    filesDataModule,
    moneyDataModule,
    exportDataModule,
    schoolDataModule,
    sessionDataModule,
    useCaseDataModule
)

// TODO(MON-24): bind the DAOs, SecurePreferences, CrashReporter, Analytics, Connectivity,
//  SignedInUser, PushQueue, PushDevice, AttachmentUploadGate, RequestClockScheduler,
//  RequestClockNotices, ReminderScheduler, SyncRequester, PairingWording, AccountConsistency,
//  GoogleCalendarApi, CredentialProvider, GoogleCalendarConnectionSource, BakalariClient,
//  SchoolImportWordings and SchoolImportScheduler on the iPhone before its app starts Koin.
/**
 * The Firestore and callable seams [dataModules] needs: the
 * [com.coparently.app.data.remote.firebase.FirestoreClient] and the
 * [com.coparently.app.data.remote.firebase.CallableFunctions]. On Android they wrap the Firebase
 * SDK instances the app's `firebaseModule` builds, so an instrumented test's replacement of those
 * reaches the data sources; on the iPhone they are GitLive's default instances.
 *
 * It is not all that [dataModules] needs from the platform. Since Task 8b its definitions also take
 * the Room DAOs, `SecurePreferences`, `CrashReporter`, `SignedInUser`, `PushQueue`,
 * `AttachmentUploadGate` and `RequestClockScheduler`, and since Task 8f `Analytics`,
 * `Connectivity`, `ReminderScheduler` (the event use cases and `ErrorHandler`), `SyncRequester` and
 * `PairingWording` (the pairing repository), and since Task 8g `PushDevice` and
 * `AccountConsistency` (the sync) and `GoogleCalendarApi`, `CredentialProvider` and
 * `GoogleCalendarConnectionSource` (the Google Calendar import), and since Task 8h
 * `RequestClockNotices` (the request clocks' steps), and since Task 8i `BakalariClient`,
 * `SchoolImportWordings` and `SchoolImportScheduler` (the school import), which the app's own
 * modules bind on Android
 * (`UserRepository` and `PairingRepository`, which the app bound until Task 8f, and
 * `RequestClockSweep`, which it bound in Task 8g, are this layer's own now). The iPhone's module
 * binds none of them yet; nothing starts Koin there, and the first resolution would fail until it
 * does.
 */
expect val platformDataModule: Module
