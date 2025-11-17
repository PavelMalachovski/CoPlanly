package com.coparently.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.remote.firebase.FcmService
import com.coparently.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Settings screen.
 * Manages settings state and user preferences.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val fcmService: FcmService,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * Loads current settings state.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                // Get current user
                val currentUser = userRepository.getCurrentUser()

                // Get FCM token status
                val fcmToken = fcmService.getCurrentToken()

                _uiState.value = _uiState.value.copy(
                    notificationsEnabled = fcmToken != null,
                    userEmail = currentUser?.email,
                    userName = currentUser?.name,
                    partnerId = currentUser?.partnerId,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Toggles push notifications on/off.
     */
    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                if (enabled) {
                    // Get and register FCM token
                    val token = fcmService.getCurrentToken()
                    if (token != null) {
                        fcmService.updateUserToken(token).getOrThrow()
                    }
                } else {
                    // Optionally clear token or unsubscribe from topics
                    // For now, just update UI state
                }

                _uiState.value = _uiState.value.copy(
                    notificationsEnabled = enabled,
                    isLoading = false,
                    successMessage = if (enabled) "Notifications enabled" else "Notifications disabled"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to toggle notifications: ${e.message}"
                )
            }
        }
    }

    /**
     * Requests notification permission and registers FCM token.
     */
    fun requestNotificationPermission() {
        viewModelScope.launch {
            try {
                val token = fcmService.getCurrentToken()
                if (token != null) {
                    fcmService.updateUserToken(token).getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        notificationsEnabled = true,
                        successMessage = "Notifications enabled"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to get notification token"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to enable notifications: ${e.message}"
                )
            }
        }
    }

    /**
     * Clears success/error messages.
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }
}

/**
 * UI state for Settings screen.
 *
 * @property notificationsEnabled Whether push notifications are enabled
 * @property userEmail Current user's email
 * @property userName Current user's name
 * @property partnerId Partner's Firebase UID if paired
 * @property isLoading Loading state
 * @property successMessage Success message to display
 * @property errorMessage Error message to display
 */
data class SettingsUiState(
    val notificationsEnabled: Boolean = false,
    val userEmail: String? = null,
    val userName: String? = null,
    val partnerId: String? = null,
    val isLoading: Boolean = true,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

