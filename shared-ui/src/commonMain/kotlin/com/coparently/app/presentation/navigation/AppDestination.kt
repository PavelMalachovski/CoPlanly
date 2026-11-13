package com.coparently.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.coparently.app.presentation.auth.AuthScreen
import com.coparently.app.presentation.calendar.CalendarScreen
import com.coparently.app.presentation.changerequests.AgreementScreen
import com.coparently.app.presentation.childinfo.ChildInfoScreen
import com.coparently.app.presentation.common.animations.fadeThroughIn
import com.coparently.app.presentation.common.animations.fadeThroughOut
import com.coparently.app.presentation.common.animations.slideInFromLeft
import com.coparently.app.presentation.common.animations.slideInFromRight
import com.coparently.app.presentation.common.animations.slideOutToLeft
import com.coparently.app.presentation.common.animations.slideOutToRight
import com.coparently.app.presentation.consent.TelemetryConsentScreen
import com.coparently.app.presentation.documents.FamilyDocumentsScreen
import com.coparently.app.presentation.event.AddEditEventScreen
import com.coparently.app.presentation.export.ExportScreen
import com.coparently.app.presentation.journal.JournalEditorScreen
import com.coparently.app.presentation.journal.JournalEditorViewModel
import com.coparently.app.presentation.journal.JournalListScreen
import com.coparently.app.presentation.onboarding.OnboardingScreen
import com.coparently.app.presentation.pairing.PairingScreen
import com.coparently.app.presentation.parentingplan.ParentingPlanScreen
import com.coparently.app.presentation.pets.AddEditPetScreen
import com.coparently.app.presentation.pets.PetsScreen
import com.coparently.app.presentation.review.MonthReviewScreen
import com.coparently.app.presentation.settings.SettingsScreen
import com.coparently.app.presentation.stickers.MyStickersScreen
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.navigation_loading
import org.jetbrains.compose.resources.stringResource

/**
 * How a shell presents a destination (MON-24 phase 3). Android draws every kind in its one
 * `NavHost`; the iPhone's native shell (phase 3, Task 8) hosts each destination itself and decides
 * by this where it goes.
 */
enum class DestinationKind {
    /** One of the four tabs' first screens ([BottomNavDestination]); on Android a full-width destination. */
    TAB_ROOT,

    /** A detail screen pushed over a tab; on Android one readable column (design item 19). */
    PUSH,

    /**
     * A detail screen that keeps the whole width: Expenses, design item 19's one exception, which
     * lays a month out in two panes from 840 dp.
     */
    FULL_WIDTH_PUSH,

    /**
     * A form that adds or edits one record — an event, an expense, a child, a pet, a journal entry,
     * a change request. A readable column on Android, a sheet on the iPhone.
     */
    FORM,

    /**
     * The screens before the app proper — loading, the telemetry question, sign-in, onboarding —
     * with no tab bar.
     */
    ROOT_FLOW
}

/**
 * The animations a destination names on Android, navigation's own four, with its defaults: a pop
 * transition defaults to its push. The iPhone's shell uses the system's and reads none of them.
 */
class DestinationTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = enter,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = exit
) {
    /** The three sets the graph names (design item 11). */
    companion object {
        /** The standard push, the same as the NavHost's default. */
        val PUSH = DestinationTransitions(
            enter = { slideInFromRight() },
            exit = { slideOutToLeft() },
            popEnter = { slideInFromLeft() },
            popExit = { slideOutToRight() }
        )

        /** A plain fade, for the screens before the app proper. */
        val FADE = DestinationTransitions(
            enter = { fadeIn() },
            exit = { fadeOut() }
        )

        /** A tab: fade-through between two tabs, the standard push to and from a detail screen. */
        val TAB = DestinationTransitions(
            enter = { tabEnter(forward = true) },
            exit = { tabExit(forward = true) },
            popEnter = { tabEnter(forward = false) },
            popExit = { tabExit(forward = false) }
        )
    }
}

/**
 * One destination of the app, as data both shells read (MON-24 phase 3): its route and arguments,
 * the kind a shell presents it as, the transitions Android names, and its content.
 *
 * @property route The route pattern, one of [Screen]'s.
 * @property arguments The route's arguments, as navigation declares them.
 * @property kind How a shell presents it; always [AppDestinationCatalog]'s kind for [route].
 * @property transitions Android only; null takes the NavHost's default push.
 * @property content The screen, given the back-stack entry its arguments are read from.
 */
class AppDestination(
    val route: String,
    val arguments: List<NamedNavArgument>,
    val kind: DestinationKind,
    val transitions: DestinationTransitions?,
    val content: @Composable (NavBackStackEntry) -> Unit
)

/**
 * The data half of the destinations: every route the app declares, with its kind, in the order the
 * graph declares them. The one place a route's kind is decided; [rememberAppDestinations] reads it,
 * and a test reads it without Compose.
 */
