package com.coparently.app.ios.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.coparently.app.presentation.common.BackSteps
import com.coparently.app.presentation.common.BarAction
import com.coparently.app.presentation.common.BarHost
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.BarMenuItem
import com.coparently.app.presentation.common.ScreenBar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSSelectorFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIAction
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIColor
import platform.UIKit.UILayoutPriorityDefaultHigh
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UINavigationItemLargeTitleDisplayMode
import platform.UIKit.UIScreen
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
 * button or a close button, the actions as bar buttons with SF Symbols (a form's primary action as
 * its label, an action with a menu as a `UIMenu`), the family switcher chip as a bar button whose
 * custom view is a Compose host of its own, and a screen's title composable as the title view,
 * hosted the same way.
 *
 * Idempotent: a bar whose visible part is the one already drawn changes no item, so a screen that
 * composes again (every time it re-enters the window) does not rebuild or re-animate its buttons.
 * The items call the latest bar's lambdas, and the chip reads the latest bar from snapshot state,
 * so both follow the screen's newest composition. Each screen's bar lives on its own view
 * controller's `navigationItem`, so a popped screen's bar leaves with it.
 *
 * @property largeTitle Whether this screen shows a large title whatever its bar says (Settings).
 * @property sheetRoot Whether the screen is a sheet's first screen, whose back is a close button.
 * @property backSteps The screen's inner steps ([BackSteps]): while one is enabled the back button
 *   undoes it rather than leaving the screen, so the system back button gives way to one of the
 *   shell's own that asks them first.
 * @property chipFrame Wraps the chip and the title view in the providers the screen is composed in.
 */
