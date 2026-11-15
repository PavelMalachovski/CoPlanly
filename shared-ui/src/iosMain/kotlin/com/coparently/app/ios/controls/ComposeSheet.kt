package com.coparently.app.ios.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.coparently.app.ios.IosLifecycle
import com.coparently.app.presentation.common.LocalBarHost
import com.coparently.app.presentation.common.LocalScreenVisible
import com.coparently.app.presentation.common.LocalSheetGuard
import com.coparently.app.presentation.common.SheetGuard
import com.coparently.app.presentation.common.SheetHeight
import com.coparently.app.presentation.theme.Spacing
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIPresentationController
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UISheetPresentationControllerDetentIdentifierMedium
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.darwin.NSObject

/**
 * An in-screen sheet presented as the system's page sheet (MON-24 phase 3, part C, Task 6): what
 * every shared `ModalBottomSheet` is on the iPhone. It holds a view controller [makeController]
 * builds when it is first presented, with the grabber, at [height]'s detents.
 *
 * It ends once, whichever way: a swipe down (UIKit's `presentationControllerDidDismiss`), a
 * presentation UIKit dropped, or the screen under it no longer seen ([screenHidden]) calls
 * [onDismissed]; [close] takes it away without calling back, for a sheet whose composable left
 * composition. It is presented at most once. A medium sheet leaves the screen above it usable only
 * when [undimmed]; otherwise the screen is dimmed and cannot be touched, as under Material's scrim.
 *
 * A form inside reports its edits through [guard], as a form in the shell's own form sheet does:
 * while it holds edits the sheet is modal (`isModalInPresentation`), and a swipe down asks the
 * form's question instead of closing it. The object holds the presentation delegate, which UIKit
 * does not retain, so it must be kept for as long as the sheet is up.
 */
internal class ComposeSheet(
    private val height: SheetHeight,
    private val undimmed: Boolean,
    private val onDismissed: () -> Unit,
    private val makeController: (SheetGuard) -> UIViewController
) {
    /** Whether the sheet has ended, by any of its four ways. */
    var finished: Boolean = false
        private set

    /** The presented controller, once [present] built it. */
    var controller: UIViewController? = null
        private set

    private var dirty = false
    private var ask: (() -> Unit)? = null
    private val delegate = Delegate()

    /** What a form in the sheet reports its edits into. */
    val guard: SheetGuard = SheetGuard { isDirty, askQuestion ->
        dirty = isDirty
        ask = askQuestion
        controller?.modalInPresentation = isDirty
    }

    /**
     * Builds the sheet and presents it over the top of what [from] presents, once nothing there is
     * moving ([presentWhenSettled]). It does nothing once presented or ended: a sheet is presented
     * once. If it cannot be presented, it ends as a swipe down does.
     */
    fun present(from: UIViewController?) {
        if (finished || controller != null) return
        val sheet = prepare()
        presentWhenSettled(sheet, from, isWanted = { !finished }, onDropped = ::dismissed)
    }

    /** The sheet's controller, built and configured on the first call and the same one after. */
    fun prepare(): UIViewController = controller ?: configured(makeController(guard)).also { controller = it }

    /** The sheet went by itself (a swipe down), or could not be shown: [onDismissed], once. */
    fun dismissed() {
        if (finished) return
        finished = true
        onDismissed()
    }

    /** Takes the sheet away without calling back: its composable left composition. */
    fun close() {
        if (finished) return
        finished = true
        takeAway(animated = true)
    }

    /**
     * The screen under the sheet stopped being seen while the app is in the foreground (a screen
     * pushed over it, its tab left): the sheet goes at once, without an animation that would race
     * what covers the screen, and [onDismissed] is called once, so the caller's flag is released
     * and a later open works.
     */
    fun screenHidden() {
        if (finished) return
        finished = true
        takeAway(animated = false)
        onDismissed()
    }

    private fun takeAway(animated: Boolean) {
        val sheet = controller ?: return
        if (sheet.presentingViewController != null && !sheet.beingDismissed) {
            sheet.dismissViewControllerAnimated(animated, completion = null)
        }
    }

    /** A swipe down refused over edits: the form's own question. */
    fun attemptedToDismiss() {
        ask?.invoke()
    }

    private fun configured(sheet: UIViewController): UIViewController = sheet.also {
        it.modalPresentationStyle = UIModalPresentationPageSheet
        it.sheetPresentationController?.let { presentation ->
            presentation.detents = detentsFor(height)
            presentation.prefersGrabberVisible = true
            // At half the screen what lies above stays usable where asked: the calendar's grid.
            if (height == SheetHeight.Medium && undimmed) {
                presentation.largestUndimmedDetentIdentifier = UISheetPresentationControllerDetentIdentifierMedium
            }
        }
        it.presentationController?.delegate = delegate
        it.modalInPresentation = dirty
    }

    /** The sheet's presentation delegate: a swipe refused over edits asks, a swipe allowed has closed it. */
    private inner class Delegate : NSObject(), UIAdaptivePresentationControllerDelegateProtocol {
        override fun presentationControllerDidAttemptToDismiss(presentationController: UIPresentationController) {
            attemptedToDismiss()
        }

        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            dismissed()
        }
    }
}

