package com.coparently.app.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.presentation.changerequests.RequestsBadgeViewModel
import com.coparently.app.presentation.chat.ChatViewModel
import com.coparently.app.presentation.common.ConnectivityBanner
import com.coparently.app.presentation.common.ConnectivityViewModel
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.animations.*
import com.coparently.app.presentation.consent.TelemetryConsentViewModel
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import com.coparently.app.presentation.theme.appGround
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navigation graph for the app.
 * Builds its `NavHost` from [rememberAppDestinations], the destinations both shells read (MON-24
 * phase 3), and provides [LocalTabSwitcher] as the bottom bar's own tab switch.
 * Includes authentication guard to redirect unauthenticated users to AuthScreen.
 * Top-level destinations (Calendar / Chat / Expenses / Settings) share a bottom
 * navigation bar; detail screens hide it.
 *
 * @param pendingInviteCodes The `coplanly://pair` and `coplanly://guest` codes awaiting
 *   hand-off (the shell owns both: `MainActivity` on Android), each with its own consumption
 *   callback — see [PendingInviteCodes].
 * @param pendingChatOpen A `coplanly://chat` deep link awaiting hand-off to the Chat tab,
 *   bundled with its own consumption callback (see [PendingChatOpen]) rather than as two more
 *   loose parameters — that shape would have pushed this function's parameter count to
 *   detekt's `LongParameterList` threshold of 6, which is also why the two invite codes
 *   above travel together.
 * @param pendingDestinationOpen The screen a tapped push names ([PushDestination], D-13),
 *   awaiting hand-off, with its consumption callback — see [PendingDestinationOpen].
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    syncViewModel: SyncViewModel,
    pendingInviteCodes: PendingInviteCodes,
    pendingChatOpen: PendingChatOpen,
    pendingDestinationOpen: PendingDestinationOpen
) {
    val authStateViewModel: AuthStateViewModel = koinViewModel()
    val telemetryConsentViewModel: TelemetryConsentViewModel = koinViewModel()
    val connectivityViewModel: ConnectivityViewModel = koinViewModel()
    val isAuthenticated by authStateViewModel.isAuthenticated.collectAsState()
    val isLoading by authStateViewModel.isLoading.collectAsState()
    val needsOnboarding by authStateViewModel.needsOnboarding.collectAsState()
    val chatUnreadCount = rememberChatUnreadCount()
    val homeBadgeCount = rememberHomeRequestsCount()

    // Determine start destination based on authentication state, and — for a signed-in account
    // — on whether the first-run questionnaire still has to run. That second answer is a Room
    // read, so it is unknown for a moment after authentication resolves; while it is unknown
    // this must stay on Loading. Routing an unknown answer to Home would flash the dashboard
    // and then replace it with a questionnaire, which is worse than a moment's spinner.
    //
    // The telemetry question comes before everything, sign-in included (REL-5): both SDKs would
    // otherwise have collected a session — an app_open, a screen_view of the auth screen — before
    // anybody had been asked. It is also why this is not a step inside the onboarding wizard,
    // which belongs to an account: this question is older than the account.
    //
    // `UNANSWERED` is a real answer here rather than an absence, so there is no third "still
    // reading" state to park on Loading for; the value is one already-read field.
    val telemetryConsent by telemetryConsentViewModel.consent.collectAsState()

    val startDestination = startDestinationFor(telemetryConsent, isLoading, isAuthenticated, needsOnboarding)
    val destinations = rememberAppDestinations(navController, syncViewModel, authStateViewModel)
    // A tab switch from inside a destination, or from a tapped push, takes the bottom bar's own
    // back-stack policy (MON-24 phase 3; the iPhone's shell provides its own switcher).
    val tabSwitcher = remember(navController) {
        object : TabSwitcher {
            override fun select(tab: BottomNavDestination) = navController.navigateToTab(tab)

            override fun selectFresh(tab: BottomNavDestination) = navController.navigateToTabFresh(tab)
        }
    }

    CompositionLocalProvider(LocalTabSwitcher provides tabSwitcher) {
        PairingDeepLinkEffect(
            pendingInviteCodes.pairing,
            isAuthenticated,
            navController,
            pendingInviteCodes.onPairingConsumed
        )
        GuestDeepLinkEffect(pendingInviteCodes, isAuthenticated, navController)
        ChatDeepLinkEffect(pendingChatOpen, isAuthenticated, navController)
        PushDestinationEffect(pendingDestinationOpen, isAuthenticated, navController)

        NavGraphScaffold(
            navController,
            startDestination,
            destinations,
            TabBadges(chatUnreadCount, homeBadgeCount),
            connectivityViewModel
        )
    }
}

