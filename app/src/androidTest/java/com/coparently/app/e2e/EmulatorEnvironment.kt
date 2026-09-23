package com.coparently.app.e2e

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the Firebase emulators are, and whether this run has any.
 *
 * **The two-parent tests run only when asked to.** They need Auth, Firestore and Functions
 * emulators listening on the host, which only the `e2e` CI job (and `tools/e2e/run-two-parent-
 * tests.sh` locally) starts. Everything else — the ordinary `instrumented` job, a developer's
 * `connectedDebugAndroidTest` — passes no [HOST_ARGUMENT], and every test in this package then
 * reports itself as skipped through [assumeEmulators] rather than failing on a refused connection.
 * The e2e job checks the XML afterwards and fails on a skip, so this switch cannot quietly turn
 * that job green either.
 *
 * The ports are `firebase.json`'s. They are constants rather than arguments on purpose: the file
 * is the single statement of where the emulators listen, and a second one here that could drift
 * from it would produce a connection error that names neither.
 */
object EmulatorEnvironment {

    /** A `demo-` project needs no credentials and cannot reach production by construction. */
    const val PROJECT_ID = "demo-coplanly"

    /** `emulators.auth.port` in `firebase.json`. */
    const val AUTH_PORT = 9099

    /** `emulators.firestore.port` in `firebase.json`. */
    const val FIRESTORE_PORT = 8080

    /** `emulators.functions.port` in `firebase.json`. */
    const val FUNCTIONS_PORT = 5001

    /**
     * The instrumentation argument naming the emulator host — `10.0.2.2` from an Android emulator,
     * which is its alias for the host machine's loopback.
     */
    const val HOST_ARGUMENT = "coplanlyEmulatorHost"

    private const val HTTP_OK = 200
    private const val HTTP_NOT_FOUND = 404
    private const val CONNECT_TIMEOUT_MS = 10_000

    /** The emulator host, or null when this run was started without one. */
    val host: String?
        get() = InstrumentationRegistry.getArguments().getString(HOST_ARGUMENT)
            ?.takeIf { it.isNotBlank() }

    /** Skips the calling test unless this run was started against the emulators. */
    fun assumeEmulators() {
        assumeTrue(
            "No -e $HOST_ARGUMENT: the two-parent tests need the Firebase emulators",
            host != null
        )
    }

    /** [host], for code that only runs after [assumeEmulators] has passed. */
    fun requireHost(): String = checkNotNull(host) { "assumeEmulators() was not called" }

    /**
     * Whether `documents/[path]` exists, read **around** the security rules.
     *
     * The one oracle in these tests that is not a parent. A client cannot answer "is there no such
     * document": the `events` read rule dereferences `resource.data`, so a get of a missing
     * document is denied rather than empty, and "denied" is also what a present-but-unshared one
     * returns. The emulator accepts `Bearer owner` as an admin token, so this asks it directly.
     * Only ever used to *read*; every write in these tests goes through the rules.
     *
     * @param path A document path such as `events/abc`.
     */
    fun documentExists(path: String): Boolean {
        val url = URL(
            "http://${requireHost()}:$FIRESTORE_PORT/v1/projects/$PROJECT_ID" +
                "/databases/(default)/documents/$path"
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = CONNECT_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer owner")
            when (val code = connection.responseCode) {
                HTTP_OK -> true
                HTTP_NOT_FOUND -> false
                else -> error("Firestore emulator answered $code for $path")
            }
        } finally {
            connection.disconnect()
        }
    }
}
