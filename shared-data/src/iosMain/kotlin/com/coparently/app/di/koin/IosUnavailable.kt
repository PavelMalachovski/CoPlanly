package com.coparently.app.di.koin

import com.coparently.app.data.remote.firebase.PushRegistration
import com.coparently.app.data.school.SchoolImportScheduler
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.domain.model.Event
import com.coparently.app.domain.notification.ReminderScheduler
import com.coparently.app.domain.notification.RequestClockScheduler

/*
 * What the data layer asks of the platform that the iPhone does not have yet (MON-24 phase 2, Task
 * 8j): each answers as a phone with nothing of the kind would — no
 * push token, nothing picked — or does nothing, so `platformDataModule` resolves
 * every definition of `dataModules` without promising a feature (design item 8). Each is replaced by
 * the real one as the iPhone app gains it (phase 3 and 4: the push token, the
 * Storage SDK, the picker and the background jobs). None of them writes or reads anything.
 * The sign-in's stand-ins (`NoSignedInUser`, `NoAuthSession`, `NoAccountSignIn`, `NoAccountSwitch`,
 * `NoAccountTraces`) left in Task 5 of phase 3, for `IosFirebaseAuth`, `IosAccountSwitch` and
 * `IosAccountTraces`; in Task 6 the push queue, the sync requests and the wording left too
 * (`NoPush` kept only the push switch, `NoSyncRequests` and `NoWording` went), for
 * `FirestorePushQueue`, `ForegroundSyncRunner` and `:shared-ui`'s `IosPairingWording` and
 * `IosSchoolImportWordings`; in part B's Task 11 `NoAccountDeletion`, for `IosAccountDeletion`; in
 * part C's Task 10 `NoStorageBucket`, for `IosStorageBucket`; in its Task 11 `NoPickedFiles` and
 * `NoRecordPhotoStorage`, for `IosPickedFiles` and `IosRecordPhotoStorage`; in its Task 12
 * `NoReceiptText`, `NoRecordFiles` and `NoExportVerifyAddress`, for `VisionReceiptText`,
 * `IosRecordFileWriter` and `InfoPlistExportVerifyAddress`; in phase 4's Task 7 `NoGoogleCalendar`,
 * for `IosGoogleCalendarAccount`.
 */

/**
 * No push token (slice 10e: Settings' switch and sign-out ask [PushRegistration]): the iPhone
 * registers with no messaging service yet, so the switch reads off, turning it on fails as a phone
 * with no messaging service would, and there is no token to detach.
 *
 * Since phase 3's Task 6 the iPhone queues pushes for the co-parent (`FirestorePushQueue`, bound as
 * `PushDevice` and `PushQueue`), which holds no token either: it reads none and writes none, so this
 * no-op detach is still safe. **Phase 4 must replace the token's registration (the queue's
 * `getCurrentToken` and `updateUserToken`) and this unregistration together**: a real token with
 * this unregistration left behind would keep a signed-out account's pushes arriving on the phone
 * (CLAUDE.md item 23).
 */
internal object NoPush : PushRegistration {
    override val available: Boolean = false

    override fun isPushEnabled(): Boolean = false

    override suspend fun setPushEnabled(enabled: Boolean): Result<Unit> =
        if (enabled) Result.failure(IllegalStateException(UNAVAILABLE)) else Result.success(Unit)

    override suspend fun unregisterToken() = Unit
}

/** No background jobs yet: no event reminder is scheduled, so none is cancelled either. */
internal object NoEventReminders : ReminderScheduler {
    override fun schedule(event: Event) = Unit
    override fun cancel(eventId: String) = Unit
    override fun cancelAll() = Unit
}

/**
 * No request clock's job on the iPhone, ever: the reminders, the deadline notice and the end are
 * the server's (MON-25; iPhone phase 4 part C), so there is nothing to cancel either.
 */
internal object NoRequestClockJobs : RequestClockScheduler {
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
 * reminder on). [available] says so, so the shared screen offers no reminder here (slice 11e); a
 * choice made elsewhere is still kept, in the sealed store, for the job phase 3 brings.
 */
internal object NoSupportReminderJob : SupportReminderScheduler {

    /** Says so, so the shared child support screen offers no reminder here. */
    override val available: Boolean = false

    override fun schedule() = Unit
    override fun cancel() = Unit
}

private const val UNAVAILABLE = "Not available in this iPhone build yet"
