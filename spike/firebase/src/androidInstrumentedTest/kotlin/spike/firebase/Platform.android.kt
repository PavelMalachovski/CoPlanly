package spike.firebase

import android.content.Context
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.StorageReference
import dev.gitlive.firebase.storage.android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/** The Android emulator's alias for the host's loopback. */
actual val emulatorHost: String = "10.0.2.2"

private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

actual val platformContext: Any? get() = context

actual val appIdPlatform: String = "android"

actual fun runSpike(timeout: Duration, body: suspend CoroutineScope.() -> Unit) {
    runBlocking { withTimeout(timeout) { body() } }
}

actual fun tempFilePath(name: String): String = java.io.File(context.cacheDir, name).absolutePath

actual fun writeTempFile(name: String, bytes: ByteArray): File {
    val file = java.io.File(tempFilePath(name))
    file.writeBytes(bytes)
    return File(Uri.fromFile(file))
}

actual fun readFile(path: String): ByteArray = java.io.File(path).readBytes()

actual suspend fun downloadToFile(ref: StorageReference, path: String) {
    ref.android.getFile(java.io.File(path)).await()
}
