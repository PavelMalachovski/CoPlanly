package com.coparently.app.presentation.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.family.FamilyMemberRef

/**
 * Asks before a form holding unsaved edits is left (docs/AUDIT-2026-10-design.md D-11).
 *
 * Handles the system Back, gesture included, while [dirty], and returns the function the form's
 * own up arrow calls, so the two ways out ask the same question. With nothing changed both leave
 * at once: a question on every Back would teach people to tap through it.
 *
 * @param dirty Whether the form holds edits that leaving would lose.
 * @param onLeave Leaves the form, dropping them.
 * @return What the form's up arrow calls.
 */
@Composable
fun rememberDiscardGuard(dirty: Boolean, onLeave: () -> Unit): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(dirty)
    val currentOnLeave by rememberUpdatedState(onLeave)

    BackHandler(enabled = dirty) { asking = true }

    if (asking) {
        ConfirmationDialog(
            title = stringResource(R.string.common_discard_changes_title),
            message = stringResource(R.string.common_discard_changes_message),
            confirmText = stringResource(R.string.common_discard),
            dismissText = stringResource(R.string.common_keep_editing),
            onConfirm = {
                asking = false
                currentOnLeave()
            },
            onDismiss = { asking = false },
            isDestructive = true
        )
    }

    return remember {
        {
            if (currentDirty) asking = true else currentOnLeave()
        }
    }
}

/**
 * Saves a selection of children and pets as the stored strings [FamilyMemberRef] defines, so a
 * form's member chips survive rotation and process death with the rest of the form.
 */
val FamilyMemberRefListSaver: Saver<List<FamilyMemberRef>, ArrayList<String>> = Saver(
    save = { refs -> ArrayList(FamilyMemberRef.store(refs)) },
    restore = { stored -> FamilyMemberRef.parse(stored) }
)
