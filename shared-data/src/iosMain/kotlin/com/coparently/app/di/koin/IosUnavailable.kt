package com.coparently.app.di.koin

import com.coparently.app.data.export.ExportVerifyAddress
import com.coparently.app.data.files.PickedFiles
import com.coparently.app.data.files.StorageBucket
import com.coparently.app.data.remote.firebase.PushDevice
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
import com.coparently.app.data.sync.SyncRequester
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler
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

/** No push token and no push: the iPhone registers with no messaging service yet. */
internal object NoPushDevice : PushDevice {
    override suspend fun queueNotificationForUser(
        targetUserId: String,
        notificationData: Map<String, String>
    ): Result<Unit> = Result.failure(IllegalStateException(UNAVAILABLE))

    override suspend fun getCurrentToken(): String? = null

    override suspend fun updateUserToken(token: String): Result<Unit> =
        Result.failure(IllegalStateException(UNAVAILABLE))
}

/** Nothing to compare: with nobody signed in, no account can follow another. */
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

/** Nothing can be picked yet: no file has a type or a name, and none opens. */
internal object NoPickedFiles : PickedFiles {
    override fun typeOf(contentUri: String): String? = null
    override fun displayNameOf(contentUri: String): String? = null
    override fun open(contentUri: String): RawSource = throw IOException(UNAVAILABLE)
}

/** No verification page is configured for the iPhone yet, so an export prints no address. */
internal val NoExportVerifyAddress = ExportVerifyAddress { "" }

private const val UNAVAILABLE = "Not available in this iPhone build yet"
