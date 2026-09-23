package com.coparently.app.data.documents

import android.content.Context
import android.util.Log
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStager
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.documents.FamilyDocumentPaths
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.repository.FamilyDocumentRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore- and Storage-backed [FamilyDocumentRepository] (MON-23).
 *
 * Observed live and never cached in Room, like the calendar-friend list: the vault is read
 * rarely, and a vault table would be a schema version (ROADMAP MON-23 records it as a follow-up).
 * The list query filters on `sharedWith` *and* `familyId`, the two fields the read rule and the
 * create rule key on (CLAUDE.md item 12), so a parent with two families sees the selected one's
 * documents only.
 */
@Singleton
class FamilyDocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val authService: FirebaseAuthService,
    private val stager: SharedFileStager,
    private val storage: SharedFileStorage,
    private val cache: SharedFileCache
) : FamilyDocumentRepository {

    override fun observe(familyId: String): Flow<List<FamilyDocument>?> {
        val uid = authService.getCurrentUser()?.uid ?: return flowOf(null)
        if (FamilyKey.membersOf(familyId) == null) return flowOf(emptyList())
        return callbackFlow {
            val registration = firestore.collection(COLLECTION)
                .whereArrayContains("sharedWith", uid)
                .whereEqualTo("familyId", familyId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Not closed: a refused or dropped listener must not end the screen's flow.
                        // Null is "unavailable", which the screen says in words — never an empty
                        // vault that would read as "nothing was ever filed".
                        Log.w(TAG, "Vault listener failed", error)
                        trySend(null)
                        return@addSnapshotListener
                    }
                    val documents = snapshot?.documents.orEmpty()
                        .mapNotNull { FamilyDocumentMapper.fromFirestore(it.id, it.data) }
                        .sortedWith(FamilyDocumentMapper.listOrder)
                    trySend(documents)
                }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun add(
        familyId: String,
        title: String,
        category: DocumentCategory,
        contentUri: String
    ): Result<FamilyDocument> = guarded("add") {
        val uid = authService.getCurrentUser()?.uid ?: throw IOException("Not signed in")
        val docId = UUID.randomUUID().toString()
        val staging = File(context.cacheDir, "$UPLOAD_DIRECTORY/$docId")
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
                createdAtMillis = System.currentTimeMillis()
            )
            storage.upload(document.storagePath, staged.file, staged.contentType, staged.sha256, uid)
            writeIndexOrRemoveFile(document)
            cache.adopt(staged.file, staged.fileName, staged.sha256)
            document
        } finally {
            staging.deleteRecursively()
        }
    }

    override suspend fun delete(document: FamilyDocument): Result<Unit> = guarded("delete") {
        val uid = authService.getCurrentUser()?.uid ?: throw IOException("Not signed in")
        // `update()`, never `set()` (CLAUDE.md item 14): the tombstone keeps the audience the read
        // rule is keyed on, so the co-parent's listener sees the document leave rather than lose it.
        firestore.collection(COLLECTION).document(document.id)
            .update(FamilyDocumentMapper.tombstone(uid, System.currentTimeMillis()))
            .await()
        Unit
    }

    override suspend fun localCopy(document: FamilyDocument): Result<File> = guarded("open") {
        cache.localCopy(document.storagePath, document.fileName, document.sha256)
    }

    /**
     * Writes the vault document for a file that is already stored, and takes the file back out
     * when the write is refused — the uploader may delete their own file, and an orphan nobody
     * can list is a file nobody can ever remove except by account deletion.
     */
    private suspend fun writeIndexOrRemoveFile(document: FamilyDocument) {
        try {
            firestore.collection(COLLECTION).document(document.id)
                .set(FamilyDocumentMapper.toFirestoreMap(document))
                .await()
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
        Log.w(TAG, "Vault $operation failed", e)
        Result.failure(e)
    }

    private companion object {
        const val TAG = "FamilyDocuments"
        const val COLLECTION = "family_documents"

        /** Under `cacheDir`: a copy only lives while its upload runs. */
        const val UPLOAD_DIRECTORY = "shared_files/upload"
    }
}
