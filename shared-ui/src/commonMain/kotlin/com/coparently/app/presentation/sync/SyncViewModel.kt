package com.coparently.app.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.platform.SecurePreferences
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.google.GoogleAccess
import com.coparently.app.data.remote.google.GoogleCalendarAccount
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import com.coparently.app.data.remote.google.GoogleSignInFailure
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.SyncFailure
import com.coparently.app.data.sync.SyncResult
import com.coparently.app.data.sync.SyncService
import com.coparently.app.data.sync.SyncStage
import com.coparently.app.data.sync.SyncStatus
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.platform.GoogleCalendarSignIn
import com.coparently.app.presentation.platform.GoogleSignInAnswer
import com.coparently.app.presentation.platform.GoogleSignInCompletion
import com.coparently.app.presentation.platform.GoogleSignInStart
import com.coparently.app.resources.Res
import com.coparently.app.resources.sync_google_error_access_denied
import com.coparently.app.resources.sync_google_error_authentication
import com.coparently.app.resources.sync_google_error_calendar_not_found
import com.coparently.app.resources.sync_google_error_network
import com.coparently.app.resources.sync_google_error_not_connected
import com.coparently.app.resources.sync_google_error_rate_limited
import com.coparently.app.resources.sync_google_error_reconnect
import com.coparently.app.resources.sync_google_error_signed_out
import com.coparently.app.resources.sync_google_error_unavailable
import com.coparently.app.resources.sync_google_error_unknown
import com.coparently.app.resources.sync_google_error_unreachable
import com.coparently.app.resources.sync_google_no_calendar_access
import com.coparently.app.resources.sync_google_opening_sign_in
import com.coparently.app.resources.sync_google_processing_sign_in
import com.coparently.app.resources.sync_google_progress_fetching
import com.coparently.app.resources.sync_google_progress_found
import com.coparently.app.resources.sync_google_progress_starting
import com.coparently.app.resources.sync_google_server_failed
import com.coparently.app.resources.sync_google_sign_in_cancelled
import com.coparently.app.resources.sync_google_sign_in_failed
import com.coparently.app.resources.sync_google_sign_in_refused
import com.coparently.app.resources.sync_google_sign_in_unavailable
import com.coparently.app.resources.sync_google_signed_in_as
import com.coparently.app.resources.sync_google_synced
import com.coparently.app.resources.sync_google_synced_truncated
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для управления операциями синхронизации.
 * Обрабатывает синхронизацию с Firestore и Google Calendar.
 *
 * Использует Google Sign-In API для аутентификации с OAuth2 токенами.
 *
 * In `:shared-ui` since MON-24 phase 2 (Task 11): the connection is [GoogleCalendarAccount] (the
 * app's `CredentialManagerService` on Android), the store [SecurePreferences], and Google's sign-in
 * screen the shell's [GoogleCalendarSignIn], whose answer `MainActivity` hands to
 * [handleSignInResult].
 */
class SyncViewModel(
    private val syncService: SyncService,
    private val calendarSyncRepository: CalendarSyncRepository,
    private val credentialManagerService: GoogleCalendarAccount,
    private val encryptedPreferences: SecurePreferences
) : ViewModel() {

    companion object {
        private const val TAG = "SyncViewModel"
    }

    // Google Calendar sync state
    private val _connection = MutableStateFlow(GoogleCalendarConnection.NOT_CONNECTED)

    /**
     * Whether Google Calendar is connected on this device, needs a reconnect, or never was — read
     * from what the device stores ([GoogleCalendarAccount.connection]), so a connection whose
     * access token has run out still reads as connected: its refresh token renews it.
     */
    val connection: StateFlow<GoogleCalendarConnection> = _connection.asStateFlow()

    private val _isSyncEnabled = MutableStateFlow(false)

    /**
     * The "Enable sync" switch, stored ([PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED]) rather than
     * held here alone: it used to read off after every restart, which greyed the Sync button.
     */
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    private val _syncState = MutableStateFlow<GoogleCalendarSyncState>(GoogleCalendarSyncState.Idle)
    val syncState: StateFlow<GoogleCalendarSyncState> = _syncState.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    // Firestore sync status
    val firestoreSyncStatus: StateFlow<SyncStatus> = syncService.syncStatus

    init {
        refreshConnection()
    }

    /**
     * Re-reads the Google Calendar connection, its address and the sync switch from what this
     * device stores.
     *
     * Settings calls it each time it opens: this ViewModel lives as long as the activity, and an
     * account deleted or switched in the meantime changes the store behind its back.
     */
    fun refreshConnection() {
        try {
            _connection.value = credentialManagerService.connection()
            _userEmail.value = encryptedPreferences.getUserEmail()
            _isSyncEnabled.value = encryptedPreferences.getBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, false)
        } catch (e: Exception) {
            logger.e(TAG, "Error checking sign-in status", e)
            _connection.value = GoogleCalendarConnection.NOT_CONNECTED
            _userEmail.value = null
        }
    }

    /**
     * Opens Google's sign-in screen through [signIn], the shell's.
     *
     * The same flow connects for the first time and reconnects a connection Google ended
     * ([GoogleCalendarConnection.NEEDS_RECONNECT]); either way it asks Google for a new refresh token.
     * It said "opening" before the sign-in client was built, as it did when this built the intent
     * itself; the answer comes back through [handleSignInResult] or [handleSignInCancellation].
     */
    fun startGoogleSignIn(signIn: GoogleCalendarSignIn) {
        _syncState.value = GoogleCalendarSyncState.Syncing(UiText.Res(Res.string.sync_google_opening_sign_in))
        when (signIn.open()) {
            GoogleSignInStart.OPENED -> Unit
            GoogleSignInStart.UNAVAILABLE -> {
                logger.e(TAG, "Google Sign-In client is not available. OAuth may not be configured.")
                _syncState.value = GoogleCalendarSyncState.Error(
                    UiText.Res(Res.string.sync_google_sign_in_unavailable)
                )
            }
            GoogleSignInStart.FAILED ->
                _syncState.value = GoogleCalendarSyncState.Error(UiText.Res(Res.string.sync_google_sign_in_failed))
        }
    }

    /**
     * Сообщает об отмене/ошибке входа, чтобы обновить UI.
     *
     * @param message A resource reference, resolved when the screen draws it (CQ-14).
     */
    fun handleSignInCancellation(message: UiText) {
        _syncState.value = GoogleCalendarSyncState.Error(message)
    }

    /**
     * Обрабатывает результат Google Sign-In.
     *
     * @param answer What Google's sign-in screen answered, as the shell hands it over; redeeming it
     *   ([GoogleSignInAnswer.complete]) stores the connection
     */
    suspend fun handleSignInResult(answer: GoogleSignInAnswer): GoogleCalendarSyncState {
        _syncState.value = GoogleCalendarSyncState.Syncing(UiText.Res(Res.string.sync_google_processing_sign_in))

        // The failure names the step that stopped, and the sentence follows it, so a screenshot
        // says whether Google, the parent or our server ended the connection (CQ-14: localised,
        // with nothing but a status code or the server's short reason in it).
        val state = when (val completion = answer.complete()) {
            // Проверяем, можем ли получить access token
            is GoogleSignInCompletion.Connected -> when (val access = credentialManagerService.accessToken()) {
                is GoogleAccess.Granted -> {
                    val who = completion.displayName ?: completion.email.orEmpty()
                    GoogleCalendarSyncState.Success(UiText.Res(Res.string.sync_google_signed_in_as, listOf(who)))
                }
                is GoogleAccess.Denied -> {
                    logger.w(TAG, "No Calendar access token: ${access.failure}")
                    GoogleCalendarSyncState.Error(UiText.Res(Res.string.sync_google_no_calendar_access))
                }
            }
            is GoogleSignInCompletion.Failed -> {
                logger.w(TAG, "Google sign-in failed: ${completion.failure}")
                GoogleCalendarSyncState.Error(failureText(completion.failure))
            }
        }
        // A first connection and a reconnect alike: the row reads what is stored now.
        refreshConnection()
        _syncState.value = state
        return state
    }

    /** What the parent reads when connecting stopped at [failure]. */
    private fun failureText(failure: GoogleSignInFailure?): UiText = when (failure) {
        GoogleSignInFailure.Cancelled -> UiText.Res(Res.string.sync_google_sign_in_cancelled)
        is GoogleSignInFailure.Google ->
            UiText.Res(Res.string.sync_google_sign_in_refused, listOf(failure.statusCode))
        is GoogleSignInFailure.Server ->
            UiText.Res(Res.string.sync_google_server_failed, listOf(failure.diagnosis))
        GoogleSignInFailure.NoAuthCode, GoogleSignInFailure.Unexpected, null ->
            UiText.Res(Res.string.sync_google_sign_in_failed)
    }

    /**
     * Переключает состояние синхронизации Google Calendar.
     */
    fun toggleSync(enabled: Boolean) {
        _isSyncEnabled.value = enabled
        // Stored, so the switch — and the Sync button it unlocks — read the same after a restart.
        encryptedPreferences.putBoolean(PreferenceKeys.GOOGLE_CALENDAR_SYNC_ENABLED, enabled)
        if (enabled) {
            syncFromGoogle()
        }
    }

    /**
     * Синхронизирует события из Google Calendar.
     *
     * Starts only on a connection this device still holds; one that needs a reconnect says so
     * instead of trying. The access token is renewed — or the connection ended — inside the import,
     * by `CredentialProvider.withCredential`.
     */
    fun syncFromGoogle() {
        // Read again rather than trusted: the connection may have ended since this was created.
        refreshConnection()
        val refused = when (_connection.value) {
            GoogleCalendarConnection.NOT_CONNECTED -> SyncFailure.NOT_SIGNED_IN_GOOGLE
            GoogleCalendarConnection.NEEDS_RECONNECT -> SyncFailure.RECONNECT
            GoogleCalendarConnection.CONNECTED -> null
        }
        if (refused != null) {
            _syncState.value = GoogleCalendarSyncState.Error(refused.toUiText())
            return
        }

        viewModelScope.launch {
            calendarSyncRepository.syncFromGoogle().collect { result ->
                _syncState.value = result.toSyncState()
            }
            // An import that ended the connection changes the row now, not on the next launch.
            refreshConnection()
        }
    }

    /**
     * Signs this device out of Google entirely — the first half of "Sign out of app", which
     * clears every stored preference ([GoogleCalendarAccount.signOut]). Disconnecting Google
     * Calendar alone is [disconnectGoogleCalendar].
     */
    suspend fun signOut(): Pair<Boolean, String?> {
        val (success, error) = credentialManagerService.signOut()
        if (success) {
            _connection.value = GoogleCalendarConnection.NOT_CONNECTED
            // The whole store was cleared, the switch's own key with it.
            _isSyncEnabled.value = false
            _syncState.value = GoogleCalendarSyncState.Idle
            _userEmail.value = null
        }
        return Pair(success, error)
    }

    /**
     * Disconnects Google Calendar and nothing else ("Sign out from Calendar sync"): Google's own
     * keys go, every other setting stays — the "Enable sync" switch included, so a reconnect finds
     * it as it was left.
     */
    suspend fun disconnectGoogleCalendar() {
        credentialManagerService.disconnect()
        _syncState.value = GoogleCalendarSyncState.Idle
        refreshConnection()
    }

    /**
     * Выполняет полную синхронизацию с Firestore.
     */
    fun performFirestoreSync() {
        viewModelScope.launch {
            syncService.performFullSync()
        }
    }
}

