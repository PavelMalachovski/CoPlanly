@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.platform.logger
import com.coparently.app.ios.controls.presentWhenSettled
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewController
import platform.UIKit.presentationController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/**
 * The iPhone's pickers (MON-24 phase 3, part C, Task 11): a photo through `PHPickerViewController`
 * (images, one), a document through `UIDocumentPickerViewController(forOpeningContentTypes:,
 * asCopy: true)` limited to the MIME types asked for (mapped through `UTType`).
 *
 * What is picked is copied into `picked/<uuid>/` under the app's cache directory
 * ([IosPickedFiles.keep]) and handed on as that copy's absolute path, the iPhone's content URI,
 * which `PickedFiles`, the image seam and the record photo upload read. A picker closed without a
 * choice (Cancel, or a swipe down) calls nothing, as on Android, and leaves the launcher ready for
 * the next tap. Each picker is presented from the screen's own view controller once nothing there
 * is moving ([presentWhenSettled]), and a picker still up when its screen leaves composition is
 * taken away without calling back.
 */
internal object IosFilePicker : FilePicker {

    override val available: Boolean = true

    @Composable
    override fun rememberImagePicker(onPicked: (contentUri: String) -> Unit): PlatformLauncher {
        val current by rememberUpdatedState(onPicked)
        val files = remember { IosPickedFiles.forApp() }
        return rememberPresenterLauncher { onEnded ->
            PhotoPickerPresentation(files, onPicked = { current(it) }, onEnded = onEnded)
        }
    }

    @Composable
    override fun rememberDocumentPicker(
        contentTypes: List<String>,
        onPicked: (contentUri: String) -> Unit
    ): PlatformLauncher {
        val current by rememberUpdatedState(onPicked)
        val types by rememberUpdatedState(contentTypes)
        val files = remember { IosPickedFiles.forApp() }
        return rememberPresenterLauncher { onEnded ->
            DocumentPickerPresentation(files, types, onPicked = { current(it) }, onEnded = onEnded)
        }
    }
}

/**
 * Something a launcher presents: presented once from a view controller, and taken away without
 * calling back by [close] when its screen leaves composition.
 */
internal interface Presentation {

    /** Presents it over what [from] presents. */
    fun present(from: UIViewController?)

    /** Takes it away, if it is still up, and calls nothing back. */
    fun close()
}

/**
 * A launcher that presents what [make] builds, one at a time: a second tap while one is up does
 * nothing, and once it has ended (its `onEnded` called) the next tap presents a new one. What is up
 * when the composable leaves is closed without calling back.
 */
@Composable
internal fun rememberPresenterLauncher(make: (onEnded: () -> Unit) -> Presentation): PlatformLauncher {
    val host = LocalUIViewController.current
    val build by rememberUpdatedState(make)
    val presenter = remember { Presenter() }
    DisposableEffect(presenter) {
        onDispose { presenter.close() }
    }
    return remember(presenter, host) { PlatformLauncher { presenter.open(host) { onEnded -> build(onEnded) } } }
}

/** Holds the one [Presentation] that is up: UIKit keeps no strong reference to a delegate. */
internal class Presenter {

    private var active: Presentation? = null

    /** Presents what [make] builds from [host], unless one is up already. */
    fun open(host: UIViewController?, make: (onEnded: () -> Unit) -> Presentation) {
        if (active != null) return
        var made: Presentation? = null
        val presentation = make { if (active === made) active = null }
        made = presentation
        active = presentation
        presentation.present(host)
    }

    /** Closes what is up, without calling back. */
    fun close() {
        active?.close()
        active = null
    }
}

/**
 * The photo picker, images only, one. A pick is copied before the picker's own file goes (the
 * system removes it when the load's callback returns) and handed to [onPicked] on the main queue.
 * The picker's delegates are [delegate], which UIKit does not retain, so this keeps it.
 */
