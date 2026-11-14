package com.coparently.app.ios.shell

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import com.coparently.app.presentation.navigation.AppDestination
import com.coparently.app.presentation.navigation.AppDestinationCatalog
import com.coparently.app.presentation.navigation.DestinationKind
import com.coparently.app.presentation.navigation.Screen
import com.coparently.app.presentation.navigation.StackChange
import com.coparently.app.presentation.navigation.StackMirror
import com.coparently.app.presentation.navigation.appDestinations
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.transitionCoordinator
import platform.darwin.NSObject

/**
 * One stack of the iPhone's shell (MON-24 phase 3, spec §5.2): a tab, or the screens before the app
 * proper. It owns a shared [NavHostController] built from the app's destinations, as Android's
 * `NavHost` builds its own, and a [UINavigationController] that it keeps showing what that
 * controller holds.
 *
 * **The shared controller is the source of truth.** Every change to its back stack — a
 * `navigate`, a `popBackStack` from any screen — is mirrored into the navigation controller by
 * [StackMirror.diff]: a new entry pushed, a removed one popped, a form presented as a sheet
 * ([FormSheet]). The one move UIKit makes by itself is the user's back: the back button, the edge
 * swipe, a second tap on the selected tab, a sheet swiped down. When a transition ends
 * ([NavigationDelegate]) the host compares what UIKit shows with what it asked for, and
 * [StackMirror.poppedByUser] says how many entries the shared controller pops to stand there. A
 * swipe that was cancelled leaves UIKit where it was asked to be, so it pops nothing; a change the
 * shared controller makes during a transition waits for the transition's end.
 *
 * **Entries live as under `NavHost`.** A finished transition marks every visible entry's
 * transition complete, as `NavHost` does when its animation settles: a shown entry reaches
 * RESUMED, and a popped one DESTROYED, with its ViewModels cleared.
 *
 * **The navigation bar follows the screen on top, after every transition.** UIKit hides or shows the
 * bar in `willShow` for the screen it is moving to, and a back swipe let go of never says it went
 * back. So whenever the host has mirrored the stack — after a push or pop it asked for, a transition
 * that ended, or one that was cancelled — [applyBars] sets each navigation controller's bar for the
 * screen actually on top: shown over a screen that describes its bar, hidden over one that draws
 * its own. A form's sheet gets its bar state the same way, as soon as it is built.
 *
 * **A tab never shows the screens before the app proper.** A tab's screen that navigates to one
 * (Settings' sign-out navigates to the sign-in screen) is not mirrored: [onRootFlowRequested] hands
 * the route to the shell, which ends the tabs and shows the root flow from that route.
 *
 * **A screen knows whether it is seen.** After each mirror and each finished push or pop the host
 * writes the entries on top — the stack's, and the sheet's while one is up — into [visibility],
 * which each screen reads as `LocalScreenVisible` ([ScreenController]).
 *
 * @property startRoute The route the stack starts on: a tab's first screen, or a root-flow screen.
 * @property visibility Which of the stack's screens are seen, the shell's answer for this stack.
 * @property session The owner of the ViewModels this session's screens share (the palette's, the
 *   family view's), which ends with the tabs or the root flow it was made for.
 * @property onTabRootReached The root flow's way out: a screen there navigated to a tab's first
 *   screen (onboarding finishing at Home), which the shell then shows as the tabs. Null for a tab.
 * @property onRootFlowRequested A tab's way out: a screen there navigated to a root-flow route,
 *   which the shell then shows as the root flow. Null for the root flow.
 */
