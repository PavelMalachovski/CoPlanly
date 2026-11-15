package com.coparently.app.presentation.platform

import com.coparently.app.data.files.IosPickedFiles
import com.coparently.app.data.platform.logger
import com.coparently.app.ios.controls.presentWhenSettled
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController

/**
 * The iPhone's share sheet (MON-24 phase 3, part C, Task 11): `UIActivityViewController` with the
 * text, or with the file's URL, presented from the top-most presented controller once nothing there
 * is moving ([presentWhenSettled]), as Android's `ACTION_SEND` in a chooser.
 *
 * A file is named by its absolute path (a picked copy, the payment code's PNG in `payment_codes/`, an
 * exported record) or by a `file://` address; the receiving app gets that one file, as Android's
 * one-off read grant gives it. A file that is not there opens no sheet, rather than an empty one.
 * The sheet has no title of its own on the iPhone, so `chooserTitle` is not shown.
 */
internal object IosShareSheet : ShareSheet {

    override val available: Boolean = true

    override fun shareText(text: String, chooserTitle: String) {
        present(listOf(text))
    }

    override fun shareFile(contentUri: String, mimeType: String, chooserTitle: String) {
        val path = IosPickedFiles.pathOf(contentUri)
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
            logger.w(TAG, "No file to share")
            return
        }
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    private fun present(items: List<Any>) {
        val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
        presentWhenSettled(
            controller = sheet,
            from = null,
            isWanted = { true },
            onDropped = { logger.w(TAG, "The share sheet could not be presented") }
        )
    }

    private const val TAG = "IosShareSheet"
}
