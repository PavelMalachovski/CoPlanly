package com.coparently.app.presentation.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.coparently.app.data.repository.RatioSubmission
import com.coparently.app.domain.model.CustodyModelType
import com.coparently.app.domain.model.FamilyKind
import com.coparently.app.presentation.common.AppTopBar
import com.coparently.app.presentation.common.BarAction
import com.coparently.app.presentation.common.FamilySwitcherChip
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ScreenBar
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionGroupScope
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.rememberParentNames
import com.coparently.app.presentation.custody.labelRes
import com.coparently.app.presentation.home.MonthMoney
import com.coparently.app.presentation.home.balanceCaption
import com.coparently.app.presentation.home.formatMoney
import com.coparently.app.presentation.parentingplan.coParentLabel
import com.coparently.app.presentation.settings.FamilyKindDialog
import com.coparently.app.presentation.settings.SettingsViewModel
import com.coparently.app.presentation.settings.SplitRatioDialog
import com.coparently.app.presentation.settings.caresForSummary
import com.coparently.app.presentation.stickers.HomeStickers
import com.coparently.app.presentation.stickers.StickersRow
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.documents_settings_description
import com.coparently.app.resources.documents_settings_title
import com.coparently.app.resources.earlier_families_row_supporting
import com.coparently.app.resources.earlier_families_title
import com.coparently.app.resources.export_settings_description
import com.coparently.app.resources.export_settings_title
import com.coparently.app.resources.family_children_add
import com.coparently.app.resources.family_coparent_shared
import com.coparently.app.resources.family_group_agreements
import com.coparently.app.resources.family_group_home
import com.coparently.app.resources.family_group_mine
import com.coparently.app.resources.family_group_money
import com.coparently.app.resources.family_group_people
import com.coparently.app.resources.family_group_records
import com.coparently.app.resources.family_money_this_month
import com.coparently.app.resources.family_pairing_linked
import com.coparently.app.resources.family_title
import com.coparently.app.resources.friend_section_supporting
import com.coparently.app.resources.friend_section_title
import com.coparently.app.resources.home_contacts
import com.coparently.app.resources.home_contacts_supporting
import com.coparently.app.resources.journal_settings_description
import com.coparently.app.resources.journal_title
import com.coparently.app.resources.month_review_settings_description
import com.coparently.app.resources.month_review_title
import com.coparently.app.resources.nav_settings
import com.coparently.app.resources.parenting_plan_settings_description
import com.coparently.app.resources.parenting_plan_title
import com.coparently.app.resources.professional_section_supporting
import com.coparently.app.resources.professional_section_title
import com.coparently.app.resources.settings_child_info_description
import com.coparently.app.resources.settings_child_info_title
import com.coparently.app.resources.settings_coparent_profile_description
import com.coparently.app.resources.settings_custody_description
import com.coparently.app.resources.settings_custody_title
import com.coparently.app.resources.settings_family_kind_kept_by_co_parent
import com.coparently.app.resources.settings_family_kind_not_saved
import com.coparently.app.resources.settings_family_kind_records_kept
import com.coparently.app.resources.settings_family_kind_title
import com.coparently.app.resources.settings_pairing_description
import com.coparently.app.resources.settings_pairing_title
import com.coparently.app.resources.settings_pets_description
import com.coparently.app.resources.settings_pets_title
import com.coparently.app.resources.settings_split_ratio_applied
import com.coparently.app.resources.settings_split_ratio_proposed
import com.coparently.app.resources.settings_split_ratio_refused
import com.coparently.app.resources.settings_split_ratio_title
import com.coparently.app.resources.settings_split_ratio_value_named
import com.coparently.app.resources.support_row_supporting
import com.coparently.app.resources.support_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Slot 1 and slot 2, as the split row names them. */
private const val SLOT_ONE = "mom"
private const val SLOT_TWO = "dad"

/**
 * Where the Family tab's rows lead. One callback per destination, all screens that already exist.
 *
 * @property expenses The month's expenses, from the money card
 * @property support Child support: the agreement and each month paid
 * @property custody The custody schedule editor
 * @property parentingPlan The parenting plan
 * @property export The communication record export
 * @property monthReview Month in review
 * @property childInfo The child records
 * @property pets The pet records
 * @property contacts The family's contacts
 * @property documents The shared documents vault
 * @property pairing The co-parent link
 * @property coParentProfile What the co-parent has shared
 * @property friends Family friends who read the calendar
 * @property professionals Mediators, lawyers and therapists
 * @property unassignedRecords Records that name no family yet
 * @property journal The private journal
 * @property stickers Stickers received and sent
 * @property earlierFamilies Families this parent was in, read-only
 * @property settings The app's settings
 */