internal class StackHost(
    private val shell: ShellEnvironment,
    private val session: ViewModelStoreOwner,
    val startRoute: String,
    private val visibility: StackVisibility,
    private val onTabRootReached: (() -> Unit)? = null,
    private val onRootFlowRequested: ((String) -> Unit)? = null
) {
    private val lifecycleOwner = HostLifecycle()
    private val store = ViewModelStore()
    private val composeNavigator = ComposeNavigator()
    private val scope = MainScope()

    /** The stack's shared controller, which its screens navigate with. */
    val navController: NavHostController = NavHostController().apply {
        navigatorProvider.addNavigator(NavGraphNavigator(navigatorProvider))
        navigatorProvider.addNavigator(composeNavigator)
        setLifecycleOwner(lifecycleOwner)
        setViewModelStore(store)
    }

    private val destinations: Map<String, AppDestination> =
        appDestinations(navController, shell.syncViewModel, shell.authStateViewModel).associateBy { it.route }

    private val navigationDelegate = NavigationDelegate()

    /** The navigation controller the shell shows. */
    val navigation: UINavigationController = UINavigationController().apply {
        navigationBar.prefersLargeTitles = true
        delegate = navigationDelegate
    }

    /** Whether the screen a view controller shows has an inner step its back undoes first. */
    private val stepPendingOn: (UIViewController) -> Boolean = { controller ->
        screens.values.firstOrNull { it.viewController === controller }?.backSteps?.hasEnabledStep == true
    }

    /** The edge swipe back over every pushed screen, a Compose bar's included ([EdgeSwipeBack]). */
    private val edgeSwipe = EdgeSwipeBack(navigation, stepPendingOn)
    private var sheetEdgeSwipe: EdgeSwipeBack? = null

    /** Each entry's screen, by entry id, while the entry is on the stack. */
    private val screens = mutableMapOf<String, ScreenController>()

    /** The entry ids the host last asked UIKit to show, bottom first, the sheet's last. */
    private var asked: List<String> = emptyList()

    private var sheet: FormSheet? = null
    private var sheetMoving = false
    private var disposed = false

    init {
        navController.graph = navController.createGraph(startDestination = startRoute) {
            destinations.values.forEach { destination ->
                // The content is the shell's to draw (ScreenController); navigation only routes.
                composable(route = destination.route, arguments = destination.arguments) { }
            }
        }
        scope.launch { navController.currentBackStack.collect { sync() } }
    }

    /** Pops the stack to its first screen, as a second tap on its tab would. */
    fun popToStart() {
        navController.popBackStack(startRoute, inclusive = false)
    }

    /** Ends the stack: its screens leave, its entries are destroyed and their ViewModels cleared. */
    fun dispose() {
        if (disposed) return
        disposed = true
        scope.cancel()
        sheet?.navigationOrNull?.let { it.presentingViewController?.dismissViewControllerAnimated(false, null) }
        sheet = null
        navigation.delegate = null
        edgeSwipe.detach()
        sheetEdgeSwipe?.detach()
        navigation.setViewControllers(emptyList<UIViewController>(), animated = false)
        screens.values.forEach { it.release() }
        screens.clear()
        lifecycleOwner.destroy()
        store.clear()
    }

    /** The shared back stack's screen entries, bottom first (the graph's own entry left out). */
    private fun wantedEntries(): List<NavBackStackEntry> =
        navController.currentBackStack.value.filter { it.destination !is NavGraph }

    private fun moving(): Boolean =
        sheetMoving || navigation.transitionCoordinator != null ||
            sheet?.navigationOrNull?.transitionCoordinator != null

    /**
     * Makes UIKit show the shared back stack, unless a transition is under way: then it runs again
     * when the transition ends.
     */
    private fun sync() {
        if (disposed || moving()) return
        mirror()
        if (!disposed) {
            applyBars()
            visibility.topIds = topIds(navigation, sheet?.navigationOrNull, screens)
        }
    }

    /** Makes UIKit's stacks hold the shared back stack's entries; see [sync]. */
    private fun mirror() {
        val entries = wantedEntries()
        // The root flow never shows a tab's first screen: onboarding's finish navigates to Home with
        // its own entry popped, so Home may be the only entry left.
        if (onTabRootReached != null && entries.any { kindOf(it) == DestinationKind.TAB_ROOT }) {
            onTabRootReached.invoke()
            return
        }
        // A tab never mirrors the screens before the app proper: the shell shows them in their flow.
        val flowEntry = entries.lastOrNull { kindOf(it) == DestinationKind.ROOT_FLOW }
        if (onRootFlowRequested != null && flowEntry != null) {
            flowEntry.destination.route?.let { onRootFlowRequested.invoke(it) }
            return
        }
        val wanted = entries.map { it.id }
        val changes = StackMirror.diff(asked, wanted)
        if (changes.isEmpty()) return
        val animated = asked.isNotEmpty() && changes.none { it is StackChange.Replace }
        // A form and whatever opens above it go into its sheet; the rest is the stack itself.
        val formAt = entries.indexOfFirst { kindOf(it) == DestinationKind.FORM }.takeIf { it > 0 } ?: entries.size
        val inSheet = entries.drop(formAt)
        prepareSheet(inSheet.firstOrNull(), animated)
        entries.forEach { entry -> screens.getOrPut(entry.id) { screenFor(entry) } }
        asked = wanted
        showMain(entries.take(formAt), animated)
        if (inSheet.isNotEmpty()) showSheet(inSheet, animated)
        (screens.keys - wanted.toSet()).forEach { screens.remove(it)?.release() }
        if (!animated) completeTransitions()
    }

    /** Closes a sheet whose form left, and starts one for a form that arrived, before its screen is built. */
    private fun prepareSheet(form: NavBackStackEntry?, animated: Boolean) {
        if (form?.id != sheet?.entryId) closeSheet(animated)
        if (form != null && sheet == null) sheet = FormSheet(form.id) { uikitMoved() }
    }

    private fun showMain(main: List<NavBackStackEntry>, animated: Boolean) {
        val controllers = main.map { screens.getValue(it.id).viewController }
        if (!sameControllers(navigation.viewControllers, controllers)) {
            navigation.setViewControllers(controllers, animated)
        }
        main.lastOrNull()?.let { top ->
            navigation.setNavigationBarHidden(!screens.getValue(top.id).nativeBar, animated)
        }
    }

    private fun showSheet(entries: List<NavBackStackEntry>, animated: Boolean) {
        val form = sheet ?: return
        val controllers = entries.map { screens.getValue(it.id).viewController }
        val existing = form.navigationOrNull
        if (existing == null) {
            val sheetNavigation = form.navigation(controllers.first(), navigationDelegate)
            if (controllers.size > 1) sheetNavigation.setViewControllers(controllers, animated = false)
            // UIKit need not call `willShow` for a presented navigation controller's first screen.
            sheetNavigation.applyBar(screens.values)
            sheetEdgeSwipe = EdgeSwipeBack(sheetNavigation, stepPendingOn)
            sheetMoving = true
            navigation.presentViewController(sheetNavigation, animated) { transitionEnded() }
        } else if (!sameControllers(existing.viewControllers, controllers)) {
            existing.setViewControllers(controllers, animated)
        }
    }

    private fun closeSheet(animated: Boolean) {
        val closing = sheet ?: return
        sheet = null
        val presented = closing.navigationOrNull ?: return
        if (presented.presentingViewController == null) return
        sheetMoving = true
        presented.dismissViewControllerAnimated(animated) { transitionEnded() }
    }

    /**
     * Sets each navigation controller's bar for the screen on its top: shown over a screen that
     * describes its bar, hidden over one that draws its own. The one place the bar is settled once
     * a stack has moved, whichever way it moved.
     */
    private fun applyBars() {
        navigation.applyBar(screens.values)
        sheet?.navigationOrNull?.applyBar(screens.values)
    }

    /**
     * A transition was cancelled (a back swipe let go of): once UIKit has let go of it, the stacks
     * are reconciled and the bar set again for the screen that stayed, which `willShow` changed for
     * the screen the swipe was heading to.
     */
    private fun transitionCancelled() {
        shell.transitionCancelled()
        scope.launch {
            var waited = 0
            while (moving() && waited < SETTLE_FRAMES) {
                delay(FRAME_MILLIS)
                waited++
            }
            uikitMoved()
        }
    }

    /** A presentation or a dismissal of the sheet ended. */
    private fun transitionEnded() {
        sheetMoving = false
        uikitMoved()
    }

    /**
     * A UIKit transition ended: the shared controller pops what the user popped, every transition
     * is marked complete, and the stack is mirrored again for what changed meanwhile.
     */
    private fun uikitMoved() {
        if (disposed || moving()) return
        val shown = shownIds()
        val pops = StackMirror.poppedByUser(asked, shown, wantedEntries().map { it.id })
        asked = shown
        repeat(pops) { navController.popBackStack() }
        completeTransitions()
        sync()
    }

    /** What UIKit shows now, bottom first: the stack's screens, then the sheet's while it is up. */
    private fun shownIds(): List<String> {
        val main = navigation.viewControllers.map { idOf(it) }
        val form = sheet?.navigationOrNull?.takeIf { it.presentingViewController != null }
        val inSheet = form?.viewControllers?.map { idOf(it) }.orEmpty()
        if (form == null) sheet = null
        return (main + inSheet).filterNotNull()
    }

    private fun idOf(controller: Any?): String? =
        screens.entries.firstOrNull { it.value.viewController === controller }?.key

    /** Marks every visible entry's transition complete, as `NavHost` does once its animation settles. */
    private fun completeTransitions() {
        navController.visibleEntries.value
            .filter { it.destination is ComposeNavigator.Destination }
            .forEach { composeNavigator.onTransitionComplete(it) }
    }

    private fun screenFor(entry: NavBackStackEntry): ScreenController {
        val route = checkNotNull(entry.destination.route) { "A destination with no route" }
        val form = sheet?.takeIf { it.entryId == entry.id }
        return ScreenController(
            entry = entry,
            destination = destinations.getValue(route),
            shell = shell,
            session = session,
            nativeBar = showsNativeBar(route),
            sheetGuard = form?.guard,
            visible = visibility.of(entry.id)
        )
    }

    private fun kindOf(entry: NavBackStackEntry): DestinationKind? =
        entry.destination.route?.let { AppDestinationCatalog.kindOf(it) }

    /** UIKit's word that a push or a pop of either navigation controller ended. */
    private inner class NavigationDelegate : NSObject(), UINavigationControllerDelegateProtocol {
        @ObjCSignatureOverride
        override fun navigationController(
            navigationController: UINavigationController,
            willShowViewController: UIViewController,
            animated: Boolean
        ) {
            // The edge swipe's delegate, once the navigation controller has made its gesture.
            edgeSwipe.attach()
            sheetEdgeSwipe?.attach()
            // The bar shows for a screen that describes its bar, and hides over one that draws its own.
            val nativeBar = screens.values.firstOrNull { it.viewController === willShowViewController }?.nativeBar
            if (nativeBar != null) navigationController.setNavigationBarHidden(!nativeBar, animated)
            // A back swipe can be let go of: the transition then ends without `didShow`, and with
            // the stacks as they were. Whatever the shared controller did meanwhile is mirrored then.
            navigationController.transitionCoordinator?.animateAlongsideTransition(null) { context ->
                if (context?.isCancelled() == true) transitionCancelled()
            }
        }

        @ObjCSignatureOverride
        override fun navigationController(
            navigationController: UINavigationController,
            didShowViewController: UIViewController,
            animated: Boolean
        ) {
            // The screen shown is seen from now; the rest follows once UIKit has let go of the
            // transition, on the next turn of the main loop.
            visibility.topIds = topIds(navigation, sheet?.navigationOrNull, screens)
            scope.launch { uikitMoved() }
        }
    }

    internal companion object {
        /** A frame's time, how often a cancelled transition is checked for having let go. */
        private const val FRAME_MILLIS = 16L

        /** How many frames a cancelled transition is waited for, two seconds' worth. */
        private const val SETTLE_FRAMES = 125

        /**
         * The routes whose screens draw no bar at all, over which the navigation bar is hidden: the
         * loading screen, the telemetry question and the sign-in screen (MON-24 phase 3, part B,
         * Task 9). Every other route describes its bar (`AppTopBar`) and the navigation bar draws it;
         * part A began with Home, Family, Expenses and Settings, and part B converted the rest, task
         * by task, onboarding last.
         */
        val NO_BAR_ROUTES: Set<String> = setOf(
            Screen.Loading.route,
            Screen.PrivacyConsent.route,
            Screen.Auth.route
        )

        /** Whether the navigation bar shows over [route]'s screen: every route but [NO_BAR_ROUTES]. */
        fun showsNativeBar(route: String): Boolean = route !in NO_BAR_ROUTES

        /** Whether two lists hold the same view controllers, in order. */
        private fun sameControllers(current: List<*>, wanted: List<UIViewController>): Boolean =
            current.size == wanted.size && current.indices.all { current[it] === wanted[it] }
    }
}

