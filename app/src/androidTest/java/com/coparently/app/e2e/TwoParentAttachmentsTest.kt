package com.coparently.app.e2e

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.chat.ConversationKey
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Files between two phones (MON-23): a chat attachment and a vault document, through the
 * production outbox, Storage client, verified-download cache and vault repository, against the
 * real `storage.rules` and `firestore.rules` on the emulators.
 *
 * What the rules suite proves offline (`firestore-tests/rules/storage-shared-files.test.js`) is
 * each rule alone. What only this can prove is the client and the rules agreeing: that what the
 * outbox stamps on an upload is what the rule requires, that the message is written only after its
 * file is stored, that the co-parent's download is the co-parent's own (each phone has its own
 * cache directory, so nothing is "opened" from the sender's leftover copy) and matches its digest.
 *
 * What it cannot do: open a file in a viewer app, or draw the bubble. Those stay on
 * `docs/DEVICE-CHECKLIST.md` §5.5.
 */
@RunWith(AndroidJUnit4::class)
class TwoParentAttachmentsTest : TwoParentTest() {

    @Test
    fun anAttachmentReachesBobOnlyAfterItsFileIsStoredAndOpensWithTheSameBytes() = runBlocking<Unit> {
        val conversationId = ConversationKey.of(alice.uid, bob.uid)
        val messageId = UUID.randomUUID().toString()
        val bytes = pdfBytes("Vaccination record")
        val attachment = alice.attachmentOutbox.stage(conversationId, messageId, uriOf("record.pdf", bytes))
        val message = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = alice.uid,
            senderName = alice.name,
            content = "The vaccination record",
            attachments = listOf(ChatAttachmentCodec.encode(attachment))
        )

        // Offline, as far as the upload can tell: the message must stay on Alice's phone.
        alice.uploads.failing = true
        runCatching { alice.messageRepository.sendMessage(message) }
        assertFalse(
            "A message whose file is not stored reached the server",
            EmulatorEnvironment.documentExists("messages/$messageId")
        )
        assertEquals(MessageSendStatus.ERROR, statusOf(alice, message))
        assertTrue(
            "The staged file left the outbox before its upload landed",
            alice.attachmentOutbox.stagedFile(messageId, attachment) != null
        )

        // The network is back: the outbox's retry uploads the file, then writes the message.
        alice.uploads.failing = false
        alice.messageRepository.flushOutbox()
        assertTrue(EmulatorEnvironment.documentExists("messages/$messageId"))
        assertEquals(MessageSendStatus.SENT, statusOf(alice, message))

        val received = withTimeout(EmulatorParent.WAIT_MS) {
            bob.messageRepository.observeMessages(conversationId).first { list -> list.any { it.id == messageId } }
        }.single { it.id == messageId }
        val reference = ChatAttachmentCodec.attachmentsOf(received.attachments).single()
        assertEquals(attachment, reference)
        assertTrue(ChatAttachmentCodec.belongsTo(reference, conversationId, messageId))
        assertEquals(null, bob.sharedFileCache.cached(reference.fileName, reference.sha256))

        val opened = bob.sharedFileCache.localCopy(reference.storagePath, reference.fileName, reference.sha256)
        assertArrayEquals(bytes, opened.readBytes())
    }

    @Test
    fun aStrangerCannotFetchTheFamilysChatFile() = runBlocking<Unit> {
        val conversationId = ConversationKey.of(alice.uid, bob.uid)
        val messageId = UUID.randomUUID().toString()
        val attachment = alice.attachmentOutbox.stage(conversationId, messageId, uriOf("note.pdf", pdfBytes("Note")))
        alice.messageRepository.sendMessage(
            Message(
                id = messageId,
                conversationId = conversationId,
                senderId = alice.uid,
                senderName = alice.name,
                content = "A note",
                attachments = listOf(ChatAttachmentCodec.encode(attachment))
            )
        )

        val carol = newParent("Carol")
        val target = File.createTempFile("stranger", ".pdf")
        assertRefused { carol.sharedFileStorage.download(attachment.storagePath, target) }
    }

    @Test
    fun aVaultDocumentOpensForBobWhoCannotDeleteItAndAliceDeletesItAsATombstone() = runBlocking<Unit> {
        val familyId = FamilyKey.of(alice.uid, bob.uid)
        val bytes = pdfBytes("Court order")
        val filed = alice.documentRepository
            .add(familyId, "Court order", DocumentCategory.COURT_ORDER, uriOf("order.pdf", bytes))
            .getOrThrow()

        val seen = awaitListing(bob, familyId) { docs -> docs.any { it.id == filed.id } }
            .single { it.id == filed.id }
        assertEquals(filed.sha256, seen.sha256)
        assertEquals(alice.uid, seen.createdByFirebaseUid)
        assertArrayEquals(bytes, bob.documentRepository.localCopy(seen).getOrThrow().readBytes())

        assertTrue("Bob deleted a document Alice filed", bob.documentRepository.delete(seen).isFailure)
        assertTrue(EmulatorEnvironment.documentExists("family_documents/${filed.id}"))

        alice.documentRepository.delete(filed).getOrThrow()
        awaitListing(bob, familyId) { docs -> docs.none { it.id == filed.id } }
        // A tombstone, never a removal (item 14): the document is still there to carry the deletion.
        assertTrue(EmulatorEnvironment.documentExists("family_documents/${filed.id}"))
    }

    /** The status [parent]'s own thread holds for [message]. */
    private suspend fun statusOf(parent: EmulatorParent, message: Message): MessageSendStatus =
        parent.messageRepository.observeMessages(message.conversationId).first()
            .single { it.id == message.id }.status

    /** Waits until [parent]'s vault listing for [familyId], from the server, satisfies [condition]. */
    private suspend fun awaitListing(
        parent: EmulatorParent,
        familyId: String,
        condition: (List<FamilyDocument>) -> Boolean
    ): List<FamilyDocument> = withTimeout(EmulatorParent.WAIT_MS) {
        parent.documentRepository.observe(familyId)
            .first { it != null && !it.possiblyOutdated && condition(it.documents) }!!
            .documents
    }

    /** A file the stager can read, as a picker would hand it over: a URI with a display name. */
    private fun uriOf(fileName: String, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "e2e-picked/${UUID.randomUUID()}").apply { mkdirs() }
        return Uri.fromFile(File(dir, fileName).apply { writeBytes(bytes) }).toString()
    }

    /** A small, well-formed PDF whose text makes each file's bytes distinct. */
    private fun pdfBytes(text: String): ByteArray =
        buildString {
            append("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n")
            append("2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\n% $text ${UUID.randomUUID()}\n")
            append("trailer<</Root 1 0 R>>\n%%EOF\n")
        }.toByteArray()

    /** Fails unless [block] is refused by the Storage rules. */
    private suspend fun assertRefused(block: suspend () -> Unit) {
        try {
            block()
            fail("A stranger's request was allowed")
        } catch (e: StorageException) {
            assertEquals(StorageException.ERROR_NOT_AUTHORIZED, e.errorCode)
        }
    }
}
