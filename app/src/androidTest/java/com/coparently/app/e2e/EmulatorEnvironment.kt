package com.coparently.app.e2e

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.rules.TestWatcher
import org.junit.rules.Timeout
import org.junit.runner.Description
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Where the Firebase emulators are, and whether this run has any.
 *
 * **The two-parent tests run only when asked to.** They need Auth, Firestore, Functions and Storage
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

    /** `emulators.storage.port` in `firebase.json`. */
    const val STORAGE_PORT = 9199

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

    /**
     * The Firebase app the **app under test** uses — the Hilt graph's `FirebaseAuth`, `Firestore`,
     * `Storage` and `Functions` — when this run has emulators, else null.
     *
     * `FakeFirebaseModule` reads it: with no emulator host it keeps providing relaxed mocks, which
     * is every run of the ordinary `instrumented` job; with one, the app's own screens talk to the
     * same emulators the other phone does (`OneParentOnScreenTest`). One per process, like the
     * default app it stands in for.
     */
    val appUnderTest: FirebaseApp? by lazy {
        host?.let {
            startFirebaseApp(InstrumentationRegistry.getInstrumentation().targetContext, "e2e-app-under-test")
        }
    }

    /**
     * Initialises a named Firebase app for the credential-free [PROJECT_ID] and points every SDK
     * the app uses at the emulators — before the first call on each, which is the only time
     * redirection is allowed.
     */
    fun startFirebaseApp(context: Context, name: String): FirebaseApp {
        val host = requireHost()
        val options = FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:000000000000:android:0000000000000000")
            // Not a key: Firebase Installations (which Functions calls for a token) refuses
            // any value that does not match `A[\w-]{38}`, emulator or not. Kept off the
            // `AIza` shape so secret scanning never mistakes it for a Google API key.
            .setApiKey("A-fake-key-for-the-firebase-emulators-x")
            // The Storage emulator serves any bucket name; this is the project's default one.
            .setStorageBucket("$PROJECT_ID.appspot.com")
            .build()
        val app = FirebaseApp.initializeApp(context, options, name)
        FirebaseAuth.getInstance(app).useEmulator(host, AUTH_PORT)
        FirebaseFirestore.getInstance(app).apply {
            useEmulator(host, FIRESTORE_PORT)
            // Memory only: a persisted cache would let a read be answered by this phone's
            // own earlier write rather than by the server the other phone reads.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        FirebaseFunctions.getInstance(app).useEmulator(host, FUNCTIONS_PORT)
        FirebaseStorage.getInstance(app).useEmulator(host, STORAGE_PORT)
        return app
    }

    /**
     * A hard limit for one two-parent test, `@Before` and `@After` included, that fails it with the
     * stack of the thread that was stuck instead of letting it hang. A run on PR #103 passed its
     * first test and then printed nothing for ten minutes, until the CI step's own limit killed it
     * with no result and no trace; this turns that into a failure that names the line.
     */
    fun testTimeout(): Timeout = Timeout.builder()
        .withTimeout(TEST_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .withLookingForStuckThread(true)
        .build()

    private const val TEST_TIMEOUT_MINUTES = 3L

    /** The logcat tag of [step] and of the thread dump; the e2e job prints both on a failure. */
    const val LOG_TAG = "E2E"

    /**
     * Marks where a two-parent test is, in logcat. When a test times out the stuck-thread
     * detector names whichever thread it guesses — on PR #103 a Firebase `TokenRefresher` that
     * was merely idle — so the last step logged is what says where the test itself was waiting.
     */
    fun step(name: String) {
        Log.i(LOG_TAG, "step: $name")
    }

    /**
     * On a failure, writes every thread's stack to logcat under [LOG_TAG], one entry per thread
     * (a logcat entry is capped near 4 KB). Must sit *outside* the timeout rule so it runs while
     * the abandoned test thread is still stuck where it was.
     */
    fun threadDumpOnFailure(): TestWatcher = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            Log.e(LOG_TAG, "FAILED ${description.displayName}: $e")
            for ((thread, frames) in Thread.getAllStackTraces()) {
                if (frames.isEmpty()) continue
                val stack = frames.take(MAX_FRAMES).joinToString("\n") { "    at $it" }
                Log.e(LOG_TAG, "thread \"${thread.name}\" ${thread.state}\n$stack")
            }
        }
    }

    private const val MAX_FRAMES = 40

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
     * The documents of [collection] whose [field] equals [value], read around the rules like
     * [documentExists] — for what no client may read back, such as `notification_queue`. Each
     * document is its fields, decoded from the REST form: strings, integers, booleans, doubles,
     * nulls, arrays and maps; a timestamp comes back as its RFC 3339 string.
     */
    fun queryAsAdmin(collection: String, field: String, value: String): List<Map<String, Any?>> {
        val url = URL(
            "http://${requireHost()}:$FIRESTORE_PORT/v1/projects/$PROJECT_ID" +
                "/databases/(default)/documents:runQuery"
        )
        val query = JSONObject().put(
            "structuredQuery",
            JSONObject()
                .put("from", JSONArray().put(JSONObject().put("collectionId", collection)))
                .put(
                    "where",
                    JSONObject().put(
                        "fieldFilter",
                        JSONObject()
                            .put("field", JSONObject().put("fieldPath", field))
                            .put("op", "EQUAL")
                            .put("value", JSONObject().put("stringValue", value))
                    )
                )
        )
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = CONNECT_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(query.toString().toByteArray()) }
            check(connection.responseCode == HTTP_OK) {
                "Firestore emulator answered ${connection.responseCode} for a query on $collection"
            }
            val rows = JSONArray(connection.inputStream.bufferedReader().readText())
            (0 until rows.length())
                .mapNotNull { rows.getJSONObject(it).optJSONObject("document")?.optJSONObject("fields") }
                .map(::decodeFields)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Waits for a push of [type] addressed to [targetUid] to appear in `notification_queue` —
     * queued by a phone's `FcmService` or by a Cloud Function — and returns its `data` payload.
     * Delivery is the one thing FCM has no emulator for; what *was* sent, to whom and with which
     * family, is this document, and `sendNotification` reads nothing else.
     */
    suspend fun awaitQueuedPush(targetUid: String, type: String, timeoutMs: Long = PUSH_WAIT_MS): Map<*, *> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val match = queryAsAdmin("notification_queue", "targetUserId", targetUid)
                .mapNotNull { it["data"] as? Map<*, *> }
                .firstOrNull { it["type"] == type }
            if (match != null) return match
            check(System.currentTimeMillis() < deadline) { "No \"$type\" push was queued for $targetUid" }
            delay(PUSH_POLL_MS)
        }
    }

    private const val PUSH_WAIT_MS = 30_000L
    private const val PUSH_POLL_MS = 500L

    private fun decodeFields(fields: JSONObject): Map<String, Any?> =
        fields.keys().asSequence().associateWith { decodeValue(fields.getJSONObject(it)) }

    private fun decodeValue(value: JSONObject): Any? = when {
        value.has("stringValue") -> value.getString("stringValue")
        value.has("integerValue") -> value.getString("integerValue").toLong()
        value.has("doubleValue") -> value.getDouble("doubleValue")
        value.has("booleanValue") -> value.getBoolean("booleanValue")
        value.has("timestampValue") -> value.getString("timestampValue")
        value.has("mapValue") -> decodeFields(value.getJSONObject("mapValue").optJSONObject("fields") ?: JSONObject())
        value.has("arrayValue") -> value.getJSONObject("arrayValue").optJSONArray("values")
            ?.let { values -> (0 until values.length()).map { decodeValue(values.getJSONObject(it)) } }
            .orEmpty()
        else -> null
    }

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
