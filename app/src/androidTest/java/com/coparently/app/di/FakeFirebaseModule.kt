package com.coparently.app.di

import com.coparently.app.data.remote.firebase.QRCodeService
import com.coparently.app.di.koin.FUNCTIONS_REGION
import com.coparently.app.e2e.EmulatorEnvironment
import com.coparently.app.testing.NoFcm
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import io.mockk.mockk
import org.koin.dsl.module

/**
 * Overrides `firebaseModule` for instrumented tests, so nothing reaches a real Firebase SDK.
 * `KoinTestRule` loads it after the app's modules; each definition here replaces the app's of the
 * same type, and a test that loads it carries `@UsesFakeFirebase`.
 *
 * Without this the suite cannot run at all. Every definition in `firebaseModule` calls a
 * `getInstance()` that requires a default `FirebaseApp`, and `FirebaseApp` is initialised by a
 * content provider that reads `google-services.json` — which is gitignored and has never existed
 * in CI. The first definition the graph resolves therefore throws
 * `IllegalStateException: Default FirebaseApp is not initialized`, and because the graph is
 * resolved while `MainActivity` is being created, that kills the process and takes the whole
 * instrumentation run with it rather than failing one test.
 *
 * `KoinTestRunner` substituting `KoinTestApplication` does not prevent it: that stops
 * `CoPlanlyApplication.onCreate` from running, not the graph from being resolved.
 *
 * Shipping a fake `google-services.json` would also have worked and was rejected: the Google
 * Services and Crashlytics plugins in `app/build.gradle.kts` apply *only* when that file is
 * present, so adding one changes what every Android job builds — to fix a problem that belongs
 * to the tests. Overriding the module keeps the change inside `androidTest`.
 *
 * The mocks are relaxed because no test here asserts on Firebase: these tests are about
 * composables and about Room. What matters is that the graph resolves and that
 * [com.coparently.app.data.local.CoPlanlyDatabase] is still the real one, so opening it through
 * SQLCipher (SEC-2) is genuinely exercised rather than stubbed out.
 *
 * **One exception, and only on the emulators.** When the run was started with an emulator host —
 * the `e2e` job, which runs nothing but `com.coparently.app.e2e` — Auth, Firestore, Storage and
 * Functions are the real SDKs of [EmulatorEnvironment.appUnderTest], pointed at the Firebase
 * emulators, so the app's own screens can be driven against a co-parent on the same backend
 * (`OneParentOnScreenTest`). Messaging, Analytics and Crashlytics stay mocks there too: FCM has no
 * emulator, and telemetry must never switch on in a test. Every other run sees exactly the mocks.
 */
val fakeFirebaseModule = module {
    single<FirebaseAuth> {
        EmulatorEnvironment.appUnderTest?.let { FirebaseAuth.getInstance(it) } ?: mockk(relaxed = true)
    }

    single<FirebaseFirestore> {
        EmulatorEnvironment.appUnderTest?.let { FirebaseFirestore.getInstance(it) } ?: mockk(relaxed = true)
    }

    single<FirebaseStorage> {
        EmulatorEnvironment.appUnderTest?.let { FirebaseStorage.getInstance(it) } ?: mockk(relaxed = true)
    }

    single<FirebaseMessaging> { NoFcm.messaging() }

    single<FirebaseAnalytics> { mockk(relaxed = true) }

    single<FirebaseCrashlytics> { mockk(relaxed = true) }

    single<FirebaseFunctions> {
        EmulatorEnvironment.appUnderTest?.let {
            FirebaseFunctions.getInstance(it, FUNCTIONS_REGION)
        } ?: mockk(relaxed = true)
    }

    // The real service, not a mock: it encodes a bitmap with ZXing and touches no Firebase despite
    // its package. Defined here because `firebaseModule` defines it, and this module stands in for
    // that one as a whole.
    single { QRCodeService() }
}
