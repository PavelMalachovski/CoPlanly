package com.coparently.app.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import com.coparently.app.resources.Res
import com.coparently.app.resources.form_close
import org.jetbrains.compose.resources.stringResource

/**
 * A form's top bar, by the form rule of MON-24 phase 3, part B.
 *
 * With no [LocalBarHost] (Android) it is the Material bar the form drew before: [title] (or
 * [androidTitle] where the form styled it), the up arrow ([backLabel], [onLeave], [leaveEnabled])
 * and [androidActions] in the actions slot, drawn as the form drew them (an error-tinted delete, a
 * spinner while deleting). The form's Save stays its own button under the form.
 *
 * Where a shell draws the bars (the iPhone, whose form is a sheet) it is a cross at the start that
 * leaves the same way, [shellActions], and [save] as the bar's primary action at the end; the
 * form's `StickyActionBar` draws nothing there, and a form whose Save is a button at its end hides
 * that button ([shellDrawsBars]).
 *
 * @param title The form's title, already resolved
 * @param backLabel The up arrow's content description on Android
 * @param onLeave What leaving does: the form's discard guard
 * @param save The form's Save, as the iPhone's bar carries it
 * @param leaveEnabled Whether the arrow and the cross can be tapped now
 * @param shellActions The form's other actions, as the iPhone's bar carries them before Save
 * @param androidActions The form's other actions as Android draws them in its bar
 * @param androidTitle The title as Android draws it, where it is not plain text
 */
@Composable
@Suppress("LongParameterList") // one bar for two shells: each parameter is a thing one of them draws
fun FormTopBar(
    title: String,
    backLabel: String,
    onLeave: () -> Unit,
    save: FormSave,
    leaveEnabled: Boolean = true,
    shellActions: List<BarAction> = emptyList(),
    androidActions: (@Composable () -> Unit)? = null,
    androidTitle: (@Composable () -> Unit)? = null
) {
    val bar = if (shellDrawsBars()) {
        ScreenBar(
            title = title,
            leading = BarLeading.Close(
                label = stringResource(Res.string.form_close),
                onClick = onLeave,
                enabled = leaveEnabled
            ),
            actions = shellActions + BarAction(
                label = save.label,
                icon = Icons.Default.Check,
                sfSymbol = "checkmark",
                onClick = save.onClick,
                enabled = save.enabled,
                primary = true
            )
        )
    } else {
        ScreenBar(
            title = title,
            leading = BarLeading.Back(label = backLabel, onClick = onLeave, enabled = leaveEnabled),
            trailingContent = androidActions,
            titleContent = androidTitle
        )
    }
    AppTopBar(bar)
}
