package com.coparently.app.di.koin

import com.coparently.app.data.remote.firebase.QRCodeService
import com.coparently.app.data.repository.UserRepositoryImpl
import com.coparently.app.domain.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import org.koin.dsl.module

/**
 * The region every Cloud Function runs in: Frankfurt, so a family's data is processed inside
 * the EEA (`docs/legal/LEGAL-REVIEW-2026-09.md` L-3). It has to match `FUNCTIONS_REGION` in
 * `functions/index.js`: a callable asked for in another region answers NOT_FOUND.
 */
const val FUNCTIONS_REGION = "europe-west3"

/** Cap Storage retries at 20s so photo uploads fail fast instead of hanging ~2min. */
private const val STORAGE_RETRY_TIMEOUT_MS = 20_000L

/**
 * The Firebase SDKs, one instance each for the process.
 *
 * Instrumented tests replace this whole module (`fakeFirebaseModule` in `androidTest`): every
 * definition here calls a `getInstance()` that needs a default `FirebaseApp`, which CI has no
 * `google-services.json` for.
 *
 * **Analytics and Crashlytics are created collecting nothing.** A definition closes the gate; it
 * never opens it (REL-5). [com.coparently.app.data.telemetry.TelemetryConsentApplier] is the one
 * place that enables either SDK, because the graph builds them once and consent changes
 * afterwards. What closing them here still buys is the case the applier cannot cover: a
 * resolution that happens before `Application.onCreate` has got as far as starting it, and any
 * build or test that resolves the SDK without an applier at all. The build flags themselves —
 * false for debug, true for release — are read only by the applier, and they still mean what they
 * always did: which *project* may receive data, never that a user agreed.
 */
val firebaseModule = module {
    single<FirebaseAuth> { FirebaseAuth.getInstance() }

    // The default database, "(default)". Offline persistence is on by default since SDK v24, so
    // the app caches data locally for offline access without configuring it.
    single<FirebaseFirestore> {
        FirebaseFirestore.getInstance().also {
            android.util.Log.d("FirebaseModule", "Firestore initialized (offline persistence enabled by default)")
        }
    }

    // Receipt photos and other attachments. The default retry windows are about two minutes, so
    // an upload to a bucket that is unreachable (Storage not enabled yet, say) hung the Save for
    // that long before failing. Capped, an upload fails fast and the record is saved promptly
    // without the photo, with a warning.
    single<FirebaseStorage> {
        FirebaseStorage.getInstance().apply {
            maxUploadRetryTimeMillis = STORAGE_RETRY_TIMEOUT_MS
            maxDownloadRetryTimeMillis = STORAGE_RETRY_TIMEOUT_MS
            maxOperationRetryTimeMillis = STORAGE_RETRY_TIMEOUT_MS
        }
    }

    single<FirebaseMessaging> { FirebaseMessaging.getInstance() }

    single<FirebaseAnalytics> { Firebase.analytics.apply { setAnalyticsCollectionEnabled(false) } }

    // A debug crash is already in front of the developer who caused it; uploading it adds nothing.
    // A release crash is worth having and is still not worth taking unasked.
    single<FirebaseCrashlytics> {
        FirebaseCrashlytics.getInstance().apply { setCrashlyticsCollectionEnabled(false) }
    }

    // The callables, in FUNCTIONS_REGION — not the SDK's default, which is us-central1.
    single<FirebaseFunctions> { FirebaseFunctions.getInstance(FUNCTIONS_REGION) }

    // Encodes the pairing QR with ZXing; it touches no Firebase despite its package.
    single { QRCodeService() }
}

/** The Firebase-backed user repository, bound to its domain interface. */
val firebaseRepositoryModule = module {
    single<UserRepository> { get<UserRepositoryImpl>() }
}
