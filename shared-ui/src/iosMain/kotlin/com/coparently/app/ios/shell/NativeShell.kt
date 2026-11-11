package com.coparently.app.ios.shell

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.presentation.chat.ChatViewModel
import com.coparently.app.presentation.common.AppMessages
import com.coparently.app.presentation.common.ConnectivityViewModel
import com.coparently.app.presentation.navigation.AppDestinationCatalog
import com.coparently.app.presentation.navigation.BottomNavDestination
import com.coparently.app.presentation.navigation.CalendarTabTaps
import com.coparently.app.presentation.navigation.DestinationKind
import com.coparently.app.presentation.navigation.TabSwitcher
import com.coparently.app.presentation.navigation.startDestinationFor
import com.coparently.app.presentation.platform.AppBuildValues
import com.coparently.app.presentation.sync.AuthStateViewModel
import com.coparently.app.presentation.sync.SyncViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.Koin
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
 * values, the owner of the ViewModels Android scopes to its activity, those ViewModels the screens'
 * hosts read, the messages that outlive a screen and the tab switcher.
 */
internal interface ShellEnvironment {
    val buildValues: AppBuildValues
    val owner: ViewModelStoreOwner
    val syncViewModel: SyncViewModel
    val authStateViewModel: AuthStateViewModel
    val connectivity: ConnectivityViewModel
    val messages: AppMessages
    val tabSwitcher: TabSwitcher
}

/**
 * The iPhone app's native shell (MON-24 phase 3, spec §5.2): the window's root view controller,
 * which shows either the screens before the app proper — loading, sign-in, onboarding — in one
 * navigation controller with no tab bar, or the four tabs ([TabShell]), as the start destination
 * says ([startDestinationFor], the one Android's `NavGraph` reads).
 *
 * It holds one owner for what Android scopes to the activity: [SyncViewModel],
 * [AuthStateViewModel], [ConnectivityViewModel] and the Chat badge's [ChatViewModel] here, and the
 * palette's and the family view's, which every hosted screen's `AppProviders` resolves from the
 * same owner, so the stacks share one of each.
 *
 * The telemetry question is not asked on the iPhone (spec §5.2): it collects nothing, so the start
 * destination is decided as for an answer that collects nothing, and nothing is stored.
 *
 * Deep links are not read yet in part A. When they are, the family is switched before any is armed
 * (CLAUDE.md item 15), as `MainActivity` does.
 */
internal class NativeShell(
    koin: Koin,
    override val buildValues: AppBuildValues
) : ShellEnvironment, TabSwitcher {

    /** The window's root: a container that holds the root flow or the tabs. */
    val root: UIViewController = UIViewController()

    private val scope: CoroutineScope = MainScope()
    private val store = ViewModelStore()
    override val owner: ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore get() = store
    }
    override val authStateViewModel: AuthStateViewModel = shellViewModel(koin)
    override val syncViewModel: SyncViewModel = shellViewModel(koin)
    override val connectivity: ConnectivityViewModel = shellViewModel(koin)
    override val messages: AppMessages = AppMessages(scope, SnackbarHostState())
    override val tabSwitcher: TabSwitcher get() = this
    private val chat: ChatViewModel = shellViewModel(koin)

    private var flow: StackHost? = null
    private var tabs: TabShell? = null
    private var shown: UIViewController? = null

    /** Set when the root flow reached a tab's first screen before the start destination did. */
    private var tabsReached = false

    /** Starts following the start destination. Call once. */
    fun start() {
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
    }

    override fun select(tab: BottomNavDestination) {
        tabs?.select(tab)
    }

    override fun selectFresh(tab: BottomNavDestination) {
        tabs?.selectFresh(tab)
    }

    private fun show(route: String) {
        val flowRoute = AppDestinationCatalog.kindOf(route) == DestinationKind.ROOT_FLOW
        if (tabsReached || !flowRoute) showTabs() else showFlow(route)
    }

    private fun showFlow(route: String) {
        if (flow?.startRoute == route) return
        tabs?.dispose()
        tabs = null
        flow?.dispose()
        val host = StackHost(this, route) {
            // Onboarding finished at Home: the tabs, now rather than after the next decision.
            scope.launch {
                tabsReached = true
                showTabs()
            }
        }
        flow = host
        showChild(host.navigation)
    }

    private fun showTabs() {
        if (tabs != null) return
        flow?.dispose()
        flow = null
        val created = TabShell(this, chat)
        tabs = created
        showChild(created.controller)
    }

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

    /** The shell's instance of [T], in its own store, which `koinViewModel()` resolves in the screens. */
    private inline fun <reified T : ViewModel> shellViewModel(koin: Koin): T =
        ViewModelProvider.create(store, viewModelFactory { initializer { koin.get<T>() } })[T::class]
}

/**
 * The four tabs (spec §5.2): a tab bar controller with one [StackHost] per tab, each its own
 * navigation controller with its own shared controller, so a push from inside a tab goes onto that
 * tab's stack and each tab keeps its stack while another is shown.
 *
 * The tab items carry the shared labels (`nav_home` …, read in the reader's language) and SF
 * Symbols ([SfSymbols]); the Chat item's badge is the unread count; a tap on the Calendar tab is
 * counted for `CalendarTabTaps`, a second tap on a selected tab pops it to its first screen (UIKit's
 * own), which its host mirrors into its shared controller.
 */
internal class TabShell(shell: ShellEnvironment, chat: ChatViewModel) {

    private val scope: CoroutineScope = MainScope()
    private val hosts: List<StackHost> = BottomNavDestination.entries.map { StackHost(shell, it.route) }
    private val tabDelegate = TabDelegate { selected ->
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
            chat.unreadCount.collect { count ->
                host(BottomNavDestination.CHAT).navigation.tabBarItem.badgeValue = count.takeIf { it > 0 }?.toString()
            }
        }
    }

    /** Shows [tab] as it was left. */
    fun select(tab: BottomNavDestination) {
        controller.selectedIndex = tab.ordinal.toULong()
    }

    /** Shows [tab] at its first screen. */
    fun selectFresh(tab: BottomNavDestination) {
        host(tab).popToStart()
        select(tab)
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
