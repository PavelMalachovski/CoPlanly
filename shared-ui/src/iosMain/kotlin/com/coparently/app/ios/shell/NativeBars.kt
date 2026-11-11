package com.coparently.app.ios.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.ComposeUIViewController
import com.coparently.app.presentation.common.BarAction
import com.coparently.app.presentation.common.BarHost
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.ScreenBar
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIViewController
import platform.UIKit.accessibilityLabel
import platform.UIKit.beginAppearanceTransition
import platform.UIKit.endAppearanceTransition
import platform.UIKit.navigationItem
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The navigation bar of one hosted screen, drawn by UIKit from the screen's [ScreenBar] (MON-24
 * phase 3, spec §5.3): the title (large on a tab's first screen and on Settings), the system back
 * button or a close button, the actions as bar buttons with SF Symbols, and the family switcher chip
 * as a bar button whose custom view is a Compose host of its own.
 *
 * Idempotent: a bar whose visible part is the one already drawn changes no item, so a screen that
 * composes again (every time it re-enters the window) does not rebuild or re-animate its buttons.
 * The items call the latest bar's lambdas, and the chip reads the latest bar from snapshot state,
 * so both follow the screen's newest composition. Each screen's bar lives on its own view
 * controller's `navigationItem`, so a popped screen's bar leaves with it.
 *
 * @property largeTitle Whether this screen shows a large title whatever its bar says (Settings).
 * @property sheetRoot Whether the screen is a sheet's first screen, whose back is a close button.
 * @property chipFrame Wraps the chip in the providers the screen is composed in.
 */
internal class NativeBars(
    private val largeTitle: Boolean,
    private val sheetRoot: Boolean,
    private val chipFrame: @Composable (content: @Composable () -> Unit) -> Unit
) : BarHost {

    /** The screen's view controller, whose `navigationItem` is drawn. Set once, before it loads. */
    lateinit var viewController: UIViewController

    /** The bar the screen handed over last: what the items and the chip run. Snapshot state. */
    private var current: ScreenBar? by mutableStateOf(null)

    /** The visible part of the bar drawn now; a bar with the same one draws nothing new. */
    private var drawn: VisibleBar? = null

    private var chip: UIViewController? = null
    private var chipWidth: NSLayoutConstraint? = null
    private var chipHeight: NSLayoutConstraint? = null

    override fun show(bar: ScreenBar) {
        current = bar
        val visible = VisibleBar.of(bar)
        if (visible == drawn) return
        drawn = visible
        draw(bar)
    }

    /** Whether the screen is on screen now, as its view controller last appeared or disappeared. */
    private var screenVisible = false

    /**
     * The screen appeared or disappeared: the chip's host is told the same. It is no child of the
     * screen (UIKit refuses a child whose view sits in the navigation bar, outside its parent's
     * view), so its appearance is forwarded by hand, which is what starts and ends its composition.
     */
    fun screenAppeared(visible: Boolean) {
        screenVisible = visible
        chip?.let { forwardAppearance(it, visible) }
    }

    /** Lets go of the chip's host: the screen has left its stack. */
    fun release() {
        chip = null
    }

    private fun forwardAppearance(host: UIViewController, visible: Boolean) {
        host.beginAppearanceTransition(visible, animated = false)
        host.endAppearanceTransition()
    }

    // `NSSelectorFromString` is Kotlin/Native's C interop.
    @OptIn(ExperimentalForeignApi::class)
    private fun draw(bar: ScreenBar) {
        val item = viewController.navigationItem
        item.title = bar.title
        item.largeTitleDisplayMode = if (bar.largeTitle || largeTitle) {
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeAlways
        } else {
            UINavigationItemLargeTitleDisplayMode.UINavigationItemLargeTitleDisplayModeNever
        }
        // `subtitle` is iOS 26's; on an older iPhone the line is left out.
        if (item.respondsToSelector(NSSelectorFromString("setSubtitle:"))) item.subtitle = bar.subtitle
        drawLeading(bar.leading)
        item.rightBarButtonItems = buildList {
            // UIKit draws the first right item at the edge: the last action, as Android does.
            bar.actions.indices.reversed().forEach { add(actionItem(it, bar.actions[it])) }
            if (bar.trailingContent != null) add(chipItem())
        }
    }

    private fun drawLeading(leading: BarLeading) {
        val item = viewController.navigationItem
        when (leading) {
            BarLeading.None -> {
                item.leftBarButtonItem = null
                item.hidesBackButton = false
            }
            is BarLeading.Back -> if (sheetRoot) {
                // A sheet has no screen to go back to: its way out is the close button.
                item.leftBarButtonItem = closeItem(leading.label, leading.enabled)
            } else {
                item.leftBarButtonItem = null
                item.hidesBackButton = !leading.enabled
            }
            is BarLeading.Close -> item.leftBarButtonItem = closeItem(leading.label, leading.enabled)
        }
    }

    private fun closeItem(label: String, enabled: Boolean): UIBarButtonItem {
        val action = UIAction.actionWithTitle(title = label, image = null, identifier = null) { _ -> clickLeading() }
        return UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemClose,
            primaryAction = action
        ).apply {
            setEnabled(enabled)
            accessibilityLabel = label
        }
    }

    private fun clickLeading() {
        when (val leading = current?.leading) {
            is BarLeading.Back -> leading.onClick()
            is BarLeading.Close -> leading.onClick()
            BarLeading.None, null -> Unit
        }
    }

    private fun actionItem(index: Int, action: BarAction): UIBarButtonItem {
        val uiAction = UIAction.actionWithTitle(
            title = action.label,
            image = SfSymbols.image(action.sfSymbol),
            identifier = null
        ) { _ -> current?.actions?.getOrNull(index)?.onClick?.invoke() }
        return UIBarButtonItem(primaryAction = uiAction).apply {
            setEnabled(action.enabled)
            accessibilityLabel = action.label
            style = if (action.primary) {
                UIBarButtonItemStyle.UIBarButtonItemStyleDone
            } else {
                UIBarButtonItemStyle.UIBarButtonItemStylePlain
            }
        }
    }

    /**
     * The family switcher chip: a bar button whose custom view is a Compose host, a child of the
     * screen's view controller, drawing the latest bar's trailing content. Its size follows what
     * the chip draws, which is nothing with one family.
     */
    // A transparent Compose host (`opaque`) is still experimental in Compose Multiplatform 1.9.3.
    @OptIn(ExperimentalComposeUiApi::class)
    private fun chipItem(): UIBarButtonItem {
        val host = chip ?: ComposeUIViewController(configure = { opaque = false }) {
            chipFrame {
                val density = LocalDensity.current
                Box(
                    Modifier
                        .wrapContentSize(unbounded = true)
                        .onSizeChanged { size ->
                            with(density) { resizeChip(size.width.toDp().value, size.height.toDp().value) }
                        }
                ) {
                    current?.trailingContent?.invoke()
                }
            }
        }.also { created ->
            created.view.backgroundColor = UIColor.clearColor
            created.view.translatesAutoresizingMaskIntoConstraints = false
            chipWidth = created.view.widthAnchor.constraintEqualToConstant(MIN_CHIP_SIDE).apply { active = true }
            chipHeight = created.view.heightAnchor.constraintEqualToConstant(MIN_CHIP_SIDE).apply { active = true }
            chip = created
            // Once the bar has put the chip in the window: the next turn of the main loop.
            dispatch_async(dispatch_get_main_queue()) {
                if (screenVisible && chip === created) forwardAppearance(created, true)
            }
        }
        return UIBarButtonItem(customView = host.view)
    }

    /** Sizes the chip's host to what the chip draws, in points; never zero, see [MIN_CHIP_SIDE]. */
    private fun resizeChip(width: Float, height: Float) {
        chipWidth?.constant = width.toDouble().coerceAtLeast(MIN_CHIP_SIDE)
        chipHeight?.constant = height.toDouble().coerceAtLeast(MIN_CHIP_SIDE)
    }

    private companion object {
        /**
         * The smallest side the chip's host keeps, in points: a Compose host with no area draws no
         * frame, so it would never measure the chip again once a second family arrived.
         */
        const val MIN_CHIP_SIDE = 1.0
    }
}

