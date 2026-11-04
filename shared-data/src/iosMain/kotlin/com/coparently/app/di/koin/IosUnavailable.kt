package com.coparently.app.di.koin

import com.coparently.app.data.export.ExportVerifyAddress
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.data.export.RecordFileWriter
import com.coparently.app.data.files.PickedFiles
import com.coparently.app.data.files.StorageBucket
import com.coparently.app.data.remote.firebase.AuthSession
import com.coparently.app.data.remote.firebase.PushDevice
import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.remote.firebase.SignedInAccount
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.data.remote.google.CredentialProvider
import com.coparently.app.data.remote.google.GoogleAccessException
import com.coparently.app.data.remote.google.GoogleAccessFailure
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import com.coparently.app.data.remote.google.GoogleCalendarConnectionSource
import com.coparently.app.data.remote.google.GoogleCredential
import com.coparently.app.data.repository.PairingWording
import com.coparently.app.data.requests.RequestClockNotices
import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.school.SchoolImportWordings
import com.coparently.app.data.session.AccountConsistency
import com.coparently.app.data.session.AccountDeletion
import com.coparently.app.data.session.AccountTraces
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.export.CommunicationRecord
import com.coparently.app.domain.export.ExportFormat
import com.coparently.app.domain.export.RecordLabels
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
import com.coparently.app.domain.receipts.ReceiptTextRecognizer
import com.coparently.app.domain.repository.RecordPhotoStorage
import com.coparently.app.domain.school.SchoolImportWording
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.IOException
import kotlinx.io.RawSource
import kotlinx.io.files.Path

/*
 * What the data layer asks of the platform that the iPhone does not have yet (MON-24 phase 2, Task
 * 8j): each answers as a phone with nothing of the kind would — nobody signed in, no Google Calendar
 * connected, no push, no Cloud Storage, nothing picked — or does nothing, so `platformDataModule`
 * resolves every definition of `dataModules` without promising a feature (design item 8). Each is
 * replaced by the real one as the iPhone app gains it (phase 3: Firebase Auth, the push token, the
 * Storage SDK, the picker, the background jobs and the app's own wording). None of them writes or
 * reads anything.
 */

/** Nobody is signed in: the iPhone has no sign-in yet. */
internal object NoSignedInUser : SignedInUser {
    override fun getCurrentUid(): String? = null
    override fun getCurrentDisplayName(): String? = null
    override fun getCurrentEmail(): String? = null
    override fun getCurrentPhotoUrl(): String? = null
    override fun observeCurrentUid(): Flow<String?> = flowOf(null)
    override fun getCurrentAccount(): SignedInAccount? = null
}

/**
 * No session (slice 10e: the start and sign-out ask for it): nobody is signed in, as [NoSignedInUser]
 * says, and ending a session there is nothing to do. Replace the two together.
 */
internal object NoAuthSession : AuthSession {
    override suspend fun awaitSignedInUid(): String? = null

    override suspend fun signOutCompletely(): Result<Unit> = Result.success(Unit)
}

/**
 * Nothing kept outside the database to remove at sign-out (slice 10e): with no Cloud Storage
 * ([NoStorageBucket]) no file is downloaded, and no notification is posted ([NoPush]). Replace it
 * with those two: once the iPhone downloads files or posts notifications, sign-out must remove them.
 */
internal val NoAccountTraces = AccountTraces { }

/**
 * No push token and no push (the sync and the repositories ask [PushDevice]; slice 10e: Settings'
 * switch and sign-out ask [PushRegistration]): the iPhone registers with no messaging service yet,
 * so nothing is queued, no token is read or written, the switch reads off, turning it on fails as a
 * phone with no messaging service would, and there is no token to detach.
 *
 * **One object for both, as `FcmService` is on Android**, bound under both types: phase 3 must
 * replace push delivery and the token's unregistration together. A real token with this
 * unregistration left behind would keep a signed-out account's pushes arriving on the phone
 * (CLAUDE.md item 23).
 */
internal object NoPush : PushDevice, PushRegistration {
    override suspend fun queueNotificationForUser(
        targetUserId: String,
        notificationData: Map<String, String>
    ): Result<Unit> = Result.failure(IllegalStateException(UNAVAILABLE))

