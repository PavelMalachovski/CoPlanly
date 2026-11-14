package com.coparently.app.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable

/**
 * The top bar of a screen pushed onto a stack (MON-24 phase 3, part B): its title, the up arrow
 * (the system back button on the iPhone) and its actions, if any. On Android it draws the
 * Material `TopAppBar` such a screen drew itself before; on the iPhone the shell's navigation bar
 * shows it.
 *
 * @param title The screen's title, already resolved
 * @param backLabel The up arrow's content description, already resolved
 * @param onBack Where the arrow goes, usually up
 * @param actions The bar's actions, in the order they are drawn
 * @param save The screen's primary action, where it has one pinned under it (a `StickyActionBar`,
 *   which draws nothing under a host): on the iPhone the bar's primary action after [actions], by
 *   the form rule, with the up arrow kept; Android passes none to its bar and keeps the button
 */
@Composable
fun PushedTopBar(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    actions: List<BarAction> = emptyList(),
    save: FormSave? = null
) {
    val primary = save?.takeIf { shellDrawsBars() }?.let(::saveBarAction)
    AppTopBar(
        ScreenBar(
            title = title,
            leading = BarLeading.Back(label = backLabel, onClick = onBack),
            actions = actions + listOfNotNull(primary)
        )
    )
}

/**
 * A screen's pinned primary action as the iPhone's bar carries it: its label, `checkmark` (the
 * host draws a primary action as its word) and enabled exactly when the button could be pressed.
 *
 * @param save The action, as the screen's own button holds it
 */
fun saveBarAction(save: FormSave): BarAction = BarAction(
    label = save.label,
    icon = Icons.Default.Check,
    sfSymbol = "checkmark",
    onClick = save.onClick,
    enabled = save.enabled,
    primary = true
)

/**
 * The "+" a list screen's floating button becomes in the iPhone's bar: `plus`, labelled as the
 * floating button is.
 *
 * @param label What the floating button says or is described as, already resolved
 * @param onClick What it does
 */
fun addBarAction(label: String, onClick: () -> Unit): BarAction =
    BarAction(label = label, icon = Icons.Default.Add, sfSymbol = "plus", onClick = onClick)
