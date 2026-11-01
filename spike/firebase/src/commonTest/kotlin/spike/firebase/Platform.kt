package spike.firebase

import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

/** The address the device reaches the host's Firebase emulators on. */
expect val emulatorHost: String

/** What `Firebase.initialize` takes as its context: an Android `Context`, or nothing on iOS. */
expect val platformContext: Any?

/** The platform segment of a Google App ID ("android" or "ios"). */
expect val appIdPlatform: String

/**
 * Runs a test body with a real-time limit. On iOS the Firebase SDK calls back on the main queue,
 * so the main run loop is kept turning while the body runs.
 */
expect fun runSpike(timeout: Duration, body: suspend CoroutineScope.() -> Unit)

/** Writes [bytes] to a fresh file in the platform's temporary directory. */
expect fun writeTempFile(name: String, bytes: ByteArray): File

/** A path in the platform's temporary directory. */
expect fun tempFilePath(name: String): String

/** Reads a whole file. */
expect fun readFile(path: String): ByteArray

/**
 * Downloads [ref] to a file at [path]. GitLive's common API has no download-to-file, so this is the
 * small platform half beside it: the Android SDK's `getFile` and the iOS SDK's `writeToFile`.
 */
expect suspend fun downloadToFile(ref: StorageReference, path: String)
