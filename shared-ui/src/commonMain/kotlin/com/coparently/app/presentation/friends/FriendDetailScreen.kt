package com.coparently.app.presentation.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.friends.FriendRole
import com.coparently.app.presentation.common.AccountAvatar
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.friend_detail_title
import com.coparently.app.resources.friend_profile_blood_group
import com.coparently.app.resources.friend_profile_phone
import com.coparently.app.resources.friend_revoke
import com.coparently.app.resources.friend_revoke_confirm_message
import com.coparently.app.resources.friend_revoke_confirm_title
import com.coparently.app.resources.friend_revoke_failed
import com.coparently.app.resources.friend_role_friend
import com.coparently.app.resources.friend_role_grandparent
import com.coparently.app.resources.friend_role_guardian
import com.coparently.app.resources.pairing_cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * A friend, as the two parents read them: their face, what they are to the family, and the two
 * facts worth having in a hurry — a phone number and a blood group.
 *
 * Read-only, always. The friend authors this about themselves and the security rule refuses a
 * parent's write, so offering an edit here would promise something the server rejects.
 *
 * Removing their access lives at the bottom, per the destructive-action anatomy the sign-out row
 * set: a danger action belongs at the end of its screen, not mid-list beside a phone number.
 *
 * @param friendUid Whose profile to read.
 * @param onNavigateUp Returns to the friend list.
 * @param onRevoked Called once access has been ended, so the caller can leave the screen.
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailScreen(
    friendUid: String,
    onNavigateUp: () -> Unit,
    onRevoked: () -> Unit,
    viewModel: FriendViewModel = koinViewModel()
) {
    LaunchedEffect(friendUid) { viewModel.openFriend(friendUid) }
    val profile by viewModel.viewedProfile.collectAsState()
    val friends by viewModel.friends.collectAsState()

    // The grant is the fallback for both the name and the face: it carries them from accept
    // time, so this screen says who somebody is even before their profile document arrives —
    // and still does if they never wrote one.
    val grant = friends.firstOrNull { it.friendUid == friendUid }
    val name = profile?.name?.takeIf { it.isNotBlank() }
        ?: grant?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.friend_detail_title)
    val photoUrl = profile?.photoUrl ?: grant?.photoUrl

    var confirmingRevoke by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val revokeFailed = stringResource(Res.string.friend_revoke_failed)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccountAvatar(name = name, photoUrl = photoUrl, size = 64.dp)
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge
                    )
                    profile?.role?.let { role ->
                        Text(
                            text = stringResource(role.detailLabelRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ParentColors.friendText
                        )
                    }
                }
            }

            val phones = profile?.phones.orEmpty()
            val bloodGroup = profile?.bloodGroup
            if (phones.isNotEmpty() || !bloodGroup.isNullOrBlank()) {
                SectionGroup {
                    phones.forEachIndexed { index, phone ->
                        SectionRow(
                            icon = Icons.Default.Phone,
                            title = phone,
                            supporting = stringResource(Res.string.friend_profile_phone)
                        )
                        if (index != phones.lastIndex || !bloodGroup.isNullOrBlank()) Divider()
                    }
                    bloodGroup?.takeIf { it.isNotBlank() }?.let { group ->
                        SectionRow(
                            icon = Icons.Default.Bloodtype,
                            title = group,
                            supporting = stringResource(Res.string.friend_profile_blood_group)
                        )
                    }
                }
            }

            // Last on the screen, per the destructive-action anatomy.
            SectionGroup {
                SectionRow(
                    title = stringResource(Res.string.friend_revoke),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmingRevoke = true }
                )
            }
        }
    }

    if (confirmingRevoke) {
        ConfirmationDialog(
            title = stringResource(Res.string.friend_revoke_confirm_title, name),
            message = stringResource(Res.string.friend_revoke_confirm_message),
            confirmText = stringResource(Res.string.friend_revoke),
            dismissText = stringResource(Res.string.pairing_cancel),
            onConfirm = {
                confirmingRevoke = false
                viewModel.revoke(friendUid) { succeeded ->
                    if (succeeded) {
                        onRevoked()
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(revokeFailed) }
                    }
                }
            },
            onDismiss = { confirmingRevoke = false },
            isDestructive = true
        )
    }
}

/** The localized name of a role, as the parents read it. */
@androidx.annotation.StringRes
private fun FriendRole.detailLabelRes(): StringResource = when (this) {
    FriendRole.GUARDIAN -> Res.string.friend_role_guardian
    FriendRole.FRIEND -> Res.string.friend_role_friend
    FriendRole.GRANDPARENT -> Res.string.friend_role_grandparent
}
