package com.coparently.app.ios.shell

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavOptionsBuilder
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.ios.IosLifecycle
import com.coparently.app.presentation.AppLaunch
import com.coparently.app.presentation.changerequests.RequestsBadgeViewModel
import com.coparently.app.presentation.chat.ChatViewModel
import com.coparently.app.presentation.common.AppMessages
import com.coparently.app.presentation.common.ConnectivityViewModel
import com.coparently.app.presentation.navigation.AppDestinationCatalog
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.presentation.navigation.CalendarTabTaps
import com.coparently.app.presentation.navigation.DestinationKind
import com.coparently.app.presentation.navigation.Screen
import com.coparently.app.presentation.navigation.TabSwitcher
import com.coparently.app.presentation.navigation.chatDeepLinkRoute
import com.coparently.app.presentation.navigation.startDestinationFor
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.presentation.platform.CredentialSignIn
import com.coparently.app.presentation.platform.ProvideIosShell
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.Koin
import platform.Foundation.setValue
import platform.UIKit.UITabBarController
import platform.UIKit.UITabBarControllerDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.tabBarItem
import platform.UIKit.willMoveToParentViewController
import platform.darwin.NSObject

/**
 * What every screen the shell hosts shares (MON-24 phase 3), held once by [NativeShell]: the build's
 * values, the ViewModels Android scopes to its activity that the screens' hosts read, the messages
 * that outlive a screen and the tab switcher, and the shell's answers to a stack.
 */
internal interface ShellEnvironment {
    val buildValues: AppBuildValues

    /** The credential sheets every screen is drawn with ([ProvideIosShell]). */
    val credentialSignIn: CredentialSignIn
    val syncViewModel: SyncViewModel
    val authStateViewModel: AuthStateViewModel
    val connectivity: ConnectivityViewModel
    val messages: AppMessages
    val tabSwitcher: TabSwitcher

    /** Which hosted screens the parent sees now (`LocalScreenVisible`). */
    val visibility: ShellVisibility

    /** A tab's screen navigated to [route], one of the screens before the app proper: show them. */
    fun showRootFlow(route: String)

    /** A stack's transition was cancelled (a back swipe let go of); see [NativeShell]. */
    fun transitionCancelled()
}

/**
 * The ViewModels one session of the shell shares — the root flow's screens, or the tabs' — such as
 * the palette's and the family view's, which `AppProviders` resolves: made with the session and
 * cleared with it, so nothing of a signed-out account's survives into the next.
 */
internal class ShellSession {
    private val store = ViewModelStore()

    /** The owner the session's screens resolve their shared ViewModels from. */
    val owner: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore get() = store
    }

    /** Ends the session: its ViewModels are cleared. */
    fun clear() = store.clear()
}

/**
 * The iPhone app's native shell (MON-24 phase 3, spec §5.2): the window's root view controller,
 * which shows either the screens before the app proper — loading, sign-in, onboarding — in one
 * navigation controller with no tab bar, or the four tabs ([TabShell]), as the start destination
 * says ([startDestinationFor], the one Android's `NavGraph` reads).
 *
 * It holds for its whole life what decides the start destination and what any screen reads
 * of it: [SyncViewModel], [AuthStateViewModel] and [ConnectivityViewModel]. What the screens share
 * beyond that — the palette's and the family view's ViewModels, the Chat badge's [ChatViewModel] —
 * belongs to a [ShellSession], made when the shell shows the root flow or the tabs and cleared
 * once they leave, so the four tabs share one of each and a sign-out keeps none of them.
 *
 * **The shell owns the switch to the screens before the app proper.** When a tab's screen
 * navigates to one (Settings' sign-out navigates to the sign-in screen), the tab does not mirror it
 * ([StackHost]): the shell shows the root flow from that route at once and ends the tabs, their
 * stacks and their ViewModels. Signing in again lands on fresh tabs.
 *
 * Each back swipe let go of is counted in the tab bar's accessibility identifier
 * ([CANCELLED_TRANSITIONS]), which the UI test reads to know a transition began and was cancelled;
 * nothing reads it in the app, and VoiceOver does not speak an identifier.
 *
 * The telemetry question is not asked on the iPhone (spec §5.2): it collects nothing, so the start
 * destination is decided as for an answer that collects nothing, and nothing is stored.
 *
 * **A `coplanly://` link opens once the tabs show a signed-in account** ([links], which
 * `IosApp.openUrl` arms after any family switch the link names, CLAUDE.md item 15), and each is
 * cleared once opened, as Android's `NavGraph` opens and clears `MainActivity`'s: a pairing or guest
 * code pushes its screen onto the selected tab (a pairing screen already there is replaced, so a
 * second link re-arms its question), a chat link selects the Chat tab and pushes the thread it
 * names. A link that arrives before sign-in or during onboarding waits for the tabs.
 */
