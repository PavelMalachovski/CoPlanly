package com.coparently.app.presentation.documents

import androidx.compose.foundation.layout.fillMaxSize
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
import com.coparently.app.domain.documents.FamilyDocument
import com.coparently.app.domain.files.SharedFilePolicy
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.LocalAppMessages
import com.coparently.app.presentation.common.SideBySideOrStacked
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.platform.CameraCapture
import com.coparently.app.presentation.platform.CaptureResult
import com.coparently.app.presentation.platform.CaptureTarget
import com.coparently.app.presentation.platform.LocalCameraCapture
import com.coparently.app.presentation.platform.LocalFilePicker
import com.coparently.app.presentation.platform.LocalOpenUrl
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.documents_add_file
import com.coparently.app.resources.documents_camera_unavailable
import com.coparently.app.resources.documents_no_family_description
import com.coparently.app.resources.documents_no_family_title
import com.coparently.app.resources.documents_take_photo
import com.coparently.app.resources.documents_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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
    viewModel: FamilyDocumentsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val names = rememberParentNames(viewModel.parents.collectAsState().value)
    val openUrl = LocalOpenUrl.current
    val textResolver = rememberUiTextResolver()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<FamilyDocument?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error.asString(textResolver))
            viewModel.errorShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.opened.collect { opened ->
            if (!openUrl.openFile(opened.path.toString(), opened.contentType)) viewModel.noViewer()
        }
    }

    val canAdd = state.list !is DocumentsList.NoFamily && !state.uploading && !state.readOnly
    val pickers = rememberDocumentPickers(onPicked = { pendingUri = it })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.documents_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (state.list !is DocumentsList.NoFamily && !state.readOnly) {
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
                title = stringResource(Res.string.documents_no_family_title),
                description = stringResource(Res.string.documents_no_family_description),
                modifier = bodyModifier
            )
            else -> DocumentsBody(
                list = list,
                uploading = state.uploading,
                myUid = state.myUid,
                nameFor = names::labelForUid,
                onOpen = viewModel::open,
                onDelete = { deleting = it },
                modifier = bodyModifier,
                readOnly = state.readOnly
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

/**
 * The two ways into the vault: a file from the system picker, or a photo taken now. Each is null
 * where the phone cannot offer it, so no button promises what it cannot do (design item 8).
 */
private class DocumentPickers(val pickFile: (() -> Unit)?, val takePhoto: (() -> Unit)?)

/**
 * The system file picker, limited to what the rules accept, and the camera behind its runtime
 * permission (on Android the manifest declares `CAMERA`, so the capture needs it granted:
 * [CameraCapture] asks first).
 */
@Composable
private fun rememberDocumentPickers(onPicked: (String) -> Unit): DocumentPickers {
    val appMessages = LocalAppMessages.current
    val files = LocalFilePicker.current
    val cameraCapture = LocalCameraCapture.current
    val cameraUnavailable = stringResource(Res.string.documents_camera_unavailable)
    val filePicker = files.rememberDocumentPicker(SharedFilePolicy.CONTENT_TYPES) { uri -> onPicked(uri) }
    val camera = cameraCapture.rememberCapture(CaptureTarget.SHARED_FILE) { result ->
        when (result) {
            is CaptureResult.Taken -> onPicked(result.contentUri)
            CaptureResult.NoCameraApp -> appMessages?.show(cameraUnavailable)
            CaptureResult.Cancelled, CaptureResult.PermissionDenied -> Unit
        }
    }
    val canPick = remember(files) { files.available }
    val canPhotograph = remember(cameraCapture) { cameraCapture.available }
    return remember(filePicker, camera, canPick, canPhotograph) {
        DocumentPickers(
            pickFile = if (canPick) filePicker::launch else null,
            takePhoto = if (canPhotograph) camera::launch else null
        )
    }
}

/**
 * The two add actions, pinned under the list: side by side, or one above the other from
 * [com.coparently.app.presentation.common.STACK_CONTROLS_FONT_SCALE], where half the width broke
 * German labels inside a word ("hinzufüge|n"). An action the phone cannot offer is left out.
 */
@Composable
private fun AddActions(enabled: Boolean, onPickFile: (() -> Unit)?, onTakePhoto: (() -> Unit)?) {
    val controls = buildList<@Composable (Modifier) -> Unit> {
        if (onPickFile != null) {
            add { modifier ->
                Button(onClick = onPickFile, enabled = enabled, modifier = modifier) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Text(
                        stringResource(Res.string.documents_add_file),
                        modifier = Modifier.padding(start = Spacing.S)
                    )
                }
            }
        }
        if (onTakePhoto != null) {
            add { modifier ->
                OutlinedButton(onClick = onTakePhoto, enabled = enabled, modifier = modifier) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Text(
                        stringResource(Res.string.documents_take_photo),
                        modifier = Modifier.padding(start = Spacing.S)
                    )
                }
            }
        }
    }
    if (controls.isEmpty()) return
    SideBySideOrStacked(
        controls = controls,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.L, vertical = Spacing.M),
        spacing = Spacing.M
    )
}
