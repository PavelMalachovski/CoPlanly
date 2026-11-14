package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.coparently.app.presentation.platform.ProvideAndroidPlatform
import com.coparently.app.presentation.theme.CoPlanlyTheme
import com.coparently.app.presentation.theme.ContrastLevel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What every pushed-bar test shares (MON-24 phase 3, part B, Tasks 5 and 6): a recording [BarHost]
 * for the iPhone's shell, the screen composed as the app draws it, and the assertions on the bar a
 * host was handed or the Material bar Android draws.
 */
abstract class PushedBarsHarness {

    @get:Rule
    val rule = createComposeRule()

    /** Every bar a host was handed, in order. */
    protected val shown = mutableListOf<ScreenBar>()
    private val host = BarHost { shown += it }

    /** What the screens' callbacks did, in order. */
    protected val clicks = mutableListOf<String>()
    private var graphStarted = false

    @After
    fun stopGraph() {
        if (graphStarted) stopKoin()
    }

    protected fun checkPlainBar(title: String, content: @Composable () -> Unit) {
        compose(withHost = true, content = content)
        assertBar(title = title, back = "Back", actions = emptyList())
        rule.runOnIdle { (shown.last().leading as BarLeading.Back).onClick() }
        assertEquals(listOf("up"), clicks)
    }

    protected fun checkAndroidBar(title: String, content: @Composable () -> Unit) {
        compose(withHost = false, content = content)
        assertAndroidBar(title = title, back = "Back")
    }

    protected fun startGraph(definitions: Module? = null) {
        val parentsSource = mockk<ParentsSource> { every { observe() } returns MutableStateFlow(PARENTS) }
        startKoin { modules(listOfNotNull(module { single { parentsSource } }, definitions)) }
        graphStarted = true
    }

    /** The screen as the app draws it: the platform, the theme and, for the iPhone, a bar host. */
    protected fun compose(withHost: Boolean, content: @Composable () -> Unit) {
        rule.setContent {
            ProvideAndroidPlatform {
                CoPlanlyTheme(darkTheme = false, contrastLevel = ContrastLevel.STANDARD) {
                    if (withHost) {
                        CompositionLocalProvider(LocalBarHost provides host) { content() }
                    } else {
                        content()
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /**
     * The bar last shown: [title], the back button labelled [back] (none at all when null), exactly
     * [actions] (symbol to label), and [primary], the label of the one primary action, if any.
     */
    protected fun assertBar(
        title: String,
        back: String?,
        actions: List<Pair<String, String>>,
        primary: String? = null
    ) {
        rule.waitForIdle()
        val bar = shown.last()
        assertEquals(title, bar.title)
        val leading = bar.leading
        if (back == null) {
            assertEquals(BarLeading.None, leading)
        } else {
            assertTrue("The leading is $leading", leading is BarLeading.Back)
            assertEquals(back, (leading as BarLeading.Back).label)
        }
        assertEquals(actions, bar.actions.map { it.sfSymbol to it.label })
        assertEquals(listOfNotNull(primary), bar.actions.filter { it.primary }.map { it.label })
    }

    /**
     * How many extended floating buttons labelled [label] are drawn. Material's extended button may
     * keep its text out of the semantics tree, so a clickable node with neither text nor a
     * description (the screen's only such node, its rows and arrow being labelled) counts as one too.
     */
    protected fun extendedFabCount(label: String): Int {
        val labelled = rule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size
        val unlabelled = rule.onAllNodes(hasClickAction() and Unlabelled).fetchSemanticsNodes().size
        return maxOf(labelled, unlabelled)
    }

    /** Taps the bar's action labelled [label], as the iPhone's bar button does. */
    protected fun clickAction(label: String) {
        rule.runOnIdle { shown.last().actions.single { it.label == label }.onClick() }
        rule.waitForIdle()
    }

    /** Without a host nothing reaches a host, and the Material bar shows [title] and its arrow. */
    protected fun assertAndroidBar(title: String, back: String) {
        rule.waitForIdle()
        assertTrue("A bar reached a host on Android", shown.isEmpty())
        assertTrue("No title $title", rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            "No up arrow labelled $back",
            rule.onAllNodesWithContentDescription(back).fetchSemanticsNodes().isNotEmpty()
        )
    }


    companion object {
        /** A node with neither text nor a content description. */
        val Unlabelled = SemanticsMatcher("has neither text nor a content description") {
            SemanticsProperties.Text !in it.config && SemanticsProperties.ContentDescription !in it.config
        }

        /** The fixtures' day. */
        val DAY = LocalDate(2026, 5, 13)
        /** The fixtures' moment. */
        val NOW = LocalDateTime(2026, 5, 1, 9, 0)

        /** Alice, signed in, with no co-parent on screen. */
        val PARENTS = Parents(me = NamedParent(uid = "alice", slot = "mom", name = "Alice"), loaded = true)
    }
}
