package com.coparently.app.presentation.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.local.preferences.EncryptedPreferences
import com.coparently.app.data.remote.google.CredentialManagerService
import com.coparently.app.data.sync.CalendarSyncRepository
import com.coparently.app.data.sync.SyncResult
import com.coparently.app.data.sync.SyncService
import com.coparently.app.data.sync.SyncStatus
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
        _isSignedIn.value = credentialManagerService.isSignedIn()
        _userEmail.value = encryptedPreferences.getUserEmail()
    }

    /**
     * Создает Intent для запуска Google Sign-In flow через Credential Manager.
     */
    fun createGoogleSignInIntent(): android.content.Intent? {
        return try {
            _syncState.value = GoogleCalendarSyncState.Syncing("Opening Google Sign-In...")
            credentialManagerService.getGoogleSignInClient().signInIntent
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create Google Sign-In intent", e)
            _syncState.value = GoogleCalendarSyncState.Error(
                "Unable to start Google Sign-In: ${e.message ?: "Unknown error"}"
            )
            null
        }
    }

    /**
     * Сообщает об отмене/ошибке входа, чтобы обновить UI.
     */
    fun handleSignInCancellation(message: String) {
        _syncState.value = GoogleCalendarSyncState.Error(message)
    }

    /**
     * Обрабатывает результат Google Sign-In.
     *
     * @param completedTask Task с результатом Google Sign-In
     */
    suspend fun handleSignInResult(completedTask: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>): GoogleCalendarSyncState {
        _syncState.value = GoogleCalendarSyncState.Syncing("Processing sign-in...")

        val (account, error) = credentialManagerService.handleSignInResult(completedTask)

        return if (account != null) {
            _isSignedIn.value = true
            _userEmail.value = account.email

            // Проверяем, можем ли получить access token
            val (token, tokenError) = credentialManagerService.getAccessToken()
            if (token != null) {
                val successState = GoogleCalendarSyncState.Success("Signed in successfully as ${account.displayName ?: account.email}")
                _syncState.value = successState
                successState
            } else {
                val errorState = GoogleCalendarSyncState.Error(
                    tokenError ?: "Failed to get Calendar access"
                )
                _syncState.value = errorState
                errorState
            }
        } else {
            val errorState = GoogleCalendarSyncState.Error(
                error ?: "Sign in failed"
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
            _syncState.value = GoogleCalendarSyncState.Error("Not signed in to Google")
            return
        }

        viewModelScope.launch {
            // Access token refresh is handled automatically in CredentialProvider
            calendarSyncRepository.syncFromGoogle().collect { result ->
                _syncState.value = when (result) {
                    is SyncResult.Progress -> GoogleCalendarSyncState.Syncing(result.message)
                    is SyncResult.Success -> GoogleCalendarSyncState.Success(result.message)
                    is SyncResult.Error -> GoogleCalendarSyncState.Error(result.message)
                }
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
 */
sealed class GoogleCalendarSyncState {
    data object Idle : GoogleCalendarSyncState()
    data class Syncing(val message: String) : GoogleCalendarSyncState()
    data class Success(val message: String) : GoogleCalendarSyncState()
    data class Error(val message: String) : GoogleCalendarSyncState()
}
