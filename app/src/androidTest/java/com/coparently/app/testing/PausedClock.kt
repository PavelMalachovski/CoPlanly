package com.coparently.app.testing

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * Helpers for tests that launch the whole app with the Compose clock paused.
 *
 * The clock has to be paused (`mainClock.autoAdvance = false`) for a test of `MainActivity`: the
 * splash pulses forever until it is dismissed, and the list skeletons shimmer for as long as a
 * screen waits on a Firestore read — which, against the mocked Firestore of `FakeFirebaseModule`,
 * is for ever. With the clock running, every `waitForIdle` would wait on those animations and time
 * out. Paused, the test moves Compose time forward itself, while the app's real work (Room, the
 * view models' coroutines) runs in real time underneath.
 */
private const val STEP_MILLIS = 100L
private const val REAL_PAUSE_MILLIS = 20L
private const val DEFAULT_TIMEOUT_MILLIS = 20_000L

/** Enough Compose time for any navigation transition or menu to finish (the longest is 500 ms). */
const val SETTLE_MILLIS = 1_500L

/** Moves Compose time forward until [condition] holds, or fails after [timeoutMillis] real time. */
fun ComposeTestRule.pumpUntil(
    description: String,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    condition: ComposeTestRule.() -> Boolean
) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (true) {
        mainClock.advanceTimeBy(STEP_MILLIS)
        if (condition()) return
        if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Timed out waiting for $description")
        SystemClock.sleep(REAL_PAUSE_MILLIS)
    }
}

/** Lets a transition or an animation that was just started run to its end. */
fun ComposeTestRule.settle() {
    mainClock.advanceTimeBy(SETTLE_MILLIS)
}

/** Whether any node matches [matcher], without failing when there is no Compose root yet. */
fun ComposeTestRule.exists(matcher: SemanticsMatcher): Boolean =
    onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

/**
 * A control that shows no text: something clickable whose merged semantics hold no `Text`. An
 * icon button, in practice — the kind of control that is announced by its `contentDescription`
 * alone, or not at all.
 */
val iconOnlyControl: SemanticsMatcher = SemanticsMatcher("a clickable control with no text") { node ->
    node.config.contains(SemanticsActions.OnClick) &&
        !node.config.contains(SemanticsProperties.Text) &&
        !node.config.contains(SemanticsProperties.EditableText)
}

/**
 * Fails, naming every offender, when an icon-only control on the current screen has no
 * `contentDescription` or is smaller than the 48 dp minimum touch target in either direction.
 *
 * This stands in for the Accessibility Test Framework checks (`enableAccessibilityChecks()`),
 * which need an extra artifact this build does not declare. It covers the two findings that
 * matter most for a control a screen reader user has to find and a finger has to hit: an unnamed
 * button, and one too small to tap. The size is the node's **touch bounds**
 * (`touchBoundsInRoot`), which is what a finger hits and what ATF measures: a Material 3
 * `IconButton` draws 40 dp and reaches 48 dp through `minimumInteractiveComponentSize`, which
 * widens the touch target around the node rather than the node's own layout — so measuring
 * `size` flags every standard icon button (the first run did, on Home's gear).
 *
 * Those bounds are clipped to the window, so a control below the fold of a scrolling screen
 * measures 0x0 (Settings' sync button did). For such a node the check applies the rule Compose's
 * own hit testing applies on screen — the node's layout size, widened to
 * `viewConfiguration.minimumTouchTargetSize` — rather than skipping it, which would let the check
 * pass on whatever is not on the first screen. Scrolling it into view was tried and does not work
 * here: `performScrollTo` waits on an animation the paused clock never advances.
 */
fun ComposeTestRule.assertIconOnlyControlsAreAccessible(screen: String) {
    val offenders = onAllNodes(iconOnlyControl).fetchSemanticsNodes().mapNotNull { node ->
        val minimum = with(node.layoutInfo.density) { MIN_TOUCH_TARGET_DP.dp.roundToPx() }
        val label = node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
            .orEmpty()
            .joinToString(" ")
        val problems = buildList {
            if (label.isBlank()) add("no contentDescription")
            val (width, height) = touchTargetPx(node)
            if (width < minimum || height < minimum) {
                add("${width}x${height}px, under ${MIN_TOUCH_TARGET_DP}dp ($minimum px)")
            }
        }
        if (problems.isEmpty()) null else "node #${node.id} \"$label\": ${problems.joinToString("; ")}"
    }
    assertTrue(
        "$screen has icon-only controls that fail basic accessibility:\n" + offenders.joinToString("\n"),
        offenders.isEmpty()
    )
}

/** The node's touch target in px: what the window shows, or — off screen — what Compose would give it. */
private fun touchTargetPx(node: SemanticsNode): Pair<Int, Int> {
    val touch = node.touchBoundsInRoot
    if (!touch.isEmpty) return touch.width.toInt() to touch.height.toInt()
    val floor = node.layoutInfo.viewConfiguration.minimumTouchTargetSize
    return with(node.layoutInfo.density) {
        maxOf(node.size.width, floor.width.roundToPx()) to maxOf(node.size.height, floor.height.roundToPx())
    }
}

private const val MIN_TOUCH_TARGET_DP = 48
