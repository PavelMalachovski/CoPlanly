package com.coparently.app.presentation.childinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.coparently.app.presentation.childinfo.components.GuestInviteSheet
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.ListSkeleton
import com.coparently.app.presentation.common.UiText
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.rememberUiTextResolver
import com.coparently.app.presentation.platform.LocalShareSheet
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.screenTopAppBarColors
import com.coparently.app.resources.Res
import com.coparently.app.resources.child_photo_delete_failed
import com.coparently.app.resources.child_photo_upload_failed
import com.coparently.app.resources.childinfo_back
import com.coparently.app.resources.childinfo_record_gone_description
import com.coparently.app.resources.childinfo_record_gone_title
import com.coparently.app.resources.childinfo_screen_title
import com.coparently.app.resources.guest_invite_share
import com.coparently.app.resources.guest_invite_share_message
import com.coparently.app.resources.guest_revoke_failed
import com.coparently.app.resources.medical_photos_delete_failed
import com.coparently.app.resources.medical_photos_upload_failed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * One child's record: everything the family holds about them, read-only, every row opening the
 * editor.
 *
 * Split out of `ChildInfoScreen` when that became a list. The screen it came from showed
 * `childInfoList.first()`, so with two children the second was unreachable — and, less obviously,
 * so was their guest access: `openGuestInvite` was bound to the first record's id, meaning a
 * grandparent could never be granted access to a second child.
 *
 * The child is looked up **by id** from the list this ViewModel already holds, never from
 * `currentChildInfo`. That state is the editor's, written by `loadChildInfoById`, and a screen
 * that fed it would reintroduce the CQ-9 defect where an edit of one child landed on another.
 * A read-only screen needs no per-id observation of its own: the list flow already re-emits on
 * every write.
 *
 * @param childInfoId The child to show.
 * @param onNavigateBack Up-arrow.
 * @param onEditClick Opens the editor, by id.
 * @param viewModel ViewModel for child information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailScreen(
    childInfoId: String,
    onNavigateBack: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: ChildInfoViewModel = koinViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val shareSheet = LocalShareSheet.current
    val textResolver = rememberUiTextResolver()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val guestInvite by viewModel.guestInvite.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // A failed revoke has to be said out loud. The row vanishes from the list either way once
    // Room is written, so silence here would leave the parent believing access is gone.
    val revokeFailed by viewModel.guestRevokeFailed.collectAsState()
    val revokeFailedMessage = stringResource(Res.string.guest_revoke_failed)
    LaunchedEffect(revokeFailed) {
        if (revokeFailed) {
            snackbarHostState.showSnackbar(revokeFailedMessage)
            viewModel.clearGuestRevokeFailed()
        }
    }

    val photoError by viewModel.photoError.collectAsState()
    val uploadFailed = stringResource(Res.string.medical_photos_upload_failed)
    val deleteFailed = stringResource(Res.string.medical_photos_delete_failed)
    val portraitUploadFailed = stringResource(Res.string.child_photo_upload_failed)
    val portraitDeleteFailed = stringResource(Res.string.child_photo_delete_failed)
    LaunchedEffect(photoError) {
        val message = when (photoError) {
            MedicalPhotoError.UPLOAD_FAILED -> uploadFailed
            MedicalPhotoError.DELETE_FAILED -> deleteFailed
            MedicalPhotoError.PORTRAIT_UPLOAD_FAILED -> portraitUploadFailed
            MedicalPhotoError.PORTRAIT_DELETE_FAILED -> portraitDeleteFailed
            null -> null
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearPhotoError()
        }
    }

    val childInfo = (uiState as? ChildInfoUiState.Success)
        ?.childInfoList
        ?.firstOrNull { it.id == childInfoId }

    GuestInviteSheet(
        state = guestInvite,
        onChooseDuration = viewModel::chooseGuestDuration,
        onCreate = viewModel::createGuestInvite,
        // The message is worded at the tap, through Compose Multiplatform's reader, which reads a
        // string outside composition in a coroutine; no button where the phone has no share sheet.
        onShare = if (shareSheet.available) {
            { link ->
                scope.launch {
                    shareSheet.shareText(
                        text = UiText.Res(Res.string.guest_invite_share_message, listOf(link)).asString(textResolver),
                        chooserTitle = UiText.Res(Res.string.guest_invite_share).asString(textResolver)
                    )
                }
            }
        } else {
            null
        },
        onDismiss = viewModel::dismissGuestInvite
    )

    Scaffold(
        containerColor = ScreenContainerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = screenTopAppBarColors(),
                title = {
                    Text(childInfo?.childName ?: stringResource(Res.string.childinfo_screen_title))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.childinfo_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState is ChildInfoUiState.Loading -> ListSkeleton(rows = 3)
                childInfo != null -> ChildInfoContent(
                    childInfo = childInfo,
                    onEditClick = onEditClick,
                    onInviteGuest = { viewModel.openGuestInvite(childInfo.id) },
                    onRevokeGuest = { uid -> viewModel.revokeGuest(childInfo, uid) },
                    portrait = {
                        ChildPortrait(
                            child = childInfo,
                            coParentName = rememberCoParentName(),
                            onPick = { uri -> viewModel.setPhoto(childInfo, uri) },
                            onRemove = { viewModel.removePhoto(childInfo) }
                        )
                    }
                )
                // The record was deleted, here or on the co-parent's phone, while this screen was
                // open. Saying so beats an empty page that looks like a failed load — and it is
                // its own sentence: "No child information yet" read as though the child had
                // never been added.
                else -> EmptyState(
                    icon = Icons.Default.ChildCare,
                    title = stringResource(Res.string.childinfo_record_gone_title),
                    description = stringResource(Res.string.childinfo_record_gone_description),
                    actionLabel = stringResource(Res.string.childinfo_back),
                    onAction = onNavigateBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
