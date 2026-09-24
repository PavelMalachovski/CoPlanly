package com.coparently.app.presentation.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.R
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.google.CredentialManagerService
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
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _isSyncEnabled = MutableStateFlow(false)
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    private val _syncState = MutableStateFlow<GoogleCalendarSyncState>(GoogleCalendarSyncState.Idle)
    val syncState: StateFlow<GoogleCalendarSyncState> = _syncState.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    // Firestore sync status
    val firestoreSyncStatus: StateFlow<SyncStatus> = syncService.syncStatus

    init {
        checkSignInStatus()
    }

    /**
     * Проверяет текущий статус входа в Google.
     */
    private fun checkSignInStatus() {
        try {
            _isSignedIn.value = credentialManagerService.isSignedIn()
            _userEmail.value = encryptedPreferences.getUserEmail()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sign-in status", e)
            _isSignedIn.value = false
            _userEmail.value = null
        }
    }

    /**
     * Создает Intent для запуска Google Sign-In flow через Credential Manager.
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

        return if (account != null) {
            _isSignedIn.value = true
            _userEmail.value = account.email

            // Проверяем, можем ли получить access token
            val (token, tokenError) = credentialManagerService.getAccessToken()
            if (token != null) {
                val who = account.displayName ?: account.email.orEmpty()
                val successState = GoogleCalendarSyncState.Success(
                    UiText.Res(R.string.sync_google_signed_in_as, listOf(who))
                )
                _syncState.value = successState
                successState
            } else {
                Log.w(TAG, "No Calendar access token: $tokenError")
                val errorState = GoogleCalendarSyncState.Error(
                    UiText.Res(R.string.sync_google_no_calendar_access)
                )
                _syncState.value = errorState
                errorState
            }
        } else {
            Log.w(TAG, "Google sign-in failed: $error")
            val errorState = GoogleCalendarSyncState.Error(
                UiText.Res(R.string.sync_google_sign_in_failed)
            )
            _syncState.value = errorState
            errorState
        }
    }

    /**
     * Переключает состояние синхронизации Google Calendar.
     */
    fun toggleSync(enabled: Boolean) {
        _isSyncEnabled.value = enabled
        if (enabled) {
            syncFromGoogle()
        }
    }

    /**
     * Синхронизирует события из Google Calendar.
     */
    fun syncFromGoogle() {
        if (!_isSignedIn.value) {
            _syncState.value = GoogleCalendarSyncState.Error(SyncFailure.NOT_SIGNED_IN_GOOGLE.toUiText())
            return
        }

        viewModelScope.launch {
            // Access token refresh is handled automatically in CredentialProvider
            calendarSyncRepository.syncFromGoogle().collect { result ->
                _syncState.value = result.toSyncState()
            }
        }
    }

    /**
     * Выполняет выход из учетной записи Google.
     */
    suspend fun signOut(): Pair<Boolean, String?> {
        val (success, error) = credentialManagerService.signOut()
        if (success) {
            _isSignedIn.value = false
            _isSyncEnabled.value = false
            _syncState.value = GoogleCalendarSyncState.Idle
            _userEmail.value = null
        }
        return Pair(success, error)
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
        SyncFailure.NOT_SIGNED_IN_APP -> R.string.sync_google_error_signed_out
        SyncFailure.AUTHENTICATION -> R.string.sync_google_error_authentication
        SyncFailure.ACCESS_DENIED -> R.string.sync_google_error_access_denied
        SyncFailure.CALENDAR_NOT_FOUND -> R.string.sync_google_error_calendar_not_found
        SyncFailure.RATE_LIMITED -> R.string.sync_google_error_rate_limited
        SyncFailure.SERVICE_UNAVAILABLE -> R.string.sync_google_error_unavailable
        SyncFailure.NETWORK -> R.string.sync_google_error_network
        SyncFailure.UNKNOWN -> R.string.sync_google_error_unknown
    }
)
