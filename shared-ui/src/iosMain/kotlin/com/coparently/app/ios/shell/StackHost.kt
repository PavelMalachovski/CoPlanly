package com.coparently.app.ios.shell

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
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
 * @property startRoute The route the stack starts on: a tab's first screen, or a root-flow screen.
 * @property onTabRootReached The root flow's way out: a screen there navigated to a tab's first
 *   screen (onboarding finishing at Home), which the shell then shows as the tabs. Null for a tab.
 */
internal class StackHost(
    private val shell: ShellEnvironment,
    val startRoute: String,
    private val onTabRootReached: (() -> Unit)? = null
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
        val entries = wantedEntries()
        // The root flow never shows a tab's first screen: onboarding's finish navigates to Home with
        // its own entry popped, so Home may be the only entry left.
        if (onTabRootReached != null && entries.any { kindOf(it) == DestinationKind.TAB_ROOT }) {
            onTabRootReached.invoke()
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
            .filter { it.destination.navigatorName == composeNavigator.name }
            .forEach { composeNavigator.onTransitionComplete(it) }
    }

    private fun screenFor(entry: NavBackStackEntry): ScreenController {
        val route = checkNotNull(entry.destination.route) { "A destination with no route" }
        val form = sheet?.takeIf { it.entryId == entry.id }
        return ScreenController(
            entry = entry,
            destination = destinations.getValue(route),
            shell = shell,
            nativeBar = route in NATIVE_BAR_ROUTES,
            largeTitle = route == Screen.Settings.route,
            sheetGuard = form?.guard
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
            // The bar shows for a screen that describes its bar, and hides over one that draws its own.
            val nativeBar = screens.values.firstOrNull { it.viewController === willShowViewController }?.nativeBar
            if (nativeBar != null) navigationController.setNavigationBarHidden(!nativeBar, animated)
            // A back swipe can be let go of: the transition then ends without `didShow`, and with
            // the stacks as they were. Whatever the shared controller did meanwhile is mirrored then.
            navigationController.transitionCoordinator?.animateAlongsideTransition(null) { context ->
                if (context?.isCancelled() == true) scope.launch { sync() }
            }
        }

        @ObjCSignatureOverride
        override fun navigationController(
            navigationController: UINavigationController,
            didShowViewController: UIViewController,
            animated: Boolean
        ) {
            // On the next turn of the main loop, once UIKit has let go of the transition.
            scope.launch { uikitMoved() }
        }
    }

    private companion object {
        /**
         * The routes whose screens describe their bar (`AppTopBar`) in part A: the tabs' first
         * screens Home and Family, the path Family → Expenses → Add expense, and Settings. Every
         * other screen draws its own Compose bar, with the navigation bar hidden over it; Calendar's
         * and Chat's first screens keep their Compose headers until part B.
         */
        val NATIVE_BAR_ROUTES = setOf(
            Screen.Home.route,
            Screen.Family.route,
            Screen.Expenses.route,
            Screen.AddExpense.route,
            Screen.EditExpense.route,
            Screen.Settings.route
        )

        /** Whether two lists hold the same view controllers, in order. */
        fun sameControllers(current: List<*>, wanted: List<UIViewController>): Boolean =
            current.size == wanted.size && current.indices.all { current[it] === wanted[it] }
    }
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