internal class NativeShell(
    private val koin: Koin,
    override val buildValues: AppBuildValues,
    override val credentialSignIn: CredentialSignIn,
    private val links: AppLaunch
) : ShellEnvironment, TabSwitcher {

    /** The window's root: a container that holds the root flow or the tabs. */
    val root: UIViewController = UIViewController()

    private val scope: CoroutineScope = MainScope()
    private val store = ViewModelStore()
    override val authStateViewModel: AuthStateViewModel = shellViewModel(store)
    override val syncViewModel: SyncViewModel = shellViewModel(store)
    override val connectivity: ConnectivityViewModel = shellViewModel(store)
    override val messages: AppMessages = AppMessages(scope, SnackbarHostState())
    override val tabSwitcher: TabSwitcher get() = this
    override val visibility: ShellVisibility = ShellVisibility()

    private var flow: StackHost? = null

    /** The tabs while the shell shows them, which a pending link waits for. */
    private val shownTabs = MutableStateFlow<TabShell?>(null)
    private var tabs: TabShell?
        get() = shownTabs.value
        set(value) {
            shownTabs.value = value
        }
    private var session: ShellSession? = null
    private var shown: UIViewController? = null
    private var cancelledTransitions = 0

    /** Set when the root flow reached a tab's first screen before the start destination did. */
    private var tabsReached = false

    /** Starts following the start destination. Call once. */
    fun start() {
        scope.launch {
            IosLifecycle.foreground.collect { visibility.foreground = it }
        }
        scope.launch {
            combine(
                authStateViewModel.isLoading,
                authStateViewModel.isAuthenticated,
                authStateViewModel.needsOnboarding
            ) { loading, signedIn, onboarding ->
                startDestinationFor(TelemetryConsent.DENIED, loading, signedIn, onboarding)
            }.distinctUntilChanged().collect { route ->
                tabsReached = false
                show(route)
            }
        }
        openLinks()
    }

    /** Opens each pending link once the tabs show a signed-in account; see the class's KDoc. */
    private fun openLinks() {
        val codes = links.inviteCodes
        handOff(codes.pairing, codes.onPairingConsumed) { shown, code ->
            shown.navigateOnSelected(Screen.Pairing.routeWithCode(code)) {
                popUpTo(Screen.Pairing.route) { inclusive = true }
            }
        }
        handOff(codes.guest, codes.onGuestConsumed) { shown, code ->
            shown.navigateOnSelected(Screen.GuestAccept.routeWithCode(code))
        }
        handOff(links.chatOpen.link, links.chatOpen.onConsumed) { shown, link ->
            shown.select(BottomNavDestination.CHAT)
            // A bare link opens the tab's own first screen, the list; a thread is pushed over it.
            if (!link.conversationId.isNullOrBlank()) {
                shown.navigateOn(BottomNavDestination.CHAT, chatDeepLinkRoute(link.conversationId)) {
                    launchSingleTop = true
                }
            }
        }
    }

    /** Opens [pending]'s value with [open] once the tabs show a signed-in account, then [consume]s it. */
    private fun <T : Any> handOff(pending: StateFlow<T?>, consume: () -> Unit, open: (TabShell, T) -> Unit) {
        scope.launch {
            combine(shownTabs, authStateViewModel.isAuthenticated, pending) { shown, signedIn, value ->
                if (shown != null && signedIn == true) value?.let { shown to it } else null
            }.collect { ready ->
                if (ready != null) {
                    open(ready.first, ready.second)
                    consume()
                }
            }
        }
    }

    override fun select(tab: BottomNavDestination) {
        tabs?.select(tab)
    }

    override fun selectFresh(tab: BottomNavDestination) {
        tabs?.selectFresh(tab)
    }

    override fun showRootFlow(route: String) {
        // On the next turn of the main loop: the tab's host is still mirroring when it asks.
        scope.launch {
            if (tabs == null) return@launch
            tabsReached = false
            showFlow(route)
        }
    }

    override fun transitionCancelled() {
        cancelledTransitions++
        tabs?.markCancelledTransitions(CANCELLED_TRANSITIONS + cancelledTransitions)
    }

    private fun show(route: String) {
        val flowRoute = AppDestinationCatalog.kindOf(route) == DestinationKind.ROOT_FLOW
        if (tabsReached || !flowRoute) showTabs() else showFlow(route)
    }

    private fun showFlow(route: String) {
        if (flow?.startRoute == route) return
        val oldTabs = tabs
        val oldFlow = flow
        val oldSession = session
        tabs = null
        val newSession = ShellSession()
        session = newSession
        val host = StackHost(this, newSession.owner, route, visibility.stack(tab = null), onTabRootReached = {
            // Onboarding finished at Home: the tabs, now rather than after the next decision.
            scope.launch {
                tabsReached = true
                showTabs()
            }
        })
        flow = host
        showChild(host.navigation)
        disposeLater(oldTabs, oldFlow, oldSession)
    }

    private fun showTabs() {
        if (tabs != null) return
        val oldFlow = flow
        val oldSession = session
        flow = null
        val newSession = ShellSession()
        session = newSession
        val chat: ChatViewModel = shellViewModel(newSession.owner.viewModelStore)
        val requests: RequestsBadgeViewModel = shellViewModel(newSession.owner.viewModelStore)
        val created = TabShell(this, newSession, TabBadgeSources(chat, requests))
        tabs = created
        showChild(created.controller)
        disposeLater(null, oldFlow, oldSession)
    }

    /**
     * Ends the stacks the window no longer shows, on the next turn of the main loop: once UIKit has
     * taken their views out of the window, so no screen of theirs composes over a destroyed entry.
     */
    private fun disposeLater(oldTabs: TabShell?, oldFlow: StackHost?, oldSession: ShellSession?) {
        if (oldTabs == null && oldFlow == null && oldSession == null) return
        scope.launch {
            oldTabs?.dispose()
            oldFlow?.dispose()
            oldSession?.clear()
        }
    }

    // `bounds` is a C struct, read through Kotlin/Native's C interop.
    @OptIn(ExperimentalForeignApi::class)
    private fun showChild(child: UIViewController) {
        shown?.let { old ->
            old.willMoveToParentViewController(null)
            old.view.removeFromSuperview()
            old.removeFromParentViewController()
        }
        root.addChildViewController(child)
        child.view.setFrame(root.view.bounds)
        child.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        root.view.addSubview(child.view)
        child.didMoveToParentViewController(root)
        shown = child
    }

    /** The instance of [T] in [store], under the key `koinViewModel()` resolves in the screens. */
    private inline fun <reified T : ViewModel> shellViewModel(store: ViewModelStore): T =
        ViewModelProvider.create(store, viewModelFactory { initializer { koin.get<T>() } })[T::class]

    private companion object {
        /** The tab bar's accessibility identifier, followed by the number of cancelled transitions. */
        const val CANCELLED_TRANSITIONS = "coplanly.shell.cancelledTransitions."
    }
}