object AppDestinationCatalog {
    /** Every route of [Screen], with the kind a shell presents it as. */
    val all: List<Pair<String, DestinationKind>> = listOf(
        Screen.Loading.route to DestinationKind.ROOT_FLOW,
        Screen.PrivacyConsent.route to DestinationKind.ROOT_FLOW,
        Screen.Onboarding.route to DestinationKind.ROOT_FLOW,
        Screen.Auth.route to DestinationKind.ROOT_FLOW,
        Screen.Home.route to DestinationKind.TAB_ROOT,
        Screen.Calendar.route to DestinationKind.TAB_ROOT,
        Screen.AddEvent.route to DestinationKind.FORM,
        Screen.EditEvent.route to DestinationKind.FORM,
        Screen.Contacts.route to DestinationKind.PUSH,
        Screen.ChangeRequests.route to DestinationKind.PUSH,
        Screen.RequestChange.route to DestinationKind.FORM,
        Screen.Settings.route to DestinationKind.PUSH,
        Screen.ParentingPlan.route to DestinationKind.PUSH,
        Screen.Export.route to DestinationKind.PUSH,
        Screen.MonthReview.route to DestinationKind.PUSH,
        Screen.UnassignedRecords.route to DestinationKind.PUSH,
        Screen.Documents.route to DestinationKind.PUSH,
        Screen.Support.route to DestinationKind.PUSH,
        Screen.EarlierFamilies.route to DestinationKind.PUSH,
        Screen.EarlierFamily.route to DestinationKind.PUSH,
        Screen.Journal.route to DestinationKind.PUSH,
        Screen.Stickers.route to DestinationKind.PUSH,
        Screen.JournalEditor.route to DestinationKind.FORM,
        Screen.ChildInfo.route to DestinationKind.PUSH,
        Screen.ChildDetail.route to DestinationKind.PUSH,
        Screen.EditChildInfo.route to DestinationKind.FORM,
        Screen.Pets.route to DestinationKind.PUSH,
        Screen.EditPet.route to DestinationKind.FORM,
        Screen.Pairing.route to DestinationKind.PUSH,
        Screen.GuestAccept.route to DestinationKind.PUSH,
        Screen.CustodyConflict.route to DestinationKind.PUSH,
        Screen.SchoolImport.route to DestinationKind.PUSH,
        Screen.SchoolConnect.route to DestinationKind.PUSH,
        Screen.CalendarFeed.route to DestinationKind.PUSH,
        Screen.DataSources.route to DestinationKind.PUSH,
        Screen.Friends.route to DestinationKind.PUSH,
        Screen.FriendDetail.route to DestinationKind.PUSH,
        Screen.FriendProfile.route to DestinationKind.PUSH,
        Screen.FriendCalendar.route to DestinationKind.PUSH,
        Screen.Professionals.route to DestinationKind.PUSH,
        Screen.ProfessionalCalendar.route to DestinationKind.PUSH,
        Screen.ProfessionalPlan.route to DestinationKind.PUSH,
        Screen.CustodySetup.route to DestinationKind.PUSH,
        Screen.MyProfile.route to DestinationKind.PUSH,
        Screen.CoParentProfile.route to DestinationKind.PUSH,
        Screen.Conversations.route to DestinationKind.TAB_ROOT,
        Screen.Chat.route to DestinationKind.PUSH,
        Screen.Family.route to DestinationKind.TAB_ROOT,
        Screen.Expenses.route to DestinationKind.FULL_WIDTH_PUSH,
        Screen.AddExpense.route to DestinationKind.FORM,
        Screen.EditExpense.route to DestinationKind.FORM
    )

    private val kinds: Map<String, DestinationKind> = all.toMap()

    /** The kind of [route]; throws for a route the catalogue does not hold. */
    internal fun kindOf(route: String): DestinationKind =
        requireNotNull(kinds[route]) { "No destination kind for $route" }
}

/**
 * Switches to one of the four tabs from inside a destination: Home's chat tile, a tapped push,
 * settle-up's thread. On Android `NavGraph` provides [navigateToTab] on its controller, the bottom
 * bar's own back-stack policy; the iPhone's shell selects its tab.
 */
interface TabSwitcher {
    /** Shows [tab], as tapping it in the bar would: the tab as it was left. */
    fun select(tab: BottomNavDestination)

    /**
     * Shows [tab] at its first screen, not as it was left: settle-up shows the thread its card was
     * posted in. On Android [navigateToTabFresh], the bar's policy with `restoreState = false`; on
     * the iPhone the tab, popped to its first screen.
     */
    fun selectFresh(tab: BottomNavDestination)
}

/**
 * The shell's [TabSwitcher]. Reading it outside a provider throws, as every platform local does: an
 * "unavailable" default would hide a broken tab switch.
 */
val LocalTabSwitcher: ProvidableCompositionLocal<TabSwitcher> =
    staticCompositionLocalOf { error("No TabSwitcher provided: compose inside NavGraph or the iPhone's shell") }

/**
 * Every destination of the app, with its content (MON-24 phase 3). Android's `NavGraph` builds its
 * `NavHost` from this list; the iPhone's native shell hosts each one itself. The bodies are the
 * ones the `NavHost` declared inline until then, moved unchanged.
 *
 * @param navController Where a destination's own navigation goes.
 * @param syncViewModel The shell's [SyncViewModel], which Settings shares with the activity.
 * @param authStateViewModel The start destination's one source, which sign-in refreshes.
 */
@Composable
fun rememberAppDestinations(
    navController: NavHostController,
    syncViewModel: SyncViewModel,
    authStateViewModel: AuthStateViewModel
): List<AppDestination> = remember(navController, syncViewModel, authStateViewModel) {
    appDestinations(navController, syncViewModel, authStateViewModel).also { destinations ->
        check(destinations.map { it.route } == AppDestinationCatalog.all.map { it.first }) {
            "The destinations and AppDestinationCatalog list different routes"
        }
    }
}

/** A destination of [route], of the kind [AppDestinationCatalog] gives it. */
private fun destination(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    transitions: DestinationTransitions? = null,
    content: @Composable (NavBackStackEntry) -> Unit
): AppDestination = AppDestination(
    route = route,
    arguments = arguments,
    kind = AppDestinationCatalog.kindOf(route),
    transitions = transitions,
    content = content
)

