package com.coparently.app.data.remote.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage

/**
 * The named Firebase apps this module's emulator tests run on, made once per test process, with
 * Storage pointed at the emulator on every one of them before any of them is used (MON-24 phase 3,
 * part C).
 *
 * Firebase iOS 11.8.0's Storage refuses `useEmulator` once any Storage in the process has made a
 * reference: "Cannot connect to emulator after Storage SDK initialization", a fatal error that ends
 * the test process. In CI run 11, `IosStorageBucketEmulatorTest` used its app's Storage first, and
 * the write gate's `useEmulator` on its own, different, app then ended the process. So the first
 * test that asks for any of these apps makes all of them and configures all of their Storage at
 * once; each test then takes its app from here instead of initializing one.
 *
 * Auth, Firestore and Functions keep their own `useEmulator` calls in each test: each refuses a
 * late call only for its own instance (an app's Auth, an app's Firestore after first use), and each
 * test makes the one call for its own app first, before using it.
 *
 * Never the real project: the options are the fakes `IosFirebase` uses for `demo-coplanly`, which
 * only the emulators accept.
 */
internal object EmulatorApps {

    /** The Storage emulator's port in `firebase.json`. */
    private const val STORAGE_PORT = 9199

    /** The bucket in [OPTIONS]. */
    private const val BUCKET = "demo-coplanly.appspot.com"

    /** The emulators' fake options, which no production service accepts. */
    val OPTIONS = FirebaseOptions(
        applicationId = "1:000000000000:ios:0000000000000000",
        apiKey = "A-fake-key-for-the-firebase-emulators-x",
        projectId = "demo-coplanly",
        storageBucket = BUCKET,
        gcmSenderId = "000000000000",
    )

    /**
     * The bucket `IosStorageBucketEmulatorTest` uses, apart from the default one the write gate and
     * the checker (`tools/ios/check-ios-writes.js`) use. Firebase iOS 11.8.0's Storage keeps one
     * request service per bucket for the whole process, with the credentials of the app that made
     * the first reference to it: in CI run 12 the bucket test, signed out at its end, had made it,
     * and every request the gate's phones then sent went without a signed-in user, which
     * `storage.rules` refuses (`-13021`, unauthorized, on the pet's first read). The same sharing
     * is why one app's late `useEmulator` ended the process for another (run 11). The emulator
     * serves any bucket under the same rules.
     */
    private const val TEST_BUCKET = "demo-coplanly-storage-test"

    /** `IosStorageBucketEmulatorTest`'s app. */
    const val STORAGE_BUCKET_TEST = "storage_bucket_test"

    /** Every named app a test in this module asks for, the write gate's four phones included. */
    private val NAMES = listOf(STORAGE_BUCKET_TEST, "gate_alice", "gate_bob", "gate_carol", "gate_dan")

    private var apps: Map<String, Pair<FirebaseApp, FirebaseStorage>>? = null

    /** The app named [name], one of [NAMES], on the emulators at [host]. */
    fun app(name: String, host: String): FirebaseApp = all(host).getValue(name).first

    /** [name]'s Storage, already on the emulator at [host]; see the class comment. */
    fun storage(name: String, host: String): FirebaseStorage = all(host).getValue(name).second

    private fun all(host: String): Map<String, Pair<FirebaseApp, FirebaseStorage>> = apps ?: run {
        val made = NAMES.associateWith { name ->
            val app = Firebase.initialize(context = null, options = OPTIONS, name = name)
            // The bucket test on a bucket of its own: see [TEST_BUCKET].
            val storage = if (name == STORAGE_BUCKET_TEST) {
                Firebase.storage(app, "gs://$TEST_BUCKET")
            } else {
                Firebase.storage(app)
            }
            app to storage.also { it.useEmulator(host, STORAGE_PORT) }
        }
        made.also { apps = it }
    }
}