/** The detents of a sheet [height] tall: medium and large, or large alone. */
internal fun detentsFor(height: SheetHeight): List<UISheetPresentationControllerDetent> = when (height) {
    SheetHeight.Medium -> listOf(
        UISheetPresentationControllerDetent.mediumDetent(),
        UISheetPresentationControllerDetent.largeDetent()
    )
    SheetHeight.Large -> listOf(UISheetPresentationControllerDetent.largeDetent())
}

/**
 * Presents [content] in a [ComposeSheet] from the caller's view controller while this is composed,
 * and takes it away without calling back when this leaves.
 *
 * The sheet's Compose view controller is a composition of its own, so [content] is drawn there
 * under the caller's locals, captured here with [currentCompositionLocalContext] and re-provided,
 * and then under the sheet's own platform locals again, provided over them: the caller's
 * ViewModels' owner, Koin, `ProvideIosShell`'s platform, the theme and the native controls cross
 * into the sheet, while the sheet keeps its own view controller, lifecycle, saved state, keyboard,
 * focus and interop container, which belong to the controller that draws it (a text field or a
 * native control in the sheet works in the sheet's own window, as a dialog's does on Android). The
 * caller's locals are read again at every recomposition of the caller, and [content] is the latest
 * one, so the sheet follows what the screen shows. There is no bar host inside, and the sheet's own
 * [ComposeSheet.guard] is the [LocalSheetGuard].
 *
 * The sheet lasts only while the caller's screen is seen ([LocalScreenVisible]): a hidden tab's
 * screens and a screen under a pushed one stay composed on the iPhone, so a sheet whose screen is
 * covered or whose tab is left is taken away and [onDismiss] called once. Going to the background
 * is not that: the shell's visibility turns false then too, but only after `IosLifecycle.foreground`
 * has, which is how the two are told apart, and the sheet stays for the parent's return.
 *
 * The sheet is painted in Material's bottom sheet colour (`BottomSheetDefaults.ContainerColor`,
 * `surfaceContainerLow`), the colour the sites' `ModalBottomSheet`s take, so a native control inside
 * passes it as what it sits on.
 */
// Turning Compose's own back gesture off is experimental in Compose Multiplatform 1.9.3; the sheet's
// colour is Material's bottom sheet default, which is experimental with the sheet.
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PresentComposeSheet(
    height: SheetHeight,
    undimmed: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val host = LocalUIViewController.current
    val visibility = LocalScreenVisible.current
    val dismiss by rememberUpdatedState(onDismiss)
    val latestContent = rememberUpdatedState(content)
    val callerContext = currentCompositionLocalContext
    val callerLocals = remember { mutableStateOf(callerContext) }
    SideEffect { callerLocals.value = callerContext }
    val sheetColor = BottomSheetDefaults.ContainerColor
    val sheetColorState = rememberUpdatedState(sheetColor)

    val sheet = remember {
        ComposeSheet(height = height, undimmed = undimmed, onDismissed = { dismiss() }) { guard ->
            ComposeUIViewController(configure = { enableBackGesture = false }) {
                SheetComposition(
                    caller = callerLocals.value,
                    guard = guard,
                    color = sheetColorState.value,
                    content = latestContent.value
                )
            }.also { it.view.backgroundColor = uiColorOf(sheetColorState.value) }
        }
    }
    DisposableEffect(sheet) {
        sheet.present(host)
        onDispose { sheet.close() }
    }
    // Followed in a coroutine, as `WhileScreenVisible` does: a covered screen draws no frames, so a
    // recomposition would wait until it is shown again.
    if (visibility != null) {
        LaunchedEffect(sheet, visibility) {
            snapshotFlow { visibility.value }.collect { visible ->
                if (!visible && IosLifecycle.foreground.value) sheet.screenHidden()
            }
        }
    }
}

/**
 * The sheet's own composition: the caller's locals, then the sheet's own platform locals over them,
 * then no bar host and the sheet's guard, and [content] on a surface in the sheet's [color], below
 * the grabber.
 */
@Composable
private fun SheetComposition(
    caller: CompositionLocalContext,
    guard: SheetGuard,
    color: Color,
    content: @Composable () -> Unit
) {
    val own = currentCompositionLocalContext
    CompositionLocalProvider(caller) {
        CompositionLocalProvider(own) {
            CompositionLocalProvider(LocalBarHost provides null, LocalSheetGuard provides guard) {
                Surface(modifier = Modifier.fillMaxSize(), color = color) {
                    // Clear of the grabber, where Material's sheet draws its drag handle.
                    // and of the home indicator, as Material's sheet keeps its content.
                    Box(Modifier.padding(top = Spacing.XL).navigationBarsPadding()) { content() }
                }
            }
        }
    }
}