/**
 * The destinations [rememberAppDestinations] remembers, in [AppDestinationCatalog]'s order. Internal
 * for the iPhone's shell, which builds each stack's list outside composition (MON-24 phase 3).
 */
// One flat list of route declarations, not branching logic — splitting it would only relocate the
// length into a second function without reducing what a reader has to scan to find a given route.
// The NavHost's body it was moved from carried the same suppression for the same reason.
@Suppress("LongMethod")
internal fun appDestinations(
    navController: NavHostController,
    syncViewModel: SyncViewModel,
    authStateViewModel: AuthStateViewModel
): List<AppDestination> = listOf(
    // Loading screen while checking authentication
    destination(
        route = Screen.Loading.route,
        transitions = DestinationTransitions.FADE
    ) {
        LoadingScreen()
    },

    // The telemetry consent, asked once before anything else happens.
    destination(
        route = Screen.PrivacyConsent.route,
        transitions = DestinationTransitions.FADE
    ) {
        TelemetryConsentScreen(
            // Answering re-runs the start-destination decision above, which now falls
            // through to whatever this account's real next screen is. Navigating to a
            // named route here would have to duplicate that decision, and the two copies
            // would drift.
            onAnswered = {
                navController.navigate(Screen.Loading.route) {
                    popUpTo(Screen.PrivacyConsent.route) { inclusive = true }
                }
            }
        )
    },

    // The first-run questionnaire, for an account that has not been through it.
    destination(
        route = Screen.Onboarding.route,
        transitions = DestinationTransitions.FADE
    ) {
        OnboardingScreen(
            onFinished = {
                navController.navigate(Screen.Calendar.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }
            },
            onOpenCustodySetup = { navController.navigate(Screen.CustodySetup.routeFor()) },
            // The wizard's first step offers both halves of pairing as two buttons —
            // "I have their code" opens on code entry, "Invite" on this account's own
            // code — because the second parent to install the app is holding a code and
            // must not be shown their own first.
            onOpenPairing = { enterCode ->
                navController.navigate(
                    if (enterCode) {
                        Screen.Pairing.routeForCodeEntry()
                    } else {
                        Screen.Pairing.routeWithCode(null)
                    }
                )
            }
        )
    },

    // Authentication screen for unauthenticated users
    destination(
        route = Screen.Auth.route,
        transitions = DestinationTransitions.PUSH
    ) {
        AuthScreen(
            onAuthSuccess = {
                // Re-runs the whole start-destination decision, questionnaire included,
                // and parks on Loading until it resolves. Navigating straight to Home
                // here — as this did — is what would let a parent who signed up in this
                // very session never see the wizard at all: the start-destination
                // decision above resolves once per authentication check, not per frame.
                authStateViewModel.refreshAuthState()
                navController.navigate(Screen.Loading.route) {
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
    },

    // Home / overview dashboard — first screen (MVP 2)
    destination(
        route = Screen.Home.route,
        transitions = DestinationTransitions.TAB
    ) {
        val tabSwitcher = LocalTabSwitcher.current
        com.coparently.app.presentation.home.HomeScreen(
            onOpenEvent = { eventId ->
                navController.navigate(Screen.EditEvent.createRoute(eventId))
            },
            onOpenChangeRequests = {
                navController.navigate(Screen.ChangeRequests.createRoute())
            },
            onOpenRequestHistory = {
                navController.navigate(
                    Screen.ChangeRequests.createRoute(
                        section = com.coparently.app.presentation.changerequests.InboxSection.HISTORY
                    )
                )
            },
            onOpenSettings = {
                navController.navigate(Screen.Settings.route)
            },
            onNavigateToPairing = {
                navController.navigate(Screen.Pairing.routeWithCode(null))
            },
            // The dashboard's stat tiles deep-link into what owns those numbers:
            // Expenses, a detail screen since the Family tab replaced its tab, and the
            // Chat tab, through the tab switcher like tapping the tab itself.
            onOpenExpenses = {
                navController.navigate(Screen.Expenses.route)
            },
            onOpenChat = {
                tabSwitcher.select(BottomNavDestination.CHAT)
            },
            // A week tile opens the calendar's Day view on its day (redesign, November
            // 2026, variant A): the request first, then the tab, which answers it.
            onOpenDay = { date ->
                CalendarDayRequests.open(date)
                tabSwitcher.select(BottomNavDestination.CALENDAR)
            },
            onOpenUnassignedRecords = {
                navController.navigate(Screen.UnassignedRecords.route)
            }
        )
    },

    destination(
        route = Screen.Calendar.route,
        transitions = DestinationTransitions.TAB
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
            },
            onChangeRequestsClick = {
                navController.navigate(Screen.ChangeRequests.createRoute())
            }
        )
    },

    destination(
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
        )
    ) { backStackEntry ->
        val dateString = backStackEntry.stringArgument(Screen.AddEvent.ARG_DATE)
        val hourValue = backStackEntry.intArgument(Screen.AddEvent.ARG_HOUR) ?: -1
        val hour = if (hourValue >= 0) hourValue else null
        val initialDate = dateString?.takeIf { it != "null" }?.let { kotlinx.datetime.LocalDate.parse(it) }

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
    },

    destination(
        route = Screen.EditEvent.route,
        arguments = listOf(
            navArgument(Screen.EditEvent.ARG_EVENT_ID) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val eventId = backStackEntry.stringArgument(Screen.EditEvent.ARG_EVENT_ID)
            ?: return@destination
        AddEditEventScreen(
            eventId = eventId,
            onSave = {
                navController.popBackStack()
            },
            onCancel = {
                navController.popBackStack()
            },
            onRequestChange = { id ->
                navController.navigate(Screen.RequestChange.createRoute(id))
            }
        )
    },

    // Contacts — the numbers worth finding in a hurry. A detail screen, deliberately
    // not a tab: it is opened rarely and urgently, not browsed.
    destination(
        route = Screen.Contacts.route,
        transitions = DestinationTransitions.PUSH
    ) {
        com.coparently.app.presentation.contacts.ContactsScreen(
            onNavigateUp = { navController.popBackStack() },
            // A contact lives on a child's record, so adding one starts at the children.
            onAddContact = { navController.navigate(Screen.ChildInfo.route) }
        )
    },

    // The requests inbox (MVP 2; three sections since phase 2)
    destination(
        route = Screen.ChangeRequests.route,
        arguments = listOf(
            navArgument(Screen.ChangeRequests.ARG_EVENT_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(Screen.ChangeRequests.ARG_SECTION) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) { backStackEntry ->
        val linkedEventId = backStackEntry.stringArgument(Screen.ChangeRequests.ARG_EVENT_ID)
            ?.takeIf { it != "null" }
        com.coparently.app.presentation.changerequests.ChangeRequestsScreen(
            onBack = { navController.popBackStack() },
            onOpenEvent = { eventId ->
                navController.navigate(Screen.EditEvent.createRoute(eventId))
            },
            linkedEventId = linkedEventId,
            openSection = com.coparently.app.presentation.changerequests.InboxSection.fromRoute(
                backStackEntry.stringArgument(Screen.ChangeRequests.ARG_SECTION)
            ),
            onOpenAgreement = { screen -> navController.navigate(screen.route) }
        )
    },

    // Propose a new time for an event (MVP 2). The thread the proposal is announced in
    // is resolved from the two uids by `ActivityAnnouncer`, not carried in the route.
    destination(
        route = Screen.RequestChange.route,
        arguments = listOf(
            navArgument(Screen.RequestChange.ARG_EVENT_ID) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val eventId = backStackEntry.stringArgument(Screen.RequestChange.ARG_EVENT_ID) ?: return@destination
        com.coparently.app.presentation.changerequests.RequestChangeScreen(
            eventId = eventId,
            onBack = {
                navController.popBackStack()
            }
        )
    },

    destination(
        route = Screen.Settings.route,
        transitions = DestinationTransitions.PUSH
    ) {
        SettingsScreen(
            // Reached via the gear action in the top-level top bars — it opens as
            // a detail screen, so it gets a back arrow.
            onNavigateUp = { navController.popBackStack() },
            onNavigateToCalendarFeed = {
                navController.navigate(Screen.CalendarFeed.route)
            },
            onNavigateToSchoolImport = {
                navController.navigate(Screen.SchoolImport.route)
            },
            onNavigateToDataSources = {
                navController.navigate(Screen.DataSources.route)
            },
            onNavigateToMyProfile = {
                navController.navigate(Screen.MyProfile.route)
            },
            onSignOut = {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(Screen.Calendar.route) { inclusive = true }
                }
            },
            syncViewModel = syncViewModel
        )
    },

    // A detail screen off Settings, like Custody Setup: it is a document the two parents
    // fill in over weeks, not something the bottom bar should carry.
    destination(
        route = Screen.ParentingPlan.route,
        transitions = DestinationTransitions.PUSH
    ) {
        ParentingPlanScreen(
            onNavigateBack = { navController.popBackStack() },
            onProposeSchedule = { questionId ->
                navController.navigate(Screen.CustodySetup.routeFor(questionId))
            }
        )
    },

    // The communication record (MON-3), off Settings beside the parenting plan: both are
    // documents two parents may hand to a court, and neither is a tab's daily business.
    destination(
        route = Screen.Export.route,
        arguments = listOf(
            navArgument(Screen.Export.ARG_THREAD) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(Screen.Export.ARG_FAMILY) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) {
        ExportScreen(onNavigateBack = { navController.popBackStack() })
    },

    // A month in review, beside the export: another look back over the family's records.
    destination(
        route = Screen.MonthReview.route,
        transitions = DestinationTransitions.PUSH
    ) {
        MonthReviewScreen(onNavigateUp = { navController.popBackStack() })
    },

    // Records without a family (owner decision, October 2026): opened from its row in
    // Settings → Family and from Home's one-time question.
    destination(
        route = Screen.UnassignedRecords.route,
        transitions = DestinationTransitions.PUSH
    ) {
        com.coparently.app.presentation.family.UnassignedRecordsScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    // The document vault (MON-23), beside the export: the family's papers, shared with both
    // parents, opened from Settings → Family like the other family records.
    destination(
        route = Screen.Documents.route,
        arguments = listOf(
            navArgument(Screen.Documents.ARG_FAMILY) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) {
        FamilyDocumentsScreen(onNavigateUp = { navController.popBackStack() })
    },

    // Family → Money → Child support (owner decision, 25 October 2026).
    destination(
        route = Screen.Support.route,
        transitions = DestinationTransitions.PUSH
    ) {
        com.coparently.app.presentation.support.SupportScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    // Family → Earlier families (owner decision, 25 October 2026): an ended family's
    // records, out of every live family's screens and read here.
    destination(
        route = Screen.EarlierFamilies.route,
        transitions = DestinationTransitions.PUSH
    ) {
        com.coparently.app.presentation.family.EarlierFamiliesScreen(
            onNavigateUp = { navController.popBackStack() },
            onOpen = { navController.navigate(Screen.EarlierFamily.createRoute(it)) }
        )
    },
    destination(
        route = Screen.EarlierFamily.route,
        arguments = listOf(navArgument(Screen.EarlierFamily.ARG_FAMILY_ID) { type = NavType.StringType }),
        transitions = DestinationTransitions.PUSH
    ) { entry ->
        val familyId = entry.stringArgument(Screen.EarlierFamily.ARG_FAMILY_ID).orEmpty()
        com.coparently.app.presentation.family.EarlierFamilyScreen(
            familyId = familyId,
            onNavigateUp = { navController.popBackStack() },
            onOpenDocuments = { navController.navigate(Screen.Documents.forEarlierFamily(familyId)) },
            onExport = { navController.navigate(Screen.Export.forEarlierFamily(familyId)) }
        )
    },

    // The private journal (MON-22), after the vault: this parent's own notes, kept on this
    // phone only. The list, then the editor as a third level, like Pets.
    destination(
        route = Screen.Journal.route,
        transitions = DestinationTransitions.PUSH
    ) {
        JournalListScreen(
            onNavigateUp = { navController.popBackStack() },
            onOpenEntry = { id -> navController.navigate(Screen.JournalEditor.createRoute(id)) },
            onNewEntry = {
                navController.navigate(Screen.JournalEditor.createRoute(JournalEditorViewModel.NEW_ENTRY))
            }
        )
    },

    // Stickers between the parents (October 2026), off Settings → Family.
    destination(
        route = Screen.Stickers.route,
        transitions = DestinationTransitions.PUSH
    ) {
        MyStickersScreen(onNavigateUp = { navController.popBackStack() })
    },

    destination(
        route = Screen.JournalEditor.route,
        arguments = listOf(
            navArgument(JournalEditorViewModel.ARG_ENTRY_ID) { type = NavType.StringType }
        ),
        transitions = DestinationTransitions.PUSH
    ) {
        JournalEditorScreen(onNavigateUp = { navController.popBackStack() })
    },

    destination(
        route = Screen.ChildInfo.route,
        transitions = DestinationTransitions.PUSH
    ) {
        ChildInfoScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onOpenChild = { childInfoId ->
                navController.navigate(Screen.ChildDetail.createRoute(childInfoId))
            },
            onAddChild = {
                navController.navigate(Screen.EditChildInfo.createRoute("new"))
            }
        )
    },

    // The child's own record. A third level rather than the two Pets uses: the summary
    // here carries medical photos, the medical profile and the guest-access group, none
    // of which belong in a form.
    destination(
        route = Screen.ChildDetail.route,
        arguments = listOf(
            navArgument(Screen.ChildDetail.ARG_CHILD_INFO_ID) {
                type = NavType.StringType
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) { backStackEntry ->
        val childInfoId = backStackEntry.stringArgument(Screen.ChildDetail.ARG_CHILD_INFO_ID)
            .orEmpty()
        com.coparently.app.presentation.childinfo.ChildDetailScreen(
            childInfoId = childInfoId,
            onNavigateBack = { navController.popBackStack() },
            onEditClick = { id ->
                navController.navigate(Screen.EditChildInfo.createRoute(id))
            }
        )
    },

    destination(
        route = Screen.EditChildInfo.route,
        arguments = listOf(
            navArgument(Screen.EditChildInfo.ARG_CHILD_INFO_ID) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val childInfoId = backStackEntry.stringArgument(Screen.EditChildInfo.ARG_CHILD_INFO_ID)
            ?: "new"
        com.coparently.app.presentation.childinfo.AddEditChildInfoScreen(
            childInfoId = childInfoId,
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    },

    // Pets: a list screen plus its editor, both detail routes (bottom bar hidden),
    // mirroring the ChildInfo pair above.
    destination(
        route = Screen.Pets.route,
        transitions = DestinationTransitions.PUSH
    ) {
        PetsScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditPet = { petId ->
                navController.navigate(Screen.EditPet.createRoute(petId))
            }
        )
    },

    destination(
        route = Screen.EditPet.route,
        arguments = listOf(
            navArgument(Screen.EditPet.ARG_PET_ID) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val petId = backStackEntry.stringArgument(Screen.EditPet.ARG_PET_ID) ?: "new"
        AddEditPetScreen(
            petId = petId,
            onNavigateBack = { navController.popBackStack() }
        )
    },

    destination(
        route = Screen.Pairing.route,
        arguments = listOf(
            navArgument(Screen.Pairing.ARG_CODE) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(Screen.Pairing.ARG_ENTER) {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument(Screen.Pairing.ARG_UNPAIR) {
                type = NavType.BoolType
                defaultValue = false
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) { backStackEntry ->
        PairingScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onCustodyConflict = {
                navController.navigate(Screen.CustodyConflict.route)
            },
            prefilledCode = backStackEntry.stringArgument(Screen.Pairing.ARG_CODE)
                ?.takeIf { it.isNotEmpty() },
            startOnCodeEntry = backStackEntry.booleanArgument(Screen.Pairing.ARG_ENTER) ?: false,
            startOnUnpair = backStackEntry.booleanArgument(Screen.Pairing.ARG_UNPAIR) ?: false
        )
    },

    destination(
        route = Screen.GuestAccept.route,
        arguments = listOf(
            navArgument(Screen.GuestAccept.ARG_CODE) {
                type = NavType.StringType
                defaultValue = ""
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) { backStackEntry ->
        com.coparently.app.presentation.guests.GuestAcceptScreen(
            onDone = { navController.popBackStack() },
            prefilledCode = backStackEntry.stringArgument(Screen.GuestAccept.ARG_CODE)
                ?.takeIf { it.isNotEmpty() }
        )
    },

    destination(
        route = Screen.CustodyConflict.route,
        transitions = DestinationTransitions.PUSH
    ) {
        // No `onNavigateBack`: the screen offers two actions and no third exit, and
        // swallows the system back gesture itself. This lambda runs only once a choice
        // has been written (or when there is no conflict left to show), so popping here
        // never discards an unmade decision.
        com.coparently.app.presentation.pairing.CustodyConflictScreen(
            onResolved = {
                navController.popBackStack()
            }
        )
    },

    // The school import (MON-8): Settings → Sync, a detail route like the calendar links.
    destination(route = Screen.SchoolImport.route) {
        com.coparently.app.presentation.school.SchoolImportScreen(
            onNavigateUp = { navController.popBackStack() },
            onConnect = { navController.navigate(Screen.SchoolConnect.createRoute()) },
            onReconnect = { id -> navController.navigate(Screen.SchoolConnect.createRoute(id)) }
        )
    },

    // Connecting a school, or signing one in again, off the school import screen.
    destination(
        route = Screen.SchoolConnect.route,
        arguments = listOf(
            navArgument(Screen.SchoolConnect.ARG_CONNECTION_ID) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        com.coparently.app.presentation.school.ConnectSchoolScreen(
            onNavigateUp = { navController.popBackStack() },
            onDone = { navController.popBackStack() }
        )
    },

    // Read-only calendar links for an iPhone (MON-17). A Settings detail route.
    destination(route = Screen.CalendarFeed.route) {
        com.coparently.app.presentation.settings.CalendarFeedScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    // Data sources and licences (MON-13's ODbL attribution). A Settings detail route.
    destination(route = Screen.DataSources.route) {
        com.coparently.app.presentation.settings.DataSourcesScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    // The parents' friend list, and the friend's own profile. Detail routes: the
    // bottom bar hides and an up-arrow returns, like every other Settings destination.
    destination(route = Screen.Friends.route) {
        com.coparently.app.presentation.friends.FriendsScreen(
            onNavigateUp = { navController.popBackStack() },
            onOpenFriend = { uid ->
                navController.navigate(Screen.FriendDetail.createRoute(uid))
            },
            onOpenMyProfile = { navController.navigate(Screen.FriendProfile.route) },
            onOpenFriendCalendar = { familyId ->
                navController.navigate(Screen.FriendCalendar.createRoute(familyId))
            }
        )
    },

    destination(
        route = Screen.FriendDetail.route,
        arguments = listOf(
            navArgument(Screen.FriendDetail.ARG_FRIEND_UID) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val friendUid = backStackEntry.stringArgument(Screen.FriendDetail.ARG_FRIEND_UID).orEmpty()
        com.coparently.app.presentation.friends.FriendDetailScreen(
            friendUid = friendUid,
            onNavigateUp = { navController.popBackStack() },
            // Revoking removes the row this screen was opened from, so it returns to
            // the list rather than leaving a card for an access that no longer exists.
            onRevoked = { navController.popBackStack() }
        )
    },

    destination(route = Screen.FriendProfile.route) {
        com.coparently.app.presentation.friends.FriendProfileScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    // A family friend's read-only calendar (phase 2), keyed by the family it reads.
    destination(
        route = Screen.FriendCalendar.route,
        arguments = listOf(
            navArgument(Screen.FriendCalendar.ARG_FAMILY_ID) { type = NavType.StringType }
        )
    ) {
        com.coparently.app.presentation.friends.FriendCalendarScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    // Professional access (MON-18): the parents' list and, on a professional's phone, the
    // families they read. The two read-only views are detail routes keyed by grant id.
    destination(route = Screen.Professionals.route) {
        com.coparently.app.presentation.professionals.ProfessionalsScreen(
            onNavigateUp = { navController.popBackStack() },
            onOpenCalendar = { grantId ->
                navController.navigate(Screen.ProfessionalCalendar.createRoute(grantId))
            },
            onOpenPlan = { grantId ->
                navController.navigate(Screen.ProfessionalPlan.createRoute(grantId))
            }
        )
    },

    destination(
        route = Screen.ProfessionalCalendar.route,
        arguments = listOf(
            navArgument(Screen.ProfessionalCalendar.ARG_GRANT_ID) { type = NavType.StringType }
        )
    ) {
        com.coparently.app.presentation.professionals.ProfessionalCalendarScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    destination(
        route = Screen.ProfessionalPlan.route,
        arguments = listOf(
            navArgument(Screen.ProfessionalPlan.ARG_GRANT_ID) { type = NavType.StringType }
        )
    ) {
        com.coparently.app.presentation.professionals.ProfessionalPlanScreen(
            onNavigateUp = { navController.popBackStack() }
        )
    },

    destination(
        route = Screen.CustodySetup.route,
        // Read by `CustodySetupViewModel` and `SeasonalScheduleViewModel` through their
        // SavedStateHandle; blank opens the editor exactly as it always opened.
        arguments = listOf(
            navArgument(Screen.CustodySetup.ARG_PLAN_QUESTION) {
                type = NavType.StringType
                defaultValue = ""
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) {
        com.coparently.app.presentation.custody.CustodySetupScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    },

    // Both are detail screens: neither route is in BottomNavDestination.topLevelRoutes,
    // so the bottom bar hides itself automatically, same as Settings/ChildInfo above.
    destination(
        route = Screen.MyProfile.route,
        transitions = DestinationTransitions.PUSH
    ) {
        com.coparently.app.presentation.profile.ProfileScreen(
            editable = true,
            onNavigateUp = navController::popBackStack
        )
    },

    destination(
        route = Screen.CoParentProfile.route,
        transitions = DestinationTransitions.PUSH
    ) {
        com.coparently.app.presentation.profile.ProfileScreen(
            editable = false,
            onNavigateUp = navController::popBackStack
        )
    },

    // Chat & Communications
    destination(
        route = Screen.Conversations.route,
        arguments = listOf(
            navArgument(Screen.Conversations.ARG_DRAFT) {
                type = NavType.StringType
                defaultValue = ""
            }
        ),
        transitions = DestinationTransitions.TAB
    ) { backStackEntry ->
        val draft = backStackEntry.stringArgument(Screen.Conversations.ARG_DRAFT).orEmpty()
        com.coparently.app.presentation.chat.ConversationsScreen(
            onConversationClick = { conversationId ->
                navController.navigate(
                    Screen.Chat.createRoute(conversationId, draft.ifEmpty { null })
                )
            },
            onNavigateToPairing = {
                navController.navigate(Screen.Pairing.routeWithCode(null))
            },
            onOpenSettings = {
                navController.navigate(Screen.Settings.route)
            },
            // With one co-parent there is one conversation, and the tab renders that
            // thread in place — so the draft and the change-request route have to
            // reach it here too, not only via the Chat detail route below.
            draft = draft,
            onRequestChangeForEvent = { eventId ->
                navController.navigate(Screen.RequestChange.createRoute(eventId))
            },
            onOpenChangeRequest = { eventId ->
                navController.navigate(Screen.ChangeRequests.createRoute(eventId))
            },
            // A day-swap chat card, and a card about an event still waiting on this
            // parent's answer: the inbox with nothing highlighted — a day swap's
            // entity is a date the event-id argument would misread.
            onOpenInbox = {
                navController.navigate(Screen.ChangeRequests.createRoute())
            },
            // A card about an event opens its preview over the thread; the preview's
            // Edit opens the editor, as Home's and the calendar's previews do.
            onEditEvent = { eventId ->
                navController.navigate(Screen.EditEvent.createRoute(eventId))
            },
            // The banner over a thread kept after the co-parent deleted their account:
            // the same export screen Settings → Family opens, naming that thread.
            onOpenExport = { conversationId ->
                navController.navigate(Screen.Export.createRoute(conversationId))
            },
            // "Block and stop sharing" (F-10): the pairing screen, opened on its
            // existing unpair confirmation. Unpairing is the block.
            onBlock = {
                navController.navigate(Screen.Pairing.routeForUnpair())
            },
            onOpenSupport = { navController.navigate(Screen.Support.route) }
        )
    },

    destination(
        route = Screen.Chat.route,
        arguments = listOf(
            navArgument(Screen.Chat.ARG_CONVERSATION_ID) {
                type = NavType.StringType
            },
            navArgument(Screen.Chat.ARG_DRAFT) {
                type = NavType.StringType
                defaultValue = ""
            }
        ),
        transitions = DestinationTransitions.PUSH
    ) { backStackEntry ->
        val conversationId = backStackEntry.stringArgument(Screen.Chat.ARG_CONVERSATION_ID)
            ?: return@destination
        com.coparently.app.presentation.chat.ChatScreen(
            conversationId = conversationId,
            draft = backStackEntry.stringArgument(Screen.Chat.ARG_DRAFT).orEmpty(),
            onBack = {
                navController.popBackStack()
            },
            onRequestChangeForEvent = { eventId ->
                navController.navigate(
                    Screen.RequestChange.createRoute(eventId)
                )
            },
            onOpenChangeRequest = { eventId ->
                navController.navigate(Screen.ChangeRequests.createRoute(eventId))
            },
            // A day-swap chat card, and a card about an event still waiting on this
            // parent's answer: the inbox with nothing highlighted — a day swap's
            // entity is a date the event-id argument would misread.
            onOpenInbox = {
                navController.navigate(Screen.ChangeRequests.createRoute())
            },
            // A card about an event opens its preview over the thread; the preview's
            // Edit opens the editor, as Home's and the calendar's previews do.
            onEditEvent = { eventId ->
                navController.navigate(Screen.EditEvent.createRoute(eventId))
            },
            // The banner over a thread kept after the co-parent deleted their account:
            // the same export screen Settings → Family opens, naming that thread.
            onOpenExport = { conversationId ->
                navController.navigate(Screen.Export.createRoute(conversationId))
            },
            // "Block and stop sharing" (F-10): the pairing screen, opened on its
            // existing unpair confirmation. Unpairing is the block.
            onBlock = {
                navController.navigate(Screen.Pairing.routeForUnpair())
            },
            onOpenSupport = { navController.navigate(Screen.Support.route) }
        )
    },

    // The Family tab (new-user review, October 2026): it replaced the Expenses tab and
    // holds what Settings used to — the family's money, arrangements, records and people.
    destination(
        route = Screen.Family.route,
        transitions = DestinationTransitions.TAB
    ) {
        com.coparently.app.presentation.family.FamilyScreen(
            destinations = com.coparently.app.presentation.family.FamilyDestinations(
                expenses = { navController.navigate(Screen.Expenses.route) },
                support = { navController.navigate(Screen.Support.route) },
                custody = { navController.navigate(Screen.CustodySetup.routeFor()) },
                parentingPlan = { navController.navigate(Screen.ParentingPlan.route) },
                export = { navController.navigate(Screen.Export.createRoute()) },
                monthReview = { navController.navigate(Screen.MonthReview.route) },
                childInfo = { navController.navigate(Screen.ChildInfo.route) },
                pets = { navController.navigate(Screen.Pets.route) },
                contacts = { navController.navigate(Screen.Contacts.route) },
                documents = { navController.navigate(Screen.Documents.createRoute()) },
                pairing = { navController.navigate(Screen.Pairing.routeWithCode(null)) },
                coParentProfile = { navController.navigate(Screen.CoParentProfile.route) },
                friends = { navController.navigate(Screen.Friends.route) },
                professionals = { navController.navigate(Screen.Professionals.route) },
                unassignedRecords = { navController.navigate(Screen.UnassignedRecords.route) },
                journal = { navController.navigate(Screen.Journal.route) },
                stickers = { navController.navigate(Screen.Stickers.route) },
                earlierFamilies = { navController.navigate(Screen.EarlierFamilies.route) },
                settings = { navController.navigate(Screen.Settings.route) }
            )
        )
    },

    // Expenses: a detail screen since the Family tab replaced its tab, opened from the
    // tab's money card, Home's spend tile and a push. Full width rather than `pane()`
    // (design item 19's one exception): from 840 dp it lays the month out in two panes,
    // which a 640 dp column would squeeze.
    destination(route = Screen.Expenses.route) {
        val tabSwitcher = LocalTabSwitcher.current
        com.coparently.app.presentation.expenses.ExpenseScreen(
            onNavigateUp = { navController.popBackStack() },
            onAddExpense = {
                navController.navigate(Screen.AddExpense.route)
            },
            onEditExpense = { expenseId ->
                navController.navigate(Screen.EditExpense.createRoute(expenseId))
            },
            onSettleUp = {
                // The settle-up card is already in the thread (`SettleUpDialog`); this
                // only shows it. Same tab semantics as navigateToTab — a plain
                // navigate() here pushed the Chat route onto the tab Expenses was
                // opened from, so the next tab switch saved that mixed stack and a
                // later visit restored the chat screen on top of it. Through the tab
                // switcher, so the iPhone's shell selects its Chat tab (MON-24 phase 3).
                tabSwitcher.selectFresh(BottomNavDestination.CHAT)
            }
        )
    },

    destination(
        route = Screen.AddExpense.route
    ) {
        com.coparently.app.presentation.expenses.AddExpenseScreen(
            onBack = {
                navController.popBackStack()
            }
        )
    },

    destination(
        route = Screen.EditExpense.route,
        arguments = listOf(
            navArgument(Screen.EditExpense.ARG_EXPENSE_ID) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val expenseId = backStackEntry.stringArgument(Screen.EditExpense.ARG_EXPENSE_ID) ?: return@destination
        com.coparently.app.presentation.expenses.AddExpenseScreen(
            onBack = {
                navController.popBackStack()
            },
            expenseId = expenseId
        )
    }
)

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
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(Res.string.navigation_loading),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** True when both ends of the transition are bottom-bar tabs. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in BottomNavDestination.topLevelRoutes &&
        targetState.destination.route in BottomNavDestination.topLevelRoutes

/**
 * A tab's enter transition: fade-through between two tabs (peers have no direction), the
 * standard push when arriving from or returning past a detail screen.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter(forward: Boolean): EnterTransition =
    when {
        isTabSwitch() -> fadeThroughIn()
        forward -> slideInFromRight()
        else -> slideInFromLeft()
    }

/** A tab's exit transition; the counterpart of [tabEnter]. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExit(forward: Boolean): ExitTransition =
    when {
        isTabSwitch() -> fadeThroughOut()
        forward -> slideOutToLeft()
        else -> slideOutToRight()
    }

/** The route of the screen a Requests row of child support, a professional or the plan opens. */
private val AgreementScreen.route: String
    get() = when (this) {
        AgreementScreen.SUPPORT -> Screen.Support.route
        AgreementScreen.PROFESSIONALS -> Screen.Professionals.route
        AgreementScreen.PARENTING_PLAN -> Screen.ParentingPlan.route
    }

/**
 * A string argument of this entry's route, as `Bundle.getString` read it before the graph moved to
 * common code (MON-24 phase 2, Task 12): the value, or null when the entry has no arguments, the
 * key is absent or its value is null.
 */
private fun NavBackStackEntry.stringArgument(key: String): String? =
    arguments?.read { if (!contains(key) || isNull(key)) null else getString(key) }

/**
 * An integer argument of this entry's route, as `Bundle.getInt` read it: null when the entry has
 * no arguments, and 0 for an absent key, which a declared argument with a default never is.
 */
private fun NavBackStackEntry.intArgument(key: String): Int? =
    arguments?.read { if (!contains(key) || isNull(key)) 0 else getInt(key) }

/**
 * A boolean argument of this entry's route, as `Bundle.getBoolean` read it: null when the entry
 * has no arguments, and false for an absent key, which a declared argument with a default never is.
 */
private fun NavBackStackEntry.booleanArgument(key: String): Boolean? =
    arguments?.read { if (!contains(key) || isNull(key)) false else getBoolean(key) }
