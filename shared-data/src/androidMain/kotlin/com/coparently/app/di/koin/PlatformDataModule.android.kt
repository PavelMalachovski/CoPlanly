package com.coparently.app.di.koin

import com.coparently.app.data.remote.firebase.AndroidCallableFunctions
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.firestoreClientOf
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import org.koin.core.module.Module
import org.koin.dsl.module

/** The Android SDK instances of the app's `firebaseModule`, as the data layer's seams. */
actual val platformDataModule: Module = module {
    single<FirestoreClient> { firestoreClientOf(get<FirebaseFirestore>()) }
    single<CallableFunctions> { AndroidCallableFunctions(get<FirebaseFunctions>()) }
}
