package com.coparently.app.ios.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.ComposeUIViewControllerDelegate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.LocalOwnersProvider
import com.coparently.app.presentation.AppProviders
import com.coparently.app.presentation.common.ConnectivityBanner
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.LocalBarHost
import com.coparently.app.presentation.common.LocalSheetGuard
import com.coparently.app.presentation.common.SheetGuard
import com.coparently.app.presentation.navigation.AppDestination
import com.coparently.app.presentation.navigation.LocalTabSwitcher
import com.coparently.app.presentation.navigation.Screen
import com.coparently.app.presentation.platform.ProvideIosShell
import com.coparently.app.presentation.theme.CoPlanlyTheme
import platform.UIKit.UIViewController

/**
 * One back-stack entry's screen on the iPhone (MON-24 phase 3): a Compose view controller that
 * draws [destination]'s content for [entry], pushed onto a tab's navigation controller or set in a
 * form's sheet.
 *
 * Its composition is the screen alone, inside what every screen shares: the platform
 * ([ProvideIosShell]), the theme and the app-wide locals ([AppProviders], whose ViewModels the
 * [session] holds, so the four tabs share one of each), the shell's messages and tab
 * switcher, and then the entry as the owner of the screen's ViewModels, lifecycle and saved state,
 * as `NavHost` provides it on Android. The offline line sits at the top of the content, under the
 * navigation bar, and the messages that outlive a screen show at its bottom.
 *
 * UIKit's own back (the button and the edge swipe) is the back here: Compose's back gesture is off,
 * so the two never both answer one swipe.
 *
 * @property entry The back-stack entry this screen draws.
 * @property session The owner of the ViewModels the session's screens share, which ends with them.
 * @property nativeBar Whether the screen describes its bar to the navigation bar ([LocalBarHost]);
 *   otherwise it draws its own Compose bar and the navigation bar is hidden over it.
 * @property sheetGuard The sheet the screen is the first screen of, or null.
 */
internal class ScreenController(
    val entry: NavBackStackEntry,
    private val destination: AppDestination,
    private val shell: ShellEnvironment,
    private val session: ViewModelStoreOwner,
    val nativeBar: Boolean,
    private val sheetGuard: SheetGuard?
) {
    private val bars = NativeBars(
        // Settings is the one pushed screen whose title is large, as iOS's own Settings app shows it.
        largeTitle = entry.destination.route == Screen.Settings.route,
        sheetRoot = sheetGuard != null,
        // The chip's ViewModel is the screen's, as on Android, where the chip is in the screen. The
        // theme alone, without the app's background surface, so the bar shows around the chip.
        chipFrame = { content ->
            WhileAlive(entry) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
                    ProvideIosShell(shell.buildValues, shell.credentialSignIn) { CoPlanlyTheme(content = content) }
                }
            }
        }
    )

    /** The view controller a navigation controller shows. */
    // Turning Compose's own back gesture off is still experimental in Compose Multiplatform 1.9.3, and
    // the appearance callbacks' delegate is deprecated for subclassing, which Kotlin cannot do here.
    @OptIn(ExperimentalComposeUiApi::class)
    @Suppress("DEPRECATION")
    val viewController: UIViewController = ComposeUIViewController(
        configure = {
            enableBackGesture = false
            delegate = object : ComposeUIViewControllerDelegate {
                override fun viewDidAppear(animated: Boolean) = bars.screenAppeared(true)

                override fun viewDidDisappear(animated: Boolean) = bars.screenAppeared(false)
            }
        }
    ) {
        Shared {
            CompositionLocalProvider(
                LocalBarHost provides bars.takeIf { nativeBar },
                LocalSheetGuard provides sheetGuard
            ) {
                ScreenFrame(shell) {
                    WhileAlive(entry) {
                        entry.LocalOwnersProvider(rememberSaveableStateHolder()) { destination.content(entry) }
                    }
                }
            }
        }
    }.also { bars.viewController = it }

    /** Lets go of what this screen holds beyond its view controller, once it has left its stack. */
    fun release() = bars.release()

    /** What every hosted composition is drawn in: the platform, the theme and the shell's locals. */
    @Composable
    private fun Shared(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
            ProvideIosShell(shell.buildValues, shell.credentialSignIn) {
                AppProviders(darkTheme = null) {
                    CompositionLocalProvider(
                        LocalAppMessages provides shell.messages,
                        LocalTabSwitcher provides shell.tabSwitcher,
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * A hosted screen's frame: the offline line at the top and the shell's messages at the bottom,
 * around [content]. While the line shows it takes the top inset, which [content] then does not
 * apply again; while it does not, [content] applies the top inset itself (the navigation bar's and
 * the status bar's), as Android's root Scaffold leaves it (design item 14).
 */
@Composable
private fun ScreenFrame(shell: ShellEnvironment, content: @Composable () -> Unit) {
    val offline by shell.connectivity.isOffline.collectAsState()
    Scaffold(
        topBar = { ConnectivityBanner(offline = offline) },
        snackbarHost = { SnackbarHost(shell.messages.hostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
        ) {
            content()
        }
    }
}

/**
 * Draws [content] while [entry] is not destroyed. A screen popped or a stack ended destroys its
 * entry, whose ViewModels may then no more be read, while UIKit can still be drawing its view for a
 * moment; `NavHost` never composes a destroyed entry, and neither does the shell.
 */
@Composable
private fun WhileAlive(entry: NavBackStackEntry, content: @Composable () -> Unit) {
    val state by entry.lifecycle.currentStateFlow.collectAsState()
    if (state != Lifecycle.State.DESTROYED && entry.lifecycle.currentState != Lifecycle.State.DESTROYED) {
        content()
    }
}
