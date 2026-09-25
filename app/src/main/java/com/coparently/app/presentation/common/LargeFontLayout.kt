package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.coparently.app.presentation.theme.Spacing

/**
 * The font scale from which things that sit side by side at the default size go one above the
 * other — the value every `STACK_*_FONT_SCALE` of design refresh item 15 holds.
 *
 * At 150 % two buttons sharing a row got half the width each, and German broke their labels
 * inside a word ("hinzufüge|n", "aufnehme|n"); a chip beside a row's text squeezed the text into
 * a column one or two words wide. The UI tour's `light-de-150` variant is where they showed.
 */
const val STACK_CONTROLS_FONT_SCALE = 1.3f

/** Whether the reader's font scale is large enough that side-by-side content should stack. */
@Composable
fun stacksAtLargeFont(): Boolean = LocalDensity.current.fontScale >= STACK_CONTROLS_FONT_SCALE

/**
 * Controls that share one row at equal widths — or, from [STACK_CONTROLS_FONT_SCALE], stand one
 * above the other at full width, in the same order, so no label breaks inside a word.
 *
 * Each control receives the modifier that sizes it; it must apply it.
 *
 * @param controls the controls, in reading order
 * @param modifier modifier for the row or column
 * @param spacing gap between the controls
 */
@Composable
fun SideBySideOrStacked(
    controls: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.S
) {
    if (stacksAtLargeFont()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            controls.forEach { control -> control(Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            controls.forEach { control -> control(Modifier.weight(1f)) }
        }
    }
}
