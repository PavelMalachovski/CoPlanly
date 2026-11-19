package com.coparently.app.presentation.platform

import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.platform.logger
import com.coparently.app.ios.controls.presentWhenSettled
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewControllerDelegateProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSInteger
import platform.darwin.NSObject

/**
 * Hands things to other apps on the iPhone (MON-24 phase 4 part E1, Task 2), as Android's `Intent`s
 * do. Every call answers whether something took it.
 *
 * - A web page opens in the browser; no check is needed, since Safari always exists.
 * - A `mailto:` address opens only when a mail app can take it (`canOpenURL`, which needs `mailto`
 *   and `tel` in Info.plist's `LSApplicationQueriesSchemes`); a phone with no mail account says so.
 * - Any other scheme is refused: this is where a link a co-parent wrote could reach, and only a page
 *   or a mail draft is meant to open.
 * - A shared file opens in Quick Look, a sheet over the screen, with the file's own URL: no copy is
 *   handed over and no download address exists (CLAUDE.md item 31).
 */
internal object IosOpenUrl : OpenUrl {

    override val available: Boolean = true

    /** The Quick Look source of the file shown now, held while its sheet is up (UIKit holds it weakly). */
    private var shownFile: FilePreview? = null

    override fun open(url: String): Boolean {
        val target = NSURL.URLWithString(url) ?: return false
        return when (target.scheme?.lowercase()) {
            "http", "https" -> launch(target)
            "mailto" -> openIfPossible(target)
            else -> false
        }
    }

    override fun openFile(path: String, contentType: String): Boolean {
        val localPath = IosPickedFiles.pathOf(path)
        if (!NSFileManager.defaultManager.fileExistsAtPath(localPath)) return false
        val preview = FilePreview(NSURL.fileURLWithPath(localPath)) { finished ->
            if (shownFile === finished) shownFile = null
        }
        val controller = QLPreviewController()
        controller.dataSource = preview
        controller.delegate = preview
        shownFile = preview
        presentWhenSettled(
            controller = controller,
            from = null,
            isWanted = { shownFile === preview },
            onDropped = {
                logger.w(TAG, "The file preview could not be presented")
                preview.release()
            }
        )
        return true
    }

    override fun dial(number: String): Boolean {
        val uri = telUri(number) ?: return false
        val target = NSURL.URLWithString(uri) ?: return false
        return openIfPossible(target)
    }

    override fun composeEmail(address: String, subject: String, body: String): Boolean {
        val target = NSURL.URLWithString(mailtoUri(address, subject, body)) ?: return false
        return openIfPossible(target)
    }

    override fun openAppSettings(): Boolean {
        val target = UIApplicationOpenSettingsURLString?.let { NSURL.URLWithString(it) } ?: return false
        return launch(target)
    }

    private fun openIfPossible(target: NSURL): Boolean =
        UIApplication.sharedApplication.canOpenURL(target) && launch(target)

    private fun launch(target: NSURL): Boolean {
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any?>(), null)
        return true
    }

    private const val TAG = "IosOpenUrl"
}

/**
 * Quick Look's data source and delegate for one file, and its one preview item. [onFinished] is
 * called once, when the sheet is dismissed or could not be shown, so the holder lets go of it.
 */
private class FilePreview(
    file: NSURL,
    private val onFinished: (FilePreview) -> Unit
) : NSObject(), QLPreviewControllerDataSourceProtocol, QLPreviewControllerDelegateProtocol {

    private val item = FilePreviewItem(file)

    /** Lets the holder go of this source: the sheet is gone. */
    fun release() {
        onFinished(this)
    }

    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): NSInteger = 1

    override fun previewController(
        controller: QLPreviewController,
        previewItemAtIndex: NSInteger
    ): QLPreviewItemProtocol = item

    override fun previewControllerDidDismiss(controller: QLPreviewController) {
        release()
    }
}

/** The file Quick Look shows. */
private class FilePreviewItem(private val file: NSURL) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL? = file
}
