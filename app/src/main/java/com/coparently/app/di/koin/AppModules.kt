package com.coparently.app.di.koin

import org.koin.core.module.Module

/**
 * Every module of the app's Koin graph, which `CoPlanlyApplication` starts and `KoinGraphTest`
 * verifies. Instrumented tests start the same list with their overrides after it.
 *
 * One module per former Hilt module, under the same name, holds what that module bound or
 * provided; [dataModule], [domainModule], [presentationModule], [viewModelModule] and
 * [workerModule] hold what Hilt built from `@Inject` constructors. The former `serializationModule`
 * provided the one injectable Gson; it went with Gson (MON-24), since `StoredJson` is an object.
 * `:shared-data`'s [dataModules] and its Android [platformDataModule] follow (MON-24 phase 2): the
 * data layer both apps run, which binds its Firebase seams over this graph's SDK instances.
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
    platformModule,
    receiptModule,
    repositoryModule,
    requestClockModule,
    schoolModule,
    securityModule,
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
) + dataModules + platformDataModule
