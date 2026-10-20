package com.coparently.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.family.FamilyView
import com.coparently.app.data.family.FamilyViewSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The [FamilyView] `MainActivity` provides as [LocalFamilyView]. Activity-scoped like
 * `ParentPaletteViewModel`, and adds nothing but a Room observation of the signed-in parent's row.
 */
@HiltViewModel
class FamilyViewViewModel @Inject constructor(source: FamilyViewSource) : ViewModel() {

    /** The view, following every switch. */
    val view: StateFlow<FamilyView> = source.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FamilyView.EVERYTHING)

    private companion object {
        /** Keeps the upstream warm across a configuration change. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
