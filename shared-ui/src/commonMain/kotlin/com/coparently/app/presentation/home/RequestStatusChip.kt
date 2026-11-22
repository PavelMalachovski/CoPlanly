package com.coparently.app.presentation.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.resources.Res
import com.coparently.app.resources.home_waiting_for_coparent
import com.coparently.app.resources.home_waiting_for_you
import org.jetbrains.compose.resources.stringResource

/**
 * A request's status on Home in the spec's colour language (November 2026): waiting for you is a
 * filled primary chip, waiting for the co-parent an outlined one. A word always carries the status;
 * the colour repeats it, and a parent's hue never stands for one.
 *
 * Public, not internal, so the app's screenshot tests can render it on its own.
 *
 * @param waitingForYou Whether the request waits on this parent's answer.
 * @param coParentName Who it waits on otherwise, by name.
 */
@Composable
fun RequestStatusChip(waitingForYou: Boolean, coParentName: String = "") {
    if (waitingForYou) {
        PillChip(
            label = stringResource(Res.string.home_waiting_for_you),
            container = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        PillChip(
            label = stringResource(Res.string.home_waiting_for_coparent, coParentName),
            container = null,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
