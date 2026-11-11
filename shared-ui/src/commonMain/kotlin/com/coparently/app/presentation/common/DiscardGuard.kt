package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import com.coparently.app.domain.family.FamilyMemberRef
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_discard
import com.coparently.app.resources.common_discard_changes_message
import com.coparently.app.resources.common_discard_changes_title
import com.coparently.app.resources.common_keep_editing
import org.jetbrains.compose.resources.stringResource

/**
 * Asks before a form holding unsaved edits is left (docs/AUDIT-2026-10-design.md D-11).
 *
 * Handles the system Back, gesture included, while [dirty], and returns what the form's own up
 * arrow calls, so the two ways out ask the same question. With nothing changed both leave at
 * once: a question on every Back would teach people to tap through it.
 *
 * The Back is a predictive one: while the gesture is dragged over unsaved edits, the form
 * shrinks a little under the finger ([DiscardGuard.backPreview]) — the gesture was seen, and
 * what it leads to is a question rather than the previous screen, which is why the form does not
 * slide away as a plain Back's does. Releasing asks; letting go early changes nothing.
 *
 * In a sheet ([LocalSheetGuard], the iPhone's forms) it also tells the sheet whether it holds
 * edits and hands it the question, so a swipe down over edits asks it too and leaves the sheet open
 * on "Keep editing". With no sheet, which is every screen on Android, nothing more happens.
 *
 * @param dirty Whether the form holds edits that leaving would lose.
 * @param onLeave Leaves the form, dropping them.
 * @return What the form's up arrow calls, carrying the gesture's preview for the form to wear.
 */
// Compose Multiplatform's back handler is experimental at 1.9.3; on Android it is androidx.activity's.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberDiscardGuard(dirty: Boolean, onLeave: () -> Unit): DiscardGuard {
    var asking by rememberSaveable { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(dirty)
    val currentOnLeave by rememberUpdatedState(onLeave)
    val progress = remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = dirty) { events ->
        try {
            events.collect { event -> progress.floatValue = event.progress }
            asking = true
        } finally {
            progress.floatValue = 0f
        }
    }

    val sheetGuard = LocalSheetGuard.current
    if (sheetGuard != null) {
        DisposableEffect(sheetGuard, dirty) {
            sheetGuard.report(dirty) { asking = true }
            onDispose { sheetGuard.report(false) {} }
        }
    }

    if (asking) {
        ConfirmationDialog(
            title = stringResource(Res.string.common_discard_changes_title),
            message = stringResource(Res.string.common_discard_changes_message),
            confirmText = stringResource(Res.string.common_discard),
            dismissText = stringResource(Res.string.common_keep_editing),
            onConfirm = {
                asking = false
                currentOnLeave()
            },
            onDismiss = { asking = false },
            isDestructive = true
        )
    }

    return remember {
        DiscardGuard(
            onUp = { if (currentDirty) asking = true else currentOnLeave() },
            progress = progress
        )
    }
}

/**
 * A form's way out while it may hold edits: call it from the up arrow, and put [backPreview] on
 * the form so a back gesture is seen before it asks.
 *
 * A function as well, so a form passes it wherever a `() -> Unit` goes.
 */
@Stable
class DiscardGuard internal constructor(
    private val onUp: () -> Unit,
    private val progress: FloatState
) : () -> Unit {

    /** Leaves the form, or asks first when it holds edits. */
    override fun invoke() = onUp()

    /** Shrinks the form by up to [BACK_PREVIEW_SHRINK] while a back gesture is dragged over edits. */
    val backPreview: Modifier = Modifier.graphicsLayer {
        val scale = 1f - progress.floatValue * BACK_PREVIEW_SHRINK
        scaleX = scale
        scaleY = scale
    }
}

/** How much of its size a form gives up at the end of a back gesture: Material's predictive-back shrink. */
private const val BACK_PREVIEW_SHRINK = 0.1f

/**
 * Saves a selection of children and pets as the stored strings [FamilyMemberRef] defines, so a
 * form's member chips survive rotation and process death with the rest of the form.
 */
val FamilyMemberRefListSaver: Saver<List<FamilyMemberRef>, ArrayList<String>> = Saver(
    save = { refs -> ArrayList(FamilyMemberRef.store(refs)) },
    restore = { stored -> FamilyMemberRef.parse(stored) }
)
