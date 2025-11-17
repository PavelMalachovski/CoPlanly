package com.coparently.app.presentation.sync

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
 * Обновлено для использования нового Credential Manager API вместо deprecated GoogleSignIn API.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncService: SyncService,
    private val calendarSyncRepository: CalendarSyncRepository,
    private val credentialManagerService: CredentialManagerService,
    private val encryptedPreferences: EncryptedPreferences
) : ViewModel() {

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
     * Инициирует процесс входа через Google Credential Manager.
     * Должен быть вызван из Activity/Composable с соответствующим UI контекстом.
     */
    suspend fun signIn(): GoogleCalendarSyncState {
        _syncState.value = GoogleCalendarSyncState.Syncing("Signing in...")

        val (credential, error) = credentialManagerService.getGoogleIdCredential(
            filterByAuthorizedAccounts = false
        )

        return if (credential != null) {
            // Сохраняем email пользователя
            encryptedPreferences.putUserEmail(credential.id)
            _userEmail.value = credential.id
            _isSignedIn.value = true

            // Получаем access token для Calendar API
            val (token, tokenError) = credentialManagerService.getAccessToken(credential.id)
            if (token != null) {
                val successState = GoogleCalendarSyncState.Success("Signed in successfully as ${credential.displayName ?: credential.id}")
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
     * Повторная попытка входа (например, если пользователь не авторизован).
     */
    suspend fun retrySignIn(): GoogleCalendarSyncState {
        _syncState.value = GoogleCalendarSyncState.Syncing("Signing in...")

        val (credential, error) = credentialManagerService.getGoogleIdCredential(
            filterByAuthorizedAccounts = true
        )

        return if (credential != null) {
            encryptedPreferences.putUserEmail(credential.id)
            _userEmail.value = credential.id
            _isSignedIn.value = true

            val (token, tokenError) = credentialManagerService.getAccessToken(credential.id)
            if (token != null) {
                val successState = GoogleCalendarSyncState.Success("Signed in successfully")
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
            // Обновляем access token перед синхронизацией
            val email = _userEmail.value
            if (email != null) {
                credentialManagerService.refreshAccessToken(email)
            }

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
    fun signOut() {
        credentialManagerService.signOut()
        _isSignedIn.value = false
        _isSyncEnabled.value = false
        _syncState.value = GoogleCalendarSyncState.Idle
        _userEmail.value = null
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
