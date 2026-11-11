package com.coparently.app.ios

import com.coparently.app.data.chat.ChatMirror
import com.coparently.app.data.export.ExportVerifyAddress
import com.coparently.app.data.export.RecordFileWriter
import com.coparently.app.data.files.PickedFiles
import com.coparently.app.data.files.StorageBucket
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.data.local.dao.BudgetDao
import com.coparently.app.data.local.dao.ChangeRequestDao
import com.coparently.app.data.local.dao.ChildInfoDao
import com.coparently.app.data.local.dao.CustodyModelDao
import com.coparently.app.data.local.dao.CustodyScheduleDao
import com.coparently.app.data.local.dao.EventDao
import com.coparently.app.data.local.dao.EventVersionOutboxDao
import com.coparently.app.data.local.dao.ExpenseDao
import com.coparently.app.data.local.dao.FamilyDocumentCacheDao
import com.coparently.app.data.local.dao.JournalDao
import com.coparently.app.data.local.dao.KnownFamilyDao
import com.coparently.app.data.local.dao.MessageDao
import com.coparently.app.data.local.dao.ParentingPlanDao
import com.coparently.app.data.local.dao.PetDao
import com.coparently.app.data.local.dao.UserDao
import com.coparently.app.data.platform.Analytics
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.data.platform.Connectivity
import com.coparently.app.data.platform.CrashReporter
import com.coparently.app.data.platform.DeviceRegion
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.remote.firebase.AccountSignIn
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.CallableFunctions
import com.coparently.app.data.remote.firebase.FirestoreClient
import com.coparently.app.data.remote.firebase.FirestorePushQueue
import com.coparently.app.data.remote.firebase.IosFirebaseAuth
import com.coparently.app.data.remote.firebase.PushDevice
import com.coparently.app.data.remote.firebase.PushQueue
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.GoogleCalendarAccount
import com.coparently.app.data.remote.google.GoogleCalendarApi
import com.coparently.app.data.remote.google.GoogleCalendarConnectionSource
import com.coparently.app.data.repository.PairingWording
import com.coparently.app.data.requests.RequestClockNotices
import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.SchoolImportWordings
import com.coparently.app.data.school.bakalari.BakalariClient
import com.coparently.app.data.school.bakalari.BakalariSchoolDirectory
import com.coparently.app.data.session.AccountConsistency
import com.coparently.app.data.session.AccountDeletion
import com.coparently.app.data.session.AccountTraces
import com.coparently.app.data.session.IosAccountTraces
import com.coparently.app.data.session.LastSignedInUid
import com.coparently.app.data.session.SessionProfileSynchronizer
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.sync.GoogleCalendarAutoImport
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.data.sync.SyncService
import com.coparently.app.di.koin.dataModules
import com.coparently.app.di.koin.platformDataModule
import com.coparently.app.di.koin.platformUiModule
import com.coparently.app.di.koin.uiModules
import com.coparently.app.domain.ai.AiAssistAvailability
import com.coparently.app.domain.ai.AiAssistRepository
import com.coparently.app.domain.dictation.SpeechDictation
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.receipts.ReceiptTextRecognizer
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.repository.RecordPhotoStorage
import com.coparently.app.presentation.auth.AuthErrors
import com.coparently.app.presentation.pairing.InviteQrCodes
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.fail

/**
 * The iPhone's Koin graph as `IosApp.start` builds it (MON-24 phase 3, carried from Task 6): the
 * data layer's and the UI's modules, their iPhone halves and the app's own [iosAppModule], each
 * seam the iPhone binds resolved once, and the classes `IosApp.startSession` starts.
 *
 * Koin checks nothing at compile time, and Android's `KoinGraphTest` verifies Android's graph, not
 * this one: a seam `platformDataModule` or `platformUiModule` forgot would otherwise fail on the
 * phone, at the first screen that asks. Every type that fails to resolve is listed at once.
 *
 * Firebase is configured as `IosApp.start` configures it for the emulators (`IosFirebase`, fake
 * `demo-coplanly` options that no production service accepts); nothing here signs in, reads or
 * writes, so the test needs no emulator, but it runs where `:shared-ui`'s iPhone tests run (the
 * `ios-app` job's booted simulator), whose Keychain the preference store opens.
 */
class IosKoinGraphTest {

    @Test
    fun `every seam the iPhone binds resolves in the graph IosApp starts`() {
        startFirebaseOnce()
        val koin = startKoin {
            modules(dataModules + platformDataModule + uiModules + platformUiModule + iosAppModule)
        }.koin
        try {
            val missing = SEAMS.mapNotNull { type ->
                runCatching { koin.get<Any>(type) }.exceptionOrNull()?.let { "${type.simpleName}: $it" }
            }
            runCatching { koin.get<CoPlanlyDatabase>().close() }
            if (missing.isNotEmpty()) fail(missing.joinToString("\n", prefix = "Unresolved in the iPhone's graph:\n"))
        } finally {
            stopKoin()
        }
    }

    private companion object {
        /** Whether this process has configured Firebase; the SDK refuses a second time. */
        var firebaseStarted = false

        /** Firebase, once per test process, as `IosApp.start` starts it for the emulators. */
        fun startFirebaseOnce() {
            if (firebaseStarted) return
            IosFirebase.configure(
                IosAppConfig(
                    emulatorHost = "127.0.0.1",
                    versionName = "test",
                    privacyPolicyUrl = null,
                    termsUrl = null,
                    supportEmail = null,
                )
            )
            firebaseStarted = true
        }

        /** What `platformDataModule` and `platformUiModule` bind, and what `startSession` starts. */
        val SEAMS: List<KClass<*>> = listOf(
            // platformDataModule
            FirestoreClient::class, CallableFunctions::class, AppDirectories::class, SecurePreferences::class,
            CrashReporter::class, Analytics::class, Connectivity::class, DeviceRegion::class,
            CoPlanlyDatabase::class, EventDao::class, UserDao::class, CustodyScheduleDao::class,
            CustodyModelDao::class, ChildInfoDao::class, PetDao::class, MessageDao::class, ExpenseDao::class,
            BudgetDao::class, ChangeRequestDao::class, ParentingPlanDao::class, EventVersionOutboxDao::class,
            JournalDao::class, FamilyDocumentCacheDao::class, KnownFamilyDao::class,
            GoogleCalendarApi::class, BakalariClient::class, BakalariSchoolDirectory::class,
            IosFirebaseAuth::class, SignedInUser::class, AuthSession::class, AccountSignIn::class,
            IosAccountTraces::class, AccountTraces::class, LastSignedInUid::class, AccountConsistency::class,
            FirestorePushQueue::class, PushDevice::class, PushQueue::class, PushRegistration::class,
            AccountDeletion::class, ReminderScheduler::class, RequestClockScheduler::class,
            RequestClockNotices::class, SchoolImportScheduler::class, SupportReminderScheduler::class,
            SyncRequester::class, GoogleCalendarAccount::class, GoogleCalendarConnectionSource::class,
            CredentialProvider::class, StorageBucket::class, RecordPhotoStorage::class,
            ReceiptTextRecognizer::class, PickedFiles::class, ExportVerifyAddress::class,
            RecordFileWriter::class,
            // platformUiModule
            SpeechDictation::class, AiAssistAvailability::class, AiAssistRepository::class,
            InviteQrCodes::class, AuthErrors::class, PairingWording::class, SchoolImportWordings::class,
            // what IosApp.startSession starts, and what its sync runs
            ChatMirror::class, SessionProfileSynchronizer::class, SyncService::class,
            GoogleCalendarAutoImport::class, PairingRepository::class,
        )
    }
}