data class FamilyDestinations(
    val expenses: () -> Unit,
    val support: () -> Unit,
    val custody: () -> Unit,
    val parentingPlan: () -> Unit,
    val export: () -> Unit,
    val monthReview: () -> Unit,
    val childInfo: () -> Unit,
    val pets: () -> Unit,
    val contacts: () -> Unit,
    val documents: () -> Unit,
    val pairing: () -> Unit,
    val coParentProfile: () -> Unit,
    val friends: () -> Unit,
    val professionals: () -> Unit,
    val unassignedRecords: () -> Unit,
    val journal: () -> Unit,
    val stickers: () -> Unit,
    val earlierFamilies: () -> Unit,
    val settings: () -> Unit
)

/**
 * The Family tab (new-user review, October 2026, owner decision): money, the arrangements the two
 * parents keep, the children's and the home's records, the people around the family, and what is
 * only this parent's — everything Settings used to hold that is the product rather than a setting.
 *
 * It replaced the Expenses tab: the month's money is the first card here, and the full Expenses
 * screen is one tap from it. Settings, behind the gear, keeps the app and the account.
 *
 * @param destinations Where each row leads
 * @param viewModel The money card and the children hint
 * @param settingsViewModel The family answer and the split, which this tab edits through their
 *   dialogs as Settings used to
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod") // five labelled groups of rows, in order; splitting would only scatter them
fun FamilyScreen(
    destinations: FamilyDestinations,
    viewModel: FamilyViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    earlierFamiliesViewModel: EarlierFamiliesViewModel = koinViewModel()
) {
    val earlierFamilies by earlierFamiliesViewModel.families.collectAsState()
    val money by viewModel.money.collectAsState()
    val hasChildren by viewModel.hasChildren.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    val caresFor by settingsViewModel.caresFor.collectAsState()
    val myCaresFor by settingsViewModel.myCaresFor.collectAsState()
    val familySplit by settingsViewModel.split.collectAsState()
    val settingsState by settingsViewModel.settingsState.collectAsState()
    val parents by settingsViewModel.parents.collectAsState()
    val parentNames = rememberParentNames(parents)
    var showFamilyKindPicker by rememberSaveable { mutableStateOf(false) }
    var showSplitPicker by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (showSplitPicker) {
        SplitRatioDialog(
            current = familySplit.ratio,
            parentNames = parentNames,
            // As in Settings: a family that has agreed a split already is asked, not told, and a
            // new proposal is refused while the co-parent's own waits.
            proposes = familySplit.familyId != null && familySplit.settings != null &&
                familySplit.settings?.proposal?.proposedBy.let { it == null || it == settingsState.myUid },
            onConfirm = { ratio, answerBy ->
                settingsViewModel.submitRatio(ratio, familySplit, answerBy)
                showSplitPicker = false
            },
            onDismiss = { showSplitPicker = false }
        )
    }
    if (showFamilyKindPicker) {
        FamilyKindDialog(
            selected = myCaresFor,
            onConfirm = { kinds ->
                settingsViewModel.setCaresFor(kinds)
                showFamilyKindPicker = false
            },
            onDismiss = { showFamilyKindPicker = false }
        )
    }
    FamilyOutcomes(settingsViewModel, snackbarHostState)

    // The co-parent of the family on screen, by name, once linked; null until then.
    val linkedCoParent = parentNames.parents.coParent?.let { parentNames.labelForUid(it.uid) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                ScreenBar(
                    title = stringResource(Res.string.family_title),
                    largeTitle = true,
                    actions = listOf(
                        BarAction(
                            label = stringResource(Res.string.nav_settings),
                            icon = Icons.Default.Settings,
                            sfSymbol = "gearshape",
                            onClick = destinations.settings
                        )
                    ),
                    trailingContent = { FamilySwitcherChip() }
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = Spacing.L, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            Group(Res.string.family_group_money) {
                FamilyMoneyCard(money = money, coParentName = linkedCoParent, onOpen = destinations.expenses)
                Divider()
                NavRow(
                    Icons.Default.Balance,
                    Res.string.settings_split_ratio_title,
                    stringResource(
                        Res.string.settings_split_ratio_value_named,
                        parentNames.labelFor(SLOT_ONE),
                        familySplit.ratio.momPercent,
                        parentNames.labelFor(SLOT_TWO),
                        familySplit.ratio.dadPercent
                    )
                ) { showSplitPicker = true }
                // Child support (25 October 2026): its own screen, never part of the balance.
                Divider()
                NavRow(
                    Icons.Default.Payments,
                    Res.string.support_title,
                    Res.string.support_row_supporting,
                    destinations.support
                )
            }

            AgreementsGroup(schedule, destinations)

            Group(Res.string.family_group_home) {
                if (FamilyKind.CHILDREN in caresFor) {
                    NavRow(
                        Icons.Default.ChildCare,
                        Res.string.settings_child_info_title,
                        stringResource(
                            if (hasChildren) {
                                Res.string.settings_child_info_description
                            } else {
                                Res.string.family_children_add
                            }
                        ),
                        destinations.childInfo
                    )
                    Divider()
                }
                if (FamilyKind.PETS in caresFor) {
                    NavRow(
                        Icons.Default.Pets,
                        Res.string.settings_pets_title,
                        Res.string.settings_pets_description,
                        destinations.pets
                    )
                    Divider()
                }
                NavRow(
                    Icons.Default.Contacts,
                    Res.string.home_contacts,
                    Res.string.home_contacts_supporting,
                    destinations.contacts
                )
                Divider()
                NavRow(
                    Icons.Default.FolderShared,
                    Res.string.documents_settings_title,
                    Res.string.documents_settings_description,
                    destinations.documents
                )
                Divider()
                // What this family co-parents, and the way back into that answer: without it a
                // family that gets a dog a year later could never reach the pet records.
                NavRow(Icons.Default.FamilyRestroom, Res.string.settings_family_kind_title, caresForSummary(caresFor)) {
                    showFamilyKindPicker = true
                }
            }

            RecordsGroup(destinations)

            Group(Res.string.family_group_people) {
                // Records that name none of the families; draws itself and its divider only while
                // some wait.
                UnassignedRecordsRow(onOpen = destinations.unassignedRecords)
                NavRow(
                    Icons.Default.Group,
                    Res.string.settings_pairing_title,
                    // Linked: who with. Not yet: what linking gives.
                    linkedCoParent?.let { stringResource(Res.string.family_pairing_linked, it) }
                        ?: stringResource(Res.string.settings_pairing_description),
                    destinations.pairing
                )
                // Only once linked: before that the screen could say nothing but "not linked",
                // which the row above already says (second audit). Named for the co-parent, under
                // their row (UX audit M5: "Co-parent", "Co-parent details" and "My details" read as
                // three rows about the same person). This parent's own details are in Settings.
                if (linkedCoParent != null) {
                    Divider()
                    SectionRow(
                        icon = Icons.Default.PersonOutline,
                        title = stringResource(Res.string.family_coparent_shared, linkedCoParent),
                        supporting = stringResource(Res.string.settings_coparent_profile_description),
                        onClick = destinations.coParentProfile,
                        trailing = { Chevron() }
                    )
                }
                Divider()
                NavRow(
                    Icons.Default.Diversity3,
                    Res.string.friend_section_title,
                    Res.string.friend_section_supporting,
                    destinations.friends
                )
                Divider()
                NavRow(
                    Icons.Default.Gavel,
                    Res.string.professional_section_title,
                    Res.string.professional_section_supporting,
                    destinations.professionals
                )
            }

            Group(Res.string.family_group_mine) {
                StickersRow(onOpen = destinations.stickers)
                EarlierFamiliesRow(shown = !earlierFamilies.isNullOrEmpty(), onOpen = destinations.earlierFamilies)
            }
            // A sticker that has not been seen yet, and "Send a sticker": here rather than on Home
            // (owner decision, October 2026).
            HomeStickers(coParentName = parentNames.coParentLabel())
        }
    }
}

/**
 * The schedule, the plan, the record and the month review. The schedule row names the schedule in
 * force ([schedule]), and asks for one only while there is none.
 */
