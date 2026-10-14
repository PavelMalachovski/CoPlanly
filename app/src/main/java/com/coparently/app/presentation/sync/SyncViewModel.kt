package com.coparently.app.presentation.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.local.preferences.PreferenceKeys
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.remote.google.GoogleAccess
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.SyncFailure
import com.coparently.app.data.sync.SyncResult
import com.coparently.app.data.sync.SyncService
import com.coparently.app.data.sync.SyncStage
import com.coparently.app.data.sync.SyncStatus
import com.coparently.app.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для управления операциями синхронизации.
 * Обрабатывает синхронизацию с Firestore и Google Calendar.
 *
 * Использует Google Sign-In API для аутентификации с OAuth2 токенами.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncService: SyncService,
    private val calendarSyncRepository: CalendarSyncRepository,
    private val credentialManagerService: CredentialManagerService,
    private val encryptedPreferences: EncryptedPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "SyncViewModel"
    }

    // Google Calendar sync state
    private val _connection = MutableStateFlow(GoogleCalendarConnection.NOT_CONNECTED)

    /**
     * Whether Google Calendar is connected on this device, needs a reconnect, or never was — read
     * from what the device stores ([CredentialManagerService.connection]), so a connection whose
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
            Log.e(TAG, "Error checking sign-in status", e)
            _connection.value = GoogleCalendarConnection.NOT_CONNECTED
            _userEmail.value = null
        }
    }

    /**
     * Создает Intent для запуска Google Sign-In flow через Credential Manager.
     *
     * The same flow connects for the first time and reconnects a connection Google ended
     * ([GoogleCalendarConnection.NEEDS_RECONNECT]); either way it asks Google for a new refresh token.
     */
    fun createGoogleSignInIntent(): android.content.Intent? {
        return try {
            _syncState.value = GoogleCalendarSyncState.Syncing(UiText.Res(R.string.sync_google_opening_sign_in))
            val client = credentialManagerService.getGoogleSignInClient()
            if (client == null) {
                Log.e(TAG, "Google Sign-In client is not available. OAuth may not be configured.")
                _syncState.value = GoogleCalendarSyncState.Error(
                    UiText.Res(R.string.sync_google_sign_in_unavailable)
                )
                null
            } else {
                client.signInIntent
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create Google Sign-In intent", e)
            _syncState.value = GoogleCalendarSyncState.Error(UiText.Res(R.string.sync_google_sign_in_failed))
            null
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
     * @param completedTask Task с результатом Google Sign-In
     */
    suspend fun handleSignInResult(
        completedTask: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>
    ): GoogleCalendarSyncState {
        _syncState.value = GoogleCalendarSyncState.Syncing(UiText.Res(R.string.sync_google_processing_sign_in))

        // The service's error text is English and technical; it goes to the log, and the user
        // reads a localised sentence instead (CQ-14).
        val (account, error) = credentialManagerService.handleSignInResult(completedTask)

        val state = if (account != null) {
            // Проверяем, можем ли получить access token
            when (val access = credentialManagerService.accessToken()) {
                is GoogleAccess.Granted -> {
                    val who = account.displayName ?: account.email.orEmpty()
                    GoogleCalendarSyncState.Success(UiText.Res(R.string.sync_google_signed_in_as, listOf(who)))
                }
                is GoogleAccess.Denied -> {
                    Log.w(TAG, "No Calendar access token: ${access.failure}")
                    GoogleCalendarSyncState.Error(UiText.Res(R.string.sync_google_no_calendar_access))
                }
            }
        } else {
            Log.w(TAG, "Google sign-in failed: $error")
            GoogleCalendarSyncState.Error(UiText.Res(R.string.sync_google_sign_in_failed))
        }
        // A first connection and a reconnect alike: the row reads what is stored now.
        refreshConnection()
        _syncState.value = state
        return state
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
     * clears every stored preference ([CredentialManagerService.signOut]). Disconnecting Google
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
 * truncated import never reads like a complete one (CQ-7).
 */
internal fun SyncResult.toSyncState(): GoogleCalendarSyncState = when (this) {
    is SyncResult.Progress -> GoogleCalendarSyncState.Syncing(
        when (stage) {
            SyncStage.STARTING -> UiText.Res(R.string.sync_google_progress_starting)
            SyncStage.FETCHING -> UiText.Res(R.string.sync_google_progress_fetching)
            SyncStage.FOUND -> UiText.Plural(R.plurals.sync_google_progress_found, found)
        }
    )
    is SyncResult.Success -> GoogleCalendarSyncState.Success(
        UiText.Plural(
            id = if (truncated) R.plurals.sync_google_synced_truncated else R.plurals.sync_google_synced,
            count = synced,
            args = listOf(synced, UiText.Date(from), UiText.Date(until))
        )
    )
    is SyncResult.Error -> GoogleCalendarSyncState.Error(reason.toUiText())
}

/** The sentence a user reads for a failed Google Calendar import. */
internal fun SyncFailure.toUiText(): UiText = UiText.Res(
    when (this) {
        SyncFailure.NOT_SIGNED_IN_GOOGLE -> R.string.sync_google_error_not_connected
        SyncFailure.RECONNECT -> R.string.sync_google_error_reconnect
        SyncFailure.NOT_SIGNED_IN_APP -> R.string.sync_google_error_signed_out
        SyncFailure.AUTHENTICATION -> R.string.sync_google_error_authentication
        SyncFailure.ACCESS_DENIED -> R.string.sync_google_error_access_denied
        SyncFailure.CALENDAR_NOT_FOUND -> R.string.sync_google_error_calendar_not_found
        SyncFailure.RATE_LIMITED -> R.string.sync_google_error_rate_limited
        SyncFailure.SERVICE_UNAVAILABLE -> R.string.sync_google_error_unavailable
        SyncFailure.GOOGLE_UNREACHABLE -> R.string.sync_google_error_unreachable
        SyncFailure.NETWORK -> R.string.sync_google_error_network
        SyncFailure.UNKNOWN -> R.string.sync_google_error_unknown
    }
)
