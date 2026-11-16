package com.coparently.app.ios.controls

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import com.coparently.app.presentation.common.BarMenuItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSDirectionalRectEdgeTrailing
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolScaleSmall
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.accessibilityLabel
import platform.UIKit.isAccessibilityElement
import platform.UIKit.secondaryLabelColor

/** The visible part of a [BarMenuItem]: a changed label, symbol or flag rebuilds the menu. */
internal data class VisibleMenuEntry(
    val label: String,
    val sfSymbol: String?,
    val destructive: Boolean,
    val enabled: Boolean,
    val checked: Boolean
)

/** The visible part of each of [items], in order. */
internal fun visibleEntries(items: List<BarMenuItem>): List<VisibleMenuEntry> =
    items.map { VisibleMenuEntry(it.label, it.sfSymbol, it.destructive, it.enabled, it.checked) }

/**
 * A `UIMenu` of [entries], each a `UIAction` that calls [onPick] with its place: a checked entry
 * carries the system's checkmark (`UIMenuElementStateOn`), a destructive one is drawn so and a
 * disabled one dimmed.
 */
internal fun menuOf(entries: List<VisibleMenuEntry>, onPick: (Int) -> Unit): UIMenu {
    val actions = entries.mapIndexed { index, entry ->
        UIAction.actionWithTitle(
            title = entry.label,
            image = entry.sfSymbol?.let { UIImage.systemImageNamed(it) },
            identifier = null
        ) { _ -> onPick(index) }.apply {
            var flags = 0uL
            if (entry.destructive) flags = flags or UIMenuElementAttributesDestructive
            if (!entry.enabled) flags = flags or UIMenuElementAttributesDisabled
            attributes = flags
            if (entry.checked) state = UIMenuElementState.UIMenuElementStateOn
        }
    }
    return UIMenu.menuWithTitle(title = "", children = actions)
}

/**
 * How a menu button draws itself: its [title], its SF Symbol [sfSymbol] (null for an option row's
 * pop-up button, which shows the system's up-and-down chevron after the title instead) and the
 * colour of both, [tint] ([Color.Unspecified] for the system's secondary label grey).
 */
internal data class MenuButtonLook(val title: String?, val sfSymbol: String?, val tint: Color)

/** The SF Symbol an option row's pop-up button shows after its value, as UIKit's own pop-ups do. */
internal const val POP_UP_INDICATOR = "chevron.up.chevron.down"

/** The space between an option row's value and its chevron, in points. */
private const val POP_UP_INDICATOR_PADDING_PT = 4.0

/** A menu button's height if UIKit reports no size: the minimum touch target, in points. */
private const val MENU_BUTTON_FALLBACK_PT = 44.0

/**
 * The plain-style configuration of a menu button with [look]: its title, its symbol or the
 * pop-up chevron at the trailing edge, in its colour.
 */
internal fun menuButtonConfiguration(look: MenuButtonLook): UIButtonConfiguration =
    UIButtonConfiguration.plainButtonConfiguration().apply {
        title = look.title
        if (look.sfSymbol != null) {
            image = UIImage.systemImageNamed(look.sfSymbol)
        } else {
            image = UIImage.systemImageNamed(POP_UP_INDICATOR)
            imagePlacement = NSDirectionalRectEdgeTrailing
            imagePadding = POP_UP_INDICATOR_PADDING_PT
            preferredSymbolConfigurationForImage =
                UIImageSymbolConfiguration.configurationWithScale(UIImageSymbolScaleSmall)
        }
        baseForegroundColor = if (look.tint.isUnspecified) UIColor.secondaryLabelColor else uiColorOf(look.tint)
    }

/**
 * The size, in points, of a menu button drawn with [look]: UIKit's own intrinsic size, and for a
 * button that is a symbol alone (the chat's "+") at least [MIN_TOUCH_TARGET_PT] square, the touch
 * target the Human Interface Guidelines ask for, with the symbol centred in it. A titled pop-up
 * button keeps its intrinsic size, so an option row's height does not move.
 */
// `CGRectMake` and `useContents` are Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal fun menuButtonSize(look: MenuButtonLook): Pair<Double, Double> {
    val button = UIButton(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
    button.configuration = menuButtonConfiguration(look)
    val intrinsic = button.intrinsicContentSize.useContents {
        if (width > 0.0 && height > 0.0) width to height else MENU_BUTTON_FALLBACK_PT to MENU_BUTTON_FALLBACK_PT
    }
    if (look.title != null) return intrinsic
    return maxOf(intrinsic.first, MIN_TOUCH_TARGET_PT) to maxOf(intrinsic.second, MIN_TOUCH_TARGET_PT)
}

/** The smallest touch target, in points, of a menu button that is a symbol alone. */
internal const val MIN_TOUCH_TARGET_PT = 44.0

/**
 * The button `IosNativeControls.MenuButton` draws: a plain-style `UIButton` that draws itself and
 * shows its `UIMenu` as its primary action. Each entry calls [onPick] with its place, which the
 * composable maps to the latest items. It is always an accessibility element, named by the caller
 * ("Repeat, Weekly"), so VoiceOver reads and opens it whether or not an entry is checked.
 */
// `CGRectMake` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class MenuButtonView(
    private val onPick: (Int) -> Unit
) : UIButton(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    /** The entries the menu shows now. */
    var shown: List<VisibleMenuEntry>? = null
        private set

    /** How the button draws itself now. */
    var look: MenuButtonLook? = null
        private set

    init {
        showsMenuAsPrimaryAction = true
        isAccessibilityElement = true
    }

    /**
     * Shows [items] in the menu and draws [look], each rebuilt only when it changed, names the
     * button [label] for VoiceOver and paints [background] behind it.
     */
    fun show(items: List<BarMenuItem>, look: MenuButtonLook, label: String, background: UIColor) {
        if (look != this.look) {
            configuration = menuButtonConfiguration(look)
            this.look = look
        }
        val entries = visibleEntries(items)
        if (entries != shown) {
            shown = entries
            menu = menuOf(entries, onPick)
        }
        accessibilityLabel = label
        backgroundColor = background
    }
}
