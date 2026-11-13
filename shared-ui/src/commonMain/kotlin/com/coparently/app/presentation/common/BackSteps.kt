package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * One inner step of a screen that Back undoes before it leaves the screen (MON-24 phase 3, part B):
 * an open section, a search, a wizard's later step, a selection. Both properties are snapshot
 * state, so whoever reads [BackSteps.hasEnabledStep] in a snapshot observer follows them.
 *
 * @param enabled Whether the step is there to undo now.
 * @param onBack Undoes the step.
 */
class BackStep(enabled: Boolean, onBack: () -> Unit) {
    /** Whether the step is there to undo now. */
    var enabled: Boolean by mutableStateOf(enabled)

    /** Undoes the step. */
    var onBack: () -> Unit by mutableStateOf(onBack)
}

/**
 * The inner steps of one hosted screen, which the iPhone's shell asks before its own back pops the
 * screen: the navigation bar's back button runs [handle] first, and the edge swipe is refused while
 * [hasEnabledStep], since a swipe cannot be half-run against a step. Android provides none: there
 * the system back reaches each step's `BackHandler` directly.
 */
class BackSteps {
    private val steps = mutableStateListOf<BackStep>()

    /** Registers [step]; the answer unregisters it. */
    fun register(step: BackStep): () -> Unit {
        steps.add(step)
        return { steps.remove(step) }
    }

    /** Whether any registered step is enabled. Snapshot state. */
    val hasEnabledStep: Boolean
        get() = steps.any { it.enabled }

    /**
     * Runs the most recently registered enabled step and answers true, or answers false when no
     * step is enabled (the back is then the shell's to take).
     */
    fun handle(): Boolean {
        val step = steps.lastOrNull { it.enabled } ?: return false
        step.onBack()
        return true
    }
}

/** The hosted screen's inner steps: provided by the iPhone's shell alone, null on Android. */
val LocalBackSteps: ProvidableCompositionLocal<BackSteps?> = staticCompositionLocalOf { null }

/**
 * An inner step of the screen that Back undoes while [enabled]: Compose Multiplatform's
 * `BackHandler` (androidx.activity's on Android, so Android is unchanged), and, where the shell
 * provides [LocalBackSteps], a step its back button and edge swipe ask first.
 *
 * @param enabled Whether the step is there to undo now.
 * @param onBack Undoes the step.
 */
// `ExperimentalComposeUiApi`: Compose Multiplatform's common `BackHandler`.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreenBackStep(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
    val steps = LocalBackSteps.current
    if (steps != null) {
        val step = remember(steps) { BackStep(enabled, onBack) }
        SideEffect {
            step.enabled = enabled
            step.onBack = onBack
        }
        DisposableEffect(steps, step) {
            val unregister = steps.register(step)
            onDispose { unregister() }
        }
    }
}
