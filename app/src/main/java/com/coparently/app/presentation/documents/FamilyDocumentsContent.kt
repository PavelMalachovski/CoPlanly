package com.coparently.app.presentation.documents

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The words a category is listed under. */
@StringRes
internal fun DocumentCategory.labelRes(): Int = when (this) {
    DocumentCategory.COURT_ORDER -> R.string.documents_category_court_order
    DocumentCategory.SCHOOL -> R.string.documents_category_school
    DocumentCategory.MEDICAL -> R.string.documents_category_medical
    DocumentCategory.IDENTITY -> R.string.documents_category_identity
    DocumentCategory.OTHER -> R.string.documents_category_other
}

/**
 * The vault below its top bar: the shared notice, an upload in progress, then one group per
 * category that has anything in it.
 *
 * @param nameFor The name of the parent with this uid (never a role — the hard rule on labels).
 */
// One parameter per thing a row shows or does; the grouping is the body's whole job.
@Suppress("LongParameterList")
@Composable
internal fun DocumentsBody(
    list: DocumentsList,
    uploading: Boolean,
    myUid: String,
    nameFor: (String) -> String,
    onOpen: (FamilyDocument) -> Unit,
    onDelete: (FamilyDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val documents = (list as? DocumentsList.Loaded)?.documents.orEmpty()
    if (list is DocumentsList.Loaded && documents.isEmpty() && !uploading) {
        EmptyState(
            icon = Icons.Default.Description,
            title = stringResource(R.string.documents_empty_title),
            description = stringResource(R.string.documents_shared_notice) + "\n\n" +
                stringResource(R.string.documents_empty_description),
            modifier = modifier
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "notice") { SharedNotice() }
        if (uploading) item(key = "uploading") { UploadingRow() }
        if (list is DocumentsList.Unavailable) {
            item(key = "unavailable") { Text(stringResource(R.string.documents_unavailable)) }
        }
        documents.groupBy { it.category }.forEach { (category, inCategory) ->
            item(key = "group-${category.wire}") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupLabel(stringResource(category.labelRes()))
                    SectionGroup {
                        inCategory.forEachIndexed { index, document ->
                            if (index > 0) Divider()
                            DocumentRow(
                                document = document,
                                addedBy = nameFor(document.createdByFirebaseUid),
                                canDelete = document.createdByFirebaseUid == myUid,
                                onOpen = { onOpen(document) },
                                onDelete = { onDelete(document) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Everything here is shared" — first on the screen, never collapsible. */
@Composable
private fun SharedNotice() {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.documents_shared_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** An upload in flight; the list gains the row only once the server holds it. */
@Composable
private fun UploadingRow() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.documents_uploading), style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

/** One document: its kind, title, size, who added it and when; Delete only for its uploader. */
@Composable
private fun DocumentRow(
    document: FamilyDocument,
    addedBy: String,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val date = Instant.ofEpochMilli(document.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val meta = stringResource(
        R.string.documents_row_meta,
        Formatter.formatShortFileSize(context, document.sizeBytes),
        addedBy,
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    )
    SectionRow(
        title = document.title,
        icon = if (SharedFilePolicy.isImage(document.contentType)) Icons.Default.Image else Icons.Default.Description,
        supporting = meta,
        onClick = onOpen,
        trailing = if (canDelete) {
            {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.documents_delete))
                }
            }
        } else {
            null
        }
    )
}

/**
 * Names and files the picked file before anything is uploaded — the one moment a parent can still
 * decide not to share it.
 */
@Composable
internal fun AddDocumentDialog(onConfirm: (String, DocumentCategory) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(DocumentCategory.OTHER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.documents_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.documents_shared_notice), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(TITLE_MAX_LENGTH) },
                    label = { Text(stringResource(R.string.documents_add_dialog_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.documents_add_dialog_category_label),
                    style = MaterialTheme.typography.labelLarge
                )
                DocumentCategory.entries.forEach { option ->
                    CategoryOption(option, selected = option == category) { category = option }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, category) }) {
                Text(stringResource(R.string.documents_add_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shared_file_cancel)) }
        }
    )
}

@Composable
private fun CategoryOption(category: DocumentCategory, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(category.labelRes()), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Confirms a delete, saying what it does on both phones and to the file. */
@Composable
internal fun DeleteDocumentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.documents_delete_confirm_title)) },
        text = { Text(stringResource(R.string.documents_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.documents_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shared_file_cancel)) }
        }
    )
}

/** Matches `firestore.rules`' `isValidLength(title, 1, 200)`. */
private const val TITLE_MAX_LENGTH = 200
