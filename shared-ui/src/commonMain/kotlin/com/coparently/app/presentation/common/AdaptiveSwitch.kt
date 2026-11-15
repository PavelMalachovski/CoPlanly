package com.coparently.app.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * An on/off switch: Material's `Switch` where the shell has no native controls (Android), the
 * phone's own where it has ([NativeControls.Switch], `UISwitch` on the iPhone).
 *
 * Without native controls it is exactly the Material call each screen made before, with [checked],
 * [onCheckedChange], [modifier], [enabled] and [colors] passed through, so Android draws and reads
 * it as before; [accessibilityLabel] is not used there, because each screen already names its
 * switch through [modifier]'s semantics.
 *
 * @param checked Whether the switch is on
 * @param onCheckedChange Called with the new value when the parent flips it, or null
 * @param modifier Modifier applied to the switch
 * @param enabled Whether the parent can flip it now
 * @param accessibilityLabel What the switch turns on and off, for VoiceOver on the iPhone
 * @param background The colour of what the control sits on, behind the native control on the iPhone
 *   (Compose clears its pixels under it): a card's `surfaceContainer` by default; a caller on the
 *   screen's ground passes `colorScheme.background`
 * @param colors Material's colours, used only where Material draws the switch; the iPhone's is the
 *   system's green
 */
@Composable
fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
    colors: SwitchColors = SwitchDefaults.colors(),
    background: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    val native = nativeControls()
    if (native == null) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors
        )
    } else {
        native.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            accessibilityLabel = accessibilityLabel,
            background = background,
            modifier = modifier
        )
    }
}
