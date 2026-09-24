package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The primary action pinned under a form — Save, nearly always (docs/AUDIT-2026-10-design.md
 * D-15). Put it in the Scaffold's `bottomBar` slot.
 *
 * One anatomy for what four screens drew four ways (the event form, custody setup, the journal
 * editor, the profile): three different paddings, a shadow on three of them and not the fourth,
 * and a fixed 52 dp height that clipped the label at 200 % text. This one is a `surface` bar with
 * a shadow, 16 dp around a full-width button that is **at least** 52 dp tall — `heightIn`, so the
 * label grows instead of being cut — and a spinner in the label's place while [busy].
 *
 * It needs no keyboard handling of its own: the root NavHost resizes every screen above the
 * keyboard (D-9), so the bar rises with it.
 *
 * @param label What the button does, e.g. "Save event"
 * @param onClick The action
 * @param modifier Modifier for the bar
 * @param enabled Whether the action is available; the button is also disabled while [busy]
 * @param busy Shows a spinner instead of the label, and disables the button, while the action runs
 * @param notice An optional line above the button — why it is disabled, typically
 */
@Composable
@Suppress("LongParameterList") // one bar anatomy: each parameter is a thing it shows or does
fun StickyActionBar(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    notice: (@Composable () -> Unit)? = null
) {
    Surface(modifier = modifier, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            notice?.invoke()
            Button(
                onClick = onClick,
                enabled = enabled && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_HEIGHT)
            ) {
                if (busy) {
                    // The button's own content colour, which a disabled button mutes: the spinner
                    // used to be onPrimary, white on the disabled grey container in light theme.
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = LocalContentColor.current,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = label, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** The button's minimum height: roomier than M3's 40 dp for the one action a form exists for. */
private val MIN_HEIGHT = 52.dp