    override suspend fun getCurrentToken(): String? = null

    override suspend fun updateUserToken(token: String): Result<Unit> =
        Result.failure(IllegalStateException(UNAVAILABLE))

    override fun isPushEnabled(): Boolean = false

    override suspend fun setPushEnabled(enabled: Boolean): Result<Unit> =
        if (enabled) Result.failure(IllegalStateException(UNAVAILABLE)) else Result.success(Unit)

    override suspend fun unregisterToken() = Unit
}

/**
 * No account to erase yet (slice 10e: Settings asks for it): with nobody signed in
 * ([NoSignedInUser]) the erasure fails and nothing is wiped, rather than reporting an account gone.
 * It must not outlive the iPhone's lack of sign-in: an app where an account can be created must
 * offer to delete it in the app (the App Store's rule, as Google Play's), so replace it together with
 * [NoSignedInUser] and [NoAuthSession].
 */
internal val NoAccountDeletion = AccountDeletion { Result.failure(IllegalStateException(UNAVAILABLE)) }

/**
 * Nothing to compare: with nobody signed in ([NoSignedInUser]), no account can follow another.
 * Replace the two together: a real sign-in over this answer would keep the previous account's data.
 */
internal object NoAccountSwitch : AccountConsistency {
    override suspend fun ensureAccountConsistency() = Unit
}

/** No background jobs yet: no event reminder is scheduled, so none is cancelled either. */
internal object NoEventReminders : ReminderScheduler {
    override fun schedule(event: Event) = Unit
    override fun cancel(eventId: String) = Unit
    override fun cancelAll() = Unit
}

/** No background jobs yet: a request clock's steps are not scheduled. */
internal object NoRequestClockJobs : RequestClockScheduler {
    override fun scheduleReminder(clockId: String, atMillis: Long) = Unit
    override fun scheduleDeadline(clockId: String, atMillis: Long) = Unit
    override fun scheduleEnd(clockId: String, atMillis: Long) = Unit
    override fun cancelAll() = Unit
}

/** No background jobs yet: the school import's daily run is not scheduled. */
internal object NoSchoolImportJob : SchoolImportScheduler {
    override fun schedule() = Unit
    override fun cancel() = Unit
}

/**
 * No background jobs yet: the child support reminder's job is not scheduled, so the iPhone reminds
 * nobody (slice 10d: the child support screen's ViewModel asks for it when the payer turns the
 * reminder on). The choice is still kept, in the sealed store, for the job phase 3 brings.
 */
internal object NoSupportReminderJob : SupportReminderScheduler {
    override fun schedule() = Unit
    override fun cancel() = Unit
}

/** No background jobs yet: a sync asked for now is not run in the background. */
internal object NoSyncRequests : SyncRequester {
    override fun requestSyncNow() = Unit
}

/** No notifications yet: a request clock's notice is not shown. */
internal object NoRequestClockNotices : RequestClockNotices {
    override suspend fun remind(clock: RequestDeadline) = Unit
    override suspend fun deadlinePassed(clock: RequestDeadline) = Unit
    override suspend fun noAnswer(clock: RequestDeadline) = Unit
}

/** Google Calendar is not connected here: the iPhone has no Google sign-in yet. */
internal object NoGoogleCalendar : GoogleCalendarConnectionSource, CredentialProvider {
    override fun connection(): GoogleCalendarConnection = GoogleCalendarConnection.NOT_CONNECTED

    override suspend fun getCredential(forceRefresh: Boolean): GoogleCredential =
        throw GoogleAccessException(GoogleAccessFailure.NOT_CONNECTED)

    override suspend fun <T> withCredential(call: suspend (GoogleCredential) -> T): T = call(getCredential())
}

/**
 * No wording yet: the iPhone app's own strings, in the reader's language, come with its screens.
 * A pairing's thread title and a school import's titles are stored and read by the co-parent, so
 * no English stand-in is written in their place; with nobody signed in neither is ever asked for.
 */
internal object NoWording : PairingWording, SchoolImportWordings {
    override fun defaultPartnerName(): String = throw UnsupportedOperationException(UNAVAILABLE)

