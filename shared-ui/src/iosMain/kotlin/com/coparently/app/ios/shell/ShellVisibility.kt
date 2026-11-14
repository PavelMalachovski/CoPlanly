package com.coparently.app.ios.shell

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which of the shell's hosted screens the parent sees now (MON-24 phase 3, part B), the iPhone's
 * answer to `LocalScreenVisible`. The shell's tabs keep their screens composed in hidden view
 * controllers, where Android disposes a screen that leaves the screen, so a screen that acts only
 * while seen (a chat thread marking messages read) asks this instead.
 *
 * A screen is visible while the app is in the foreground ([foreground]), its stack is the one
 * selected ([selectedStack]; the root flow's stack is always selected) and it is on top of its stack
 * or of the sheet presented over it ([StackVisibility.topIds]). Every property is snapshot state, so
 * a composition that reads a screen's state follows each of them.
 */
internal class ShellVisibility {
    /** Whether the app is in the foreground, as `IosLifecycle.foreground` says. */
    var foreground: Boolean by mutableStateOf(true)

    /** The start route of the tab selected now, or null before a tab is. */
    var selectedStack: String? by mutableStateOf(null)

    /**
     * The visibility of one stack's screens: a tab's, selected while [selectedStack] is [tab], or
     * the root flow's ([tab] null), which nothing hides but the background.
     */
    fun stack(tab: String?): StackVisibility =
        StackVisibility(selected = { tab == null || selectedStack == tab }, foreground = { foreground })
}

/**
 * The visibility of one stack's screens, by back-stack entry id; see [ShellVisibility].
 *
 * @param selected Whether the stack is the one shown. Reads snapshot state.
 * @param foreground Whether the app is in the foreground. Reads snapshot state.
 */
internal class StackVisibility(private val selected: () -> Boolean, private val foreground: () -> Boolean) {
    /**
     * The entry ids on top now: the stack's top screen, and the top screen of a sheet presented over
     * it, which leaves the screen under it in view.
     */
    var topIds: Set<String> by mutableStateOf(emptySet())

    /** Whether the screen of [entryId] is seen now, as a state a composition follows. */
    fun of(entryId: String): State<Boolean> =
        derivedStateOf { foreground() && selected() && entryId in topIds }
}
