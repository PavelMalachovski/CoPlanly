package com.coparently.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for authentication screen.
 * Manages login and registration state.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseAuthService: FirebaseAuthService,
    private val analyticsManager: AnalyticsManager,
    private val crashlyticsManager: CrashlyticsManager
) : ViewModel() {

    // Callback to refresh auth state after successful authentication
    var onAuthStateChanged: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun toggleSignInMode() {
        _uiState.value = _uiState.value.copy(
            isSignInMode = !_uiState.value.isSignInMode,
            errorMessage = null
        )
    }

    fun signIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all fields")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = firebaseAuthService.signInWithEmail(state.email, state.password)
            result.fold(
                onSuccess = {
                    analyticsManager.logLogin("email")
                    _uiState.value = state.copy(isLoading = false)
                    onAuthStateChanged?.invoke()
                    onSuccess()
                },
                onFailure = { error ->
                    crashlyticsManager.recordExceptionWithContext(
                        error,
                        mapOf("action" to "sign_in", "email" to state.email)
                    )
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Sign in failed"
                    )
                }
            )
        }
    }

    fun signUp(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please fill in all fields")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = firebaseAuthService.createAccountWithEmail(state.email, state.password)
            result.fold(
                onSuccess = {
                    analyticsManager.logSignUp("email")
                    _uiState.value = state.copy(isLoading = false)
                    onAuthStateChanged?.invoke()
                    onSuccess()
                },
                onFailure = { error ->
                    crashlyticsManager.recordExceptionWithContext(
                        error,
                        mapOf("action" to "sign_up", "email" to state.email)
                    )
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Sign up failed"
                    )
                }
            )
        }
    }
}

/**
 * UI state for authentication screen.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignInMode: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

