@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package spike.firebase

import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.StorageReference
import dev.gitlive.firebase.storage.ios
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runMode
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

/** The simulator shares the host's network. */
actual val emulatorHost: String = "localhost"

actual val platformContext: Any? = null

actual val appIdPlatform: String = "ios"

actual fun runSpike(timeout: Duration, body: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        // The SDK answers on the main queue, and this thread is the main thread: the body runs
        // on the main dispatcher while this loop keeps the main run loop turning.
        val run = MainScope().async { withTimeout(timeout) { body() } }
        while (run.isActive) {
            NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.05))
            yield()
        }
        run.await()
    }
}

actual fun tempFilePath(name: String): String = NSTemporaryDirectory() + name

actual fun writeTempFile(name: String, bytes: ByteArray): File {
    val path = tempFilePath(name)
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    check(data.writeToFile(path, atomically = true)) { "could not write $path" }
    return File(NSURL.fileURLWithPath(path))
}

actual fun readFile(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path) ?: error("no file at $path")
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) {
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    }
    return bytes
}

actual suspend fun downloadToFile(ref: StorageReference, path: String) {
    suspendCancellableCoroutine { continuation ->
        ref.ios.writeToFile(NSURL.fileURLWithPath(path)) { _, error ->
            if (error == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            }
        }
    }
}
