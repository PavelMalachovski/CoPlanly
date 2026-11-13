package com.coparently.app.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coparently.app.data.consent.HealthConsentWithdrawal
import com.coparently.app.data.remote.google.GoogleCalendarConnection
import com.coparently.app.data.sync.SyncStatus
import com.coparently.app.domain.holidays.HolidayCountry
import com.coparently.app.domain.money.SupportedCurrency
import com.coparently.app.domain.telemetry.TelemetryConsent
import com.coparently.app.presentation.ai.AiConsentSettingsEntry
import com.coparently.app.presentation.chat.SendHold
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.BarLeading
import com.coparently.app.presentation.common.ConfirmationDialog
import com.coparently.app.presentation.common.FamilySwitcherDialog
import com.coparently.app.presentation.common.FamilySwitcherViewModel
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ScreenBar
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.SignedInAsRow
import com.coparently.app.presentation.common.UiState
import com.coparently.app.presentation.common.animations.sectionEnter
import com.coparently.app.presentation.common.animations.sectionExit
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.coverageNote
import com.coparently.app.presentation.common.familyLabel
import com.coparently.app.presentation.common.labelRes
import com.coparently.app.presentation.common.regionLabelRes
import com.coparently.app.presentation.common.regionName
import com.coparently.app.presentation.common.regionSummaryRes
import com.coparently.app.presentation.consent.HealthConsentSettingsRow
import com.coparently.app.presentation.consent.HealthConsentViewModel
import com.coparently.app.presentation.consent.HealthConsentWithdrawDialog
import com.coparently.app.presentation.consent.TelemetryConsentViewModel
import com.coparently.app.presentation.platform.LocalAppBuildValues
import com.coparently.app.presentation.platform.LocalAppLanguages
import com.coparently.app.presentation.platform.LocalGoogleCalendarSignIn
import com.coparently.app.presentation.platform.LocalOpenUrl
import com.coparently.app.presentation.platform.LocalPermissionRequester
import com.coparently.app.presentation.platform.PlatformPermission
import com.coparently.app.presentation.sync.GoogleCalendarSyncState
import com.coparently.app.presentation.sync.SyncViewModel
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.ParentColor
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.ParentColors
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.calendar_feed_settings_description
import com.coparently.app.resources.calendar_feed_settings_title
import com.coparently.app.resources.country_label
import com.coparently.app.resources.country_settings_summary
import com.coparently.app.resources.currency_picker_title
import com.coparently.app.resources.currency_settings_title
import com.coparently.app.resources.health_consent_withdraw_failed
import com.coparently.app.resources.health_consent_withdraw_photos_failed
import com.coparently.app.resources.health_consent_withdrawn
import com.coparently.app.resources.privacy_policy_description
import com.coparently.app.resources.privacy_policy_title
import com.coparently.app.resources.settings_about
import com.coparently.app.resources.settings_about_tagline
import com.coparently.app.resources.settings_account_delete
import com.coparently.app.resources.settings_account_delete_cancel
import com.coparently.app.resources.settings_account_delete_confirm_message
import com.coparently.app.resources.settings_account_delete_confirm_title
import com.coparently.app.resources.settings_account_delete_description
import com.coparently.app.resources.settings_account_delete_failed
import com.coparently.app.resources.settings_account_delete_final_message
import com.coparently.app.resources.settings_account_delete_final_title
import com.coparently.app.resources.settings_account_delete_final_yes
import com.coparently.app.resources.settings_account_delete_progress
import com.coparently.app.resources.settings_account_description
import com.coparently.app.resources.settings_account_sign_out
import com.coparently.app.resources.settings_back
import com.coparently.app.resources.settings_calendar_sign_out
import com.coparently.app.resources.settings_co_parent_sync
import com.coparently.app.resources.settings_collapse
import com.coparently.app.resources.settings_data_sources_description
import com.coparently.app.resources.settings_data_sources_title
import com.coparently.app.resources.settings_expand
import com.coparently.app.resources.settings_family_kind_cancel
import com.coparently.app.resources.settings_family_kind_save
import com.coparently.app.resources.settings_family_shown
import com.coparently.app.resources.settings_gcal_auto_note
import com.coparently.app.resources.settings_gcal_enable_sync
import com.coparently.app.resources.settings_gcal_needs_reconnect
import com.coparently.app.resources.settings_gcal_not_signed_in
import com.coparently.app.resources.settings_gcal_private_note
import com.coparently.app.resources.settings_google_sync
import com.coparently.app.resources.settings_group_account
import com.coparently.app.resources.settings_group_app
import com.coparently.app.resources.settings_group_chat
import com.coparently.app.resources.settings_group_privacy
import com.coparently.app.resources.settings_group_sync
import com.coparently.app.resources.settings_language_picker_cancel
import com.coparently.app.resources.settings_language_picker_title
import com.coparently.app.resources.settings_language_title
import com.coparently.app.resources.settings_my_profile_description
import com.coparently.app.resources.settings_my_profile_title
import com.coparently.app.resources.settings_operation_failed
import com.coparently.app.resources.settings_parent_color
import com.coparently.app.resources.settings_parent_color_desc
import com.coparently.app.resources.settings_pause_before_sending_description
import com.coparently.app.resources.settings_pause_before_sending_title
import com.coparently.app.resources.settings_push_notifications
import com.coparently.app.resources.settings_push_notifications_description
import com.coparently.app.resources.settings_school_import_description
import com.coparently.app.resources.settings_school_import_title
import com.coparently.app.resources.settings_sync
import com.coparently.app.resources.settings_sync_failed
import com.coparently.app.resources.settings_sync_from_google
import com.coparently.app.resources.settings_sync_idle
import com.coparently.app.resources.settings_sync_last
import com.coparently.app.resources.settings_syncing
import com.coparently.app.resources.settings_telemetry_description
import com.coparently.app.resources.settings_telemetry_title
import com.coparently.app.resources.settings_theme
import com.coparently.app.resources.settings_theme_dark
import com.coparently.app.resources.settings_theme_light
import com.coparently.app.resources.settings_theme_system
import com.coparently.app.resources.settings_title
import com.coparently.app.resources.settings_unknown
import com.coparently.app.resources.sync_google_reconnect
import com.coparently.app.resources.sync_sign_in_google
import com.coparently.app.resources.terms_of_service_description
import com.coparently.app.resources.terms_of_service_title
import com.coparently.app.utils.ReaderDateText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings screen.
 *
 * Restructured by the August 2026 design review, which found eleven differently-shaped cards
 * on one flat scroll: family setup (pairing, child, custody) sat *below* the two sync cards it
 * is a prerequisite for, and no two cards agreed on whether a title got an icon, whether a row
 * was a `ListItem` or bespoke, or how many controls one card could carry.
 *
 * What replaced it: labelled groups sharing one row anatomy. Since the new-user review (October
 * 2026, owner decision) the family's rows — the co-parent, the schedule, the plan, the export,
 * the records and the people — live on the Family tab, which is the product; Settings keeps
 * **Sync**, **App** (this parent's colour and country among them) and **Account**, sharing one row
 * anatomy (icon, title, status or value, at
 * most one trailing control). Google Calendar's toggle-plus-status-plus-two-buttons collapses
 * into a single row that expands on demand, and signing out is a red text row at the bottom
 * rather than a full-width error-coloured button.
 *
 * Stateless composable: state lives in the ViewModels, this only renders and forwards events.
 *
 * @param onNavigateUp Returns to the screen that opened Settings
 * @param onNavigateToCalendarFeed Opens the read-only calendar links (MON-17); the row shows
 *   only while the account is in a family, since a link serves one
 * @param onNavigateToDataSources Opens the data sources and licences (MON-13's ODbL notice)
 * @param onSignOut Called after the user signs out of the app
 * @param syncViewModel Sync operations
 * @param settingsViewModel Settings state
 * @param authStateViewModel Firebase auth state, used to sign out
 * @param telemetryConsentViewModel The analytics and crash-reporting answer (REL-5)
 * @param healthConsentViewModel The child-health consent, withdrawn from its row in App
 */
// The complexity is the optional-callback fan-out: each group is only rendered when the route
// behind it was wired, and every one of those checks is a separate branch.
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: (() -> Unit)? = null,
    onNavigateToCalendarFeed: (() -> Unit)? = null,
    onNavigateToSchoolImport: (() -> Unit)? = null,
    onNavigateToDataSources: (() -> Unit)? = null,
    onNavigateToMyProfile: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    syncViewModel: SyncViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    authStateViewModel: com.coparently.app.presentation.sync.AuthStateViewModel = koinViewModel(),
    telemetryConsentViewModel: TelemetryConsentViewModel = koinViewModel(),
    healthConsentViewModel: HealthConsentViewModel = koinViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val telemetryConsent by telemetryConsentViewModel.consent.collectAsState()
    val healthConsent by healthConsentViewModel.consent.collectAsState()
    val healthConsentWithdrawing by healthConsentViewModel.withdrawing.collectAsState()
    var showHealthConsentWithdraw by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    // The phone's half (MON-24 phase 2, slice 11f): links, the build's values, the notification
    // permission, the app's language and Google's sign-in, each from the shell.
    val openUrl = LocalOpenUrl.current
    val buildValues = LocalAppBuildValues.current
    val permissions = LocalPermissionRequester.current
    val appLanguages = LocalAppLanguages.current
    val googleCalendarSignIn = LocalGoogleCalendarSignIn.current

    val googleConnection by syncViewModel.connection.collectAsState()
    val isSyncEnabled by syncViewModel.isSyncEnabled.collectAsState()
    val googleSyncState by syncViewModel.syncState.collectAsState()
    val firestoreSyncStatus by syncViewModel.firestoreSyncStatus.collectAsState()
    val userEmail by syncViewModel.userEmail.collectAsState()
    // The Google Calendar row says what this device stores now, each time Settings opens: the
    // ViewModel lives as long as the activity, and outlives an account deleted or switched since.
    LaunchedEffect(Unit) { syncViewModel.refreshConnection() }

    val settingsUiState by settingsViewModel.settingsState.collectAsState()
    // The split is two numbers about money; without names they do not say whose is whose.
    val parents by settingsViewModel.parents.collectAsState()
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showCountryPicker by rememberSaveable { mutableStateOf(false) }
    val country by settingsViewModel.country.collectAsState()
    val holidayRegion by settingsViewModel.holidayRegion.collectAsState()
    var showRegionPicker by rememberSaveable { mutableStateOf(false) }
    var showFamilySwitcher by rememberSaveable { mutableStateOf(false) }
    // The same state and the same switch as the top-bar chip (M-8), so the two entry points
    // cannot disagree about which family is on screen. Observed off the signed-in row rather
    // than reloaded on entry; the co-parents' names are the one remote read, and it is cached.
    val familySwitcherViewModel: FamilySwitcherViewModel = koinViewModel()
    val familySwitcher by familySwitcherViewModel.state.collectAsState()

    if (showFamilySwitcher) {
        FamilySwitcherDialog(
            families = familySwitcher.families,
            selectedFamilyId = familySwitcher.selectedFamilyId,
            signalsOf = familySwitcher::signalsOf,
            onSelect = { familyId ->
                familySwitcherViewModel.select(familyId)
                showFamilySwitcher = false
            },
            onDismiss = { showFamilySwitcher = false }
        )
    }
    if (showCountryPicker) {
        CountryDialog(
            selected = country,
            selectedRegion = holidayRegion,
            onConfirm = { chosen ->
                settingsViewModel.setCountry(chosen)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false }
        )
    }
    if (showRegionPicker) {
        RegionDialog(
            country = country,
            selected = holidayRegion,
            onConfirm = { chosen ->
                settingsViewModel.setHolidayRegion(chosen)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }
    if (showColorPicker) {
        ParentColorDialog(
            selected = ParentColor.fromStored(parents.me?.colorCode),
            fallback = ParentColorChoice.defaultFor(parents.me?.slot.orEmpty()).color,
            onConfirm = { colour ->
                settingsViewModel.setParentColor(colour)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
    val darkTheme by settingsViewModel.darkThemeFlow.collectAsState()
    val account by settingsViewModel.account.collectAsState()
    val defaultCurrency by settingsViewModel.defaultCurrency.collectAsState()
    val pauseBeforeSending by settingsViewModel.pauseBeforeSending.collectAsState()

    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    // Deletion asks twice, the same shape unpair uses: the first dialog says what is lost,
    // the second says it is permanent. One tap on a red row must not be able to erase a
    // family's history.
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteFinalConfirm by remember { mutableStateOf(false) }
    var googleExpanded by remember { mutableStateOf(false) }
    // AppCompat is the source of truth for the per-app language; selecting a new value
    // recreates the activity, so a plain remember is enough to keep the label fresh.
    var currentLanguage by remember { mutableStateOf(appLanguages.current()) }

    // Settings had no way to report a failure at all. Account deletion is the first action
    // here that can fail in a way the user must know about — the account still exists and the
    // attempt has to be repeated — so the screen grows a snackbar rather than letting the
    // error message sit unread in the state.
    val snackbarHostState = remember { SnackbarHostState() }
    val deletionFailed = stringResource(Res.string.settings_account_delete_failed)
    LaunchedEffect(settingsUiState.errorMessage) {
        if (settingsUiState.errorMessage != null) {
            snackbarHostState.showSnackbar(deletionFailed)
            settingsViewModel.clearMessages()
        }
    }

    // Every other failure here — the push switch, loading the screen — lands in
    // `operationState`, which nothing used to collect, so it failed without a word. It gets its
    // own wording: the deletion snackbar above names an account that is still there.
    val operationState by settingsViewModel.operationState.collectAsState()
    val operationFailed = stringResource(Res.string.settings_operation_failed)
    LaunchedEffect(operationState) {
        if (operationState is UiState.Error) {
            snackbarHostState.showSnackbar(operationFailed)
            settingsViewModel.clearMessages()
        }
    }

    // A withdrawal deletes data on both phones, so how it ended is always said out loud.
    val healthWithdrawn = stringResource(Res.string.health_consent_withdrawn)
    val healthPhotosKept = stringResource(Res.string.health_consent_withdraw_photos_failed)
    val healthWithdrawFailed = stringResource(Res.string.health_consent_withdraw_failed)
    LaunchedEffect(Unit) {
        healthConsentViewModel.withdrawal.collect { outcome ->
            val message = when (outcome) {
                HealthConsentWithdrawal.WITHDRAWN -> healthWithdrawn
                HealthConsentWithdrawal.PHOTOS_NOT_DELETED -> healthPhotosKept
                HealthConsentWithdrawal.FAILED -> healthWithdrawFailed
                HealthConsentWithdrawal.SIGNED_OUT -> null
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(
        containerColor = ScreenContainerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val backLabel = stringResource(Res.string.settings_back)
            AppTopBar(
                ScreenBar(
                    title = stringResource(Res.string.settings_title),
                    leading = onNavigateUp?.let { BarLeading.Back(backLabel, it) } ?: BarLeading.None
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = Spacing.L, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            // ── SYNC ───────────────────────────────────────────────────────────
            Column {
                GroupLabel(stringResource(Res.string.settings_group_sync))
                SectionGroup {
                    // **At two, not at one.** A parent with a single co-parent sees the screen
                    // they always saw; a picker for a set of one is design item 8 in miniature.
                    // The same rule the child filter follows.
                    if (familySwitcher.canSwitch) {
                        SectionRow(
                            icon = Icons.Default.SwapHoriz,
                            title = stringResource(Res.string.settings_family_shown),
                            supporting = familyLabel(familySwitcher.selected),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showFamilySwitcher = true
                            },
                            trailing = { Chevron() }
                        )
                        Divider()
                    }
                    SectionRow(
                        icon = Icons.Default.Sync,
                        title = stringResource(Res.string.settings_co_parent_sync),
                        supporting = firestoreSyncStatus.summary(),
                        supportingColor = firestoreSyncStatus.summaryColor(),
                        supportingIcon = firestoreSyncStatus.summaryDot(),
                        trailing = {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                syncViewModel.performFirestoreSync()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(Res.string.settings_sync)
                                )
                            }
                        }
                    )
                    // Google Calendar only where the phone can sign in to Google (the iPhone cannot
                    // yet): a row whose sign-in button does nothing is design item 8's defect.
                    if (googleCalendarSignIn.available) {
                        Divider()
                        // A connection Google ended opens its section by itself: the Reconnect
                        // button should not wait behind a chevron.
                        LaunchedEffect(googleConnection) {
                            if (googleConnection == GoogleCalendarConnection.NEEDS_RECONNECT) {
                                googleExpanded = true
                            }
                        }
                        val needsReconnect = googleConnection == GoogleCalendarConnection.NEEDS_RECONNECT
                        // One row, expanded on demand — the card this replaces stacked a toggle,
                        // a status line and two full-width buttons into a single surface.
                        SectionRow(
                            icon = Icons.Default.EventAvailable,
                            title = stringResource(Res.string.settings_google_sync),
                            supporting = when (googleConnection) {
                                GoogleCalendarConnection.CONNECTED ->
                                    userEmail ?: stringResource(Res.string.settings_unknown)
                                GoogleCalendarConnection.NEEDS_RECONNECT ->
                                    stringResource(Res.string.settings_gcal_needs_reconnect)
                                GoogleCalendarConnection.NOT_CONNECTED ->
                                    stringResource(Res.string.settings_gcal_not_signed_in)
                            },
                            supportingColor = if (needsReconnect) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = { googleExpanded = !googleExpanded },
                            // One trailing control, and it is the disclosure (UX-11). The switch
                            // used to sit here beside the chevron, on a row that is itself
                            // clickable — three interaction models in one row, against the anatomy
                            // `SectionRow` exists to enforce. TalkBack announced a switch and never
                            // mentioned that the row expands, so the actions behind it were
                            // unreachable without sight.
                            //
                            // The toggle moved into the expanded block rather than the chevron
                            // being dropped, because expanding is what this row *is* — the August
                            // 2026 refresh replaced a card of stacked buttons with exactly that —
                            // and because the toggle belongs with the sign-in and sync actions that
                            // govern whether it can be used at all. It costs one extra tap on a
                            // control a parent sets once.
                            trailing = { DisclosureChevron(expanded = googleExpanded) }
                        )
                        AnimatedVisibility(visible = googleExpanded, enter = sectionEnter(), exit = sectionExit()) {
                            GoogleCalendarActions(
                                connection = googleConnection,
                                isSyncEnabled = isSyncEnabled,
                                onToggleSync = { enabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    syncViewModel.toggleSync(enabled)
                                },
                                syncState = googleSyncState,
                                onSignIn = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    syncViewModel.startGoogleSignIn(googleCalendarSignIn)
                                },
                                onSyncNow = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    syncViewModel.syncFromGoogle()
                                },
                                onCalendarSignOut = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Google's own keys only. This used to be the app's sign-out,
                                    // which reset the push switch, the family on screen and more.
                                    coroutineScope.launch { syncViewModel.disconnectGoogleCalendar() }
                                }
                            )
                        }
                    }
                    // Read-only links for a parent on an iPhone (MON-17). Only with a family: a
                    // link serves one family's calendar, and a row that could only answer "pair
                    // first" would be design item 8's empty promise.
                    onNavigateToCalendarFeed
                        ?.takeIf { familySwitcher.selectedFamilyId != null }
                        ?.let { navigate ->
                            Divider()
                            SectionRow(
                                icon = Icons.Default.Link,
                                title = stringResource(Res.string.calendar_feed_settings_title),
                                supporting = stringResource(Res.string.calendar_feed_settings_description),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    navigate()
                                },
                                trailing = { Chevron() }
                            )
                        }
                    // The school import (MON-8): Bakaláři today. It opens the connections screen,
                    // in every build — the import is real, so the row is no longer a promise.
                    // EduPage had an inert "Coming soon" row below this one; it was removed before
                    // the first release (owner decision, September 2026), and EduPage returns as a
                    // row only when its import exists (design item 8).
                    onNavigateToSchoolImport?.let { navigate ->
                        Divider()
                        SectionRow(
                            icon = Icons.Default.School,
                            title = stringResource(Res.string.settings_school_import_title),
                            supporting = stringResource(Res.string.settings_school_import_description),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navigate()
                            },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            // ── APP ────────────────────────────────────────────────────────────
            Column {
                GroupLabel(stringResource(Res.string.settings_group_app))
                SectionGroup {
                    // The parent's own colour. In the Family group rather than under App
                    // preferences because it is how this person is identified to the other one —
                    // the same kind of fact as their name, not a device setting like the theme.
                    SectionRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(Res.string.settings_parent_color),
                        supporting = stringResource(Res.string.settings_parent_color_desc),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showColorPicker = true
                        },
                        trailing = {
                            // The swatch rather than a chevron: one trailing control, and the
                            // colour itself says more than an arrow would.
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        ParentColors.choiceFill(
                                            ParentColor.fromStored(parents.me?.colorCode)
                                                ?: ParentColorChoice.defaultFor(
                                                    parents.me?.slot.orEmpty()
                                                ).color
                                        )
                                    )
                            )
                        }
                    )
                    Divider()
                    // Beside the colour rather than under App preferences: both are answers about
                    // this parent — how they are marked and where they are — while the language
                    // and the theme are answers about this device.
                    SectionRow(
                        icon = Icons.Default.Public,
                        title = stringResource(Res.string.country_label),
                        supporting = stringResource(Res.string.country_settings_summary),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showCountryPicker = true
                        },
                        // The value in the trailing slot, the way Language and Currency show
                        // theirs — one trailing control, and it says more than a chevron would.
                        trailing = { ValueLabel(stringResource(country.labelRes())) }
                    )
                    // Only for a country whose holidays vary by region — Germany's Länder,
                    // Slovakia's kraje. For every other country the row would change nothing
                    // (design rule 8).
                    val regionLabel = country.regionLabelRes()
                    val regionSummary = country.regionSummaryRes()
                    if (regionLabel != null && regionSummary != null) {
                        Divider()
                        SectionRow(
                            icon = Icons.Default.Public,
                            title = stringResource(regionLabel),
                            supporting = stringResource(regionSummary),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showRegionPicker = true
                            },
                            trailing = { ValueLabel(country.regionName(holidayRegion)) }
                        )
                    }
                    Divider()
                    SectionRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(Res.string.settings_theme),
                        onClick = { showThemePicker = true },
                        trailing = { ValueLabel(stringResource(themeLabelRes(darkTheme))) }
                    )
                    // Only where the app chooses its own language (the iPhone's is set in its own
                    // Settings app).
                    if (appLanguages.available) {
                        Divider()
                        SectionRow(
                            icon = Icons.Default.Language,
                            title = stringResource(Res.string.settings_language_title),
                            onClick = { showLanguagePicker = true },
                            trailing = { ValueLabel(stringResource(currentLanguage.labelRes)) }
                        )
                    }
                    Divider()
                    SectionRow(
                        icon = Icons.Default.Payments,
                        title = stringResource(Res.string.currency_settings_title),
                        onClick = { showCurrencyPicker = true },
                        trailing = {
                            ValueLabel("${defaultCurrency.code} ${defaultCurrency.symbol}")
                        }
                    )
                    // Only where this phone can register for pushes (the iPhone's stand-in cannot
                    // yet, and turning it on there would always fail like a network error).
                    if (settingsViewModel.pushAvailable) {
                        Divider()
                        // POST_NOTIFICATIONS is requested here, contextually, not on app start.
                        val notificationPermissionRequester =
                            com.coparently.app.presentation.common.rememberNotificationPermissionRequester()
                        SectionRow(
                            icon = Icons.Default.Notifications,
                            title = stringResource(Res.string.settings_push_notifications),
                            supporting = stringResource(Res.string.settings_push_notifications_description),
                            trailing = {
                                val pushLabel = stringResource(Res.string.settings_push_notifications)
                                Switch(
                                    modifier = Modifier.semantics { contentDescription = pushLabel },
                                    checked = settingsUiState.notificationsEnabled &&
                                        permissions.isGranted(PlatformPermission.NOTIFICATIONS),
                                    onCheckedChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (enabled) {
                                            notificationPermissionRequester.request {
                                                settingsViewModel.toggleNotifications(true)
                                            }
                                        } else {
                                            settingsViewModel.toggleNotifications(false)
                                        }
                                    },
                                    enabled = !settingsUiState.isLoading
                                )
                            }
                        )
                    }
                }
            }

            // ── CHAT ───────────────────────────────────────────────────────────
            // A setting of the chat, grouped as one so it is found where it is about (UX audit M6).
            Column {
                GroupLabel(stringResource(Res.string.settings_group_chat))
                SectionGroup {
                    // MON-19. Off by default: a pause and a hint are help a parent asks for, not
                    // one the app imposes on every message.
                    SectionRow(
                        icon = Icons.Default.HourglassTop,
                        title = stringResource(Res.string.settings_pause_before_sending_title),
                        supporting = stringResource(
                            Res.string.settings_pause_before_sending_description,
                            SendHold.PAUSE_SECONDS
                        ),
                        trailing = {
                            val pauseLabel = stringResource(Res.string.settings_pause_before_sending_title)
                            Switch(
                                checked = pauseBeforeSending,
                                modifier = Modifier.semantics { contentDescription = pauseLabel },
                                onCheckedChange = { enabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    settingsViewModel.setPauseBeforeSending(enabled)
                                }
                            )
                        }
                    )
                }
            }

            // ── PRIVACY AND DATA ───────────────────────────────────────────────
            // Everything a consent was asked for, and where the data comes from, apart from the
            // preferences above (UX audit M6: eleven rows of App mixed the two).
            Column {
                GroupLabel(stringResource(Res.string.settings_group_privacy))
                SectionGroup {
                    // The other half of what makes the first-run question a consent: a decision
                    // you cannot revisit is not one (REL-5). A switch rather than a row that
                    // opens the full screen again — the screen exists to *ask*, and re-asking
                    // somebody who has already answered is how a consent turns into nagging.
                    SectionRow(
                        icon = Icons.Default.Lock,
                        title = stringResource(Res.string.settings_telemetry_title),
                        supporting = stringResource(Res.string.settings_telemetry_description),
                        trailing = {
                            val telemetryLabel = stringResource(Res.string.settings_telemetry_title)
                            Switch(
                                checked = telemetryConsent == TelemetryConsent.GRANTED,
                                // Named for TalkBack, which otherwise reads only "Switch, on".
                                modifier = Modifier.semantics { contentDescription = telemetryLabel },
                                onCheckedChange = { granted ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    telemetryConsentViewModel.answer(granted)
                                }
                            )
                        }
                    )
                    Divider()
                    // Beside telemetry, the other consent. It withdraws the child-health consent
                    // (GDPR Art. 9(2)(a), Art. 7(3)); giving it is asked where the details are
                    // entered, not here.
                    HealthConsentSettingsRow(
                        consent = healthConsent,
                        withdrawing = healthConsentWithdrawing,
                        onWithdraw = { showHealthConsentWithdraw = true }
                    )
                    Divider()
                    // The AI-assist consent, beside the other consents: given where it is used,
                    // turned off here. Draws nothing (not even a divider) while the build has no AI.
                    AiConsentSettingsEntry(snackbarHostState = snackbarHostState)
                    // Last in Privacy and data: the attribution the OpenHolidays data's licence (ODbL 1.0)
                    // asks for, and the answer to "where do these holiday dates come from".
                    onNavigateToDataSources?.let { navigate ->
                        SectionRow(
                            icon = Icons.Default.Source,
                            title = stringResource(Res.string.settings_data_sources_title),
                            supporting = stringResource(Res.string.settings_data_sources_description),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navigate()
                            },
                            trailing = { Chevron() }
                        )
                    }
                }
            }

            // ── ACCOUNT ────────────────────────────────────────────────────────
            Column {
                GroupLabel(stringResource(Res.string.settings_group_account))
                SectionGroup {
                    // The app account, not the Google Calendar one reported under Sync. Same
                    // strip as the pairing screen, so "who am I signed in as" reads identically
                    // in both places it can be asked. It leads the group because it is the
                    // subject the other two rows are about.
                    account?.let { signedIn ->
                        SignedInAsRow(
                            account = signedIn,
                            modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.L)
                        )
                        Divider()
                    }
                    // This parent's own details, beside the account they belong to (UX audit M5:
                    // on the Family tab it sat among three other rows about people).
                    onNavigateToMyProfile?.let { navigate ->
                        SectionRow(
                            icon = Icons.Default.Person,
                            title = stringResource(Res.string.settings_my_profile_title),
                            supporting = stringResource(Res.string.settings_my_profile_description),
                            onClick = navigate,
                            trailing = { Chevron() }
                        )
                        Divider()
                    }
                    SectionRow(
                        icon = Icons.Default.Info,
                        title = stringResource(Res.string.settings_about),
                        supporting = stringResource(Res.string.settings_about_tagline),
                        trailing = {
                            ValueLabel(
                                text = buildValues.versionName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    Divider()
                    // REL-4: Play wants the policy reachable from inside an app that holds health
                    // data. Absent until the policy is hosted (`AppBuildValues.privacyPolicyUrl`), and
                    // where the phone cannot open a link.
                    buildValues.privacyPolicyUrl?.takeIf { openUrl.available }?.let { policyUrl ->
                        SectionRow(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(Res.string.privacy_policy_title),
                            supporting = stringResource(Res.string.privacy_policy_description),
                            onClick = { openUrl.open(policyUrl) },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconSizes.Standard)
                                )
                            }
                        )
                        Divider()
                    }
                    // The contract the sign-in screen pointed to, readable again afterwards (§ 1751
                    // of the Civil Code). Absent until hosted (`AppBuildValues.termsUrl`).
                    buildValues.termsUrl?.takeIf { openUrl.available }?.let { termsUrl ->
                        SectionRow(
                            icon = Icons.Default.Gavel,
                            title = stringResource(Res.string.terms_of_service_title),
                            supporting = stringResource(Res.string.terms_of_service_description),
                            onClick = { openUrl.open(termsUrl) },
                            trailing = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconSizes.Standard)
                                )
                            }
                        )
                        Divider()
                    }
                    // Destructive, so it stays at the very bottom of the screen. It reads as
                    // a red text row rather than a filled error button — but a row is easier
                    // to hit by accident than a deliberate button was, so it now confirms.
                    SectionRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(Res.string.settings_account_sign_out),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showSignOutConfirm = true }
                    )
                    Divider()
                    // Below sign-out on purpose: the two are neighbours in a user's mind and
                    // only one of them is reversible, so the reversible one is reached first.
                    // Google Play requires this path for any app that offers account creation.
                    SectionRow(
                        icon = Icons.Default.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(Res.string.settings_account_delete),
                        titleColor = MaterialTheme.colorScheme.error,
                        supporting = if (settingsUiState.isDeletingAccount) {
                            stringResource(Res.string.settings_account_delete_progress)
                        } else {
                            stringResource(Res.string.settings_account_delete_description)
                        },
                        onClick = {
                            if (!settingsUiState.isDeletingAccount) showDeleteConfirm = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.S))
        }
    }

    if (showHealthConsentWithdraw) {
        HealthConsentWithdrawDialog(
            onDismiss = { showHealthConsentWithdraw = false },
            onConfirm = {
                showHealthConsentWithdraw = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                healthConsentViewModel.withdraw()
            }
        )
    }

    if (showSignOutConfirm) {
        ConfirmationDialog(
            title = stringResource(Res.string.settings_account_sign_out),
            message = stringResource(Res.string.settings_account_description),
            confirmText = stringResource(Res.string.settings_account_sign_out),
            dismissText = stringResource(Res.string.settings_language_picker_cancel),
            isDestructive = true,
            onDismiss = { showSignOutConfirm = false },
            onConfirm = {
                showSignOutConfirm = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // Google Calendar sync first, then Firebase auth, then leave the screen — in
                // that order, on one coroutine. Launching the Google half detached let the
                // Firebase sign-out (and the screen change behind it) run ahead of it.
                coroutineScope.launch {
                    syncViewModel.signOut()
                    authStateViewModel.signOut()
                    onSignOut?.invoke()
                }
            }
        )
    }

    if (showDeleteConfirm) {
        ConfirmationDialog(
            title = stringResource(Res.string.settings_account_delete_confirm_title),
            message = stringResource(Res.string.settings_account_delete_confirm_message),
            confirmText = stringResource(Res.string.settings_account_delete),
            dismissText = stringResource(Res.string.settings_account_delete_cancel),
            isDestructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                showDeleteFinalConfirm = true
            }
        )
    }

    if (showDeleteFinalConfirm) {
        ConfirmationDialog(
            title = stringResource(Res.string.settings_account_delete_final_title),
            message = stringResource(Res.string.settings_account_delete_final_message),
            confirmText = stringResource(Res.string.settings_account_delete_final_yes),
            dismissText = stringResource(Res.string.settings_account_delete_cancel),
            isDestructive = true,
            onDismiss = { showDeleteFinalConfirm = false },
            onConfirm = {
                showDeleteFinalConfirm = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // The server erases the account and this device is wiped; only then does the
                // app leave the screen. On failure the account still exists and the row's
                // error message says so, so the user can try again.
                settingsViewModel.deleteAccount { onSignOut?.invoke() }
            }
        )
    }

    if (showThemePicker) {
        ThemePickerDialog(
            selected = darkTheme,
            onSelect = { choice ->
                showThemePicker = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (choice == null) {
                    settingsViewModel.resetThemeToSystemDefault()
                } else {
                    settingsViewModel.toggleDarkTheme(choice)
                }
            },
            onDismiss = { showThemePicker = false }
        )
    }

    if (showLanguagePicker) {
        SingleChoiceDialog(
            title = stringResource(Res.string.settings_language_picker_title),
            options = AppLanguage.entries.map { it to stringResource(it.labelRes) },
            selected = currentLanguage,
            onSelect = { language ->
                showLanguagePicker = false
                if (language != currentLanguage) {
                    currentLanguage = language
                    // Recreates the activity with the new locale and persists the choice
                    // (autoStoreLocales); below Android 13, "System default" over a choice
                    // starts the app again (SystemDefaultRelaunch). The phone's own half.
                    appLanguages.apply(language)
                }
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showCurrencyPicker) {
        SingleChoiceDialog(
            title = stringResource(Res.string.currency_picker_title),
            options = SupportedCurrency.entries.map { it to "${it.code}  ${it.symbol}" },
            selected = defaultCurrency,
            onSelect = { currency ->
                showCurrencyPicker = false
                settingsViewModel.setDefaultCurrency(currency)
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
}

/** The trailing chevron on a row that expands in place, showing which way it is now. */
@Composable
private fun DisclosureChevron(expanded: Boolean) {
    // Turns with the section it opens, over the same duration, instead of swapping glyphs in one
    // frame while the section below it animated.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) HALF_TURN_DEGREES else 0f,
        animationSpec = tween(Motion.MEDIUM_MS, easing = FastOutSlowInEasing),
        label = "disclosure_chevron"
    )
    Icon(
        imageVector = Icons.Default.ExpandMore,
        // Named, unlike [Chevron]. A navigation chevron repeats what the row already
        // announces; this one carries the row's *state*, which nothing else says aloud.
        contentDescription = stringResource(
            if (expanded) Res.string.settings_collapse else Res.string.settings_expand
        ),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(IconSizes.Standard)
            .rotate(rotation)
    )
}

/** A disclosure chevron pointing up: [Icons.Default.ExpandMore] turned half way round. */
private const val HALF_TURN_DEGREES = 180f

/** The trailing chevron on a row that navigates elsewhere. */
@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(IconSizes.Standard)
    )
}

/** The trailing current-value text on a row that opens a picker. */
@Composable
private fun ValueLabel(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}

/**
 * The Google Calendar actions, revealed under its row.
 *
 * @param connection Whether Google Calendar is connected, needs a reconnect, or never was
 * @param isSyncEnabled Whether syncing is switched on
 * @param syncState Current Google sync state
 * @param onSignIn Starts Google Sign-In — a first connection or a reconnect
 * @param onSyncNow Pulls events from Google now
 * @param onCalendarSignOut Disconnects the Google account (does not sign out of the app)
 */
@Composable
@Suppress("LongParameterList") // one call site; splitting it would only add a wrapper type
private fun GoogleCalendarActions(
    connection: GoogleCalendarConnection,
    isSyncEnabled: Boolean,
    onToggleSync: (Boolean) -> Unit,
    syncState: GoogleCalendarSyncState,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onCalendarSignOut: () -> Unit
) {
    val syncing = syncState is GoogleCalendarSyncState.Syncing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.L, end = Spacing.L, bottom = Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        // The toggle, moved off the row above (UX-11). It reads as a labelled control here
        // rather than an unexplained switch in a trailing slot, and it sits with the sign-in
        // that decides whether it can be turned on at all — `enabled` says so directly, where
        // in the trailing slot the same condition looked like an inert switch.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val syncLabel = stringResource(Res.string.settings_gcal_enable_sync)
            Text(
                text = syncLabel,
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isSyncEnabled,
                modifier = Modifier.semantics { contentDescription = syncLabel },
                onCheckedChange = onToggleSync,
                // On only while connected, off always. The switch is stored and survives a
                // disconnect now, so "on with nothing connected" is reachable — and must stay
                // switchable off. (This read `signedIn || !enabled`, the other way round.)
                enabled = connection == GoogleCalendarConnection.CONNECTED || isSyncEnabled
            )
        }

        GoogleCalendarNotes(isSyncEnabled)

        when (syncState) {
            is GoogleCalendarSyncState.Syncing -> StatusLine(
                text = syncState.message.asString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                busy = true
            )
            is GoogleCalendarSyncState.Success -> StatusLine(
                text = syncState.message.asString(),
                color = MaterialTheme.colorScheme.tertiary
            )
            is GoogleCalendarSyncState.Error -> StatusLine(
                text = syncState.message.asString(),
                color = MaterialTheme.colorScheme.error
            )
            else -> Unit
        }

        GoogleCalendarButtons(
            connection = connection,
            canSyncNow = isSyncEnabled && !syncing,
            busy = syncing,
            onSignIn = onSignIn,
            onSyncNow = onSyncNow,
            onCalendarSignOut = onCalendarSignOut
        )
    }
}