/**
 * The root Scaffold around the `NavHost`: the offline banner, the bottom bar or the rail, the
 * snackbars that outlive their screen, and the `NavHost` built from [destinations].
 *
 * @param navController The graph's controller
 * @param startDestination The route the graph starts on ([startDestinationFor])
 * @param destinations Every destination, from [rememberAppDestinations]
 * @param badges The Chat and Home tabs' badges
 * @param connectivityViewModel Whether the phone is offline, for the banner
 */
@Composable
private fun NavGraphScaffold(
    navController: NavHostController,
    startDestination: String,
    destinations: List<AppDestination>,
    badges: TabBadges,
    connectivityViewModel: ConnectivityViewModel
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val offline by connectivityViewModel.isOffline.collectAsState()

    val navigationLayout = rememberNavigationLayout()
    val onTopLevelRoute = currentRoute in BottomNavDestination.topLevelRoutes

    Scaffold(
        // The lavender ground is drawn here once; every screen's Scaffold is transparent over it.
        modifier = Modifier.appGround(),
        containerColor = Color.Transparent,
        // Empty while online, so the status-bar inset the NavHost consumes below is the
        // Scaffold's own; while offline the banner pads itself below the status bar instead.
        topBar = { ConnectivityBanner(offline = offline) },
        // Messages that outlive their screen (D-25): a save that navigates away and still owes a
        // warning. Above the bottom bar on a tab; at the bottom on a detail screen.
        snackbarHost = { LocalAppMessages.current?.let { SnackbarHost(it.hostState) } },
        bottomBar = {
            BottomBarSlot(
                layout = navigationLayout,
                visible = onTopLevelRoute,
                currentRoute = currentRoute,
                navController = navController,
                badges = badges
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            RailSlot(
                layout = navigationLayout,
                visible = onTopLevelRoute,
                currentRoute = currentRoute,
                navController = navController,
                badges = badges
            )
            NavHost(
                navController = navController,
                startDestination = startDestination,
                // Consumed as well as applied. Every screen below brings its own Scaffold and
                // TopAppBar, which apply the system bars again unless they are told these are taken:
                // padding alone gave every top bar a second status-bar inset (88 dp where M3's is 64)
                // and lifted each tab's FAB by a second navigation-bar inset. And the keyboard is
                // taken here, once, for every screen: the manifest's adjustResize stops the window
                // panning — which scrolled the chat header away and left a form's sticky Save under
                // the keyboard — and this is what then resizes the screen above it
                // (docs/AUDIT-2026-10-design.md D-2, D-9). The Row above applies and consumes the
                // Scaffold's padding, so a rail beside this is inset once too.
                modifier = Modifier
                    .weight(1f)
                    .imePadding(),
                // The standard push for any route that names no transitions of its own. Without
                // these, such a route got Navigation's own 700 ms crossfade — more than twice as
                // slow as every other screen.
                enterTransition = { slideInFromRight() },
                exitTransition = { slideOutToLeft() },
                popEnterTransition = { slideInFromLeft() },
                popExitTransition = { slideOutToRight() }
            ) {
                destinations.forEach { addDestination(it) }
            }
        }
    }
}

/**
 * The Chat tab's unread-message count, for [CoPlanlyBottomBar]'s badge.
 *
 * `koinViewModel()` resolves against [androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner],
 * which is decided by *composition position*, not by which Kotlin function the call happens
 * to sit in — so calling it here, from the same place [NavGraph] calls it for
 * `authStateViewModel`, keeps this [ChatViewModel] instance scoped to that same ambient owner
 * (the hosting Activity, so it survives for the app's lifetime) exactly as if the two lines
 * were inlined into [NavGraph] itself. They are pulled out into this small composable purely
 * to keep [NavGraph] to routing; internal so the iPhone's shell (MON-24 phase 3) can badge its own
 * tab bar the same way.
 *
 * The Chat/Conversations screens keep creating their *own* `koinViewModel()` instance, scoped
 * to their own back-stack entry, independent of this one; both merely observe the same
 * repository-backed flows, so there is no read-mark or state conflict between the two.
 */
@Composable
internal fun rememberChatUnreadCount(): Int {
    val chatViewModel: ChatViewModel = koinViewModel()
    val unreadCount by chatViewModel.unreadCount.collectAsState()
    return unreadCount
}

/**
 * The number on the Home tab's badge, read here for the reason [rememberChatUnreadCount] is: the
 * ViewModel is scoped to the ambient owner (the hosting Activity), so the bar is right while any
 * tab shows. The app opens on the Calendar, so what waits for this parent's answer has to be
 * visible from the bar (redesign, part 2a); it is the number Home's Requests row shows. Internal so
 * the iPhone's shell badges its own tab bar the same way.
 */
@Composable
internal fun rememberHomeRequestsCount(): Int {
    val badgeViewModel: RequestsBadgeViewModel = koinViewModel()
    val waiting by badgeViewModel.waitingOnMe.collectAsState()
    return waiting
}

/**
 * Navigates to [Screen.Pairing] once a `coplanly://pair` deep link's code is
 * both present and safe to act on.
 *
 * A deep-linked pairing code must never be redeemed automatically, and it
 * must never land an unauthenticated user on the pairing screen behind the
 * auth gate: it is only actioned once [isAuthenticated] is confirmed `true`
 * (never while still loading — `null` — never while confirmed `false`).
 * Until then the code stays pending and the user follows the normal Auth
 * flow; once they sign in, this recomposes and fires.
 *
 * `popUpTo(...) { inclusive = true }` (rather than `launchSingleTop`) is
 * deliberate: it drops any Pairing entry already on the back stack before
 * pushing a fresh one, so a second link opened while already on that screen
 * still creates a brand-new entry. That matters because `PairingScreen`
 * re-arms its confirmation dialog via `rememberSaveable(prefilledCode)` — a
 * reused (singleTop) entry keeps the old saved state and never shows the
 * dialog for the new code, verified on-device before switching to this
 * approach. One side effect of dropping the old entry: if the user was
 * mid-way through typing a code by hand on that screen, the in-progress
 * text and any open confirmation dialog are discarded along with it. That is
 * accepted as correct here — a deep-linked code must never silently win over
 * what the user is doing, so replacing rather than merging keeps the two
 * paths from bleeding into each other.
 *
 * @param pendingPairingCode The code awaiting hand-off, or null when none is
 *   outstanding — see [NavGraph]'s parameter of the same name.
 * @param isAuthenticated Current auth state (`null` while loading).
 * @param navController Used to perform the navigation once conditions are met.
 * @param onPairingCodeConsumed Called once the code has been handed to the
 *   pairing screen, so the caller can clear it and avoid re-navigating on
 *   the next recomposition.
 */
@Composable
internal fun PairingDeepLinkEffect(
    pendingPairingCode: StateFlow<String?>,
    isAuthenticated: Boolean?,
    navController: NavHostController,
    onPairingCodeConsumed: () -> Unit
) {
    val pairingCode by pendingPairingCode.collectAsState()
    LaunchedEffect(pairingCode, isAuthenticated) {
        if (pairingCode != null && isAuthenticated == true) {
            navController.navigate(Screen.Pairing.routeWithCode(pairingCode)) {
                popUpTo(Screen.Pairing.route) { inclusive = true }
            }
            onPairingCodeConsumed()
        }
    }
}

/**
 * The two invitation codes a deep link can carry, each with the callback that clears it.
 *
 * They travel together because they arrive the same way and are consumed the same way — and
 * because [NavGraph] cannot afford four more loose parameters (see its `pendingChatOpen`
 * doc). They stay *distinct fields* because the codes themselves are indistinguishable: six
 * characters from the same generator, redeemable by two different callables, and the host the
 * link arrived on is the only thing that says which. Collapsing them into one field would
 * throw away that answer.
 *
 * @property pairing A `coplanly://pair` code, or null when none is outstanding. Empty string
 *   means "link present, no code" — see `MainActivity.readLaunchLink`.
 * @property onPairingConsumed Clears [pairing] once the pairing screen has it.
 * @property guest A `coplanly://guest` code, or null when none is outstanding. Never empty: a
 *   bare guest link is ignored rather than opening an empty screen.
 * @property onGuestConsumed Clears [guest] once the guest-accept screen has it.
 */
class PendingInviteCodes(
    val pairing: StateFlow<String?>,
    val onPairingConsumed: () -> Unit,
    val guest: StateFlow<String?>,
    val onGuestConsumed: () -> Unit
)

/**
 * Navigates to the guest-accept screen when a `coplanly://guest` link is pending.
 *
 * Same hand-off shape as [PairingDeepLinkEffect] and, deliberately, a separate effect
 * navigating to a separate route. Nothing here should be able to end at the pairing screen.
 */
@Composable
internal fun GuestDeepLinkEffect(
    pendingInviteCodes: PendingInviteCodes,
    isAuthenticated: Boolean?,
    navController: NavHostController
) {
    val guestCode by pendingInviteCodes.guest.collectAsState()
    LaunchedEffect(guestCode, isAuthenticated) {
        if (guestCode != null && isAuthenticated == true) {
            navController.navigate(Screen.GuestAccept.routeWithCode(guestCode))
            pendingInviteCodes.onGuestConsumed()
        }
    }
}

/**
 * A `coplanly://chat` deep link awaiting hand-off, or null while none is pending.
 *
 * [conversationId] carries the id from the link's `?conversationId=…` query parameter (see
 * [com.coparently.app.domain.chat.ChatUri]), or null for a bare `coplanly://chat` link — a
 * manual test push, an older payload, or a hand-typed link may carry none, and that must
 * degrade to opening the Chat tab's list rather than fail.
 */
data class PendingChatLink(val conversationId: String?)

/**
 * A [PendingChatLink] awaiting hand-off, bundled with the callback that clears it once
 * consumed.
 *
 * Exists purely to keep [NavGraph]'s own signature from growing by two more loose parameters
 * every time another deep link is added — see [NavGraph]'s `pendingChatOpen` doc.
 *
 * @property link The pending link, or null when none is outstanding.
 * @property onConsumed Called once the link has been acted on, so the owner (the shell:
 *   `MainActivity` on Android) can clear it and avoid re-navigating on the next recomposition.
 */
class PendingChatOpen(val link: StateFlow<PendingChatLink?>, val onConsumed: () -> Unit)

/**
 * The route the graph starts on, in the order [NavGraph]'s comment explains: the telemetry
 * question before everything, then loading, sign-in, and the first-run questionnaire while its
 * answer is unknown or owed. A function of its own so [NavGraph] stays one flat list of routes,
 * and public because the Android shell holds its system splash while this answers
 * [Screen.Loading] (`MainActivity.firstScreenUnknown`).
 */
fun startDestinationFor(
    telemetryConsent: TelemetryConsent,
    isLoading: Boolean,
    isAuthenticated: Boolean?,
    needsOnboarding: Boolean?
): String = when {
    telemetryConsent == TelemetryConsent.UNANSWERED -> Screen.PrivacyConsent.route
    isLoading -> Screen.Loading.route
    isAuthenticated != true -> Screen.Auth.route
    needsOnboarding == null -> Screen.Loading.route
    needsOnboarding == true -> Screen.Onboarding.route
    else -> Screen.Calendar.route
}

/**
 * The counts the tab bar or rail badges.
 *
 * @property chatUnread Messages from the co-parent not yet read, on Chat
 * @property homeRequests Requests waiting for this parent's answer, on Home
 */
private data class TabBadges(val chatUnread: Int, val homeRequests: Int)

/**
 * The bottom bar on a narrow window, sliding in and out with the four tab routes; nothing on a
 * wide one, which gets [RailSlot] instead.
 *
 * @param layout Bar or rail, for this window
 * @param visible Whether the route on screen is one of the four tabs
 * @param currentRoute The route on screen, to mark its tab
 * @param navController Tabs switch through [navigateToTab]
 * @param badges The Chat and Home tabs' badges
 */
@Composable
private fun BottomBarSlot(
    layout: NavigationLayout,
    visible: Boolean,
    currentRoute: String?,
    navController: NavHostController,
    badges: TabBadges
) {
    if (layout != NavigationLayout.BAR) return
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        CoPlanlyBottomBar(
            currentRoute = currentRoute,
            onNavigate = navController::navigateToTab,
            chatUnreadCount = badges.chatUnread,
            homeBadgeCount = badges.homeRequests
        )
    }
}

