package com.coparently.app.di.koin

import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.documents.FamilyDocumentRepositoryImpl
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import com.coparently.app.domain.chat.AttachmentUploadGate
import com.coparently.app.domain.repository.FamilyDocumentRepository
import org.koin.dsl.module

/** Bindings for files the two parents share (MON-23): the document vault and chat attachments. */
val sharedFilesModule = module {
    // The vault, observed live from Firestore with its files in Storage.
    single<FamilyDocumentRepository> { get<FamilyDocumentRepositoryImpl>() }
    // The gate `MessageRepositoryImpl` passes every message through before writing it.
    single<AttachmentUploadGate> { get<ChatAttachmentOutbox>() }
}

/** The vault index cache's DAO (MON-23, schema 43). */
val familyDocumentCacheDaoModule = module {
    factory<FamilyDocumentCacheDao> { get<CoPlanlyDatabase>().familyDocumentCacheDao() }
}
