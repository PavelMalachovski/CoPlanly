package com.coparently.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.presentation.theme.ParentPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The family's two parent colours, for `MainActivity` to provide as
 * [LocalParentPalette][com.coparently.app.presentation.theme.LocalParentPalette] (UX-15).
 *
 * Activity-scoped on purpose, and the only consumer of it is that one provider. Every
 * `ParentColors` call reads the palette from the composition, so a screen that never collected
 * its own ViewModel's `parents` — a form-only route, the custody conflict preview — still draws
 * the family's choice rather than the default pink and blue. Threading the palette through each
 * screen instead is what left the colour picker changing nothing: nineteen call sites, and not
 * one of them had been handed it.
 *
 * It adds no Firestore listener of its own. It subscribes to the same shared [ParentsSource]
 * upstream every ViewModel exposing `parents` already uses; the provider collects it with the
 * Activity's lifecycle, so it lets go while the app is in the background and `ParentsSource`'s
 * own `WhileSubscribed` can stop.
 */
@HiltViewModel
class ParentPaletteViewModel @Inject constructor(
    parentsSource: ParentsSource
) : ViewModel() {

    /**
     * The palette of the signed-in parent's family. [ParentPalette.Default] until the parents
     * have loaded, which is what the app drew before anybody could choose, so the first frame
     * shows the default rather than nothing.
     */
    val palette: StateFlow<ParentPalette> = parentsSource.observe()
        // A rename or a pairing transition that moves no colour maps to an equal palette, and a
        // StateFlow drops an equal value — so those do not repaint the app through the static
        // CompositionLocal.
        .map { it.palette }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ParentPalette.Default
        )

    private companion object {
        /** Keeps the upstream warm across a configuration change. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
