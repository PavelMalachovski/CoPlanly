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
 * Every module of this layer both apps use. The app's `appModules` includes them, with
 * [platformDataModule]; the iPhone app's will.
 */
val dataModules: List<Module> = listOf(remoteDataModule)

/**
 * What each platform binds for [dataModules]: the
 * [com.coparently.app.data.remote.firebase.FirestoreClient] and the
 * [com.coparently.app.data.remote.firebase.CallableFunctions]. On Android they wrap the Firebase
 * SDK instances the app's `firebaseModule` builds, so an instrumented test's replacement of those
 * reaches the data sources; on the iPhone they are GitLive's default instances.
 */
expect val platformDataModule: Module
