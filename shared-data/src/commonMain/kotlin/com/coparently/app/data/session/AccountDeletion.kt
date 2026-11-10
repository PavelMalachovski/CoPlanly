package com.coparently.app.data.session

/**
 * Erasing the signed-in account (MON-24 phase 2, Task 10): what Settings asks of the `deleteAccount`
 * callable and of this phone's wipe after it.
 *
 * The app's `AccountDeletionService` answers it on Android: the server erases the account first and
 * deletes the Auth user last, and only then is this device wiped (push token, database, scheduled
 * jobs, sealed preferences, the plaintext copies of files). The iPhone's will answer it there
 * (phase 3).
 */
fun interface AccountDeletion {

    /**
     * Deletes the account and wipes this device.
     *
     * @return success once the server has erased the account and the local data is clear; a
     *   failure leaves the account in place, to be tried again.
     */
    suspend fun deleteAccount(): Result<Unit>
}
