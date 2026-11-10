package com.coparently.app.data.files

import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.remote.firebase.SignedInAccount
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.domain.files.toHex
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.platform.Sha256
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `data/files/` on kotlinx-io (MON-24 phase 2, Task 8j), over a real directory and an in-memory
 * bucket: the copy is hashed as it is read and abandoned past the cap, an upload carries its
 * uploader and digest and is skipped when the object is already there, a download is kept only
 * when its digest matches (CLAUDE.md item 31), and the chat outbox uploads before it lets go of
 * its staged file.
 */
class SharedFilesTest {

    private val root = Path(SystemTemporaryDirectory, "shared-files-test-${Random.nextLong().toULong()}")
    private val directories = object : AppDirectories {
        override val files = Path(root, "files")
        override val cache = Path(root, "cache")
        override val noBackup = Path(root, "no_backup")
    }
    private val bucket = MemoryBucket()
    private val storage = SharedFileStorage(bucket)
    private val cache = SharedFileCache(directories, storage)

    @AfterTest
    fun cleanUp() {
        LocalFiles.deleteRecursively(root)
    }

    @Test
    fun aPickedFileIsCopiedMeasuredAndHashed() = runTest {
        val bytes = Random(1).nextBytes(150_000)
        val stager = SharedFileStager(Picked(mapOf(URI to bytes), type = "application/pdf", name = "Order.pdf"))

        val staged = stager.stage(URI, Path(root, "stage"))

        assertEquals("Order.pdf", staged.fileName)
        assertEquals("application/pdf", staged.contentType)
        assertEquals(150_000L, staged.sizeBytes)
        assertEquals(Sha256.digest(bytes).toHex(), staged.sha256)
        assertContentEquals(bytes, read(staged.file))
    }

    @Test
    fun aTypeTheRulesRefuseIsNotCopied() = runTest {
        val stager = SharedFileStager(Picked(mapOf(URI to ByteArray(10)), type = "text/plain", name = "notes.txt"))

        val refused = assertFailsWith<SharedFileRejectedException> { stager.stage(URI, Path(root, "stage")) }

        assertEquals(SharedFilePolicy.Rejection.TYPE, refused.rejection)
        assertFalse(LocalFiles.exists(Path(root, "stage", "notes.txt")))
    }

    @Test
    fun aFileAtTheCapIsAbandonedAndItsCopyDeleted() = runTest {
        val stager = SharedFileStager(Picked(emptyMap(), type = "image/png", name = "big.png", endless = true))

        val refused = assertFailsWith<SharedFileRejectedException> { stager.stage(URI, Path(root, "stage")) }

        assertEquals(SharedFilePolicy.Rejection.SIZE, refused.rejection)
        assertFalse(LocalFiles.exists(Path(root, "stage", "big.png")))
    }

    @Test
    fun anUploadCarriesItsUploaderAndDigestAndIsNotRepeated() = runTest {
        storage.uploadBytes("receipts/f/r/a.jpg", byteArrayOf(1, 2), "image/jpeg", "abc", "uidA")
        storage.uploadBytes("receipts/f/r/a.jpg", byteArrayOf(1, 2), "image/jpeg", "abc", "uidA")

        assertEquals(1, bucket.puts)
        assertEquals(listOf("uploader" to "uidA", "sha256" to "abc"), bucket.metadata.getValue("receipts/f/r/a.jpg"))
        assertTrue(storage.isStored("receipts/f/r/a.jpg", "abc"))
        assertFalse(storage.isStored("receipts/f/r/a.jpg", "other"))
    }

    @Test
    fun aDownloadIsKeptOnlyWhenItsDigestMatches() = runTest {
        val bytes = Random(2).nextBytes(70_000)
        bucket.objects["vault/doc.pdf"] = bytes
        val digest = Sha256.digest(bytes).toHex()

        val copy = cache.localCopy("vault/doc.pdf", "doc.pdf", digest)

        assertContentEquals(bytes, read(copy))
        assertEquals(copy, cache.cached("doc.pdf", digest))
        assertEquals(Path(directories.cache, "shared_files", "view", digest, "doc.pdf"), copy)
    }

    @Test
    fun aDownloadThatDoesNotMatchLeavesNothingBehind() = runTest {
        bucket.objects["vault/doc.pdf"] = byteArrayOf(9, 9, 9)
        val recorded = Sha256.digest(byteArrayOf(1)).toHex()

        assertFailsWith<SharedFileIntegrityException> { cache.localCopy("vault/doc.pdf", "doc.pdf", recorded) }

        assertNull(cache.cached("doc.pdf", recorded))
        assertFalse(LocalFiles.exists(Path(directories.cache, "shared_files", "view", recorded, "doc.pdf.part")))
    }

