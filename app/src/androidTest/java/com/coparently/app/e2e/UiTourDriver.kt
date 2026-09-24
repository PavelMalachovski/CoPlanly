package com.coparently.app.e2e

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.coparently.app.testing.exists
import com.coparently.app.testing.pumpUntil
import com.coparently.app.testing.settle

/**
 * How the UI tour moves through the app: taps, Back, scrolling and bounded waits, all on the paused
 * Compose clock that every on-screen e2e test runs with (`testing/PausedClock.kt`).
 *
 * Two choices differ from `AliceOnScreenTest.tap` on purpose. [press] invokes the node's click
 * **semantics action** rather than injecting a touch, so a Settings row below the fold is reached
 * without scrolling to it first (`performScrollTo` waits on an animation the paused clock never
 * advances). And [back] sends a real Back key through Espresso, which reaches whatever window has
 * focus — a dialog, a bottom sheet, the keyboard — where the activity's back dispatcher would pop the
 * navigation graph underneath an open dialog.
 *
 * @param rule The compose rule, with `mainClock.autoAdvance` off.
 * @param isHome Whether a top-level tab (the bottom bar) is on screen — where [backToTabs] stops.
 */
class UiTourDriver(
    private val rule: ComposeTestRule,
    private val isHome: SemanticsMatcher
) {

    /** Waits, moving Compose time, until [matcher] is on screen; fails after [timeoutMillis]. */
    fun await(description: String, matcher: SemanticsMatcher, timeoutMillis: Long = WAIT_MS) {
        rule.pumpUntil(description, timeoutMillis) { exists(matcher) }
    }

    /** Whether any node matches [matcher] right now. */
    fun present(matcher: SemanticsMatcher): Boolean = rule.exists(matcher)

    /** Waits for [matcher], invokes its click action, and lets what it started settle. */
    fun press(description: String, matcher: SemanticsMatcher, timeoutMillis: Long = WAIT_MS) {
        await(description, matcher, timeoutMillis)
        rule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.settle()
    }

    /** [press] if [matcher] is on screen now; returns whether it was. */
    fun pressIfPresent(matcher: SemanticsMatcher): Boolean {
        if (!present(matcher)) return false
        rule.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        rule.settle()
        return true
    }

    /**
     * Taps [field] with a real touch — which is what raises the soft keyboard — and types [text]
     * into it when it is not null.
     */
    fun type(description: String, field: SemanticsMatcher, text: String?) {
        await(description, field)
        val node = rule.onAllNodes(field).onFirst()
        node.performClick()
        rule.settle()
        if (text != null) node.performTextInput(text)
        rule.settle()
    }

    /** One Back, to whatever window has focus. Never throws when it leaves the app. */
    fun back() {
        Espresso.pressBackUnconditionally()
        rule.settle()
    }

    /**
     * Presses Back until a top-level tab is on screen with no dialog over it, at most [MAX_BACKS]
     * times. It never presses Back on a bare tab, which on Home would close the app.
     */
    fun backToTabs() {
        repeat(MAX_BACKS) {
            if (present(isHome) && !present(isDialog())) return
            back()
        }
    }

    /** Back, but only while a dialog is open — so a dialog that never opened costs nothing. */
    fun dismissDialog() {
        if (present(isDialog())) back()
    }

    /** Hides the soft keyboard if it is up; unlike Back, never navigates. */
    fun closeKeyboard() {
        Espresso.closeSoftKeyboard()
        rule.settle()
    }

    /** Presses Back until [matcher] is on screen, at most [MAX_BACKS] times; returns whether it is. */
    fun backUntil(matcher: SemanticsMatcher): Boolean {
        repeat(MAX_BACKS) {
            if (present(matcher)) return true
            back()
        }
        return present(matcher)
    }

    /** [await] that answers instead of failing: whether [matcher] appeared within [timeoutMillis]. */
    fun appeared(matcher: SemanticsMatcher, timeoutMillis: Long = CONTENT_WAIT_MS): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (present(matcher)) return true
            rule.mainClock.advanceTimeBy(STEP_MS)
            SystemClock.sleep(PAUSE_MS)
        }
        return present(matcher)
    }

    /**
     * Lets [millis] of real time pass while moving the Compose clock with it, so what a screen loads
     * from Room or Firestore after it opens is drawn before the screenshot.
     */
    fun linger(millis: Long = LINGER_MS) {
        val deadline = SystemClock.uptimeMillis() + millis
        while (SystemClock.uptimeMillis() < deadline) {
            rule.mainClock.advanceTimeBy(STEP_MS)
            SystemClock.sleep(PAUSE_MS)
        }
    }

    /**
     * Scrolls the largest vertically scrolling container on screen by most of its height. Returns
     * false when there is none, or when it was already at its end.
     */
    fun scrollDown(): Boolean {
        val nodes = rule.onAllNodes(VERTICAL_SCROLLER).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val index = nodes.indices.maxByOrNull { nodes[it].size.width.toLong() * nodes[it].size.height } ?: return false
        val node = nodes[index]
        val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        if (range != null && range.value() >= range.maxValue()) return false
        rule.onAllNodes(VERTICAL_SCROLLER)[index].performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, node.size.height * SCROLL_FRACTION)
        }
        rule.settle()
        return true
    }

    /** Scrolls the largest vertical container back to its start. */
    fun scrollToTop() {
        repeat(MAX_SCROLLS) {
            val nodes = rule.onAllNodes(VERTICAL_SCROLLER).fetchSemanticsNodes(atLeastOneRootRequired = false)
            val index = nodes.indices.maxByOrNull { nodes[it].size.width.toLong() * nodes[it].size.height } ?: return
            val range = nodes[index].config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            if (range == null || range.value() <= 0f) return
            rule.onAllNodes(VERTICAL_SCROLLER)[index].performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                scrollBy(0f, -range.value())
            }
            rule.settle()
        }
    }

    /**
     * Pages the widest horizontal pager on screen one page forward — the next month on the month
     * grid, the next day or week in Day and Week view. Returns false when there is none.
     */
    fun pageForward(): Boolean {
        val nodes = rule.onAllNodes(HORIZONTAL_SCROLLER).fetchSemanticsNodes(atLeastOneRootRequired = false)
        val index = nodes.indices.maxByOrNull { nodes[it].size.width.toLong() * nodes[it].size.height } ?: return false
        val width = nodes[index].size.width.toFloat()
        rule.onAllNodes(HORIZONTAL_SCROLLER)[index].performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(width, 0f)
        }
        rule.settle()
        return true
    }

    /** Scrolls the main container to its end, a screenful at a time. */
    fun scrollToEnd() {
        repeat(MAX_SCROLLS) {
            if (!scrollDown()) return
        }
    }

    private companion object {
        const val WAIT_MS = 20_000L
        const val CONTENT_WAIT_MS = 8_000L
        const val LINGER_MS = 1_500L
        const val STEP_MS = 100L
        const val PAUSE_MS = 20L
        const val MAX_BACKS = 5
        const val MAX_SCROLLS = 12
        const val SCROLL_FRACTION = 0.8f

        val VERTICAL_SCROLLER: SemanticsMatcher =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollAction()
        val HORIZONTAL_SCROLLER: SemanticsMatcher =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasScrollAction()
    }
}
