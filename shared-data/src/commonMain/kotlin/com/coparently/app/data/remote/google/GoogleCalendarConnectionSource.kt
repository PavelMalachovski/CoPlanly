package com.coparently.app.data.remote.google

/**
 * Where this device's Google Calendar connection is read from (MON-24 phase 2, Task 8g): what the
 * automatic import asks before it calls Google.
 *
 * The app's `CredentialManagerService`, which holds the stored tokens and the Credential Manager
 * sign-in, answers it on Android; the iPhone's Google sign-in will answer it there.
 */
interface GoogleCalendarConnectionSource {

    /** Whether Google Calendar is connected here, from what the device stores, without asking Google. */
    fun connection(): GoogleCalendarConnection
}
