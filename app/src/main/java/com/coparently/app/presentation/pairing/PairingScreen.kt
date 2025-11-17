package com.coparently.app.presentation.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen for managing co-parent pairing and invitations.
 * Stateless composable that receives navigation callbacks and ViewModel.
 *
 * @param onNavigateBack Navigation callback to go back
 * @param viewModel ViewModel for pairing operations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onNavigateBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Co-Parent Pairing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {

        if (uiState.partnerEmail != null) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Paired with",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = uiState.partnerEmail ?: "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.removePairing() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Unpair")
                    }
                }
            }
        } else {
            // Show invitation or send invitation section
            OutlinedTextField(
                value = uiState.invitationEmail,
                onValueChange = viewModel::updateInvitationEmail,
                label = { Text("Partner email") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                isError = uiState.emailError != null,
                supportingText = if (uiState.emailError != null) {
                    { Text(
                        text = uiState.emailError ?: "",
                        color = MaterialTheme.colorScheme.error
                    ) }
                } else null,
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(
                    onClick = { viewModel.sendInvitation(onNavigateBack) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Invitation")
                }
            }
        }

        if (uiState.pendingInvitations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Pending Invitations",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            uiState.pendingInvitations.forEach { invitation ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = invitation["fromUserName"] as? String ?: "Unknown",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = invitation["fromUserEmail"] as? String ?: "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row {
                            TextButton(
                                onClick = {
                                    viewModel.acceptInvitation(
                                        invitation["id"] as? String ?: ""
                                    )
                                }
                            ) {
                                Text("Accept")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.rejectInvitation(
                                        invitation["id"] as? String ?: ""
                                    )
                                }
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

