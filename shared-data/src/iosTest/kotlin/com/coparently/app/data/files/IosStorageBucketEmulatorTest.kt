@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.files

import com.coparently.app.data.remote.firebase.AuthEmulator
import com.coparently.app.data.remote.firebase.EmulatorApps
import com.coparently.app.data.remote.firebase.runOnMainRunLoop
import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.platform.Sha256
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [IosStorageBucket] against the Storage emulator and the real `storage.rules` (MON-24 phase 3, part
 * C, Task 10): what `SharedFileStorage` stamps on a create reads back by key, a second put to the
 * same path is refused (the rules' `resource == null`), and a download writes the bytes uploaded.
 *
 * On a named Firebase app of its own (`storage_bucket_test`, never deleted, as the write gate's
 * phones are), whose Auth signs a fresh account up so the path's family id names it. It runs where
 * [AuthEmulator]'s tests run, under the same rule: without an emulator host it skips itself, and
 * fails instead under `COPLANLY_REQUIRE_EMULATORS=1`.
 */
class IosStorageBucketEmulatorTest {

    @Test
    fun `put then read back the uploader and sha256 metadata`() = onEmulator("storage metadata") { bucket, uid ->
        val path = vaultPath(uid)
        val bytes = documentBytes()
        val digest = Sha256.digest(bytes).toHex()
        assertNull(bucket.customMetadata(path, "sha256"), "an object before it was uploaded")
        bucket.putBytes(path, bytes, PDF, linkedMapOf("uploader" to uid, "sha256" to digest))
        assertEquals(uid, bucket.customMetadata(path, "uploader"))
        assertEquals(digest, bucket.customMetadata(path, "sha256"))
        assertNull(bucket.customMetadata(path, "absent"), "a key the object does not carry")
        // No delete of a missing path here: `storage.rules` allows a vault delete only by the
        // object's uploader, so with no object the rule denies before existence is checked — the
        // same refusal Android gets. The not-found mapping is held where it is reachable, above.
    }

    @Test
    fun `a second put to the same path is refused`() = onEmulator("storage overwrite") { bucket, uid ->
        val path = vaultPath(uid)
        val bytes = documentBytes()
        val metadata = linkedMapOf("uploader" to uid, "sha256" to Sha256.digest(bytes).toHex())
        bucket.putBytes(path, bytes, PDF, metadata)
        assertFailsWith<IOException>("an overwrite the rules refuse") {
            bucket.putBytes(path, bytes, PDF, metadata)
        }
        // The idempotent caller sees the first upload and does not try again.
        assertTrue(SharedFileStorage(bucket).isStored(path, metadata.getValue("sha256")))
    }

    @Test
    fun `getFile into a target writes the same bytes`() = onEmulator("storage download") { bucket, uid ->
        val folder = Path(NSTemporaryDirectory(), "coplanly-storage-" + NSUUID().UUIDString)
        SystemFileSystem.createDirectories(folder)
        try {
            val bytes = documentBytes()
            val source = Path(folder, "order.pdf")
            SystemFileSystem.sink(source).buffered().use { it.write(bytes) }
            val path = vaultPath(uid)
            val progress = mutableListOf<Float>()
            val metadata = linkedMapOf("uploader" to uid, "sha256" to Sha256.digest(bytes).toHex())
            bucket.putFile(path, source, PDF, metadata) { progress += it }
            // The SDK may report no progress at all for a body this small, sent in one write; what it
            // reports must be fractions that end at the whole file.
            assertTrue(progress.all { it in 0f..1f }, "progress is a fraction: $progress")
            assertTrue(progress.isEmpty() || progress.last() == 1f, "progress ends at 1: $progress")

            val target = Path(folder, "downloaded.pdf")
            bucket.getFile(path, target)
            val downloaded = SystemFileSystem.source(target).buffered().use { it.readByteArray() }
            assertContentEquals(bytes, downloaded)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(folder.toString(), error = null)
        }
    }

    /** A fresh vault path in a family whose id names [uid], so the rules' `isOneOfPair` admits it. */
    private fun vaultPath(uid: String): String =
        "family_documents/${uid}__other-parent/${NSUUID().UUIDString}/order.pdf"

    /** A small PDF's bytes, different each time. */
    private fun documentBytes(): ByteArray = ("%PDF-1.4\n% " + NSUUID().UUIDString + "\n%%EOF\n").encodeToByteArray()

    /**
     * Runs [test] with the bucket of a freshly signed-up account, or returns at once without an
     * emulator host (failing instead when one is required).
     */
    private fun onEmulator(name: String, test: suspend (IosStorageBucket, String) -> Unit) {
        val host = AuthEmulator.host
        if (host == null) {
            if (AuthEmulator.required) {
                fail("COPLANLY_REQUIRE_EMULATORS=1 but no COPLANLY_EMULATOR_HOST reached the test")
            }
            println("${AuthEmulator.SKIPPED} $name")
            return
        }
        runOnMainRunLoop {
            val (auth, bucket) = emulatorApp(host)
            val email = "storage-${NSUUID().UUIDString.lowercase()}@example.test"
            val uid = checkNotNull(auth.createUserWithEmailAndPassword(email, PASSWORD).user).uid
            try {
                test(bucket, uid)
            } finally {
                auth.signOut()
            }
        }
        println("${AuthEmulator.RAN} $name")
    }

    private companion object {
        const val PDF = "application/pdf"
        const val PASSWORD = "storage-password-1"
        const val AUTH_PORT = 9099

        /** The named app, made once per process: the SDKs refuse `useEmulator` after first use. */
        private var app: Pair<FirebaseAuth, IosStorageBucket>? = null

        fun emulatorApp(host: String): Pair<FirebaseAuth, IosStorageBucket> = app ?: run {
            // The app and its Storage from EmulatorApps, which configures every test app's Storage
            // before any is used: the SDK ends the process on a late `useEmulator`.
            val firebaseApp = EmulatorApps.app(EmulatorApps.STORAGE_BUCKET_TEST, host)
            val auth = Firebase.auth(firebaseApp).also { it.useEmulator(host, AUTH_PORT) }
            val storage = EmulatorApps.storage(EmulatorApps.STORAGE_BUCKET_TEST, host)
            Pair(auth, IosStorageBucket(storage)).also { app = it }
        }
    }
}
