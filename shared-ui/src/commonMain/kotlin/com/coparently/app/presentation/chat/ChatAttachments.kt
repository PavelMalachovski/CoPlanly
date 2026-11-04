package com.coparently.app.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.platform.AppImage
import com.coparently.app.presentation.platform.LocalFileDetails
import com.coparently.app.presentation.platform.LocalFilePicker
import com.coparently.app.presentation.platform.LocalOpenUrl
import com.coparently.app.presentation.platform.LocalPlatformImages
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.chat_attach
import com.coparently.app.resources.chat_attach_confirm_body
import com.coparently.app.resources.chat_attach_confirm_title
import com.coparently.app.resources.chat_attach_open
import com.coparently.app.resources.chat_attach_send
import com.coparently.app.resources.chat_attach_uploading
import com.coparently.app.resources.chat_attach_waiting
import com.coparently.app.resources.chat_composer_actions
import com.coparently.app.resources.chat_request_change
import com.coparently.app.resources.chat_templates
import com.coparently.app.resources.shared_file_cancel
import kotlinx.io.files.Path
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * How a bubble draws the files its message carries (MON-23), or null where no thread provides
 * one — a preview, a screenshot test — so `MessageItem` renders the text alone there.
 *
 * A CompositionLocal rather than a parameter because `MessageItem`'s signature is what the detekt
 * baseline keys two of its entries on; changing it would turn accepted debt into new findings.
 */
val LocalChatAttachments = compositionLocalOf<(@Composable (Message, Boolean) -> Unit)?> { null }

/**
 * Provides [LocalChatAttachments] for [content] and handles what a tap on an attachment produces:
 * a viewer app opened on the checked local copy, or a sentence when that fails.
 */
@Composable
fun ChatAttachmentsHost(
    viewModel: ChatAttachmentViewModel = koinViewModel(),
    content: @Composable () -> Unit
) {
    val openUrl = LocalOpenUrl.current
    val textResolver = rememberUiTextResolver()
    val appMessages = LocalAppMessages.current
    val error by viewModel.error.collectAsState()
    LaunchedEffect(error) {
        error?.let {
            appMessages?.show(it.asString(textResolver))
            viewModel.errorShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.opened.collect { opened ->
            if (!openUrl.openFile(opened.path.toString(), opened.contentType)) viewModel.noViewer()
        }
    }
    CompositionLocalProvider(
        LocalChatAttachments provides { message, mine -> ChatAttachmentBlock(message, mine, viewModel) },
        content = content
    )
}

/**
 * The composer's `+`: a labelled menu of what a message can carry besides its words — a file, a
 * change request about an event, a template (UX audit M12). The request and template chips used to
 * sit above the field for good, a whole row taken from the thread while the keyboard was open, and
 * the paperclip beside it was a third control for the same corner.
 *
 * Attaching is real, because attachments exist (design item 8 forbade it until they did): it opens
 * the system picker limited to what the rules accept, then asks before sending — a file in the chat
 * stays there for good, like every message. Where the phone has no picker (`FilePicker.available`,
 * the iPhone until phase 3) the entry is left out, and the menu keeps the other two.
 *
 * @param conversationId The thread a file is sent into.
 * @param onRequestChange Opens the event picker that starts a change request.
 * @param onTemplates Opens the message templates.
 */
@Composable
fun ChatComposerMenu(
    conversationId: String,
    onRequestChange: () -> Unit,
    onTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatAttachmentViewModel = koinViewModel()
) {
    val filePicker = LocalFilePicker.current
    val fileDetails = LocalFileDetails.current
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    // A picker closed without a choice calls nothing, which leaves `picked` as it was: null.
    val picker = filePicker.rememberDocumentPicker(SharedFilePolicy.CONTENT_TYPES.toList()) { uri -> picked = uri }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.chat_composer_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (filePicker.available) {
                ComposerMenuItem(Icons.Default.AttachFile, stringResource(Res.string.chat_attach)) {
                    open = false
                    picker.launch()
                }
            }
            ComposerMenuItem(Icons.Default.SwapHoriz, stringResource(Res.string.chat_request_change)) {
                open = false
                onRequestChange()
            }
            ComposerMenuItem(Icons.Default.Bolt, stringResource(Res.string.chat_templates)) {
                open = false
                onTemplates()
            }
        }
    }
    picked?.let { uri ->
        val name = remember(uri) { fileDetails.describe(uri) }
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(Res.string.chat_attach_confirm_title)) },
            text = { Text(stringResource(Res.string.chat_attach_confirm_body, name.name, name.size)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.send(conversationId, uri)
                    picked = null
                }) { Text(stringResource(Res.string.chat_attach_send)) }
            },
            dismissButton = {
                TextButton(onClick = { picked = null }) { Text(stringResource(Res.string.shared_file_cancel)) }
            }
        )
    }
}

