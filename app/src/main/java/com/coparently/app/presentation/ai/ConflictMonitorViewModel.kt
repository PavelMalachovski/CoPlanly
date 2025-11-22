package com.coparently.app.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.domain.model.ai.UpcomingConflict
import com.coparently.app.domain.usecase.ai.ProactiveConflictMonitorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for proactive conflict monitoring
 * Day 1 - Feature 1.2: Proactive conflict alerts
 */
@HiltViewModel
class ConflictMonitorViewModel @Inject constructor(
    private val proactiveConflictMonitorUseCase: ProactiveConflictMonitorUseCase
) : ViewModel() {

    private val _conflictsState = MutableStateFlow<ConflictsUiState>(ConflictsUiState.Loading)
    val conflictsState: StateFlow<ConflictsUiState> = _conflictsState.asStateFlow()

    init {
        checkForConflicts()
    }

    fun checkForConflicts() {
        viewModelScope.launch {
            _conflictsState.value = ConflictsUiState.Loading
            try {
                val conflicts = proactiveConflictMonitorUseCase.checkForUpcomingConflicts()
                _conflictsState.value = if (conflicts.isEmpty()) {
                    ConflictsUiState.NoConflicts
                } else {
                    ConflictsUiState.Success(conflicts)
                }
            } catch (e: Exception) {
                _conflictsState.value = ConflictsUiState.Error(
                    e.message ?: "Failed to check for conflicts"
                )
            }
        }
    }

    fun dismissConflict(conflict: UpcomingConflict) {
        viewModelScope.launch {
            val currentState = _conflictsState.value
            if (currentState is ConflictsUiState.Success) {
                val updatedConflicts = currentState.conflicts.filter { it != conflict }
                _conflictsState.value = if (updatedConflicts.isEmpty()) {
                    ConflictsUiState.NoConflicts
                } else {
                    ConflictsUiState.Success(updatedConflicts)
                }
            }
        }
    }
}

sealed class ConflictsUiState {
    data object Loading : ConflictsUiState()
    data object NoConflicts : ConflictsUiState()
    data class Success(val conflicts: List<UpcomingConflict>) : ConflictsUiState()
    data class Error(val message: String) : ConflictsUiState()
}
