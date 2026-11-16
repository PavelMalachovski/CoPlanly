package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.coparently.app.presentation.theme.Spacing

/**
 * A row that offers a choice of one among a few [options]: the dialog the row opens where the shell
 * has no native controls (Android), the system's pop-up button at the row's end where it has
 * ([NativeControls.MenuButton], a `UIButton` showing a `UIMenu` on the iPhone).
 *
 * Without native controls it draws `row(open)`, and while open, `dialog(dismiss)` — exactly what
 * each site drew with its own flag before, so Android draws and reads it as before. The dialog
 * calls [onSelect] itself, with `dismiss`, as its picks did.
 *
 * Under native controls it draws a [SectionRow] with [title] and [icon] that is not clickable, and
 * at its end the pop-up button, which shows the [selected] option's label (nothing when no option
 * matches; it still opens) and the menu of [options], the selected one checked, each calling
 * [onSelect] with its value. VoiceOver reads the button as "[title], value". No dialog is drawn,
 * and nothing is laid over the row.
 *
 * @param title What the row chooses ("Repeat")
 * @param options Each option's value and label, in order
 * @param selected The value chosen now
 * @param onSelect Called with the value the parent picks in the menu
 * @param icon The row's leading icon under native controls, as the site's row draws it
 * @param background What the row sits on, behind the native button: a `SectionGroup`'s
 *   `surfaceContainer` by default
 * @param row The row that shows the value on Android; `open` opens the dialog
 * @param dialog The dialog of options, shown while open; `dismiss` closes it
 */
@Composable
@Suppress("LongParameterList") // the choice, its row's two looks and the colour under the button
fun <T> AdaptiveOptionRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    icon: ImageVector? = null,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
    row: @Composable (open: () -> Unit) -> Unit,
    dialog: @Composable (dismiss: () -> Unit) -> Unit
) {
    val native = nativeControls()
    if (native == null) {
        var open by remember { mutableStateOf(false) }
        row { open = true }
        if (open) dialog { open = false }
    } else {
        SectionRow(
            title = title,
            icon = icon,
            trailing = { OptionMenuButton(native, title, options, selected, onSelect, background) }
        )
    }
}

/**
 * A form's choice as the iPhone's own forms show one: [label] in Compose, not clickable, and at the
 * line's end the system's pop-up button showing the [selected] option ([OptionMenuButton]). What a
 * dropdown field (blood type, expense category, pet species) draws under native controls.
 *
 * @param native The shell's native controls
 * @param label The field's label
 * @param options Each option's value and label, in order
 * @param selected The value chosen now
 * @param onSelect Called with the value the parent picks
 * @param background What the line sits on, behind the native button
 * @param modifier Modifier applied to the line; it fills the width
 */
@Composable
@Suppress("LongParameterList") // the controls, the field's label, the choice and the colour under it
fun <T> MenuFieldRow(
    native: NativeControls,
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    background: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OptionMenuButton(native, label, options, selected, onSelect, background)
    }
}

/**
 * The system's pop-up button of a choice: the [selected] option's label (nothing when none
 * matches) with the up-and-down chevron, in the secondary label grey, opening the menu of
 * [options] with the selected one checked. VoiceOver reads "[label], value".
 */
@Composable
@Suppress("LongParameterList") // the controls, the label, the choice and the colour under it
private fun <T> OptionMenuButton(
    native: NativeControls,
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    background: Color
) {
    val value = options.firstOrNull { it.first == selected }?.second
    native.MenuButton(
        items = optionMenuItems(options, selected, onSelect),
        title = value,
        sfSymbol = null,
        accessibilityLabel = value?.let { "$label, $it" } ?: label,
        tint = Color.Unspecified,
        background = background,
        modifier = Modifier
    )
}

/**
 * The menu entries of a choice: one per option, in order, the [selected] one checked, each calling
 * [onSelect] with its value.
 */
fun <T> optionMenuItems(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit): List<BarMenuItem> =
    options.map { (value, label) ->
        BarMenuItem(label = label, sfSymbol = null, onClick = { onSelect(value) }, checked = value == selected)
    }
