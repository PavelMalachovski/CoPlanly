package com.coparently.app.ios.controls

import com.coparently.app.presentation.common.BarMenuItem
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIMenuElementAttributesDisabled
import platform.UIKit.UIMenuElementState
import platform.UIKit.accessibilityLabel
import platform.UIKit.isAccessibilityElement

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
 * The transparent button laid over a row by `IosNativeControls.MenuAnchor`: its `UIMenu` is the
 * row's options and it shows it as its primary action, so a tap on the row opens the menu from the
 * row. Each entry calls [onPick] with its place, which the anchor maps to the latest items.
 *
 * VoiceOver reads it, when an entry is checked, as that entry's label (the value the row shows),
 * and opens the menu from it; otherwise it is left out, and the row's own semantics stand.
 */
// `CGRectMake` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class MenuAnchorButton(
    private val onPick: (Int) -> Unit
) : UIButton(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    /** The entries the menu shows now. */
    var shown: List<VisibleMenuEntry>? = null
        private set

    init {
        backgroundColor = UIColor.clearColor
        showsMenuAsPrimaryAction = true
    }

    /** Shows [items] in the menu, rebuilding it only when their visible part changed. */
    fun show(items: List<BarMenuItem>) {
        val entries = visibleEntries(items)
        if (entries == shown) return
        shown = entries
        menu = menuOf(entries, onPick)
        val checked = entries.firstOrNull { it.checked }
        isAccessibilityElement = checked != null
        accessibilityLabel = checked?.label
    }
}