internal class NativeBars(
    private val largeTitle: Boolean,
    private val sheetRoot: Boolean,
    private val backSteps: BackSteps,
    private val chipFrame: @Composable (content: @Composable () -> Unit) -> Unit
) : BarHost {

    /** The screen's view controller, whose `navigationItem` is drawn. Set once, before it loads. */
    lateinit var viewController: UIViewController

    /** The bar the screen handed over last: what the items and the chip run. Snapshot state. */
    private var current: ScreenBar? by mutableStateOf(null)

    /** The visible part of the bar drawn now; a bar with the same one draws nothing new. */
    private var drawn: VisibleBar? = null

    private var chip: BarComposition? = null

    /** The host of the screen's title composable, while its bar has one. */
    private var titleView: BarComposition? = null

    override fun show(bar: ScreenBar) {
        current = bar
        val visible = VisibleBar.of(bar)
        if (visible == drawn) return
        drawn = visible
        draw(bar)
    }

    /** Whether the screen has an inner step its back undoes first, as last told ([stepPending]). */
    private var stepPending = false

    /**
     * Whether the screen has an inner step its back undoes first. While it has, a back button of the
     * shell's own stands in for the system one, which UIKit would answer by popping the screen.
     */
    fun stepPending(pending: Boolean) {
        if (stepPending == pending) return
        stepPending = pending
        current?.let { drawLeading(it.leading) }
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
        chip?.let { forwardAppearance(it.controller, visible) }
        titleView?.let { forwardAppearance(it.controller, visible) }
    }

    /** Lets go of the chip's and the title view's hosts: the screen has left its stack. */
    fun release() {
        chip = null
        titleView = null
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
        drawTitleView(hasContent = bar.titleContent != null)
        item.rightBarButtonItems = buildList {
            // UIKit draws the first right item at the edge: the last action, as Android does.
            bar.actions.indices.reversed().forEach { add(actionItem(it, bar.actions[it])) }
            if (bar.trailingContent != null) add(chipItem())
        }
    }

    /**
     * The title composable as the navigation item's title view, rebuilt only when it appears or
     * goes: the host draws the latest bar's title content whatever else changed.
     */
    private fun drawTitleView(hasContent: Boolean) {
        val item = viewController.navigationItem
        if (!hasContent) {
            if (titleView != null) {
                item.titleView = null
                titleView = null
            }
            return
        }
        if (titleView != null) return
        val created = BarComposition(maxWidth = titleViewMaxWidth()) { current?.titleContent?.invoke() }
        titleView = created
        item.titleView = created.controller.view
    }

    private fun drawLeading(leading: BarLeading) {
        val item = viewController.navigationItem
        when (leading) {
            BarLeading.None -> {
                // Nothing at the start: a stack's first screen has no back button anyway, and a
                // pushed screen that offers no way back (the custody conflict) hides UIKit's, which
                // also refuses the edge swipe (`EdgeSwipeBack`).
                item.leftBarButtonItem = null
                item.hidesBackButton = true
            }
            is BarLeading.Back -> if (sheetRoot) {
                // A sheet has no screen to go back to: its way out is the close button.
                item.leftBarButtonItem = closeItem(leading.label, leading.enabled)
            } else if (stepPending) {
                // UIKit's own back button pops the screen with no word to ask first: while a step
                // waits, a button of the shell's own, drawn as the back chevron, undoes it instead.
                item.hidesBackButton = false
                item.leftBarButtonItem = backItem(leading.label, leading.enabled)
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

    private fun backItem(label: String, enabled: Boolean): UIBarButtonItem {
        val action = UIAction.actionWithTitle(
            title = label,
            image = SfSymbols.image(BACK_SYMBOL),
            identifier = null
        ) { _ -> clickLeading() }
        return UIBarButtonItem(primaryAction = action).apply {
            setEnabled(enabled)
            accessibilityLabel = label
        }
    }

    /** The leading item was tapped: a back undoes the screen's inner step first, if it has one. */
    private fun clickLeading() {
        when (val leading = current?.leading) {
            is BarLeading.Back -> if (!backSteps.handle()) leading.onClick()
            is BarLeading.Close -> leading.onClick()
            BarLeading.None, null -> Unit
        }
    }

    private fun actionItem(index: Int, action: BarAction): UIBarButtonItem {
        val menu = action.menu
        if (menu != null) return menuItem(index, action, menu)
        val uiAction = UIAction.actionWithTitle(
            title = action.label,
            // A form's primary action reads as its word (Save), as iOS forms do; the rest as symbols.
            image = if (action.primary) null else SfSymbols.image(action.sfSymbol),
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
     * An action whose tap opens its [menu] as a `UIMenu`: each entry a `UIAction` calling the
     * latest bar's entry at the same place, a destructive one marked so, a disabled one dimmed.
     */
    private fun menuItem(index: Int, action: BarAction, menu: List<BarMenuItem>): UIBarButtonItem {
        val entries = menu.mapIndexed { entry, menuItem ->
            UIAction.actionWithTitle(
                title = menuItem.label,
                image = menuItem.sfSymbol?.let { SfSymbols.image(it) },
                identifier = null
            ) { _ -> current?.actions?.getOrNull(index)?.menu?.getOrNull(entry)?.onClick?.invoke() }.apply {
                var flags = 0uL
                if (menuItem.destructive) flags = flags or UIMenuElementAttributesDestructive
                if (!menuItem.enabled) flags = flags or UIMenuElementAttributesDisabled
                attributes = flags
            }
        }
        return UIBarButtonItem().apply {
            image = SfSymbols.image(action.sfSymbol)
            // With a menu and no primary action, a tap opens the menu.
            this.menu = UIMenu.menuWithTitle(title = "", children = entries)
            setEnabled(action.enabled)
            accessibilityLabel = action.label
        }
    }

    /**
     * The family switcher chip: a bar button whose custom view is a Compose host, a child of the
     * screen's view controller, drawing the latest bar's trailing content. Its size follows what
     * the chip draws, which is nothing with one family.
     */
    // `NSSelectorFromString` is Kotlin/Native's C interop.
    @OptIn(ExperimentalForeignApi::class)
    private fun chipItem(): UIBarButtonItem {
        val host = chip ?: BarComposition { current?.trailingContent?.invoke() }.also { chip = it }
        return UIBarButtonItem(customView = host.controller.view).apply {
            // iOS 26 sets each bar button on glass; the chip draws its own pill, and nothing at all
            // with one family, where an empty glass circle would stand in the bar.
            if (respondsToSelector(NSSelectorFromString("setHidesSharedBackground:"))) hidesSharedBackground = true
        }
    }

    /**
     * The widest the title view may draw, in points: the screen's width less room for the leading
     * item and two bar buttons on each side, so a long title (a co-parent's name in a chat thread's
     * header) ends in its ellipsis rather than running under the buttons.
     */
    // `useContents` reads a C struct: Kotlin/Native's C interop.
    @OptIn(ExperimentalForeignApi::class)
    private fun titleViewMaxWidth(): Double {
        val screenWidth = UIScreen.mainScreen.bounds.useContents { size.width }
        return (screenWidth - 2 * TITLE_VIEW_SIDE_ROOM).coerceAtLeast(MIN_HOST_SIDE)
    }

    /**
     * A Compose host in the navigation bar (the chip, the title view): a transparent view sized to
     * what [content] draws, composed in the screen's providers. It is no child of the screen's view
     * controller, so its appearance follows [screenAppeared], starting once the bar has put it in
     * the window.
     *
     * @param maxWidth The widest [content] may draw, in points, or null for no limit (the chip).
     *   With a limit the host's width also gives way to the bar's buttons (a constraint below
     *   required), so the title view is narrowed rather than drawn over them.
     */
    // A transparent Compose host (`opaque`) is still experimental in Compose Multiplatform 1.9.3.
    @OptIn(ExperimentalComposeUiApi::class)
    private inner class BarComposition(maxWidth: Double? = null, content: @Composable () -> Unit) {

        private var width: NSLayoutConstraint? = null
        private var height: NSLayoutConstraint? = null

        val controller: UIViewController = ComposeUIViewController(configure = { opaque = false }) {
            chipFrame {
                val density = LocalDensity.current
                Box(
                    Modifier
                        .wrapContentSize(unbounded = true)
                        .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth.dp) else Modifier)
                        .onSizeChanged { size ->
                            with(density) { resize(size.width.toDp().value, size.height.toDp().value) }
                        }
                ) {
                    content()
                }
            }
        }

        init {
            controller.view.backgroundColor = UIColor.clearColor
            controller.view.translatesAutoresizingMaskIntoConstraints = false
            width = controller.view.widthAnchor.constraintEqualToConstant(MIN_HOST_SIDE).apply {
                // A priority is set before the constraint is active: UIKit refuses to move one
                // between required and optional once it is.
                if (maxWidth != null) priority = UILayoutPriorityDefaultHigh
                active = true
            }
            height = controller.view.heightAnchor.constraintEqualToConstant(MIN_HOST_SIDE).apply { active = true }
            // Once the bar has put the host in the window: the next turn of the main loop.
            dispatch_async(dispatch_get_main_queue()) {
                if (screenVisible && isShown()) forwardAppearance(controller, true)
            }
        }

        private fun isShown(): Boolean = chip === this || titleView === this

        /** Sizes the host to what it draws, in points; never zero, see [MIN_HOST_SIDE]. */
        private fun resize(width: Float, height: Float) {
            this.width?.constant = width.toDouble().coerceAtLeast(MIN_HOST_SIDE)
            this.height?.constant = height.toDouble().coerceAtLeast(MIN_HOST_SIDE)
        }
    }

    private companion object {
        /**
         * The smallest side a bar's Compose host (the chip's, the title view's) keeps, in points: a
         * host with no area draws no frame, so it would never measure its content again, as the
         * chip, empty with one family, must once a second family arrives.
         */
        const val MIN_HOST_SIDE = 1.0

        /**
         * The room kept on each side of the title view, in points: the leading item or two bar
         * buttons (about 44 points each) with the bar's margins.
         */
        const val TITLE_VIEW_SIDE_ROOM = 100.0

        /** The system back button's chevron, which the shell's own back button draws. */
        const val BACK_SYMBOL = "chevron.backward"
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
    val trailing: Boolean,
    val titleContent: Boolean
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
            actions = bar.actions.map { action ->
                VisibleAction(
                    label = action.label,
                    sfSymbol = action.sfSymbol,
                    enabled = action.enabled,
                    primary = action.primary,
                    dot = action.dot,
                    menu = action.menu?.map { VisibleMenuItem(it.label, it.sfSymbol, it.destructive, it.enabled) }
                )
            },
            trailing = bar.trailingContent != null,
            titleContent = bar.titleContent != null
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
    val dot: Boolean,
    val menu: List<VisibleMenuItem>?
)

/** The visible part of a [BarMenuItem]: a changed label, symbol or flag redraws the menu. */
private data class VisibleMenuItem(
    val label: String,
    val sfSymbol: String?,
    val destructive: Boolean,
    val enabled: Boolean
)