private class PhotoPickerPresentation(
    private val files: IosPickedFiles,
    private val onPicked: (String) -> Unit,
    private val onEnded: () -> Unit
) : Presentation {

    private var finished = false

    /** Set by [close]: a pick still being copied when its screen left is not handed on. */
    private var disposed = false
    private val delegate = Delegate()
    private val picker = PHPickerViewController(
        configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
    )

    override fun present(from: UIViewController?) {
        picker.delegate = delegate
        picker.presentationController?.delegate = delegate
        presentWhenSettled(picker, from, isWanted = { !finished }, onDropped = ::ended)
    }

    override fun close() {
        disposed = true
        if (finished) return
        finished = true
        takeAway(picker)
    }

    /** Done or Cancel: the pick, if there is one, copied and handed on; then the end. */
    fun finishedPicking(results: List<*>) {
        if (finished) return
        finished = true
        takeAway(picker)
        val provider = (results.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onEnded()
            return
        }
        val suggested = provider.suggestedName
        provider.loadFileRepresentationForTypeIdentifier(UTTypeImage.identifier) { url, _ ->
            val copy = url?.let { file -> keep(file, suggested) }
            dispatch_async(dispatch_get_main_queue()) {
                if (!disposed) copy?.let(onPicked)
                onEnded()
            }
        }
    }

    /** The picker's file kept under `picked/`, named [suggested] with the file's extension. */
    private fun keep(file: NSURL, suggested: String?): String? {
        val source = file.path ?: return null
        val extension = file.pathExtension.orEmpty()
        val name = suggested?.takeIf { it.isNotBlank() }
            ?.let { if (extension.isEmpty() || it.endsWith(".$extension")) it else "$it.$extension" }
            ?: file.lastPathComponent
            ?: IosPickedFiles.FALLBACK_NAME
        return runCatching { files.keep(source, name) }
            .onFailure { logger.w(PICKER_TAG, "The picked photo could not be kept", it) }
            .getOrNull()
    }

    /** A swipe down took the picker away without a choice. */
    fun ended() {
        if (finished) return
        finished = true
        onEnded()
    }

    /** The picker's delegate and its presentation's. */
    private inner class Delegate :
        NSObject(),
        PHPickerViewControllerDelegateProtocol,
        UIAdaptivePresentationControllerDelegateProtocol {

        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            finishedPicking(didFinishPicking)
        }

        override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
            ended()
        }
    }
}

/**
 * The document picker, one file of [contentTypes] (MIME types, each mapped through `UTType`; one
 * the phone does not know is left out, and none known offers any item). It copies the pick into the
 * app's inbox (`asCopy`), from where it is copied under `picked/`, the inbox's copy removed,
 * and handed to [onPicked]. UIKit calls `documentPickerWasCancelled` for Cancel and for a swipe down
 * alike, and takes the picker away itself.
 */
private class DocumentPickerPresentation(
    private val files: IosPickedFiles,
    contentTypes: List<String>,
    private val onPicked: (String) -> Unit,
    private val onEnded: () -> Unit
) : Presentation {

    private var finished = false

    /** Set by [close]: a pick still being copied when its screen left is not handed on. */
    private var disposed = false
    private val delegate = Delegate()
    private val picker = UIDocumentPickerViewController(
        forOpeningContentTypes = contentTypes.mapNotNull { UTType.typeWithMIMEType(it) }
            .ifEmpty { listOf(UTTypeItem) },
        asCopy = true
    ).apply { allowsMultipleSelection = false }

    override fun present(from: UIViewController?) {
        picker.delegate = delegate
        presentWhenSettled(picker, from, isWanted = { !finished }, onDropped = ::ended)
    }

    override fun close() {
        disposed = true
        if (finished) return
        finished = true
        takeAway(picker)
    }

    /**
     * A document was picked: kept off the main queue (a large file takes a while to copy), then
     * handed on back on it, unless its screen left meanwhile; then the end.
     */
    fun picked(urls: List<*>) {
        if (finished) return
        finished = true
        val url = urls.firstOrNull() as? NSURL
        val inbox = url?.path
        if (inbox == null) {
            onEnded()
            return
        }
        val name = url.lastPathComponent ?: IosPickedFiles.FALLBACK_NAME
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val copy = runCatching { files.keep(inbox, name) }
                .onFailure { logger.w(PICKER_TAG, "The picked document could not be kept", it) }
                .getOrNull()
            NSFileManager.defaultManager.removeItemAtPath(inbox, error = null)
            dispatch_async(dispatch_get_main_queue()) {
                if (!disposed) copy?.let(onPicked)
                onEnded()
            }
        }
    }

    /** Cancel, a swipe down, or a presentation UIKit dropped: the end alone. */
    fun ended() {
        if (finished) return
        finished = true
        onEnded()
    }

    /** The picker's delegate. */
    private inner class Delegate : NSObject(), UIDocumentPickerDelegateProtocol {

        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            picked(didPickDocumentsAtURLs)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            ended()
        }
    }
}

private const val PICKER_TAG = "IosFilePicker"

/** Dismisses [controller] if it is still presented and not already going. */
internal fun takeAway(controller: UIViewController) {
    if (controller.presentingViewController != null && !controller.beingDismissed) {
        controller.dismissViewControllerAnimated(true, completion = null)
    }
}
