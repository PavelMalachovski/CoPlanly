package com.coparently.app.data.remote.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRunLoop
import platform.Foundation.create
import platform.Foundation.runMode
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Firebase Auth emulator, as this module's iPhone tests reach it (MON-24 phase 3, Task 5).
 *
 * **Where it runs.** `COPLANLY_EMULATOR_HOST` names the host of the emulators `firebase.json`
 * configures (the `ios-app` CI job runs the tests inside `firebase emulators:exec` with
 * `127.0.0.1`, which the simulator shares with the Mac). The simulator hands a test process only the
 * variables Gradle passes as `SIMCTL_CHILD_…`, which `shared-data/build.gradle.kts` does for both
 * names here, and which arrive without the prefix.
 *
 * **The skip and fail rule**, as `web-tests` has it: with no host a test returns at once and prints
 * [SKIPPED] (`shared-ios` runs these tests without emulators); with `COPLANLY_REQUIRE_EMULATORS=1`
 * and no host it fails instead, so the job that starts the emulators cannot pass by running
 * nothing. A test that ran prints [RAN], and CI checks that no [SKIPPED] line and at least one [RAN]
 * line came out of that job — the guard against the variables not reaching the simulator at all,
 * where both would be missing.
 *
 * **Never the real project.** The options are the fakes `IosFirebase` uses for `demo-coplanly`, which
 * only the emulators accept.
 */
internal object AuthEmulator {

    /** Printed by a test that ran against the emulator. */
    const val RAN = "COPLANLY_EMULATOR_TEST_RAN"

    /** Printed by a test that found no emulator host and returned without testing anything. */
    const val SKIPPED = "COPLANLY_EMULATOR_TEST_SKIPPED"

    /** The Auth emulator's port in `firebase.json`. */
    private const val AUTH_PORT = 9099

    private val host: String? = variable("COPLANLY_EMULATOR_HOST")

    private val required: Boolean = variable("COPLANLY_REQUIRE_EMULATORS") == "1"

    /**
     * The default app on `demo-coplanly`, with Auth on the emulator: once per test process, before
     * anything uses Auth (the SDK refuses `useEmulator` after that).
     */
    private val auth: FirebaseAuth by lazy {
        Firebase.initialize(
            options = FirebaseOptions(
                applicationId = "1:000000000000:ios:0000000000000000",
                apiKey = "A-fake-key-for-the-firebase-emulators-x",
                projectId = "demo-coplanly",
                storageBucket = "demo-coplanly.appspot.com",
                gcmSenderId = "000000000000",
            )
        )
        Firebase.auth.also { it.useEmulator(checkNotNull(host), AUTH_PORT) }
    }

    /**
     * Runs [test] with the emulator's Auth, signed out at its end, or returns at once when there is
     * no emulator (and fails then if one is required); see the class comment.
     */
    fun run(name: String, test: suspend CoroutineScope.(FirebaseAuth) -> Unit) {
        if (host == null) {
            if (required) fail("COPLANLY_REQUIRE_EMULATORS=1 but no COPLANLY_EMULATOR_HOST reached the test")
            println("$SKIPPED $name")
            return
        }
        val auth = auth
        try {
            runOnMainRunLoop { test(auth) }
        } finally {
            runOnMainRunLoop { auth.signOut() }
        }
        println("$RAN $name")
    }

    private fun variable(name: String): String? =
        (NSProcessInfo.processInfo.environment[name] as? String)?.takeIf { it.isNotBlank() }
}

/**
 * Runs [block] on the main queue and turns the main run loop until it ends, as GitLive's own Apple
 * tests do: Firebase answers on the main queue, and a test that blocked the main thread (the
 * Kotlin/Native test runner runs every test there) would never hear back.
 *
 * @throws Throwable what [block] threw, or a timeout after [timeout]
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun runOnMainRunLoop(timeout: Duration = 60.seconds, block: suspend CoroutineScope.() -> Unit) {
    val scope = MainScope()
    val run = scope.async { withTimeout(timeout) { block() } }
    while (run.isActive) {
        NSRunLoop.mainRunLoop.runMode(
            NSDefaultRunLoopMode,
            beforeDate = NSDate.create(timeInterval = 0.05, sinceDate = NSDate())
        )
    }
    scope.cancel()
    run.getCompletionExceptionOrNull()?.let { throw it }
}