/**
 * What the Google Calendar switch does, said out loud, and when it runs.
 *
 * An import is private — it stays on the device that pulled it and never reaches the co-parent —
 * and a sync control that did not say so would be read as "publish my calendar to them", which is
 * the opposite (design item 8). While the switch is on the import also runs on its own
 * (`GoogleCalendarAutoImport`), so the button reads as "now", not as the only way in.
 */
@Composable
private fun GoogleCalendarNotes(isSyncEnabled: Boolean) {
    Text(
        text = stringResource(Res.string.settings_gcal_private_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (isSyncEnabled) {
        Text(
            text = stringResource(Res.string.settings_gcal_auto_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The buttons under the Google Calendar row: sign in while nothing is connected, Reconnect once
 * Google has ended the connection, Sync now while it holds — and the disconnect wherever a Google
 * account is still stored, so a parent who no longer wants it need not reconnect to be rid of it.
 *
 * Reconnect runs the same sign-in as a first connection. Before it existed, a connection Google had
 * ended still showed the address and the Sync button, and every sync failed with "Not connected".
 *
 * @param connection Whether Google Calendar is connected, needs a reconnect, or never was
 * @param canSyncNow Whether "Sync from Google Calendar" can run: sync on, and nothing running
 * @param busy Whether something is running, which every button waits for
 * @param onSignIn Starts Google Sign-In
 * @param onSyncNow Pulls events from Google now
 * @param onCalendarSignOut Disconnects the Google account (does not sign out of the app)
 */
@Composable
@Suppress("LongParameterList") // one call site, the section above
private fun GoogleCalendarButtons(
    connection: GoogleCalendarConnection,
    canSyncNow: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onCalendarSignOut: () -> Unit
) {
    when (connection) {
        GoogleCalendarConnection.NOT_CONNECTED -> Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text(stringResource(Res.string.sync_sign_in_google))
        }
        GoogleCalendarConnection.NEEDS_RECONNECT -> Button(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text(stringResource(Res.string.sync_google_reconnect))
        }
        GoogleCalendarConnection.CONNECTED -> Button(
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSyncNow
        ) {
            Text(stringResource(Res.string.settings_sync_from_google))
        }
    }
    if (connection != GoogleCalendarConnection.NOT_CONNECTED) {
        OutlinedButton(
            onClick = onCalendarSignOut,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text(stringResource(Res.string.settings_calendar_sign_out))
        }
    }
}

/**
 * A sync status line: a coloured dot (or spinner while working) and a message.
 *
 * Replaces the "✓ …" / "✗ …" text glyphs the audit flagged — a tick typed into a string is not
 * a status component, and it carries no colour or accessibility meaning.
 *
 * @param text Status message
 * @param color Dot and text colour, carrying the state
 * @param busy Shows a spinner instead of the dot while a sync is running
 */
@Composable
private fun StatusLine(text: String, color: Color, busy: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Theme picker. The three FilterChips this replaces were 12sp labels squeezed three-across;
 * a single-choice list is the Material pattern for picking one of three.
 *
 * @param selected Current choice — null means "follow the system"
 * @param onSelect Called with the new choice
 * @param onDismiss Dismisses without changing anything
 */
@Composable
private fun ThemePickerDialog(
    selected: Boolean?,
    onSelect: (Boolean?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf<Pair<Boolean?, String>>(
        null to stringResource(Res.string.settings_theme_system),
        false to stringResource(Res.string.settings_theme_light),
        true to stringResource(Res.string.settings_theme_dark)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_theme)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    ChoiceRow(
                        label = label,
                        selected = value == selected,
                        onSelect = { onSelect(value) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_language_picker_cancel))
            }
        }
    )
}

/**
 * A single-choice dialog, shared by the language and currency pickers so the two stop being
 * two hand-rolled copies of the same list.
 *
 * @param title Dialog title
 * @param options Value/label pairs, in display order
 * @param selected Currently selected value
 * @param onSelect Called with the chosen value
 * @param onDismiss Dismisses without changing anything
 */
@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    ChoiceRow(
                        label = label,
                        selected = value == selected,
                        onSelect = { onSelect(value) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_language_picker_cancel))
            }
        }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // One focus stop that announces itself as a radio button, instead of a clickable row
            // and a second, separately focusable RadioButton inside it.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.S)
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(Spacing.S))
        Text(text = label)
    }
}

