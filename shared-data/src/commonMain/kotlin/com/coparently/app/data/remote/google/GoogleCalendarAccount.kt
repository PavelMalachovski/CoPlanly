package com.coparently.app.data.remote.google

/**
 * This device's Google Calendar connection as Settings manages it (MON-24 phase 2, Task 11): what
 * `SyncViewModel` asks of it beside the connection itself. Google's sign-in screen is the shell's
 * (`GoogleCalendarSignIn` in `:shared-ui`), because on Android it is an activity result.
 *
 * The app's `CredentialManagerService`, which holds the stored tokens, answers it on Android; on
 * the iPhone, which has no Google sign-in yet, nothing is ever connected.
 */
interface GoogleCalendarAccount : GoogleCalendarConnectionSource {

    /**
     * An access token to call Google with, renewed when the stored one has run out or
     * [forceRefresh] says Google has just refused it; each way it can fail is typed.
     */
    suspend fun accessToken(forceRefresh: Boolean = false): GoogleAccess

    /**
     * Signs the whole app out of Google on this device: every stored preference is cleared first,
     * then the Google sign-in ends (CLAUDE.md item 23: the local credential before the network
     * revoke, so an offline sign-out leaves nothing for the next account).
     *
     * @return whether it succeeded, and why not
     */
    suspend fun signOut(): Pair<Boolean, String?>

    /** Disconnects Google Calendar alone: Google's own keys go first, then the sign-in ends. */
    suspend fun disconnect()
}
