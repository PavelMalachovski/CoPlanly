package com.coparently.app.data.documents

import com.coparently.app.data.files.LocalFiles
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStager
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.FamilyDocumentPaths
import com.coparently.app.domain.documents.VaultListing
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.repository.FamilyDocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Firestore- and Storage-backed [FamilyDocumentRepository] (MON-23).
 *
 * The index lives in Firestore ([FamilyDocumentIndex]), observed live; Room keeps a read-through
 * cache of the last server answer (schema 43, [FamilyDocumentIndexCache]) that the screen shows,
 * labelled, while the server cannot be reached. Every write goes to Firestore — the cache is never
 * written from here and never uploads. The bytes live in Storage and are never reached by a
 * download URL (CLAUDE.md item 31).
 *
 * In `:shared-data` since MON-24 phase 2 (Task 8j), on kotlinx-io paths, asking [SignedInUser] for
 * the account (the app's `FirebaseAuthService` before; the parameter kept its name). A document's
 * id is a random UUID in the text `java.util.UUID` wrote (`kotlin.uuid.Uuid`), its time the epoch
 * millis `System.currentTimeMillis()` read.
 */
class FamilyDocumentRepositoryImpl(
    private val directories: AppDirectories,
    private val index: FamilyDocumentIndex,
    private val authService: SignedInUser,
    private val stager: SharedFileStager,
    private val storage: SharedFileStorage,
    private val cache: SharedFileCache
) : FamilyDocumentRepository {

    override fun observe(familyId: String): Flow<VaultListing?> {
        val uid = authService.getCurrentUid() ?: return flowOf(null)
        val members = FamilyKey.membersOf(familyId) ?: return flowOf(VaultListing(emptyList()))
        // A family this account is not in has nothing to show — not even a cached copy a previous
        // account left behind.
        val mine = uid == members.first || uid == members.second
        return if (mine) index.observe(uid, familyId) else flowOf(null)
    }

    override suspend fun add(
        familyId: String,
        title: String,
        category: DocumentCategory,
        contentUri: String
    ): Result<FamilyDocument> = guarded("add") {
        val uid = authService.getCurrentUid() ?: throw IOException("Not signed in")
        val docId = newDocumentId()
        val staging = Path(directories.cache, UPLOAD_DIRECTORY, UPLOAD_SUBDIRECTORY, docId)
        try {
            val staged = stager.stage(contentUri, staging)
            val document = FamilyDocument(
                id = docId,
                familyId = familyId,
                createdByFirebaseUid = uid,
                title = title.trim().ifBlank { staged.fileName },
                category = category,
                fileName = staged.fileName,
                storagePath = FamilyDocumentPaths.storagePath(familyId, docId, staged.fileName),
                contentType = staged.contentType,
                sizeBytes = staged.sizeBytes,
                sha256 = staged.sha256,
                createdAtMillis = nowMillis()
            )
            storage.upload(document.storagePath, staged.file, staged.contentType, staged.sha256, uid)
            writeIndexOrRemoveFile(document)
            cache.adopt(staged.file, staged.fileName, staged.sha256)
            document
        } finally {
            LocalFiles.deleteRecursively(staging)
        }
    }

    override suspend fun delete(document: FamilyDocument): Result<Unit> = guarded("delete") {
        val uid = authService.getCurrentUid() ?: throw IOException("Not signed in")
        index.tombstone(document.id, uid, nowMillis())
    }

    override suspend fun localCopy(document: FamilyDocument): Result<Path> = guarded("open") {
        cache.localCopy(document.storagePath, document.fileName, document.sha256)
    }

    /**
     * Writes the vault document for a file that is already stored, and takes the file back out
     * when the write is refused — the uploader may delete their own file, and an orphan nobody
     * can list is a file nobody can ever remove except by account deletion.
     */
    private suspend fun writeIndexOrRemoveFile(document: FamilyDocument) {
        try {
            index.write(document)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            runCatching { storage.delete(document.storagePath) }
            throw e
        }
    }

    /** Runs [block] as a [Result], logging a failure and never swallowing cancellation. */
    private suspend fun <T> guarded(operation: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        logger.w(TAG, "Vault $operation failed", e)
        Result.failure(e)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newDocumentId(): String = Uuid.random().toString()

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val TAG = "FamilyDocuments"

        /** Under `cacheDir`, `shared_files/upload`: a copy only lives while its upload runs. */
        const val UPLOAD_DIRECTORY = "shared_files"
        const val UPLOAD_SUBDIRECTORY = "upload"
    }
}
