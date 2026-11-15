package com.coparently.app.presentation.common

import androidx.compose.runtime.Composable

/**
 * How tall an in-screen sheet opens on the iPhone (MON-24 phase 3, part C, Task 6).
 *
 * [Medium] opens at half the screen and can be pulled up to the full height, and what lies above
 * it stays in view (the event preview, the day swap's review, over the grid they are about), and
 * can be touched where the site asks for it. [Large] opens at the full height, the system's page
 * sheet.
 */
enum class SheetHeight {
    /** Half the screen, pulled up to the full height; the screen above it stays in view. */
    Medium,

    /** The full height. */
    Large
}

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
 */
@Composable
fun AdaptiveSheet(
    onDismiss: () -> Unit,
    height: SheetHeight,
    material: @Composable () -> Unit,
    content: @Composable () -> Unit,
    undimmed: Boolean = false
) {
    val native = nativeControls()
    if (native == null) {
        material()
    } else {
        native.Sheet(height = height, undimmed = undimmed, onDismiss = onDismiss, content = content)
    }
}
