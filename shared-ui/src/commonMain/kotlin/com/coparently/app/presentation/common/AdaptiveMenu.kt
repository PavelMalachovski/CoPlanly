package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A row that offers a choice of one among a few [options]: the dialog the row opens where the shell
 * has no native controls (Android), the system's menu opened from the row where it has
 * ([NativeControls.MenuAnchor], a `UIMenu` on the iPhone).
 *
 * Without native controls it draws `row(open)`, and while open, `dialog(dismiss)` — exactly what
 * each site drew with its own flag before, so Android draws and reads it as before. The dialog
 * calls [onSelect] itself, with `dismiss`, as its picks did. Under native controls it draws
 * `row({})` under a menu anchor whose entries are [options], the [selected] one checked, each
 * calling [onSelect] with its value; no dialog is drawn.
 *
 * @param options Each option's value and label, in order
 * @param selected The value chosen now
 * @param onSelect Called with the value the parent picks in the menu
 * @param row The row, field or button that shows the value; `open` opens the dialog
 * @param dialog The dialog of options, shown while open; `dismiss` closes it
 */
@Composable
fun <T> AdaptiveOptionRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    row: @Composable (open: () -> Unit) -> Unit,
    dialog: @Composable (dismiss: () -> Unit) -> Unit
) {
    val native = nativeControls()
    if (native == null) {
        var open by remember { mutableStateOf(false) }
        row { open = true }
        if (open) dialog { open = false }
    } else {
        native.MenuAnchor(items = optionMenuItems(options, selected, onSelect), modifier = Modifier) { row {} }
    }
}

/**
 * The menu entries of a choice: one per option, in order, the [selected] one checked, each calling
 * [onSelect] with its value.
 */
fun <T> optionMenuItems(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit): List<BarMenuItem> =
    options.map { (value, label) ->
        BarMenuItem(label = label, sfSymbol = null, onClick = { onSelect(value) }, checked = value == selected)
    }
