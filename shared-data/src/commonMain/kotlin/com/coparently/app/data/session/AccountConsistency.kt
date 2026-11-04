package com.coparently.app.data.session

/**
 * The check that the local data still belongs to the account now signed in (MON-24 phase 2, Task
 * 8g): what `SyncService.performFullSync` asks before its first upload.
 *
 * A periodic sync can fire before the session boundary has seen an account switch, and uploading
 * the previous account's still-unsynced rows under the new uid is the leak the check stops. The
 * app's `AccountSwitchGuard` answers it on Android (it wipes Room, the reminders, the request
 * clocks' jobs and the sealed preferences on a switch); the iPhone's session will answer it there.
 */
interface AccountConsistency {

    /**
     * Wipes this device's local data when a different account has signed in since the last check;
     * a no-op while signed out and on the same account.
     */
    suspend fun ensureAccountConsistency()
}
