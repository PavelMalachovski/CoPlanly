package com.coparently.app.data.remote.google

/**
 * Provides the Google credential for the Calendar API, and answers Google's refusals of it.
 */
interface CredentialProvider {
    /**
     * The Google credential for this device, refreshing the access token if it has expired.
     *
     * `suspend` because obtaining one can perform a **network** refresh. It used to be a plain
     * function whose implementation wrapped that refresh in `runBlocking`, and
     * `CalendarSyncRepository` called it from `viewModelScope` — on the main thread — before
     * entering its own `withContext(Dispatchers.IO)` a few lines later. Enabling Google
     * Calendar sync on a slow connection therefore blocked the UI thread for the length of an
     * OAuth round-trip.
     *
     * @param forceRefresh Renew the token even though its stored expiry says it is still good —
     *   what [withCredential] does once after Google refused it.
     * @throws GoogleAccessException when there is no credential to be had, saying why: not
     *   connected, a connection Google ended, or Google out of reach for now. It used to return
     *   null for all three, so an import could only ever say "not connected".
     */
    suspend fun getCredential(forceRefresh: Boolean = false): GoogleCredential

    /**
     * Runs [call] with this device's credential, answering Google's refusals the one way each
     * allows: a 401 renews the token once and runs [call] again, because a token its stored expiry
     * still calls good can have been revoked or cut short; a 401 on the renewed token, or a 403
     * for the Calendar permission the account never granted, ends the connection — its tokens are
     * dropped and it reads as one to reconnect.
     *
     * @throws GoogleAccessException when there is no credential, or the connection was just ended.
     *   Anything else [call] throws comes through unchanged.
     */
    suspend fun <T> withCredential(call: suspend (GoogleCredential) -> T): T
}