/**
 * Shows this navigation controller's bar over a top screen that describes its bar, and hides it
 * over one that draws its own; [screens] are the stack's screens.
 */
private fun UINavigationController.applyBar(screens: Collection<ScreenController>) {
    val top = topViewController ?: return
    val nativeBar = screens.firstOrNull { it.viewController === top }?.nativeBar ?: return
    if (isNavigationBarHidden() == nativeBar) setNavigationBarHidden(!nativeBar, animated = false)
}

/**
 * The entry ids UIKit shows on top: [navigation]'s top screen, and [sheet]'s while it is presented
 * over it, which leaves the screen under it in view; [screens] are the stack's, by entry id.
 */
private fun topIds(
    navigation: UINavigationController,
    sheet: UINavigationController?,
    screens: Map<String, ScreenController>
): Set<String> {
    val sheetTop = sheet?.takeIf { it.presentingViewController != null }?.topViewController
    val tops = listOfNotNull(navigation.topViewController, sheetTop)
    return screens.filterValues { screen -> tops.any { it === screen.viewController } }.keys
}

/**
 * The lifecycle a stack's shared controller follows, as `NavHost` follows its activity's: resumed
 * while the stack exists, destroyed with it, which takes every entry left on it to DESTROYED.
 */
private class HostLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    /** Destroys the stack's lifecycle. */
    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
