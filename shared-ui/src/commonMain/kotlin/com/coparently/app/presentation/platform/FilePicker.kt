package com.coparently.app.presentation.platform

import androidx.compose.runtime.Composable

/**
 * The system's pickers: a photo from the gallery, a document from the files.
 *
 * A picked file arrives as its content URI in text, the form `PickedFiles` (`:shared-data`) reads
 * and the record photo upload takes. A picker closed without a choice calls nothing.
 */
interface FilePicker : PlatformCapability {

    /**
     * A launcher for the photo picker, images only.
     *
     * @param onPicked Receives the picked photo's content URI
     */
    @Composable
    fun rememberImagePicker(onPicked: (contentUri: String) -> Unit): PlatformLauncher

    /**
     * A launcher for the document picker, limited to [contentTypes].
     *
     * @param contentTypes The MIME types offered (`SharedFilePolicy.CONTENT_TYPES` for the vault and
     *   the chat)
     * @param onPicked Receives the picked file's content URI
     */
    @Composable
    fun rememberDocumentPicker(contentTypes: List<String>, onPicked: (contentUri: String) -> Unit): PlatformLauncher
}
