package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An in-screen sheet: the site's own `ModalBottomSheet` where the shell has no native controls
 * (Android), the phone's own sheet where it has ([NativeControls.Sheet], a page sheet holding
 * [content] on the iPhone).
 *
 * [material] is the site's `ModalBottomSheet { … }` call exactly as it was, so Android draws,
 * animates and reads it as before; it is not composed under native controls, so a sheet state it
 * creates is never asked to show or hide there. [content] is what the sheet holds, the same body
 * [material] wraps.
 *
 * The sheet is shown while this is composed. [onDismiss] is called once when the parent closes it
 * (a swipe down, or Material's scrim and Back); a caller that closes it itself clears its own state
 * and this leaves composition, which takes the iPhone's sheet away without calling back.
 *
 * @param onDismiss Called once when the parent closes the sheet
 * @param height How tall the iPhone's sheet opens
 * @param material The site's `ModalBottomSheet`, drawn where there are no native controls
 * @param content What the sheet holds, drawn in the iPhone's sheet
 * @param undimmed Whether the screen above a medium iPhone sheet stays usable; false dims it, as
 *   Material's scrim does. Only where the parent acts on what is above (the calendar's grid).
 * @param scrollable Whether the iPhone's sheet scrolls [content]: true where the body is a plain
 *   column that does not scroll itself, so a medium sheet or a large font never cuts it off. False
 *   where the body scrolls itself (a lazy list inside a scrolling column would fail). Ignored where
 *   Material draws the sheet, whose body is as it was.
 */
@Composable
@Suppress("LongParameterList") // the sheet's two bodies, how it closes and its three iPhone options
fun AdaptiveSheet(
    onDismiss: () -> Unit,
    height: SheetHeight,
    material: @Composable () -> Unit,
    content: @Composable () -> Unit,
    undimmed: Boolean = false,
    scrollable: Boolean = false
) {
    val native = nativeControls()
    if (native == null) {
        material()
    } else {
        val body: @Composable () -> Unit = if (scrollable) {
            { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() } }
        } else {
            content
        }
        native.Sheet(height = height, undimmed = undimmed, onDismiss = onDismiss, content = body)
    }
}