/**
 * What a [ScreenBar] draws, without its lambdas: two bars that draw the same compare equal.
 */
private data class VisibleBar(
    val title: String,
    val largeTitle: Boolean,
    val subtitle: String?,
    val leading: VisibleLeading,
    val actions: List<VisibleAction>,
    val trailing: Boolean
) {
    companion object {
        fun of(bar: ScreenBar) = VisibleBar(
            title = bar.title,
            largeTitle = bar.largeTitle,
            subtitle = bar.subtitle,
            leading = when (val leading = bar.leading) {
                BarLeading.None -> VisibleLeading(LeadingKind.NONE, label = null, enabled = true)
                is BarLeading.Back -> VisibleLeading(LeadingKind.BACK, leading.label, leading.enabled)
                is BarLeading.Close -> VisibleLeading(LeadingKind.CLOSE, leading.label, leading.enabled)
            },
            actions = bar.actions.map {
                VisibleAction(it.label, it.sfSymbol, it.enabled, it.primary, it.dot)
            },
            trailing = bar.trailingContent != null
        )
    }
}

/** The visible part of a [BarLeading]: its kind, label and whether it is enabled. */
private data class VisibleLeading(val kind: LeadingKind, val label: String?, val enabled: Boolean)

/** Which [BarLeading] a bar has. */
private enum class LeadingKind { NONE, BACK, CLOSE }

/** The visible part of a [BarAction]. */
private data class VisibleAction(
    val label: String,
    val sfSymbol: String,
    val enabled: Boolean,
    val primary: Boolean,
    val dot: Boolean
)
