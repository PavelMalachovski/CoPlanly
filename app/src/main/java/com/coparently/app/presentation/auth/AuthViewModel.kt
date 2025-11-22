package com.coparently.app.presentation.auth

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.google.CredentialManagerService
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val credentialManagerService: CredentialManagerService,
    private val analyticsManager: AnalyticsManager,
    private val crashlyticsManager: CrashlyticsManager,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // Callback to refresh auth state after successful authentication
    var onAuthStateChanged: (() -> Unit)? = null

    // Callback for navigation after successful authentication
    var onAuthSuccess: (() -> Unit)? = null

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun updateErrorMessage(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
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

    fun createGoogleSignInRequest(): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId("492948924829-m22iudtoaj437i518qm2p4do8t35vv1g.apps.googleusercontent.com") // Web client ID
            .setFilterByAuthorizedAccounts(false) // Allow new accounts
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    suspend fun signInWithGoogle(): Result<Unit> {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        return try {
            val credentialManager = CredentialManager.create(context)
            val request = createGoogleSignInRequest()

            val result = credentialManager.getCredential(context, request)
            handleCredentialResult(result)
        } catch (e: GetCredentialException) {
            val errorMessage = mapGoogleAuthError(e)
            crashlyticsManager.recordExceptionWithContext(
                e,
                mapOf("action" to "google_sign_in_credential_manager")
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = errorMessage
            )
            Result.failure(e)
        } catch (e: Exception) {
            crashlyticsManager.recordExceptionWithContext(
                e,
                mapOf("action" to "google_sign_in_unexpected_error")
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "An unexpected error occurred. Please try again."
            )
            Result.failure(e)
        }
    }

    private fun mapGoogleAuthError(e: GetCredentialException): String {
        return when (e) {
            is androidx.credentials.exceptions.GetCredentialCancellationException -> "Sign in cancelled"
            is androidx.credentials.exceptions.GetCredentialInterruptedException -> "Sign in interrupted. Please try again."
            is androidx.credentials.exceptions.NoCredentialException -> "No Google account found on this device."
            else -> "Google sign in failed. Please try again."
        }
    }

    private suspend fun handleCredentialResult(result: GetCredentialResponse): Result<Unit> {
        return try {
            val credential = result.credential

            when (credential.type) {
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val authResult = firebaseAuthService.signInWithGoogleIdToken(idToken)
                    authResult.fold(
                        onSuccess = {
                            analyticsManager.logLogin("google")
                            onAuthStateChanged?.invoke()
                            onAuthSuccess?.invoke()
                            Result.success(Unit)
                        },
                        onFailure = { error ->
                            crashlyticsManager.recordExceptionWithContext(
                                error,
                                mapOf("action" to "firebase_google_auth", "email" to googleIdTokenCredential.id)
                            )
                            Result.failure(error)
                        }
                    )
                }
                else -> {
                    Result.failure(Exception("Unexpected credential type: ${credential.type}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
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