/**
 * The files a message carries, above its bubble: a thumbnail for an image, a chip for a PDF, and —
 * on the sender's side while the message is still in the outbox — how far the upload has got.
 * Only a reference stored under this very message is drawn (`ChatAttachmentCodec.belongsTo`).
 */
@Composable
internal fun ChatAttachmentBlock(
    message: Message,
    isCurrentUser: Boolean,
    viewModel: ChatAttachmentViewModel
) {
    val attachments = remember(message.attachments, message.id) {
        ChatAttachmentCodec.attachmentsOf(message.attachments)
            .filter { ChatAttachmentCodec.belongsTo(it, message.conversationId, message.id) }
    }
    if (attachments.isEmpty()) return
    val thumbnails by viewModel.thumbnails.collectAsState()
    val progress = viewModel.progress.collectAsState().value[message.id]
    val pending = isCurrentUser &&
        (message.status == MessageSendStatus.SENDING || message.status == MessageSendStatus.ERROR)
    Column(
        modifier = Modifier.padding(top = Spacing.XS, bottom = Spacing.XXS),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS)
    ) {
        attachments.forEach { attachment ->
            key(attachment.storagePath) {
                LaunchedEffect(attachment.storagePath) { viewModel.requestThumbnail(message, attachment) }
                val onOpen = { viewModel.open(message, attachment) }
                if (attachment.isImage) {
                    ImageAttachment(attachment, thumbnails[attachment.storagePath], onOpen)
                } else {
                    FileAttachment(attachment, onOpen)
                }
            }
        }
        if (pending) UploadState(progress)
    }
}

@Composable
private fun ImageAttachment(attachment: ChatAttachment, file: Path?, onOpen: () -> Unit) {
    val label = stringResource(Res.string.chat_attach_open, attachment.fileName)
    Box(
        modifier = Modifier
            .size(THUMBNAIL_SIZE)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = label, role = Role.Image, onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        // The checked copy, where the platform draws images; else the image glyph, as while the
        // thumbnail is still on its way (the iPhone until phase 3).
        if (file != null && LocalPlatformImages.current.available) {
            AppImage(
                model = file,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(THUMBNAIL_SIZE)
            )
        } else {
            Icon(Icons.Default.Image, contentDescription = attachment.fileName)
        }
    }
}

@Composable
private fun FileAttachment(attachment: ChatAttachment, onOpen: () -> Unit) {
    val fileDetails = LocalFileDetails.current
    val label = stringResource(Res.string.chat_attach_open, attachment.fileName)
    Row(
        modifier = Modifier
            .widthIn(max = THUMBNAIL_SIZE * 2)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onOpen)
            .padding(horizontal = Spacing.M, vertical = Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(
                attachment.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                fileDetails.formatSize(attachment.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Uploading…" with the fraction while the file goes up; "Not uploaded yet" while it waits. */
@Composable
private fun UploadState(progress: Float?) {
    Column(modifier = Modifier.widthIn(max = THUMBNAIL_SIZE), horizontalAlignment = Alignment.End) {
        Text(
            stringResource(if (progress != null) Res.string.chat_attach_uploading else Res.string.chat_attach_waiting),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Edge of an image thumbnail; a PDF chip is at most twice as wide. */
private val THUMBNAIL_SIZE = 180.dp

/** One entry of [ChatComposerMenu]: an icon and what it does. */
@Composable
private fun ComposerMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}
