package com.coparently.app.di.koin

import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.GitLiveFirestoreClient
import com.coparently.app.data.remote.firebase.IosCallableFunctions
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * GitLive's default instances. Nothing builds this graph on the iPhone yet: the app that
 * configures Firebase first (`FirebaseApp.configure()`) comes in phase 3.
 */
actual val platformDataModule: Module = module {
    single<FirestoreClient> { GitLiveFirestoreClient(Firebase.firestore) }
    single<CallableFunctions> { IosCallableFunctions(Firebase.functions(FUNCTIONS_REGION)) }
}