/**
 * The rail down the start edge on a wide window (see [NavigationLayout]), on the same four routes
 * the bar shows on; nothing on a narrow window.
 *
 * @param layout Bar or rail, for this window
 * @param visible Whether the route on screen is one of the four tabs
 * @param currentRoute The route on screen, to mark its tab
 * @param navController Tabs switch through [navigateToTab]
 * @param badges The Chat and Home tabs' badges
 */
@Composable
private fun RowScope.RailSlot(
    layout: NavigationLayout,
    visible: Boolean,
    currentRoute: String?,
    navController: NavHostController,
    badges: TabBadges
) {
    if (layout != NavigationLayout.RAIL) return
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        CoPlanlyNavigationRail(
            currentRoute = currentRoute,
            onNavigate = navController::navigateToTab,
            chatUnreadCount = badges.chatUnread,
            homeBadgeCount = badges.homeRequests
        )
    }
}

/**
 * The screen a tapped push names, awaiting hand-off, bundled with the callback that clears it —
 * the same shape as [PendingChatOpen], for the same reason.
 *
 * @property destination The screen, or null when none is outstanding.
 * @property onConsumed Called once the screen has been opened.
 */
class PendingDestinationOpen(val destination: StateFlow<PushDestination?>, val onConsumed: () -> Unit)

