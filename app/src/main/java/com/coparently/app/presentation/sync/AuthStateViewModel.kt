package com.coparently.app.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing authentication state across the app.
 * Provides authentication state to determine navigation routing.
 */
@HiltViewModel
class AuthStateViewModel @Inject constructor(
    private val firebaseAuthService: FirebaseAuthService
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow<Boolean?>(null)
    val isAuthenticated: StateFlow<Boolean?> = _isAuthenticated.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        checkAuthState()
    }

    /**
     * Checks the current authentication state.
     */
    private fun checkAuthState() {
        viewModelScope.launch {
            _isLoading.value = true

            // Wait for authentication to be ready, then check state
            val user = firebaseAuthService.waitForAuthReady()
            _isAuthenticated.value = user != null
            _isLoading.value = false
        }
    }

    /**
     * Refreshes the authentication state.
     * Call this after login/logout operations.
     */
    fun refreshAuthState() {
        checkAuthState()
    }
}
