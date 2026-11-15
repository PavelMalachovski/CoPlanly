package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A segmented choice of one among [labels]: the site's own Material row where the shell has no
 * native controls (Android), the phone's where it has ([NativeControls.Segmented],
 * `UISegmentedControl` on the iPhone).
 *
 * Without native controls it composes [material], the `SingleChoiceSegmentedButtonRow` the site drew
 * before, unchanged, so Android's colours, icons, semantics and sizes are untouched; [labels],
 * [selectedIndex], [onSelect], [enabled] and [modifier] are not used there, because the row carries
 * its own. [labels] must be the strings the row's segments show.
 *
 * @param labels The segments' titles, in order
 * @param selectedIndex The chosen segment, or -1 when none is chosen
 * @param onSelect Called with the index of the segment the parent chose
 * @param modifier Modifier applied to the native control
 * @param enabled Whether the parent can choose now (the native control; the row has its own)
 * @param material The site's Material row, drawn where there are no native controls
 */
@Composable
fun AdaptiveSegmented(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    material: @Composable () -> Unit
) {
    val native = nativeControls()
    if (native == null) {
        material()
    } else {
        native.Segmented(
            labels = labels,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            enabled = enabled,
            modifier = modifier
        )
    }
}
