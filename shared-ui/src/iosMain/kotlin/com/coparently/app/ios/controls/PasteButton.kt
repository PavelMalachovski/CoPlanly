package com.coparently.app.ios.controls

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIPasteConfiguration
import platform.UIKit.UIPasteConfigurationSupportingProtocol
import platform.UIKit.UIPasteControl
import platform.UIKit.UIPasteControlConfiguration
import platform.UIKit.UIPasteControlDisplayMode
import platform.UIKit.UIResponder
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The system's paste button (`UIPasteControl`), in place, [PASTE_HEIGHT_PT] high and as wide as its
 * icon and label in the phone's language (iPhone phase 4, part E1; `NativeControls.PasteButton`).
 *
 * A tap on it pastes without iOS asking "Allow Paste?", because the parent pressed the system's own
 * button; the first item's plain text reaches [onPaste] on the main thread. Its host view is painted
 * [background], as every in-place control's is (part C's interop rule), and VoiceOver reads the
 * control's own name.
 */
@Composable
internal fun SystemPasteButton(onPaste: (String) -> Unit, background: Color, modifier: Modifier) {
    val callback by rememberUpdatedState(onPaste)
    UIKitView(
        factory = { PasteHost(PasteTarget { callback(it) }) },
        modifier = modifier.width(pasteWidthPt.dp).height(PASTE_HEIGHT_PT.dp),
        update = { view -> view.backgroundColor = uiColorOf(background) },
        properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true)
    )
}

/** The paste button's height, in points: the system's minimum touch target. */
private const val PASTE_HEIGHT_PT = 44.0

/** The paste button's width if UIKit reports none, in points. */
private const val PASTE_FALLBACK_WIDTH_PT = 100.0

/** The plain text a paste is read as: every text type the pasteboard offers conforms to it. */
private const val PLAIN_TEXT_TYPE = "public.plain-text"

/** The paste button's width in points, measured once from a control configured as [PasteHost]'s. */
// `CGSizeMake` and `useContents` are Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
private val pasteWidthPt: Double by lazy {
    UIPasteControl(configuration = pasteConfiguration())
        .sizeThatFits(CGSizeMake(Double.MAX_VALUE, PASTE_HEIGHT_PT))
        .useContents { if (width > 0.0) width else PASTE_FALLBACK_WIDTH_PT }
}

/** The system's look: the clipboard icon with the word "Paste", in a capsule. */
private fun pasteConfiguration(): UIPasteControlConfiguration =
    UIPasteControlConfiguration().apply {
        displayMode = UIPasteControlDisplayMode.UIPasteControlDisplayModeIconAndLabel
        cornerStyle = UIButtonConfigurationCornerStyleCapsule
    }

/**
 * A plain view holding the paste control at its whole size, painted with what it sits on. It keeps
 * [target] alive: `UIPasteControl.target` is a weak reference, so the paste's receiver lives as long
 * as this view does.
 */
// `CGRectMake` is Kotlin/Native's C interop.
@OptIn(ExperimentalForeignApi::class)
internal class PasteHost(private val target: PasteTarget) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private val control = UIPasteControl(configuration = pasteConfiguration())

    init {
        control.target = target
        addSubview(control)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        control.setFrame(bounds)
    }
}

/**
 * The paste control's receiver: a responder that accepts plain text and hands the first item's text
 * to [onPaste] on the main thread. Another item, or one that cannot be read as text, pastes nothing.
 */
internal class PasteTarget(
    private val onPaste: (String) -> Unit
) : UIResponder(), UIPasteConfigurationSupportingProtocol {

    private var acceptedTypes: UIPasteConfiguration? =
        UIPasteConfiguration(acceptableTypeIdentifiers = listOf(PLAIN_TEXT_TYPE))

    override fun pasteConfiguration(): UIPasteConfiguration? = acceptedTypes

    override fun setPasteConfiguration(pasteConfiguration: UIPasteConfiguration?) {
        acceptedTypes = pasteConfiguration
    }

    override fun canPasteItemProviders(itemProviders: List<*>): Boolean = firstTextProvider(itemProviders) != null

    override fun pasteItemProviders(itemProviders: List<*>) {
        val provider = firstTextProvider(itemProviders) ?: return
        provider.loadItemForTypeIdentifier(PLAIN_TEXT_TYPE, options = null) { item, _ ->
            val text = textOf(item)
            if (text != null) dispatch_async(dispatch_get_main_queue()) { onPaste(text) }
        }
    }

    private fun firstTextProvider(itemProviders: List<*>): NSItemProvider? =
        (itemProviders.firstOrNull() as? NSItemProvider)
            ?.takeIf { it.hasItemConformingToTypeIdentifier(PLAIN_TEXT_TYPE) }
}

/** The text of a loaded pasteboard item: a string as it is, UTF-8 data decoded, anything else null. */
// `NSString.create(data:encoding:)` is Kotlin/Native's Objective-C interop.
@OptIn(BetaInteropApi::class)
private fun textOf(item: Any?): String? =
    when (item) {
        is String -> item
        is NSData -> NSString.create(data = item, encoding = NSUTF8StringEncoding)?.toString()
        else -> null
    }
