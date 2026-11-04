package com.coparently.app.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.io.RawSource
import kotlinx.io.asSource
import java.io.IOException

/**
 * [PickedFiles] for a `content://` URI a picker granted: the `ContentResolver` calls
 * `SharedFileStager` made before it moved to `:shared-data` (MON-24 phase 2, Task 8j), unchanged.
 *
 * @param context Whose `ContentResolver` reads the file (an e2e parent's names its own directories).
 */
class AndroidPickedFiles(
    private val context: Context
) : PickedFiles {

    override fun typeOf(contentUri: String): String? = context.contentResolver.getType(Uri.parse(contentUri))

    override fun displayNameOf(contentUri: String): String? {
        val uri = Uri.parse(contentUri)
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
    }

    override fun open(contentUri: String): RawSource {
        val input = context.contentResolver.openInputStream(Uri.parse(contentUri))
            ?: throw IOException("Cannot open the picked file")
        return input.asSource()
    }
}
