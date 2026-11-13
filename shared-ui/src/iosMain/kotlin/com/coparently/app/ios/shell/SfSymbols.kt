package com.coparently.app.ios.shell

import com.coparently.app.presentation.navigation.BottomNavDestination
import platform.UIKit.UIImage

/**
 * The SF Symbols the iPhone's shell draws itself (MON-24 phase 3): the four tabs' icons. A screen's
 * own bar buttons name theirs in [com.coparently.app.presentation.common.BarAction.sfSymbol].
 */
internal object SfSymbols {

    /** The symbol of [tab] in the tab bar. */
    fun forTab(tab: BottomNavDestination): String = when (tab) {
        BottomNavDestination.HOME -> "house"
        BottomNavDestination.CALENDAR -> "calendar"
        BottomNavDestination.CHAT -> "bubble.left.and.bubble.right"
        BottomNavDestination.FAMILY -> "person.2"
    }

    /** The system's image of the symbol [name], or null for a name this iOS does not know. */
    fun image(name: String): UIImage? = UIImage.systemImageNamed(name)
}
