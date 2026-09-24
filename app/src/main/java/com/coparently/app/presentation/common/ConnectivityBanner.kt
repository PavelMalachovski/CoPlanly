package com.coparently.app.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Motion

/**
 * The one line the app shows while the phone is offline, above every screen
 * (docs/AUDIT-2026-10-design.md D-16).
 *
 * It says what still works rather than what failed — changes are kept and will sync — because
 * that is true of every write in this app, and a parent about to record an expense needs to know
 * it will not be lost. Neutral colours, not the error role: being offline is not a mistake the
 * parent made, and red over every screen would read as one.
 *
 * It sits in the root Scaffold's top bar slot and pads itself below the status bar, so while it
 * shows the screens below take no status-bar inset of their own (the NavHost consumes what the
 * Scaffold applies); while it does not, the slot is empty and nothing moves.
 *
 * A polite live region, so TalkBack announces it when it appears without interrupting.
 *
 * @param offline Whether the phone has been without validated internet past the grace period
 * @param modifier Modifier for the banner
 */
@Composable
fun ConnectivityBanner(offline: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = offline,
        modifier = modifier,
        enter = expandVertically(tween(Motion.MEDIUM_MS)) + fadeIn(tween(Motion.MEDIUM_MS)),
        exit = shrinkVertically(tween(Motion.MEDIUM_MS)) + fadeOut(tween(Motion.SHORT_MS))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(IconSizes.Small)
                )
                Text(
                    text = stringResource(R.string.connectivity_offline),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
