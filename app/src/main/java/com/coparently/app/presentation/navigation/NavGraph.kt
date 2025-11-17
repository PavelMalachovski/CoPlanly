package com.coparently.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.coparently.app.presentation.calendar.CalendarScreen
import com.coparently.app.presentation.childinfo.ChildInfoScreen
import com.coparently.app.presentation.common.animations.*
import com.coparently.app.presentation.event.AddEditEventScreen
import com.coparently.app.presentation.event.EventListScreen
import com.coparently.app.presentation.pairing.PairingScreen
import com.coparently.app.presentation.settings.SettingsScreen

/**
 * Navigation graph for the app.
 * Defines all navigation routes and their destinations.
 */
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Calendar.route
    ) {
        composable(
            route = Screen.Calendar.route,
            enterTransition = { fadeInSlideUp() },
            exitTransition = { fadeOutSlideDown() }
        ) {
            CalendarScreen(
                onEventClick = { eventId ->
                    navController.navigate(Screen.EditEvent.createRoute(eventId))
                },
                onAddEventClick = {
                    navController.navigate(Screen.AddEvent.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.EventList.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) {
            EventListScreen(
                onEventClick = { eventId ->
                    navController.navigate(Screen.EditEvent.createRoute(eventId))
                },
                onAddEventClick = {
                    navController.navigate(Screen.AddEvent.route)
                },
                onNavigateUp = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.AddEvent.route,
            enterTransition = { fadeInScaleUp() },
            exitTransition = { fadeOutScaleDown() },
            popEnterTransition = { fadeInScaleUp() },
            popExitTransition = { fadeOutScaleDown() }
        ) {
            AddEditEventScreen(
                eventId = null,
                onSave = {
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.EditEvent.route,
            arguments = listOf(
                navArgument(Screen.EditEvent.ARG_EVENT_ID) {
                    type = NavType.StringType
                }
            ),
            enterTransition = { fadeInScaleUp() },
            exitTransition = { fadeOutScaleDown() },
            popEnterTransition = { fadeInScaleUp() },
            popExitTransition = { fadeOutScaleDown() }
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString(Screen.EditEvent.ARG_EVENT_ID) ?: return@composable
            AddEditEventScreen(
                eventId = eventId,
                onSave = {
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Settings.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) {
            SettingsScreen(
                onNavigateUp = {
                    navController.popBackStack()
                },
                onNavigateToChildInfo = {
                    navController.navigate(Screen.ChildInfo.route)
                },
                onNavigateToPairing = {
                    navController.navigate(Screen.Pairing.route)
                }
            )
        }

        composable(
            route = Screen.ChildInfo.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) {
            ChildInfoScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onEditClick = { childInfoId ->
                    navController.navigate(Screen.EditChildInfo.createRoute(childInfoId))
                }
            )
        }

        composable(
            route = Screen.EditChildInfo.route,
            arguments = listOf(
                navArgument(Screen.EditChildInfo.ARG_CHILD_INFO_ID) {
                    type = NavType.StringType
                }
            ),
            enterTransition = { fadeInScaleUp() },
            exitTransition = { fadeOutScaleDown() },
            popEnterTransition = { fadeInScaleUp() },
            popExitTransition = { fadeOutScaleDown() }
        ) { backStackEntry ->
            val childInfoId = backStackEntry.arguments?.getString(Screen.EditChildInfo.ARG_CHILD_INFO_ID) ?: "new"
            com.coparently.app.presentation.childinfo.AddEditChildInfoScreen(
                childInfoId = childInfoId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Pairing.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) {
            PairingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Sealed class representing all navigation screens.
 */
sealed class Screen(val route: String) {
    data object Calendar : Screen("calendar")
    data object EventList : Screen("event_list")
    data object AddEvent : Screen("add_event")
    data object Settings : Screen("settings")
    data object ChildInfo : Screen("child_info")
    data object Pairing : Screen("pairing")

    data object EditEvent : Screen("edit_event/{eventId}") {
        const val ARG_EVENT_ID = "eventId"

        fun createRoute(eventId: String): String {
            return "edit_event/$eventId"
        }
    }

    data object EditChildInfo : Screen("edit_child_info/{childInfoId}") {
        const val ARG_CHILD_INFO_ID = "childInfoId"

        fun createRoute(childInfoId: String): String {
            return "edit_child_info/$childInfoId"
        }
    }
}

