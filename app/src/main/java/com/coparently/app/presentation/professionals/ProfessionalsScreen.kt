package com.coparently.app.presentation.professionals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.professionals.ProfessionalGrant
import com.coparently.app.domain.professionals.ProfessionalGrantPolicy
import com.coparently.app.presentation.common.AccountAvatar
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.Spacing

/**
 * Who outside the family can read it as a professional, and the controls that change that
 * (MON-18) — and, on a professional's own phone, the families they may read.
 *
 * Parents see every grant with where it stands: waiting for their own consent, waiting for the
 * co-parent's, active until a date, or ended. A row opens a card with the one action that row
 * needs — consent, when it is waiting for this parent — and the one every row has: end access.
 * A professional sees their families and two read-only doors, Calendar and Parenting plan, open
 * only while both parents have consented.
 *
 * @param onNavigateUp Returns to Settings.
 * @param onOpenCalendar Opens the read-only calendar for a grant, by id.
 * @param onOpenPlan Opens the read-only parenting plan for a grant, by id.
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfessionalsScreen(
    onNavigateUp: () -> Unit,
    onOpenCalendar: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    viewModel: ProfessionalsViewModel = hiltViewModel()
) {
    val familyGrants by viewModel.familyGrants.collectAsState()
    val myGrants by viewModel.myGrants.collectAsState()
    val invite by viewModel.invite.collectAsState()
    val redeem by viewModel.redeem.collectAsState()
    val openGrantId by viewModel.openGrantId.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val viewerUid = remember { viewModel.viewerUid() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(actionError) {
        actionError?.let { res ->
            snackbarHostState.showSnackbar(context.getString(res))
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = { ProfessionalsTopBar(onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            FamilyGrantsSection(familyGrants, viewerUid, viewModel::openGrant, viewModel::openInvite)
            if (myGrants.isNotEmpty()) {
                MyAccessSection(myGrants, viewerUid, onOpenCalendar, onOpenPlan)
            }
            RedeemSection(redeem, viewModel::updateCode, viewModel::redeemCode)
        }
    }

    if (invite.isOpen) {
        ProfessionalInviteSheet(
            state = invite,
            onChooseRole = viewModel::chooseRole,
            onChooseDuration = viewModel::chooseDuration,
            onCreate = viewModel::createInvite,
            onDismiss = viewModel::dismissInvite
        )
    }
    familyGrants.firstOrNull { it.id == openGrantId }?.let { grant ->
        ProfessionalGrantDialog(
            grant = grant,
            viewerUid = viewerUid,
            onConsent = { viewModel.consent(grant.id) },
            onRevoke = { viewModel.revoke(grant.id) },
            onDismiss = viewModel::closeGrant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfessionalsTopBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.professional_section_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }
        }
    )
}

/** The parents' half: every grant over this parent's families, then the invite row. */
@Composable
private fun FamilyGrantsSection(
    grants: List<ProfessionalGrant>,
    viewerUid: String?,
    onOpen: (String) -> Unit,
    onInvite: () -> Unit
) {
    if (grants.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Gavel,
            title = stringResource(R.string.professional_empty),
            description = stringResource(R.string.professional_section_supporting),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        SectionGroup {
            grants.forEachIndexed { index, grant ->
                if (index > 0) Divider()
                SectionRow(
                    title = grant.name,
                    supporting = stringResource(
                        R.string.professional_row_supporting,
                        stringResource(grant.role.labelRes()),
                        grantStatusText(grant, viewerUid)
                    ),
                    leading = { AccountAvatar(name = grant.name, photoUrl = grant.photoUrl, size = 32.dp) },
                    onClick = { onOpen(grant.id) },
                    trailing = { Chevron() }
                )
            }
        }
    }
    SectionGroup {
        SectionRow(
            icon = Icons.Default.PersonAdd,
            title = stringResource(R.string.professional_invite_action),
            onClick = onInvite
        )
    }
}

/**
 * The professional's half: one group per family, with its status and the two read-only doors.
 * The doors are drawn disabled rather than hidden while access is not active, so a professional
 * who redeemed a code can see where they will read once the second parent agrees.
 */
@Composable
private fun MyAccessSection(
    grants: List<ProfessionalGrant>,
    viewerUid: String?,
    onOpenCalendar: (String) -> Unit,
    onOpenPlan: (String) -> Unit
) {
    GroupLabel(stringResource(R.string.professional_my_access_title))
    grants.forEach { grant ->
        val active = ProfessionalGrantPolicy.isActive(grant, System.currentTimeMillis())
        SectionGroup {
            SectionRow(
                title = professionalFamilyLabel(grant),
                supporting = grantStatusText(grant, viewerUid)
            )
            Divider()
            SectionRow(
                icon = Icons.Default.CalendarMonth,
                title = stringResource(R.string.professional_open_calendar),
                onClick = if (active) ({ onOpenCalendar(grant.id) }) else null,
                trailing = { if (active) Chevron() }
            )
            Divider()
            SectionRow(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = stringResource(R.string.professional_open_plan),
                onClick = if (active) ({ onOpenPlan(grant.id) }) else null,
                trailing = { if (active) Chevron() }
            )
        }
    }
}

/** "I have a professional code": one field and one button, reaching the professional callable only. */
@Composable
private fun RedeemSection(
    state: ProfessionalRedeemState,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        GroupLabel(stringResource(R.string.professional_redeem_title))
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.professional_redeem_label)) },
            singleLine = true,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        )
        val message = state.errorRes?.let { stringResource(it) }
            ?: if (state.accepted) stringResource(R.string.professional_redeem_done) else null
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.errorRes != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Button(
            onClick = onRedeem,
            enabled = !state.isBusy && state.code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.professional_redeem_action))
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
