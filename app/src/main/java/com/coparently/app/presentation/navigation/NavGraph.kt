package com.coparently.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable as ComposeComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.presentation.LocalGoogleSignInCallback
import com.coparently.app.presentation.auth.AuthScreen
import com.coparently.app.presentation.calendar.CalendarScreen
import com.coparently.app.presentation.childinfo.ChildInfoScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.coparently.app.presentation.common.animations.*
import com.coparently.app.presentation.event.AddEditEventScreen
import com.coparently.app.presentation.event.EventListScreen
import com.coparently.app.presentation.pairing.PairingScreen
import com.coparently.app.presentation.settings.SettingsScreen
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel

/**
 * Navigation graph for the app.
 * Defines all navigation routes and their destinations.
 * Includes authentication guard to redirect unauthenticated users to AuthScreen.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    syncViewModel: SyncViewModel
) {
    val authStateViewModel: AuthStateViewModel = hiltViewModel()
    val isAuthenticated by authStateViewModel.isAuthenticated.collectAsState()
    val isLoading by authStateViewModel.isLoading.collectAsState()

    // Determine start destination based on authentication state
    val startDestination = when {
        isLoading -> Screen.Loading.route
        isAuthenticated == true -> Screen.Calendar.route
        else -> Screen.Auth.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Loading screen while checking authentication
        composable(
            route = Screen.Loading.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            LoadingScreen()
        }

        // Authentication screen for unauthenticated users
        composable(
            route = Screen.Auth.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() }
        ) {
            AuthScreen(
                onAuthSuccess = {
                    // Refresh auth state and navigate to main app
                    authStateViewModel.refreshAuthState()
                    navController.navigate(Screen.Calendar.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onViewModelReady = { authViewModel ->
                    // Set callback to refresh auth state when authentication succeeds
                    authViewModel.onAuthStateChanged = {
                        authStateViewModel.refreshAuthState()
                    }
                }
            )
        }

        composable(
            route = Screen.Calendar.route,
            enterTransition = { fadeInSlideUp() },
            exitTransition = { fadeOutSlideDown() }
        ) {
            CalendarScreen(
                onEventClick = { eventId ->
                    navController.navigate(Screen.EditEvent.createRoute(eventId))
                },
                onAddEventClick = { date, hour ->
                    navController.navigate(Screen.AddEvent.createRoute(date, hour))
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
            arguments = listOf(
                navArgument(Screen.AddEvent.ARG_DATE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Screen.AddEvent.ARG_HOUR) {
                    type = NavType.IntType
                    defaultValue = -1
                }
            ),
            enterTransition = { fadeInScaleUp() },
            exitTransition = { fadeOutScaleDown() },
            popEnterTransition = { fadeInScaleUp() },
            popExitTransition = { fadeOutScaleDown() }
        ) { backStackEntry ->
            val dateString = backStackEntry.arguments?.getString(Screen.AddEvent.ARG_DATE)
            val hourValue = backStackEntry.arguments?.getInt(Screen.AddEvent.ARG_HOUR) ?: -1
            val hour = if (hourValue >= 0) hourValue else null
            val initialDate = dateString?.let { java.time.LocalDate.parse(it) }

            AddEditEventScreen(
                eventId = null,
                initialDate = initialDate,
                initialHour = hour,
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
            val googleSignInCallback = LocalGoogleSignInCallback.current
            SettingsScreen(
                onNavigateUp = {
                    navController.popBackStack()
                },
                onNavigateToChildInfo = {
                    navController.navigate(Screen.ChildInfo.route)
                },
                onNavigateToPairing = {
                    navController.navigate(Screen.Pairing.route)
                },
                onStartGoogleSignIn = googleSignInCallback,
                onSignOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Calendar.route) { inclusive = true }
                    }
                },
                syncViewModel = syncViewModel
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

        // Chat & Communications
        composable(
            route = Screen.Conversations.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) {
            com.coparently.app.presentation.chat.ConversationsScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument(Screen.Chat.ARG_CONVERSATION_ID) {
                    type = NavType.StringType
                }
            ),
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString(Screen.Chat.ARG_CONVERSATION_ID) ?: return@composable
            com.coparently.app.presentation.chat.ChatScreen(
                conversationId = conversationId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // Expenses & Budget
        composable(
            route = Screen.Expenses.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) {
            com.coparently.app.presentation.expenses.ExpenseScreen(
                onAddExpense = {
                    navController.navigate(Screen.AddExpense.route)
                }
            )
        }

        composable(
            route = Screen.AddExpense.route,
            enterTransition = { fadeInScaleUp() },
            exitTransition = { fadeOutScaleDown() },
            popEnterTransition = { fadeInScaleUp() },
            popExitTransition = { fadeOutScaleDown() }
        ) {
            com.coparently.app.presentation.expenses.AddExpenseScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Budgets.route,
            enterTransition = { slideInFromRight() },
            exitTransition = { slideOutToLeft() },
            popEnterTransition = { slideInFromLeft() },
            popExitTransition = { slideOutToRight() }
        ) {
            com.coparently.app.presentation.expenses.BudgetScreen()
        }
    }
}

/**
 * Loading screen displayed while checking authentication state.
 */
@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Checking authentication...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Sealed class representing all navigation screens.
 */
sealed class Screen(val route: String) {
    data object Loading : Screen("loading")
    data object Auth : Screen("auth")
    data object Calendar : Screen("calendar")
    data object EventList : Screen("event_list")
    data object AddEvent : Screen("add_event?date={date}&hour={hour}") {
        const val ARG_DATE = "date"
        const val ARG_HOUR = "hour"

        fun createRoute(date: java.time.LocalDate? = null, hour: Int? = null): String {
            return if (date != null && hour != null) {
                "add_event?date=$date&hour=$hour"
            } else {
                "add_event"
            }
        }
    }
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

    data object Conversations : Screen("conversations")
    data object Chat : Screen("chat/{conversationId}") {
        const val ARG_CONVERSATION_ID = "conversationId"

        fun createRoute(conversationId: String): String {
            return "chat/$conversationId"
        }
    }
    data object Expenses : Screen("expenses")
    data object AddExpense : Screen("add_expense")
    data object Budgets : Screen("budgets")
}

