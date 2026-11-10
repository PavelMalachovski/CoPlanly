package com.coparently.app.presentation.export

import android.content.Intent
import com.coparently.app.data.export.ExportedFile
import com.coparently.app.presentation.platform.fileShareIntent

/**
 * The `ACTION_SEND` intent an exported record is shared with: the intent the share sheet's Android
 * half builds for it (`ShareSheet.shareFile`), the file's `FileProvider` URI and a one-off read
 * grant on the intent and its `ClipData`, and nothing else.
 *
 * It stayed in the app when the export screen moved to `:shared-ui` (MON-24 phase 2, Task 11, slice
 * 11g), so the instrumented export test can check the intent the share sheet receives without
 * driving the chooser.
 */
fun recordShareIntent(file: ExportedFile): Intent = fileShareIntent(file.uri, file.mimeType)
