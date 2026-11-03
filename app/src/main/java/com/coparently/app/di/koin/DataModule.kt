package com.coparently.app.di.koin

import android.content.Context
import com.coparently.app.data.ai.AiConsentManager
import com.coparently.app.data.ai.FunctionsAiAssistRepository
import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.chat.ChatAttachmentOutbox
import com.coparently.app.data.consent.HealthConsentManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.dictation.OnDeviceSpeechDictation
import com.coparently.app.data.documents.FamilyDocumentRepositoryImpl
import com.coparently.app.data.export.CommunicationRecordSource
import com.coparently.app.data.export.ExportFileWriter
import com.coparently.app.data.export.ExportReceipts
import com.coparently.app.data.export.OptionalRecordSections
import com.coparently.app.data.export.ParentingPlanRecordSource
import com.coparently.app.data.family.OtherFamiliesSignals
import com.coparently.app.data.family.UnassignedRecords
import com.coparently.app.data.files.RecordPhotoImageLoader
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStager
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.files.StoredRecordFamilies
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.security.DatabaseKey
import com.coparently.app.data.local.security.EncryptedDatabase
import com.coparently.app.data.mlkit.MlKitReceiptTextRecognizer
import com.coparently.app.data.notification.EventReminderScheduler
import com.coparently.app.data.notification.NotificationManager
import com.coparently.app.data.notification.WorkRequestClockScheduler
import com.coparently.app.data.pairing.PlayInstallReferrerSource
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.FirebaseImageStorage
import com.coparently.app.data.remote.firebase.PushDevice
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.google.CredentialAuthenticator
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.remote.google.CredentialProviderImpl
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.data.repository.AndroidPairingWording
import com.coparently.app.data.repository.PairingWording
import com.coparently.app.data.requests.RequestClockActions
import com.coparently.app.data.requests.RequestClockKeeper
import com.coparently.app.data.requests.RequestClockSteps
import com.coparently.app.data.requests.RequestClockSweep
import com.coparently.app.data.requests.RequestClocks
import com.coparently.app.data.requests.RequestNotices
import com.coparently.app.data.requests.RequestRecords
import com.coparently.app.data.requests.RequestTime
import com.coparently.app.data.school.SchoolAccounts
import com.coparently.app.data.school.SchoolConnectionStore
import com.coparently.app.data.school.SchoolEventWriter
import com.coparently.app.data.school.SchoolImportWordingSource
import com.coparently.app.data.school.SchoolImporter
import com.coparently.app.data.school.WorkManagerSchoolImportScheduler
import com.coparently.app.data.school.bakalari.BakalariAuthorizer
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariSchoolDirectory
import com.coparently.app.data.security.EncryptionManager
import com.coparently.app.data.session.AccountConsistency
import com.coparently.app.data.session.AccountDeletionService
import com.coparently.app.data.session.AccountSwitchGuard
import com.coparently.app.data.session.DeviceTraces
import com.coparently.app.data.session.SessionProfileSynchronizer
import com.coparently.app.data.session.SignedInAccountSource
import com.coparently.app.data.support.WorkManagerSupportReminderScheduler
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.GoogleCalendarAutoImport
import com.coparently.app.data.sync.WorkManagerSyncRequester
import com.coparently.app.data.telemetry.TelemetryConsentApplier
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.repository.RequestDeadlineRepository
import com.coparently.app.domain.repository.UserRepository
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Every class of the data layer the graph builds from its constructor: what Hilt found through
 * `@Inject` constructors before MON-24 phase 2. A class that was a Hilt `@Singleton` is a
 * `single`, an unscoped one a `factory`. The interfaces they implement are bound in the module
 * named after the Hilt module that bound them (`repositoryModule`, `syncModule`, ...).
 *
 * A class with a secondary constructor for the graph, a default argument, a qualified or a lazy
 * dependency is built in a lambda that names its arguments, because a constructor reference would
 * be ambiguous there or would skip the default the way Hilt never did.
 */