    @Test
    fun keptBytesAreTheUploadersOwnCopy() = runTest {
        cache.keep(byteArrayOf(4, 5, 6), "photo.jpg", "d1")

        assertContentEquals(byteArrayOf(4, 5, 6), read(checkNotNull(cache.cached("photo.jpg", "d1"))))
    }

    @Test
    fun theOutboxUploadsItsFileThenMovesItIntoTheCache() = runTest {
        val bytes = Random(3).nextBytes(5_000)
        val outbox = ChatAttachmentOutbox(
            directories,
            SharedFileStager(Picked(mapOf(URI to bytes), type = "image/jpeg", name = "a.jpg")),
            storage,
            cache,
            SignedInAs("uidA")
        )
        val attachment = outbox.stage("conv", "msg-1", URI)
        val message = Message(
            id = "msg-1",
            conversationId = "conv",
            senderId = "uidA",
            senderName = "Alice",
            content = attachment.fileName,
            attachments = listOf(ChatAttachmentCodec.encode(attachment))
        )
        assertTrue(outbox.stagedFile("msg-1", attachment) != null)

        outbox.ensureUploaded(message)

        assertContentEquals(bytes, bucket.objects.getValue(attachment.storagePath))
        assertEquals("uidA", bucket.metadata.getValue(attachment.storagePath).toMap()["uploader"])
        assertNull(outbox.stagedFile("msg-1", attachment))
        assertFalse(LocalFiles.exists(Path(directories.files, ChatAttachmentOutbox.OUTBOX_DIRECTORY, "msg-1")))
        assertContentEquals(bytes, read(checkNotNull(cache.cached(attachment.fileName, attachment.sha256))))
    }

    private fun read(path: Path): ByteArray = SystemFileSystem.source(path).buffered().use { it.readByteArray() }

    /** The picker's files, by URI; [endless] serves zeros for ever. */
    private class Picked(
        private val files: Map<String, ByteArray>,
        private val type: String?,
        private val name: String?,
        private val endless: Boolean = false
    ) : PickedFiles {
        override fun typeOf(contentUri: String): String? = type
        override fun displayNameOf(contentUri: String): String? = name
        override fun open(contentUri: String): RawSource =
            if (endless) Zeros() else Buffer().apply { write(files.getValue(contentUri)) }
    }

    private class Zeros : RawSource {
        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            val count = minOf(byteCount, CHUNK.size.toLong()).toInt()
            sink.write(CHUNK, 0, count)
            return count.toLong()
        }

        override fun close() = Unit

        private companion object {
            val CHUNK = ByteArray(8192)
        }
    }

    /** A bucket in memory, with what each object was stamped with. */
    private class MemoryBucket : StorageBucket {
        val objects = mutableMapOf<String, ByteArray>()
        val metadata = mutableMapOf<String, List<Pair<String, String>>>()
        var puts = 0

        override suspend fun customMetadata(path: String, key: String): String? =
            if (path in objects) metadata[path].orEmpty().toMap()[key] else null

        override suspend fun putFile(
            path: String,
            file: Path,
            contentType: String,
            customMetadata: Map<String, String>,
            onProgress: (Float) -> Unit
        ) {
            val bytes = SystemFileSystem.source(file).buffered().use { it.readByteArray() }
            putBytes(path, bytes, contentType, customMetadata)
            onProgress(1f)
        }

        override suspend fun putBytes(
            path: String,
            bytes: ByteArray,
            contentType: String,
            customMetadata: Map<String, String>
        ) {
            puts++
            objects[path] = bytes
            metadata[path] = customMetadata.toList()
        }

        override suspend fun getFile(path: String, target: Path) {
            SystemFileSystem.sink(target).buffered().use { it.write(objects.getValue(path)) }
        }

        override suspend fun delete(path: String) {
            objects.remove(path)
            metadata.remove(path)
        }
    }

    private class SignedInAs(private val uid: String) : SignedInUser {
        override fun getCurrentUid(): String = uid
        override fun getCurrentDisplayName(): String? = null
        override fun getCurrentEmail(): String? = null
        override fun getCurrentPhotoUrl(): String? = null
        override fun observeCurrentUid(): Flow<String?> = flowOf(uid)
        override fun getCurrentAccount(): SignedInAccount? = null
    }

    private companion object {
        const val URI = "content://picker/1"
    }
}
