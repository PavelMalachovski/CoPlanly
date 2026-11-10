package com.coparently.app.data.session

/**
 * What an account leaves on the phone outside the encrypted database (MON-24 phase 2, Task 10): the
 * plaintext copies of its files and the notifications it was shown, which sign-out removes.
 *
 * The app's `DeviceTraces` answers it on Android (security audit, October 2026); the iPhone, which
 * keeps neither yet, answers it with nothing to remove (phase 3 replaces it).
 */
fun interface AccountTraces {

    /**
     * Removes what sign-out must: every re-downloadable or one-off file the account opened, and its
     * notifications. What Room keeps, and the chat's unsent attachments, stay for the account's
     * return.
     */
    suspend fun clearAfterSignOut()
}
