package com.coparently.app.presentation.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.coparently.app.R
import com.coparently.app.domain.chat.ChatAttachment
import com.coparently.app.domain.chat.ChatAttachmentCodec
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.domain.model.Message
import com.coparently.app.domain.model.MessageSendStatus
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.openSharedFile
import com.coparently.app.presentation.theme.Spacing
import java.io.File

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
    viewModel: ChatAttachmentViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appMessages = LocalAppMessages.current
    val error by viewModel.error.collectAsState()
    LaunchedEffect(error) {
        error?.let {
            appMessages?.show(it.asString(context))
            viewModel.errorShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.opened.collect { opened ->
            if (!openSharedFile(context, opened.file, opened.contentType)) viewModel.noViewer()
        }
    }
    CompositionLocalProvider(
        LocalChatAttachments provides { message, mine -> ChatAttachmentBlock(message, mine, viewModel) },
        content = content
    )
}

/**
 * The composer's attach button — real, because attachments now exist (design item 8 forbade it
 * until they did). It opens the system picker limited to what the rules accept, then asks before
 * sending: a file in the chat stays there for good, like every message.
 */
@Composable
fun ChatAttachButton(
    conversationId: String,
    modifier: Modifier = Modifier,
    viewModel: ChatAttachmentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picked = uri?.toString()
    }
    IconButton(
        onClick = { picker.launch(SharedFilePolicy.CONTENT_TYPES.toTypedArray()) },
        modifier = modifier
    ) {
        Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.chat_attach))
    }
    picked?.let { uri ->
        val name = remember(uri) { displayName(context, uri) }
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(R.string.chat_attach_confirm_title)) },
            text = { Text(stringResource(R.string.chat_attach_confirm_body, name.first, name.second)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.send(conversationId, uri)
                    picked = null
                }) { Text(stringResource(R.string.chat_attach_send)) }
            },
            dismissButton = {
                TextButton(onClick = { picked = null }) { Text(stringResource(R.string.shared_file_cancel)) }
            }
        )
    }
}

/** The picked file's name and formatted size, for the confirmation; blanks when unknown. */
private fun displayName(context: Context, uri: String): Pair<String, String> {
    val parsed = Uri.parse(uri)
    val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    return runCatching {
        context.contentResolver.query(parsed, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val size = if (cursor.isNull(1)) "" else Formatter.formatShortFileSize(context, cursor.getLong(1))
            cursor.getString(0).orEmpty() to size
        }
    }.getOrNull() ?: ((parsed.lastPathSegment.orEmpty()) to "")
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
private fun ImageAttachment(attachment: ChatAttachment, file: File?, onOpen: () -> Unit) {
    val label = stringResource(R.string.chat_attach_open, attachment.fileName)
    Box(
        modifier = Modifier
            .size(THUMBNAIL_SIZE)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = label, role = Role.Image, onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        if (file != null) {
            AsyncImage(
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
    val context = LocalContext.current
    val label = stringResource(R.string.chat_attach_open, attachment.fileName)
    Row(
        modifier = Modifier
            .widthIn(max = THUMBNAIL_SIZE * 2)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onOpen)
            .padding(horizontal = Spacing.M, vertical = 10.dp),
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
                Formatter.formatShortFileSize(context, attachment.sizeBytes),
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
            stringResource(if (progress != null) R.string.chat_attach_uploading else R.string.chat_attach_waiting),
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
