package com.coparently.app.di.koin

import com.coparently.app.data.school.SchoolImportScheduler
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
 * for `IosGoogleCalendarAccount`; in phase 4 part C's Task 5 `NoPush`, for `IosPushRegistration`
 * (the token's registration and its unregistration replaced together, CLAUDE.md item 23); in its
 * Task 7 `NoEventReminders` and `NoSupportReminderJob`, for the local notifications of
 * `IosEventReminders` and `IosSupportReminders`.
 */

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
