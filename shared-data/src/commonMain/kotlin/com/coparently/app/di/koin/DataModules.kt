package com.coparently.app.di.koin

import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.chat.ChatPartnerSource
import com.coparently.app.data.chat.DepartedThreadSource
import com.coparently.app.data.family.FamilyViewSource
import com.coparently.app.data.family.KnownFamilies
import com.coparently.app.data.family.KnownFamilySlots
import com.coparently.app.data.family.SelectedFamilySource
import com.coparently.app.data.local.dao.CustodyModelDao
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
import com.coparently.app.data.repository.ChangeRequestRepositoryImpl
import com.coparently.app.data.repository.ChatSearchRepositoryImpl
import com.coparently.app.data.repository.ConversationMigrator
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.data.repository.EventRepositoryImpl
import com.coparently.app.data.repository.FamilySettingsRepository
import com.coparently.app.data.repository.MessageRepositoryImpl
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.data.repository.PetRepositoryImpl
import com.coparently.app.data.repository.PostPairingConversationSetup
import com.coparently.app.data.repository.RequestDeadlineRepositoryImpl
import com.coparently.app.data.requests.RequestTelling
import com.coparently.app.data.sync.ConflictResolver
import com.coparently.app.data.sync.FamilyIdBackfill
import com.coparently.app.data.versions.EventVersionRecorder
import com.coparently.app.domain.activity.ActivityAnnouncer
import com.coparently.app.domain.family.FamilySlotsSource
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.ChatSearchRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.PetRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import org.koin.core.module.Module
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
 * The sync's pure parts (MON-24 phase 2, Task 8b). Their Room DAOs and the sealed preference store
 * are the platform's database and store, bound where each platform builds them.
 */
val syncDataModule: Module = module {
    singleOf(::ConflictResolver)
    singleOf(::FamilyIdBackfill)
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
    singleOf(::PetRepositoryImpl)
    single<PetRepository> { get<PetRepositoryImpl>() }
    singleOf(::PostPairingConversationSetup)
    singleOf(::ParentingPlanRepository)
    singleOf(::RequestDeadlineRepositoryImpl)
    single<RequestDeadlineRepository> { get<RequestDeadlineRepositoryImpl>() }
}

/**
 * Which family is on screen and what it may show (MON-24 phase 2, Task 8c; CLAUDE.md items 43, 46,
 * 50): the switcher's selection, the view the repositories filter by, and each family's names and
 * slots as this phone last read them.
 */
val familyDataModule: Module = module {
    singleOf(::FamilyViewSource)
    singleOf(::KnownFamilies)
    singleOf(::KnownFamilySlots)
    factory<FamilySlotsSource> { get<KnownFamilySlots>() }
    singleOf(::SelectedFamilySource)
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
 * What the request clocks' background steps say to the answering parent (phase 3a; MON-24 phase 2,
 * Task 8c). The clocks' keeper, steps and notices are still bound in the app's `dataModule`.
 */
val requestsDataModule: Module = module {
    singleOf(::RequestTelling)
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
    requestsDataModule
)

// TODO(MON-24): bind the DAOs, SecurePreferences, CrashReporter, SignedInUser, PushQueue,
//  AttachmentUploadGate, RequestClockScheduler, UserRepository and PairingRepository on the iPhone
//  before its app starts Koin.
/**
 * The Firestore and callable seams [dataModules] needs: the
 * [com.coparently.app.data.remote.firebase.FirestoreClient] and the
 * [com.coparently.app.data.remote.firebase.CallableFunctions]. On Android they wrap the Firebase
 * SDK instances the app's `firebaseModule` builds, so an instrumented test's replacement of those
 * reaches the data sources; on the iPhone they are GitLive's default instances.
 *
 * It is not all that [dataModules] needs from the platform. Since Task 8b its definitions also take
 * the Room DAOs, `SecurePreferences`, `CrashReporter`, `SignedInUser`, `PushQueue`,
 * `AttachmentUploadGate` and `RequestClockScheduler`, and since Task 8c `UserRepository` (the
 * announcer, the family selection, the chat sources and the custody and split repositories) and
 * `PairingRepository` (`ChatPartnerSource`), which the app's own modules bind on Android.
 * The iPhone's module binds none of them yet; nothing starts Koin there, and the first resolution
 * would fail until it does.
 */
expect val platformDataModule: Module
