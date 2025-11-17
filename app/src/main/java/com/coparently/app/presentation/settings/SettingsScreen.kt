package com.coparently.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.presentation.settings.components.SettingsNavigationCard
import com.coparently.app.presentation.settings.components.SettingsSwitchCard
import com.coparently.app.presentation.sync.GoogleCalendarSyncState
import com.coparently.app.presentation.sync.SyncStatusIndicator
import com.coparently.app.presentation.sync.SyncViewModel
import kotlinx.coroutines.launch

/**
 * Settings screen для управления настройками приложения и синхронизацией.
 * Stateless composable, который делегирует управление состоянием ViewModels.
 *
 * Обновлен для использования нового Credential Manager API вместо deprecated GoogleSignIn.
 *
 * @param onNavigateUp Навигационный callback для возврата назад
 * @param onNavigateToChildInfo Навигационный callback для экрана информации о ребенке
 * @param onNavigateToPairing Навигационный callback для экрана pairing
 * @param syncViewModel ViewModel для операций синхронизации
 * @param settingsViewModel ViewModel для состояния настроек
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    onNavigateToChildInfo: (() -> Unit)? = null,
    onNavigateToPairing: (() -> Unit)? = null,
    syncViewModel: SyncViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Sync ViewModel states
    val isSignedIn by syncViewModel.isSignedIn.collectAsState()
    val isSyncEnabled by syncViewModel.isSyncEnabled.collectAsState()
    val googleSyncState by syncViewModel.syncState.collectAsState()
    val firestoreSyncStatus by syncViewModel.firestoreSyncStatus.collectAsState()
    val userEmail by syncViewModel.userEmail.collectAsState()

    // Settings ViewModel states
    val settingsUiState by settingsViewModel.settingsState.collectAsState()
    val operationState by settingsViewModel.operationState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
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
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Firestore Sync Status
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Co-Parent Sync",
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            syncViewModel.performFirestoreSync()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SyncStatusIndicator(
                        syncStatus = firestoreSyncStatus,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Automatically syncs events and child information with your co-parent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Google Calendar Sync
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Google Calendar Sync",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Sync enabled toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Sync",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isSyncEnabled,
                            onCheckedChange = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                syncViewModel.toggleSync(enabled)
                            },
                            enabled = isSignedIn || !isSyncEnabled
                        )
                    }

                    // Sign-in status
                    Text(
                        text = if (isSignedIn) "Signed in as: ${userEmail ?: "Unknown"}" else "Not signed in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSignedIn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Sync status
                    when (val state = googleSyncState) {
                        is GoogleCalendarSyncState.Syncing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        is GoogleCalendarSyncState.Success -> {
                            Text(
                                text = "✓ ${state.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        is GoogleCalendarSyncState.Error -> {
                            Text(
                                text = "✗ ${state.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        else -> {}
                    }

                    // Action buttons
                    if (!isSignedIn) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    syncViewModel.signIn()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            enabled = googleSyncState !is GoogleCalendarSyncState.Syncing
                        ) {
                            if (googleSyncState is GoogleCalendarSyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Sign in with Google")
                        }
                    } else {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                syncViewModel.syncFromGoogle()
                            },
                            enabled = isSyncEnabled && googleSyncState !is GoogleCalendarSyncState.Syncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            if (googleSyncState is GoogleCalendarSyncState.Syncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Sync from Google Calendar")
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                syncViewModel.signOut()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            enabled = googleSyncState !is GoogleCalendarSyncState.Syncing
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }

            // Co-Parent Pairing
            onNavigateToPairing?.let { navigate ->
                SettingsNavigationCard(
                    title = "Co-Parent Pairing",
                    description = "Invite or manage your co-parent",
                    icon = Icons.Default.Group,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navigate()
                    }
                )
            }

            // Child Information
            onNavigateToChildInfo?.let { navigate ->
                SettingsNavigationCard(
                    title = "Child Information",
                    description = "Medications, activities, allergies",
                    icon = Icons.Default.ChildCare,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navigate()
                    }
                )
            }

            // Notifications Settings
            SettingsSwitchCard(
                title = "Push Notifications",
                description = "Get notified about changes",
                icon = Icons.Default.Notifications,
                checked = settingsUiState.notificationsEnabled,
                onCheckedChange = { enabled ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    settingsViewModel.toggleNotifications(enabled)
                },
                enabled = !settingsUiState.isLoading
            )

            // About
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "About CoParently",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Version 1.1.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Shared calendar for co-parenting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