/**
 * The four tabs (spec §5.2): a tab bar controller with one [StackHost] per tab, each its own
 * navigation controller with its own shared controller, so a push from inside a tab goes onto that
 * tab's stack and each tab keeps its stack while another is shown.
 *
 * The tab items carry the shared labels (`nav_home` …, read in the reader's language) and SF
 * Symbols ([SfSymbols]); the Chat item's badge is the unread count and the Home item's the requests
 * waiting for this parent's answer (the tester redesign, November 2026, as Android's bar); the tabs
 * open on the Calendar, Android's start screen since that redesign; a tap on the Calendar tab is
 * counted for `CalendarTabTaps`, a second tap on a selected tab pops it to its first screen (UIKit's
 * own), which its host mirrors into its shared controller. The selected tab is the shell's
 * [ShellVisibility.selectedStack], so a hidden tab's screens know they are not seen.
 */
internal class TabShell(
    private val shell: ShellEnvironment,
    session: ShellSession,
    badges: TabBadgeSources
) {

    private val scope: CoroutineScope = MainScope()
    private val hosts: List<StackHost> = BottomNavDestination.entries.map { tab ->
        StackHost(
            shell,
            session.owner,
            tab.route,
            shell.visibility.stack(tab = tab.route),
            onRootFlowRequested = shell::showRootFlow
        )
    }
    private val tabDelegate = TabDelegate { selected ->
        hosts.firstOrNull { it.navigation === selected }?.let { shell.visibility.selectedStack = it.startRoute }
        if (selected === host(BottomNavDestination.CALENDAR).navigation) CalendarTabTaps.tap()
    }

    /** The tab bar controller the shell shows. */
    val controller: UITabBarController = UITabBarController()

    init {
        BottomNavDestination.entries.forEach { tab ->
            host(tab).navigation.tabBarItem = UITabBarItem(
                title = null,
                image = SfSymbols.image(SfSymbols.forTab(tab)),
                tag = tab.ordinal.toLong()
            )
        }
        controller.setViewControllers(hosts.map { it.navigation }, animated = false)
        controller.delegate = tabDelegate
        scope.launch {
            BottomNavDestination.entries.forEach { tab ->
                host(tab).navigation.tabBarItem.title = getString(tab.labelRes)
            }
        }
        scope.launch {
            badges.chat.unreadCount.collect { count ->
                host(BottomNavDestination.CHAT).navigation.tabBarItem.badgeValue = count.takeIf { it > 0 }?.toString()
            }
        }
        scope.launch {
            badges.requests.waitingOnMe.collect { count ->
                host(BottomNavDestination.HOME).navigation.tabBarItem.badgeValue = count.takeIf { it > 0 }?.toString()
            }
        }
        select(BottomNavDestination.CALENDAR)
    }

    /** Shows [tab] as it was left. */
    fun select(tab: BottomNavDestination) {
        controller.selectedIndex = tab.ordinal.toULong()
        // A selection made in code reaches no delegate.
        shell.visibility.selectedStack = tab.route
    }

    /** Shows [tab] at its first screen. */
    fun selectFresh(tab: BottomNavDestination) {
        host(tab).popToStart()
        select(tab)
    }

    /** Pushes [route] onto the selected tab's stack, with [options] as `NavController.navigate` takes them. */
    fun navigateOnSelected(route: String, options: NavOptionsBuilder.() -> Unit = {}) {
        val selected = hosts.getOrNull(controller.selectedIndex.toInt()) ?: host(BottomNavDestination.CALENDAR)
        selected.navController.navigate(route, options)
    }

    /** Pushes [route] onto [tab]'s stack, with [options] as `NavController.navigate` takes them. */
    fun navigateOn(tab: BottomNavDestination, route: String, options: NavOptionsBuilder.() -> Unit = {}) {
        host(tab).navController.navigate(route, options)
    }

    /**
     * Writes [marker] as the tab bar's accessibility identifier, which UI automation reads and
     * VoiceOver does not speak (the count of cancelled transitions, [NativeShell]).
     */
    fun markCancelledTransitions(marker: String) {
        // Key-value coding: Kotlin/Native lists `accessibilityIdentifier` on UIView's protocol alone.
        controller.tabBar.setValue(marker, forKey = "accessibilityIdentifier")
    }

    /** Ends the tabs: every stack's screens leave and their ViewModels are cleared. */
    fun dispose() {
        scope.cancel()
        controller.delegate = null
        hosts.forEach { it.dispose() }
    }

    private fun host(tab: BottomNavDestination): StackHost = hosts[tab.ordinal]

    /** Tells [onSelect] of every tap on a tab, the one on the tab already selected included. */
    private class TabDelegate(private val onSelect: (UIViewController) -> Unit) :
        NSObject(), UITabBarControllerDelegateProtocol {
        override fun tabBarController(
            tabBarController: UITabBarController,
            didSelectViewController: UIViewController
        ) {
            onSelect(didSelectViewController)
        }
    }
}

/**
 * What the tab bar's badges read: the Chat tab's unread count and the Home tab's requests waiting
 * for this parent's answer, each from the ViewModel Android's bar reads.
 *
 * @property chat The unread count's ViewModel
 * @property requests The waiting requests' ViewModel
 */
internal class TabBadgeSources(val chat: ChatViewModel, val requests: RequestsBadgeViewModel)