val dataModule = module {
    singleOf(::AiConsentManager)
    singleOf(::FunctionsAiAssistRepository)
    singleOf(::AnalyticsManager)
    singleOf(::ChatAttachmentOutbox)
    singleOf(::HealthConsentManager)
    singleOf(::CrashlyticsManager)
    singleOf(::OnDeviceSpeechDictation)
    singleOf(::FamilyDocumentRepositoryImpl)
    singleOf(::CommunicationRecordSource)
    singleOf(::ExportFileWriter)
    singleOf(::ExportReceipts)
    singleOf(::OptionalRecordSections)
    singleOf(::ParentingPlanRecordSource)
    singleOf(::OtherFamiliesSignals)
    singleOf(::UnassignedRecords)
    singleOf(::RecordPhotoImageLoader)
    singleOf(::SharedFileCache)
    singleOf(::SharedFileStager)
    singleOf(::SharedFileStorage)
    factoryOf(::StoredRecordFamilies)
    single { EncryptedPreferences(get<Context>(), get<EncryptionManager>()) }
    singleOf(::DatabaseKey)
    singleOf(::EncryptedDatabase)
    singleOf(::MlKitReceiptTextRecognizer)
    singleOf(::EventReminderScheduler)
    singleOf(::NotificationManager)
    singleOf(::WorkRequestClockScheduler)
    singleOf(::PlayInstallReferrerSource)
    singleOf(::FcmService)
    // Where the repositories in `:shared-data` queue a push.
    single<PushQueue> { get<FcmService>() }
    // This device's push token, which the sync in `:shared-data` keeps on the profile.
    single<PushDevice> { get<FcmService>() }
    singleOf(::FirebaseAuthService)
    // The one question the repositories in `:shared-data` ask of the signed-in account.
    single<SignedInUser> { get<FirebaseAuthService>() }
    singleOf(::FirebaseImageStorage)
    singleOf(::CredentialAuthenticator)
    singleOf(::CredentialManagerService)
    singleOf(::CredentialProviderImpl)
    single { GoogleCalendarApi(get<HttpClient>(GOOGLE_CALENDAR_HTTP_CLIENT)) }
    // The thread title PairingRepositoryImpl (in `:shared-data`) falls back to, from resources.
    single<PairingWording> { AndroidPairingWording(get()) }
    singleOf(::RequestClockActions)
    single {
        RequestClockKeeper(
            get<RequestDeadlineRepository>(),
            get<RequestClockSteps>(),
            get<RequestClockScheduler>(),
            get<RequestTime>()
        )
    }
    // The clocks' pass every full sync runs (the sync is in `:shared-data`).
    single<RequestClockSweep> { get<RequestClockKeeper>() }
    singleOf(::RequestClockSteps)
    singleOf(::RequestClocks)
    singleOf(::RequestNotices)
    singleOf(::RequestRecords)
    single { RequestTime() }
    singleOf(::SchoolAccounts)
    singleOf(::SchoolConnectionStore)
    singleOf(::SchoolEventWriter)
    singleOf(::SchoolImportWordingSource)
    single {
        SchoolImporter(
            get<BakalariAuthorizer>(),
            get<BakalariClient>(),
            get<SchoolConnectionStore>(),
            get<UserRepository>(),
            get<SchoolImporter.Collaborators>()
        )
    }
    factory { SchoolImporter.Collaborators(get(), get(), get(), get()) }
    singleOf(::WorkManagerSchoolImportScheduler)
    single { BakalariAuthorizer(get<BakalariClient>(), get<SchoolConnectionStore>()) }
    single { BakalariClient(get<HttpClient>(SCHOOL_HTTP_CLIENT)) }
    single { BakalariSchoolDirectory(get<HttpClient>(SCHOOL_HTTP_CLIENT)) }
    singleOf(::AccountDeletionService)
    singleOf(::AccountSwitchGuard)
    // The check the sync in `:shared-data` runs before its first upload.
    single<AccountConsistency> { get<AccountSwitchGuard>() }
    singleOf(::DeviceTraces)
    singleOf(::SessionProfileSynchronizer)
    singleOf(::SignedInAccountSource)
    singleOf(::WorkManagerSupportReminderScheduler)
    singleOf(::CalendarSyncRepository)
    singleOf(::GoogleCalendarAutoImport)
    singleOf(::WorkManagerSyncRequester)
    singleOf(::TelemetryConsentApplier)
}