    override suspend fun forChild(childId: String, fallbackName: String): SchoolImportWording =
        throw UnsupportedOperationException(UNAVAILABLE)
}

/** No Cloud Storage yet: every call fails as an unreachable bucket would, by path and never by URL. */
internal object NoStorageBucket : StorageBucket {
    override suspend fun customMetadata(path: String, key: String): String? = throw IOException(UNAVAILABLE)

    override suspend fun putFile(
        path: String,
        file: Path,
        contentType: String,
        customMetadata: Map<String, String>,
        onProgress: (Float) -> Unit
    ): Unit = throw IOException(UNAVAILABLE)

    override suspend fun putBytes(
        path: String,
        bytes: ByteArray,
        contentType: String,
        customMetadata: Map<String, String>
    ): Unit = throw IOException(UNAVAILABLE)

    override suspend fun getFile(path: String, target: Path): Unit = throw IOException(UNAVAILABLE)

    override suspend fun delete(path: String): Unit = throw IOException(UNAVAILABLE)
}

/**
 * Nothing can be picked yet: no file has a name, and reading one fails as unavailable, never as a
 * type the rules would refuse.
 */
internal object NoPickedFiles : PickedFiles {
    override fun typeOf(contentUri: String): String? = throw IOException(UNAVAILABLE)
    override fun displayNameOf(contentUri: String): String? = null
    override fun open(contentUri: String): RawSource = throw IOException(UNAVAILABLE)
}

/** No verification page is configured for the iPhone yet, so an export prints no address. */
internal val NoExportVerifyAddress = ExportVerifyAddress { "" }

/**
 * No Cloud Storage for a record's photos yet (phase 3), as [NoStorageBucket] has none for a shared
 * file: an upload fails, so nothing is stored that names a photo, and a delete
 * fails too, so a caller that keeps a list keeps the reference rather than dropping one to an object
 * it could not reach (`RecordPhotoStorage.delete`'s contract). The event form's photo is the first
 * caller (`EventViewModel`, slice 10b).
 */
internal object NoRecordPhotoStorage : RecordPhotoStorage {
    override suspend fun upload(
        kind: RecordPhotoKind,
        recordId: String,
        recordFamilyId: String?,
        localUri: String
    ): String = throw IOException(UNAVAILABLE)

    override suspend fun delete(reference: String, recordFamilyId: String?): Unit =
        throw IOException(UNAVAILABLE)
}

/**
 * No on-device text recognition yet (ML Kit's is Android's; the iPhone's comes with its camera,
 * phase 4): a receipt's text is not read, and [available] says so, so the shared expense form never
 * scans a photo here (slice 11e: it offers no receipt at all while the iPhone has no picker or image
 * loader either). Were a scan asked for, it would fail and the form would show its failed-scan
 * message and keep the photo, as when recognition fails on Android (`ExpenseViewModel.scanReceipt`,
 * slice 10d). Nothing is sent anywhere instead (item 10 of "Things that are easy to get wrong").
 */
internal object NoReceiptText : ReceiptTextRecognizer {

    /** Says so, so the shared expense form never scans here (slice 11e). */
    override val available: Boolean = false

    override suspend fun recognize(imageUri: String): List<String> = throw IOException(UNAVAILABLE)
}

/**
 * No communication record's file yet (slice 10f: the export's ViewModel asks for it): the PDF is
 * drawn with Android's `PdfDocument` and the file handed to its share sheet, and the iPhone has
 * neither yet, so rendering fails and nothing is saved. The export screen then says the export
 * failed, which is true. An id reserved before the render is left with no hash, as after any failed
 * export on Android, and no file names it (MON-16); the server sweeps such reservations after seven
 * days (Legal item 6). Replace it when the iPhone draws a PDF and shares a file.
 */
internal object NoRecordFiles : RecordFileWriter {
    override suspend fun render(record: CommunicationRecord, labels: RecordLabels, format: ExportFormat): ByteArray =
        throw IOException(UNAVAILABLE)

    override suspend fun save(bytes: ByteArray, record: CommunicationRecord, format: ExportFormat): ExportedFile =
        throw IOException(UNAVAILABLE)
}

private const val UNAVAILABLE = "Not available in this iPhone build yet"