/**
 * State representing Google Calendar sync operation status.
 *
 * Each message is a [UiText] the Settings screen resolves in composition (CQ-14) — this used to
 * be English, some of it raw exception text.
 */
sealed class GoogleCalendarSyncState {
    data object Idle : GoogleCalendarSyncState()
    data class Syncing(val message: UiText) : GoogleCalendarSyncState()
    data class Success(val message: UiText) : GoogleCalendarSyncState()
    data class Error(val message: UiText) : GoogleCalendarSyncState()
}

/**
 * Words an import's progress, result or failure. Pure, so `SyncStateTextTest` can pin that a
 * truncated import never reads like a complete one (CQ-7). Public for that test and
 * `SyncViewModelTest`, which stay in the app.
 */
fun SyncResult.toSyncState(): GoogleCalendarSyncState = when (this) {
    is SyncResult.Progress -> GoogleCalendarSyncState.Syncing(
        when (stage) {
            SyncStage.STARTING -> UiText.Res(Res.string.sync_google_progress_starting)
            SyncStage.FETCHING -> UiText.Res(Res.string.sync_google_progress_fetching)
            SyncStage.FOUND -> UiText.Plural(Res.plurals.sync_google_progress_found, found)
        }
    )
    is SyncResult.Success -> GoogleCalendarSyncState.Success(
        UiText.Plural(
            id = if (truncated) Res.plurals.sync_google_synced_truncated else Res.plurals.sync_google_synced,
            count = synced,
            args = listOf(synced, UiText.Date(from), UiText.Date(until))
        )
    )
    is SyncResult.Error -> GoogleCalendarSyncState.Error(reason.toUiText())
}

/** The sentence a user reads for a failed Google Calendar import (public for the app's tests). */
fun SyncFailure.toUiText(): UiText = UiText.Res(
    when (this) {
        SyncFailure.NOT_SIGNED_IN_GOOGLE -> Res.string.sync_google_error_not_connected
        SyncFailure.RECONNECT -> Res.string.sync_google_error_reconnect
        SyncFailure.NOT_SIGNED_IN_APP -> Res.string.sync_google_error_signed_out
        SyncFailure.AUTHENTICATION -> Res.string.sync_google_error_authentication
        SyncFailure.ACCESS_DENIED -> Res.string.sync_google_error_access_denied
        SyncFailure.CALENDAR_NOT_FOUND -> Res.string.sync_google_error_calendar_not_found
        SyncFailure.RATE_LIMITED -> Res.string.sync_google_error_rate_limited
        SyncFailure.SERVICE_UNAVAILABLE -> Res.string.sync_google_error_unavailable
        SyncFailure.GOOGLE_UNREACHABLE -> Res.string.sync_google_error_unreachable
        SyncFailure.NETWORK -> Res.string.sync_google_error_network
        SyncFailure.UNKNOWN -> Res.string.sync_google_error_unknown
    }
)
