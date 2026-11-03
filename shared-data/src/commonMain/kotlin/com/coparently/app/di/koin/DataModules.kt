package com.coparently.app.di.koin

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
import com.coparently.app.data.repository.ChangeRequestRepositoryImpl
import com.coparently.app.data.repository.ChatSearchRepositoryImpl
import com.coparently.app.data.repository.ConversationMigrator
import com.coparently.app.data.repository.MessageRepositoryImpl
import com.coparently.app.data.repository.ParentingPlanRepository
import com.coparently.app.data.repository.PostPairingConversationSetup
import com.coparently.app.data.repository.RequestDeadlineRepositoryImpl
import com.coparently.app.data.sync.ConflictResolver
import com.coparently.app.data.sync.FamilyIdBackfill
import com.coparently.app.data.versions.EventVersionRecorder
import com.coparently.app.domain.repository.ChangeRequestRepository
import com.coparently.app.domain.repository.ChatSearchRepository
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.RequestDeadlineRepository
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
    singleOf(::ChangeRequestRepositoryImpl)
    single<ChangeRequestRepository> { get<ChangeRequestRepositoryImpl>() }
    single { MessageRepositoryImpl(get(), get(), get(), get()) }
    single<MessageRepository> { get<MessageRepositoryImpl>() }
    // The Room-only chat search (MON-15).
    singleOf(::ChatSearchRepositoryImpl)
    single<ChatSearchRepository> { get<ChatSearchRepositoryImpl>() }
    singleOf(::ConversationMigrator)
    singleOf(::PostPairingConversationSetup)
    singleOf(::ParentingPlanRepository)
    singleOf(::RequestDeadlineRepositoryImpl)
    single<RequestDeadlineRepository> { get<RequestDeadlineRepositoryImpl>() }
}

/**
 * Every module of this layer both apps use. The app's `appModules` includes them, with
 * [platformDataModule]; the iPhone app's will.
 */
val dataModules: List<Module> = listOf(remoteDataModule, syncDataModule, versionsDataModule, repositoryDataModule)

/**
 * What each platform binds for [dataModules]: the
 * [com.coparently.app.data.remote.firebase.FirestoreClient] and the
 * [com.coparently.app.data.remote.firebase.CallableFunctions]. On Android they wrap the Firebase
 * SDK instances the app's `firebaseModule` builds, so an instrumented test's replacement of those
 * reaches the data sources; on the iPhone they are GitLive's default instances.
 */
expect val platformDataModule: Module