/**
 * Opens the screen a tapped push names (D-13) once the account is known to be signed in — the
 * guard every deep link here has: nothing opens behind the auth gate, and a destination waits
 * through the sign-in rather than being dropped. The tabs go through [LocalTabSwitcher] (on Android
 * [navigateToTab]), so a push shares the bottom bar's back-stack policy; the detail screens are
 * pushed once.
 */
@Composable
internal fun PushDestinationEffect(
    open: PendingDestinationOpen,
    isAuthenticated: Boolean?,
    navController: NavHostController
) {
    val destination by open.destination.collectAsState()
    val tabSwitcher = LocalTabSwitcher.current
    LaunchedEffect(destination, isAuthenticated) {
        val target = destination
        if (target != null && isAuthenticated == true) {
            when (target) {
                PushDestination.HOME -> tabSwitcher.select(BottomNavDestination.HOME)
                PushDestination.CALENDAR -> tabSwitcher.select(BottomNavDestination.CALENDAR)
                PushDestination.EXPENSES -> {
                    // Expenses over the Family tab, so Back lands on the tab it belongs to.
                    tabSwitcher.select(BottomNavDestination.FAMILY)
                    navController.navigate(Screen.Expenses.route) { launchSingleTop = true }
                }
                PushDestination.CHANGE_REQUESTS ->
                    navController.navigate(Screen.ChangeRequests.createRoute()) { launchSingleTop = true }
                PushDestination.CHILD_INFO ->
                    navController.navigate(Screen.ChildInfo.route) { launchSingleTop = true }
                PushDestination.PROFESSIONALS ->
                    navController.navigate(Screen.Professionals.route) { launchSingleTop = true }
                PushDestination.FRIENDS ->
                    navController.navigate(Screen.Friends.route) { launchSingleTop = true }
                PushDestination.SUPPORT -> {
                    // Child support over the Family tab, like Expenses.
                    tabSwitcher.select(BottomNavDestination.FAMILY)
                    navController.navigate(Screen.Support.route) { launchSingleTop = true }
                }
            }
            open.onConsumed()
        }
    }
}

