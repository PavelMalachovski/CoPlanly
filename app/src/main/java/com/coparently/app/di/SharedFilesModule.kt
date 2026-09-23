package com.coparently.app.di

import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.documents.FamilyDocumentRepositoryImpl
import com.coparently.app.domain.chat.AttachmentUploadGate
import com.coparently.app.domain.repository.FamilyDocumentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bindings for files the two parents share (MON-23): the document vault and chat attachments.
 *
 * A module of its own rather than more lines in `RepositoryModule`, which is close to detekt's
 * function limit and has nothing else in common with these two.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SharedFilesModule {

    /** The vault, observed live from Firestore with its files in Storage. */
    @Binds
    @Singleton
    abstract fun bindFamilyDocumentRepository(
        impl: FamilyDocumentRepositoryImpl
    ): FamilyDocumentRepository

    /** The gate `MessageRepositoryImpl` passes every message through before writing it. */
    @Binds
    @Singleton
    abstract fun bindAttachmentUploadGate(
        outbox: ChatAttachmentOutbox
    ): AttachmentUploadGate
}
