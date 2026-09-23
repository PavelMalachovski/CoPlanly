package com.coparently.app.presentation.documents

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.newSharedFileCaptureUri
import com.coparently.app.presentation.common.openSharedFile
import com.coparently.app.presentation.common.rememberParentNames

/**
 * The family's document vault (MON-23): court orders, school letters, identity scans.
 *
 * Opens on the sentence that everything here is shared (design item 8 in reverse: the screen
 * must not let a parent believe a document is theirs alone). A file comes from the system picker
 * or the camera, is named and filed under a category, then uploaded — and only an uploaded file is
 * listed, because the list is the server's. Tapping a row downloads the file, checks it against its
 * SHA-256 and hands it to a viewer app; only the parent who added a document can delete it.
 *
 * @param onNavigateUp Returns to Settings.
 * @param viewModel Screen state.
 */
// One scaffold holding its launchers, its two dialogs and its body switch; split further it would
// read as four screens.
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDocumentsScreen(
    onNavigateUp: () -> Unit,
    viewModel: FamilyDocumentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val names = rememberParentNames(viewModel.parents.collectAsState().value)
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<FamilyDocument?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error.asString(context))
            viewModel.errorShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.opened.collect { opened ->
            if (!openSharedFile(context, opened.file, opened.contentType)) viewModel.noViewer()
        }
    }

    val canAdd = state.list !is DocumentsList.NoFamily && !state.uploading
    val pickers = rememberDocumentPickers(onPicked = { pendingUri = it.toString() })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.documents_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (state.list !is DocumentsList.NoFamily) {
                AddActions(enabled = canAdd, onPickFile = pickers.pickFile, onTakePhoto = pickers.takePhoto)
            }
        }
    ) { padding ->
        val bodyModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val list = state.list) {
            DocumentsList.NoFamily -> EmptyState(
                icon = Icons.Default.FolderShared,
                title = stringResource(R.string.documents_no_family_title),
                description = stringResource(R.string.documents_no_family_description),
                modifier = bodyModifier
            )
            else -> DocumentsBody(
                list = list,
                uploading = state.uploading,
                myUid = state.myUid,
                nameFor = names::labelForUid,
                onOpen = viewModel::open,
                onDelete = { deleting = it },
                modifier = bodyModifier
            )
        }
    }

    pendingUri?.let { uri ->
        AddDocumentDialog(
            onConfirm = { title, category ->
                viewModel.add(title, category, uri)
                pendingUri = null
            },
            onDismiss = { pendingUri = null }
        )
    }
    deleting?.let { document ->
        DeleteDocumentDialog(
            onConfirm = {
                viewModel.delete(document)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/** The two ways into the vault: a file from the system picker, or a photo taken now. */
private class DocumentPickers(val pickFile: () -> Unit, val takePhoto: () -> Unit)

/**
 * The system file picker, limited to what the rules accept, and the camera behind its runtime
 * permission (the manifest declares `CAMERA`, so the capture intent needs it granted).
 */
@Composable
private fun rememberDocumentPickers(onPicked: (Uri) -> Unit): DocumentPickers {
    val context = LocalContext.current
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onPicked)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = captureUri
        if (taken && uri != null) onPicked(Uri.parse(uri))
    }
    val launchCamera = {
        val uri = newSharedFileCaptureUri(context)
        captureUri = uri.toString()
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.documents_camera_unavailable, Toast.LENGTH_LONG).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    return remember(filePicker, camera, permission) {
        DocumentPickers(
            pickFile = { filePicker.launch(SharedFilePolicy.CONTENT_TYPES.toTypedArray()) },
            takePhoto = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
            }
        )
    }
}

/** The two add actions, pinned under the list. */
@Composable
private fun AddActions(enabled: Boolean, onPickFile: () -> Unit, onTakePhoto: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onPickFile, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Text(stringResource(R.string.documents_add_file), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onTakePhoto, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Text(stringResource(R.string.documents_take_photo), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