@Composable
private fun AgreementsGroup(schedule: CustodyModelType?, destinations: FamilyDestinations) {
    Group(Res.string.family_group_agreements) {
        NavRow(
            Icons.Default.DateRange,
            Res.string.settings_custody_title,
            // The schedule in force, by kind; the set-up prompt only while there is none.
            schedule?.let { stringResource(it.labelRes()) }
                ?: stringResource(Res.string.settings_custody_description),
            destinations.custody
        )
        Divider()
        NavRow(
            Icons.AutoMirrored.Filled.Assignment,
            Res.string.parenting_plan_title,
            Res.string.parenting_plan_settings_description,
            destinations.parentingPlan
        )
    }
}

/**
 * What the family's records add up to (UX audit M5): the month in review, the record a lawyer or a
 * court is sent, and this parent's private journal, which only that record can carry out of the
 * phone. They used to sit among the agreements, and the journal in "Only mine" two groups away.
 */
@Composable
private fun RecordsGroup(destinations: FamilyDestinations) {
    Group(Res.string.family_group_records) {
        NavRow(
            Icons.Default.Insights,
            Res.string.month_review_title,
            Res.string.month_review_settings_description,
            destinations.monthReview
        )
        Divider()
        NavRow(
            Icons.Default.Description,
            Res.string.export_settings_title,
            Res.string.export_settings_description,
            destinations.export
        )
        Divider()
        NavRow(
            Icons.Default.Lock,
            Res.string.journal_title,
            Res.string.journal_settings_description,
            destinations.journal
        )
    }
}

