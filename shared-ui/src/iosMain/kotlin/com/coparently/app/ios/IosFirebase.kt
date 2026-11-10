package com.coparently.app.ios

import com.coparently.app.di.koin.FUNCTIONS_REGION
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import dev.gitlive.firebase.initialize

/**
 * Starts Firebase for the iPhone app (MON-24 phase 3), before anything reads it: the Koin graph's
 * `platformDataModule` asks GitLive for the default Firestore, Auth and the Functions of
 * [FUNCTIONS_REGION], and those must already point where this says.
 *
 * Two configurations, and the choice is the shell's ([IosAppConfig.emulatorHost]):
 * - **The emulators** on [IosAppConfig.emulatorHost], under the credential-free `demo-coplanly`
 *   project the Android e2e job uses. Firestore on [FIRESTORE_PORT], Functions on [FUNCTIONS_PORT]
 *   and, since Task 5, Auth on [AUTH_PORT], the ports `firebase.json` gives them. The options are
 *   fakes the emulators accept and no production service would, so a build without the real
 *   configuration can never reach production.
 * - **The real project**, from the `GoogleService-Info.plist` the owner drops into the app's
 *   folder (never committed): `FirebaseApp.configure()`.
 */
internal object IosFirebase {
    /** The Firestore emulator's port in `firebase.json`. */
    private const val FIRESTORE_PORT = 8080

    /** The Functions emulator's port in `firebase.json`. */
    private const val FUNCTIONS_PORT = 5001

    /** The Auth emulator's port in `firebase.json`. */
    private const val AUTH_PORT = 9099

    /**
     * The emulators' project: `demo-` makes the Firebase emulators run it without credentials and
     * refuse anything outside them. The application id has the shape `FirebaseApp` validates; the
     * sender id is the same zeros, because the iPhone SDK's options take one.
     */
    private val EMULATOR_OPTIONS = FirebaseOptions(
        applicationId = "1:000000000000:ios:0000000000000000",
        apiKey = "A-fake-key-for-the-firebase-emulators-x",
        projectId = "demo-coplanly",
        storageBucket = "demo-coplanly.appspot.com",
        gcmSenderId = "000000000000",
    )

    /** Configures Firebase as [config] says; once per process, before the Koin graph starts. */
    fun configure(config: IosAppConfig) {
        val host = config.emulatorHost
        if (host == null) {
            Firebase.initialize()
            return
        }
        Firebase.initialize(options = EMULATOR_OPTIONS)
        Firebase.firestore.useEmulator(host, FIRESTORE_PORT)
        Firebase.functions(FUNCTIONS_REGION).useEmulator(host, FUNCTIONS_PORT)
        // Before anything signs in: the SDK refuses the emulator once Auth has been used.
        Firebase.auth.useEmulator(host, AUTH_PORT)
    }
}
