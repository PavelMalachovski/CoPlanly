package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * How a screen draws an image it does not hold: an account's picture, a record's photo (a
 * `ph1|…` reference, Legal item 8), a receipt or a staged file just picked.
 *
 * On Android it is Coil 2's `AsyncImage`, with the app's `ImageLoaderFactory` and its
 * `RecordPhotoFetcher`, which downloads a record photo as the signed-in parent and keeps a copy only
 * when its digest matches; a screen never fetches an image itself. Coil 3, which runs on the iPhone
 * too, is not part of MON-24 phase 2.
 *
 * [available] is false where nothing loads an image yet (the iPhone until phase 3), so a screen
 * whose whole point is the image — a photo strip, an attachment's thumbnail — leaves it out rather
 * than draw blank, tappable frames (design item 8). An image beside other content (an avatar's
 * picture over its initial) is still drawn: its placeholder stands in.
 */
interface PlatformImages : PlatformCapability {

    /**
     * Draws [model] — a URL, a record photo reference, a local file's kotlinx-io `Path` (a chat
     * attachment's checked thumbnail), or a platform value the shell understands (on Android a `Uri`
     * or a `File`).
     *
     * @param contentDescription What TalkBack says, or null for a decorative image
     * @param fullResolution Load it at its own size rather than the size it is drawn at, for a viewer
     *   that zooms (with a short crossfade)
     * @param placeholder Drawn while the image loads and when it cannot be loaded; null draws nothing
     *   there. It and [fullResolution] are independent: a caller may ask for both.
     */
    @Suppress("LongParameterList") // the image call's own parameters, named at every call site
    @Composable
    fun Image(
        model: Any?,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        fullResolution: Boolean,
        placeholder: (@Composable () -> Unit)?,
    )
}

/**
 * Draws [model] through the shell's [PlatformImages]: the one way a shared screen draws an image it
 * loads (see [PlatformImages.Image] for the parameters).
 */
@Suppress("LongParameterList") // the image call's own parameters, named at every call site
@Composable
fun AppImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fullResolution: Boolean = false,
    placeholder: (@Composable () -> Unit)? = null,
) {
    LocalPlatformImages.current.Image(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        fullResolution = fullResolution,
        placeholder = placeholder
    )
}