/**
 * The snackbars the family answer and the split report, as Settings reported them: a split saved
 * is not a split proposed, and an answer the co-parent keeps changes nothing on screen.
 */
@Composable
private fun FamilyOutcomes(settingsViewModel: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    val splitApplied = stringResource(Res.string.settings_split_ratio_applied)
    val splitProposed = stringResource(Res.string.settings_split_ratio_proposed)
    val splitRefused = stringResource(Res.string.settings_split_ratio_refused)
    LaunchedEffect(Unit) {
        settingsViewModel.ratioSubmission.collect { outcome ->
            snackbarHostState.showSnackbar(
                when (outcome) {
                    RatioSubmission.APPLIED -> splitApplied
                    RatioSubmission.PROPOSED -> splitProposed
                    null -> splitRefused
                }
            )
        }
    }
    val keptByCoParent = stringResource(Res.string.settings_family_kind_kept_by_co_parent)
    val notSaved = stringResource(Res.string.settings_family_kind_not_saved)
    val recordsKept = stringResource(Res.string.settings_family_kind_records_kept)
    LaunchedEffect(Unit) {
        settingsViewModel.caresForOutcome.collect { outcome ->
            snackbarHostState.showSnackbar(
                when (outcome) {
                    SettingsViewModel.CaresForOutcome.KEPT_BY_CO_PARENT -> keptByCoParent
                    SettingsViewModel.CaresForOutcome.RECORDS_KEPT -> recordsKept
                    SettingsViewModel.CaresForOutcome.NOT_SAVED -> notSaved
                }
            )
        }
    }
}

/**
 * The month's money, the first thing on the Family tab: what was spent per currency and who owes
 * whom, worded as Home's money tile words it. The whole card opens Expenses, where Settle up is.
 * Amounts are never cut (design item 15): each currency has its own line and wraps.
 *
 * @param money This month's money, or null while it loads
 * @param coParentName The linked co-parent's name, so the balance says who owes whom; null unlinked
 * @param onOpen Opens the Expenses screen
 */
@Composable
fun SectionGroupScope.FamilyMoneyCard(money: MonthMoney?, coParentName: String?, onOpen: () -> Unit) {
    val amounts = money?.spend?.byCurrency.orEmpty().joinToString("\n") { formatMoney(it.amount, it.currency) }
    SectionRow(
        icon = Icons.Default.Payments,
        title = amounts.ifBlank { stringResource(Res.string.family_money_this_month) },
        supporting = money?.let {
            stringResource(Res.string.family_money_this_month) + "\n" + balanceCaption(it.balances, coParentName)
        },
        onClick = onOpen,
        trailing = { Chevron() }
    )
}

/** A labelled group of rows. */
@Composable
private fun Group(label: StringResource, content: @Composable SectionGroupScope.() -> Unit) {
    Column {
        GroupLabel(stringResource(label))
        SectionGroup(content = content)
    }
}

/**
 * Family → Earlier families, with the divider before it — only while a family has ended: its
 * records left every live screen and are read there (owner decision, 25 October 2026).
 */
@Composable
private fun SectionGroupScope.EarlierFamiliesRow(shown: Boolean, onOpen: () -> Unit) {
    if (!shown) return
    Divider()
    NavRow(Icons.Default.History, Res.string.earlier_families_title, Res.string.earlier_families_row_supporting, onOpen)
}

/** A row that opens a screen, with its title and description as resources. */
@Composable
private fun SectionGroupScope.NavRow(
    icon: ImageVector,
    title: StringResource,
    supporting: StringResource,
    onClick: () -> Unit
) = NavRow(icon, title, stringResource(supporting), onClick)

/** A row that opens a screen or a dialog, with a description already worded. */
@Composable
private fun SectionGroupScope.NavRow(
    icon: ImageVector,
    title: StringResource,
    supporting: String,
    onClick: () -> Unit
) {
    SectionRow(
        icon = icon,
        title = stringResource(title),
        supporting = supporting,
        onClick = onClick,
        trailing = { Chevron() }
    )
}

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
