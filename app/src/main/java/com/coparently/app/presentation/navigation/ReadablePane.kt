package com.coparently.app.presentation.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * The widest a screen that is not a tab is laid out: one readable column (October 2026 design
 * audit, §4 "Adaptive layouts").
 *
 * Forms, Settings and every other detail screen are single columns of fields and rows. Stretched
 * across a tablet or an unfolded phone they became lines a metre long, with a Save button at one
 * edge and its label at the other. 640 dp holds the widest phone and a portrait foldable whole,
 * so on those the cap changes nothing.
 */
internal val READABLE_PANE_WIDTH = 640.dp

/** The test tag of [ReadablePane]'s column, for a test that measures it. */
internal const val READABLE_PANE_TEST_TAG = "readable_pane"

/**
 * A destination that is not a tab: [composable], with its screen laid out by [ReadablePane].
 *
 * One wrapper at the navigation graph, not a cap in each of thirty-eight screens. Every detail
 * screen brings its own Scaffold, and a cap inside each would be thirty-eight chances to put it on
 * the wrong container, or to forget it in the next screen. The tabs stay `composable`: Home, the
 * calendar, chat and expenses use the whole window, beside the rail.
 *
 * The parameters are navigation's own, with its defaults: a null transition falls back to the
 * NavHost's, and a pop transition defaults to its push.
 */
@Suppress("LongParameterList") // mirrors navigation's own composable(), transition by transition
internal fun NavGraphBuilder.pane(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? =
        enterTransition,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? =
        exitTransition,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { entry ->
        ReadablePane { content(entry) }
    }
}

/**
 * Lays [content] out in a column at most [READABLE_PANE_WIDTH] wide, centred in the window, at
 * the window's full height. The screen's own Scaffold fills the column, its top bar included; the
 * window's background shows either side.
 */
@Composable
internal fun ReadablePane(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = READABLE_PANE_WIDTH)
                .fillMaxWidth()
                .testTag(READABLE_PANE_TEST_TAG)
        ) {
            content()
        }
    }
}
