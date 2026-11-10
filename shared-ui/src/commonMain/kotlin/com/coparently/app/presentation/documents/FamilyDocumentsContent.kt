package com.coparently.app.presentation.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.semantics.Role
import com.coparently.app.domain.documents.DocumentCategory
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.platform.LocalFileDetails
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.documents_add_dialog_category_label
import com.coparently.app.resources.documents_add_dialog_confirm
import com.coparently.app.resources.documents_add_dialog_name_label
import com.coparently.app.resources.documents_add_dialog_title
import com.coparently.app.resources.documents_category_court_order
import com.coparently.app.resources.documents_category_identity
import com.coparently.app.resources.documents_category_medical
import com.coparently.app.resources.documents_category_other
import com.coparently.app.resources.documents_category_school
import com.coparently.app.resources.documents_delete
import com.coparently.app.resources.documents_delete_confirm
import com.coparently.app.resources.documents_delete_confirm_body
import com.coparently.app.resources.documents_delete_confirm_title
import com.coparently.app.resources.documents_empty_description
import com.coparently.app.resources.documents_empty_title
import com.coparently.app.resources.documents_possibly_outdated
import com.coparently.app.resources.documents_row_meta
import com.coparently.app.resources.documents_shared_notice
import com.coparently.app.resources.documents_unavailable
import com.coparently.app.resources.documents_uploading
import com.coparently.app.resources.shared_file_cancel
import com.coparently.app.utils.readerMediumDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** The words a category is listed under. */
internal fun DocumentCategory.labelRes(): StringResource = when (this) {
    DocumentCategory.COURT_ORDER -> Res.string.documents_category_court_order
    DocumentCategory.SCHOOL -> Res.string.documents_category_school
    DocumentCategory.MEDICAL -> Res.string.documents_category_medical
    DocumentCategory.IDENTITY -> Res.string.documents_category_identity
    DocumentCategory.OTHER -> Res.string.documents_category_other
}

/**
 * The vault below its top bar: the shared notice, an upload in progress, then one group per
 * category that has anything in it.
 *
 * @param nameFor The name of the parent with this uid (never a role — the hard rule on labels).
 * @param readOnly An ended family's vault (Family → Earlier families): readable by both parents,
 *   changed by neither.
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
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    val documents = (list as? DocumentsList.Loaded)?.documents.orEmpty()
    if (list is DocumentsList.Loaded && documents.isEmpty() && !uploading) {
        EmptyState(
            icon = Icons.Default.Description,
            title = stringResource(Res.string.documents_empty_title),
            description = stringResource(Res.string.documents_shared_notice) + "\n\n" +
                stringResource(Res.string.documents_empty_description),
            modifier = modifier
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        item(key = "notice") { SharedNotice() }
        if (uploading) item(key = "uploading") { UploadingRow() }
        if (list is DocumentsList.Unavailable) {
            item(key = "unavailable") { Text(stringResource(Res.string.documents_unavailable)) }
        }
        if (list is DocumentsList.Loaded && list.possiblyOutdated) {
            item(key = "outdated") { OutdatedNotice() }
        }
        documents.groupBy { it.category }.forEach { (category, inCategory) ->
            item(key = "group-${category.wire}") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    GroupLabel(stringResource(category.labelRes()))
                    SectionGroup {
                        inCategory.forEachIndexed { index, document ->
                            if (index > 0) Divider()
                            DocumentRow(
                                document = document,
                                addedBy = nameFor(document.createdByFirebaseUid),
                                canDelete = !readOnly && document.createdByFirebaseUid == myUid,
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
    InlineBanner(text = stringResource(Res.string.documents_shared_notice), icon = Icons.Default.Info)
}

/**
 * The list below is this phone's last copy of the server's (the Room cache, schema 43), not the
 * server's answer now — a document the co-parent filed or deleted since may be missing or listed.
 */
@Composable
private fun OutdatedNotice() {
    InlineBanner(text = stringResource(Res.string.documents_possibly_outdated), icon = Icons.Default.CloudOff)
}

/** An upload in flight; the list gains the row only once the server holds it. */
@Composable
private fun UploadingRow() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(Res.string.documents_uploading), style = MaterialTheme.typography.labelMedium)
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
    val fileDetails = LocalFileDetails.current
    val date = Instant.fromEpochMilliseconds(document.createdAtMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val meta = stringResource(
        Res.string.documents_row_meta,
        fileDetails.formatSize(document.sizeBytes),
        addedBy,
        readerMediumDate(date)
    )
    SectionRow(
        title = document.title,
        icon = if (SharedFilePolicy.isImage(document.contentType)) Icons.Default.Image else Icons.Default.Description,
        supporting = meta,
        onClick = onOpen,
        trailing = if (canDelete) {
            {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.documents_delete))
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
        title = { Text(stringResource(Res.string.documents_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(Res.string.documents_shared_notice), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(TITLE_MAX_LENGTH) },
                    label = { Text(stringResource(Res.string.documents_add_dialog_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(Res.string.documents_add_dialog_category_label),
                    style = MaterialTheme.typography.labelLarge
                )
                DocumentCategory.entries.forEach { option ->
                    CategoryOption(option, selected = option == category) { category = option }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, category) }) {
                Text(stringResource(Res.string.documents_add_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.shared_file_cancel)) }
        }
    )
}

@Composable
private fun CategoryOption(category: DocumentCategory, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(category.labelRes()), modifier = Modifier.padding(start = Spacing.S))
    }
}

/** Confirms a delete, saying what it does on both phones and to the file. */
@Composable
internal fun DeleteDocumentDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.documents_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.documents_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.documents_delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.shared_file_cancel)) }
        }
    )
}

/** Matches `firestore.rules`' `isValidLength(title, 1, 200)`. */
private const val TITLE_MAX_LENGTH = 200
