package com.coparently.app.di.koin

import org.koin.core.module.Module

/**
 * Every module of the app's Koin graph, which `CoPlanlyApplication` starts and `KoinGraphTest`
 * verifies. Instrumented tests start the same list with their overrides after it.
 *
 * One module per former Hilt module, under the same name, holds what that module bound or
 * provided; [dataModule], [domainModule], [presentationModule], [viewModelModule] and
 * [workerModule] hold what Hilt built from `@Inject` constructors.
 */
val appModules: List<Module> = listOf(
    aiModule,
    databaseModule,
    dictationModule,
    firebaseModule,
    firebaseRepositoryModule,
    googleModule,
    installReferrerModule,
    journalDaoModule,
    journalModule,
    knownFamilyModule,
    notificationModule,
    receiptModule,
    repositoryModule,
    requestClockModule,
    schoolModule,
    securityModule,
    serializationModule,
    sharedFilesModule,
    familyDocumentCacheDaoModule,
    supportModule,
    syncModule,
    useCaseModule,
    dataModule,
    domainModule,
    presentationModule,
    viewModelModule,
    workerModule
)
