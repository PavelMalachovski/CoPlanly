package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One action in a screen's top bar: an icon button on Android, a bar button item on the iPhone.
 *
 * @property label What the action does, already resolved in the reader's language; the icon's
 *   content description on Android and the item's accessibility label on the iPhone
 * @property icon The Material icon Android draws
 * @property sfSymbol The SF Symbol the iPhone draws, e.g. `gearshape`, `plus`, `xmark`
 * @property onClick The action
 * @property enabled Whether the action can be taken now
 * @property primary A form's primary action (Save). Android keeps a form's `StickyActionBar` and
 *   passes no primary action to the bar; the iPhone's host draws it as the bar's prominent item
 * @property dot Whether something waits behind the action: a dot, never a count (design item 13)
 */
data class BarAction(
    val label: String,
    val icon: ImageVector,
    val sfSymbol: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val primary: Boolean = false,
    val dot: Boolean = false
)

/** What sits at the start of a screen's top bar. */
sealed interface BarLeading {

    /** Nothing: a tab's root screen. */
    data object None : BarLeading

    /**
     * The up arrow of a detail screen. The iPhone draws its system back button.
     *
     * @property label The arrow's content description, already resolved
     * @property onClick Where it goes, usually up (or a form's discard guard)
     * @property enabled Whether it can be tapped now; a form disables it while it saves
     */
    data class Back(val label: String, val onClick: () -> Unit, val enabled: Boolean = true) : BarLeading

    /**
     * A cross that closes a screen opened over another (`xmark` on the iPhone).
     *
     * @property label The cross's content description, already resolved
     * @property onClick What closing does
     * @property enabled Whether it can be tapped now
     */
    data class Close(val label: String, val onClick: () -> Unit, val enabled: Boolean = true) : BarLeading
}

/**
 * A screen's top bar described as data, so that each shell draws it its own way (MON-24 phase 3):
 * Android as today's Material `TopAppBar` ([AppTopBar]), the iPhone as UIKit's navigation bar
 * through a [BarHost].
 *
 * @property title The screen's title, already resolved
 * @property largeTitle Whether the iPhone shows a large title (a tab's root); Android draws the
 *   one small bar either way
 * @property subtitle A line under the title, such as Home's date
 * @property leading What sits at the bar's start
 * @property actions The bar's actions, in the order they are drawn
 * @property trailingContent A composable drawn before [actions]: the family switcher chip
 */
data class ScreenBar(
    val title: String,
    val largeTitle: Boolean = false,
    val subtitle: String? = null,
    val leading: BarLeading = BarLeading.None,
    val actions: List<BarAction> = emptyList(),
    val trailingContent: (@Composable () -> Unit)? = null
)

/** A shell that draws screens' bars itself: the iPhone's navigation controller. */
fun interface BarHost {

    /** Shows [bar] as the bar of the screen now composed. Called after every composition. */
    fun show(bar: ScreenBar)
}

/**
 * The shell that draws the bar, when it is not this composition.
 *
 * The deliberate exception to the platform locals' rule that a local read outside its provider
 * throws: here `null` is a real answer, "draw the bar here", which is Android's, and the iPhone's
 * shell provides a host. A throwing default would make every Android screen and every test that
 * composes one provide a value that means nothing.
 */
val LocalBarHost: ProvidableCompositionLocal<BarHost?> = staticCompositionLocalOf { null }

/**
 * A screen's top bar. With no [LocalBarHost] it draws the Material `TopAppBar` the screens drew
 * themselves before (title, the subtitle line under it, the leading arrow or cross, the trailing
 * content and then the actions); with one it hands [bar] to the host and draws nothing.
 *
 * @param bar What the bar shows
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(bar: ScreenBar) {
    val host = LocalBarHost.current
    if (host != null) {
        SideEffect { host.show(bar) }
        return
    }
    TopAppBar(
        title = { BarTitle(bar) },
        navigationIcon = { BarLeadingButton(bar.leading) },
        actions = {
            bar.trailingContent?.invoke()
            bar.actions.forEach { BarActionButton(it) }
        }
    )
}

@Composable
private fun BarTitle(bar: ScreenBar) {
    val subtitle = bar.subtitle
    if (subtitle == null) {
        Text(bar.title)
    } else {
        Column {
            Text(text = bar.title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BarLeadingButton(leading: BarLeading) {
    when (leading) {
        BarLeading.None -> Unit
        is BarLeading.Back -> IconButton(onClick = leading.onClick, enabled = leading.enabled) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = leading.label)
        }
        is BarLeading.Close -> IconButton(onClick = leading.onClick, enabled = leading.enabled) {
            Icon(imageVector = Icons.Default.Close, contentDescription = leading.label)
        }
    }
}

@Composable
private fun BarActionButton(action: BarAction) {
    IconButton(onClick = action.onClick, enabled = action.enabled) {
        if (action.dot) {
            BadgedBox(badge = { Badge() }) {
                Icon(imageVector = action.icon, contentDescription = action.label)
            }
        } else {
            Icon(imageVector = action.icon, contentDescription = action.label)
        }
    }
}