/**
 * The route a [PendingChatLink] should open: the specific thread when it carries a
 * conversation id, otherwise the Chat tab's conversation list.
 *
 * A pure function (no [Composable] dependency) purely so this fallback — the part the review
 * that added it cared about — is pinned by a plain unit test rather than only exercised
 * through Compose UI test infrastructure this project does not otherwise use.
 *
 * @param conversationId The id from the link, or null/blank for a bare `coplanly://chat` link.
 * @return The route to navigate to.
 */
internal fun chatDeepLinkRoute(conversationId: String?): String =
    if (conversationId.isNullOrBlank()) {
        Screen.Conversations.createRoute()
    } else {
        Screen.Chat.createRoute(conversationId)
    }

/**
 * Navigates to the Chat tab (or a specific thread) once a `coplanly://chat` deep link is both
 * pending and safe to act on — same authentication guard as [PairingDeepLinkEffect], for the
 * same reason: an unauthenticated user must follow the normal Auth flow rather than being
 * dropped straight onto a screen behind the auth gate.
 *
 * Unlike [PairingDeepLinkEffect], this never pops [Screen.Conversations] off the back stack
 * first: `PairingScreen` needed that because it re-arms a confirmation dialog via
 * `rememberSaveable`, but nothing in `ChatScreen`/`ConversationsScreen` has an analogous
 * stale-state problem, so a plain [NavHostController.navigate] with `launchSingleTop` is
 * enough to avoid stacking duplicate entries from a repeated tap.
 *
 * @param pendingChatOpen The pending link and its consumption callback.
 * @param isAuthenticated Current auth state (`null` while loading).
 * @param navController Used to perform the navigation once conditions are met.
 */
@Composable
internal fun ChatDeepLinkEffect(
    pendingChatOpen: PendingChatOpen,
    isAuthenticated: Boolean?,
    navController: NavHostController
) {
    val chatLink by pendingChatOpen.link.collectAsState()
    LaunchedEffect(chatLink, isAuthenticated) {
        if (chatLink != null && isAuthenticated == true) {
            navController.navigate(chatDeepLinkRoute(chatLink?.conversationId)) {
                launchSingleTop = true
            }
            pendingChatOpen.onConsumed()
        }
    }
}