private fun themeLabelRes(darkTheme: Boolean?): StringResource = when (darkTheme) {
    null -> Res.string.settings_theme_system
    true -> Res.string.settings_theme_dark
    false -> Res.string.settings_theme_light
}

/** One-line summary of co-parent sync, for the row's supporting text. */
@Composable
private fun SyncStatus.summary(): String = when (this) {
    is SyncStatus.Idle -> stringResource(Res.string.settings_sync_idle)
    is SyncStatus.Syncing -> stringResource(Res.string.settings_syncing)
    is SyncStatus.Success ->
        stringResource(Res.string.settings_sync_last, ReaderDateText.time(lastSyncTime))
    // Not `message`: that is the exception's own English text, kept for the log (CQ-14).
    is SyncStatus.Error -> stringResource(Res.string.settings_sync_failed)
}

/** Colour for [summary]; errors are the only state that shouts. */
@Composable
private fun SyncStatus.summaryColor(): Color = when (this) {
    is SyncStatus.Error -> MaterialTheme.colorScheme.error
    is SyncStatus.Success -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Status dot colour for [summary], or null while idle. */
@Composable
private fun SyncStatus.summaryDot(): Color? = when (this) {
    is SyncStatus.Idle -> null
    is SyncStatus.Syncing -> MaterialTheme.colorScheme.onSurfaceVariant
    is SyncStatus.Success -> MaterialTheme.colorScheme.tertiary
    is SyncStatus.Error -> MaterialTheme.colorScheme.error
}

/**
 * Picks the country whose public holidays the calendar draws (MON-13).
 *
 * A dialog rather than the wizard's chip row, because that is the anatomy every other Settings
 * choice here uses — colour, language, theme, currency — and a row of seven chips inside a
 * settings list would be the second interaction model in one group.
 *
 * It carries the same honesty the picker does, through the same [coverageNote]: the line under
 * the list says what the chosen country's calendar actually contains.
 */
@Composable
private fun CountryDialog(
    selected: HolidayCountry,
    selectedRegion: String?,
    onConfirm: (HolidayCountry) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by rememberSaveable(selected) { mutableStateOf(selected.name) }
    val country = HolidayCountry.entries.first { it.name == chosen }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.country_label)) },
        text = {
            Column {
                HolidayCountry.entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = chosen == entry.name, role = Role.RadioButton) {
                                chosen = entry.name
                            }
                            .padding(vertical = Spacing.S),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = chosen == entry.name,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(Spacing.M))
                        Text(stringResource(entry.labelRes()))
                    }
                }
                Text(
                    // The stored region only while the stored country is still the one chosen:
                    // switching country clears it on save (SettingsViewModel.setCountry).
                    text = country.coverageNote(selectedRegion.takeIf { country == selected }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.S)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(country) }) {
                Text(stringResource(Res.string.settings_family_kind_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_family_kind_cancel))
            }
        }
    )
}
