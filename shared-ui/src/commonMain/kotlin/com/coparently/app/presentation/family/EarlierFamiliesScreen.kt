package com.coparently.app.presentation.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.common.PushedTopBar
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.common_back
import com.coparently.app.resources.earlier_families_empty_description
import com.coparently.app.resources.earlier_families_empty_title
import com.coparently.app.resources.earlier_families_last_day
import com.coparently.app.resources.earlier_families_notice
import com.coparently.app.resources.earlier_families_title
import com.coparently.app.resources.earlier_families_unnamed
import com.coparently.app.resources.earlier_families_with
import com.coparently.app.resources.earlier_family_children
import com.coparently.app.resources.earlier_family_documents
import com.coparently.app.resources.earlier_family_documents_supporting
import com.coparently.app.resources.earlier_family_events
import com.coparently.app.resources.earlier_family_expenses
import com.coparently.app.resources.earlier_family_export
import com.coparently.app.resources.earlier_family_export_supporting
import com.coparently.app.resources.earlier_family_journal
import com.coparently.app.resources.earlier_family_more
import com.coparently.app.resources.earlier_family_nothing
import com.coparently.app.resources.earlier_family_notice
import com.coparently.app.resources.earlier_family_pets
import com.coparently.app.resources.earlier_family_total
import com.coparently.app.utils.ReaderDateText
import com.coparently.app.utils.ReaderMoneyText
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** A record's day, in the reader's own order. */
private const val DATE_SKELETON = "yMMMd"

/** Between a row's parts. */
private const val DETAIL_JOIN = " · "

/** The most rows a section lists before it says how many it left out. */
private const val MAX_ROWS = 30

/**
 * Family → Earlier families: one row per family this parent was in and is no longer, each opening
 * its records (owner decision, 25 October 2026). Nothing here is edited: an ended family's records
 * are history, kept out of every live family's screens and read here.
 *
 * @param onNavigateUp Returns to the Family tab.
 * @param onOpen Opens one family's records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarlierFamiliesScreen(
    onNavigateUp: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: EarlierFamiliesViewModel = koinViewModel()
) {
    val families by viewModel.families.collectAsState()
    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = { BackTopBar(stringResource(Res.string.earlier_families_title), onNavigateUp) }
    ) { padding ->
        val bodyModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        val list = families ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = stringResource(Res.string.earlier_families_empty_title),
                description = stringResource(Res.string.earlier_families_empty_description),
                modifier = bodyModifier
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = bodyModifier,
            contentPadding = PaddingValues(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            item(key = "notice") {
                InlineBanner(text = stringResource(Res.string.earlier_families_notice), tone = BannerTone.INFO)
            }
            item(key = "families") {
                SectionGroup {
                    list.forEachIndexed { index, family ->
                        if (index > 0) Divider()
                        SectionRow(
                            title = familyTitle(family),
                            icon = Icons.Default.History,
                            supporting = family.lastDay?.let { day ->
                                stringResource(
                                    Res.string.earlier_families_last_day,
                                    ReaderDateText.date(day, DATE_SKELETON)
                                )
                            },
                            onClick = { onOpen(family.familyId) },
                            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * One ended family's records, read-only: its events, expenses with their totals, children, pets
 * and journal notes, plus its documents and its export, which both parents may still open.
 *
 * @param familyId The ended family.
 * @param onOpenDocuments Opens its vault, read-only.
 * @param onExport Opens the export for it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarlierFamilyScreen(
    familyId: String,
    onNavigateUp: () -> Unit,
    onOpenDocuments: () -> Unit,
    onExport: () -> Unit,
    viewModel: EarlierFamiliesViewModel = koinViewModel()
) {
    val families by viewModel.families.collectAsState()
    val family = families?.firstOrNull { it.familyId == familyId }
    val title = family?.let { familyTitle(it) } ?: stringResource(Res.string.earlier_families_title)
    Scaffold(containerColor = ScreenContainerColor, topBar = { BackTopBar(title, onNavigateUp) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            item(key = "notice") {
                InlineBanner(text = stringResource(Res.string.earlier_family_notice), tone = BannerTone.INFO)
            }
            item(key = "papers") {
                SectionGroup {
                    SectionRow(
                        title = stringResource(Res.string.earlier_family_documents),
                        icon = Icons.Default.Description,
                        supporting = stringResource(Res.string.earlier_family_documents_supporting),
                        onClick = onOpenDocuments
                    )
                    Divider()
                    SectionRow(
                        title = stringResource(Res.string.earlier_family_export),
                        icon = Icons.Default.FileDownload,
                        supporting = stringResource(Res.string.earlier_family_export_supporting),
                        onClick = onExport
                    )
                }
            }
            if (family == null) return@LazyColumn
            if (family.holdsNoRecords) {
                item(key = "nothing") { Text(stringResource(Res.string.earlier_family_nothing)) }
            }
            records(family)
        }
    }
}

private fun LazyListScope.records(family: EarlierFamily) {
    section("events", Res.string.earlier_family_events, family.events) { event ->
        RecordRow(
            title = event.title,
            supporting = ReaderDateText.date(event.startDateTime.date, DATE_SKELETON),
            iconRes = Icons.Default.Event
        )
    }
    if (family.expenses.isNotEmpty()) {
        item(key = "expenses-total") {
            val totals = family.expenses.groupBy { it.currency }
                .map { (currency, expenses) -> ReaderMoneyText.amount(expenses.sumOf { it.amount }, currency) }
            Text(
                text = stringResource(Res.string.earlier_family_total, totals.joinToString(DETAIL_JOIN)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    section("expenses", Res.string.earlier_family_expenses, family.expenses) { expense ->
        RecordRow(
            title = expense.title,
            supporting = ReaderMoneyText.amount(expense.amount, expense.currency) + DETAIL_JOIN +
                ReaderDateText.date(expense.date, DATE_SKELETON),
            iconRes = Icons.Default.Payments
        )
    }
    section("children", Res.string.earlier_family_children, family.children) { child ->
        RecordRow(title = child.childName, supporting = null, iconRes = Icons.Default.ChildCare)
    }
    section("pets", Res.string.earlier_family_pets, family.pets) { pet ->
        RecordRow(title = pet.name, supporting = null, iconRes = Icons.Default.Pets)
    }
    section("journal", Res.string.earlier_family_journal, family.journal) { entry ->
        RecordRow(
            title = entry.text.lineSequence().firstOrNull().orEmpty(),
            supporting = ReaderDateText.date(entry.entryDate, DATE_SKELETON),
            iconRes = Icons.Default.Book
        )
    }
}

/** A labelled group of up to [MAX_ROWS] rows, saying how many more there are. */
private fun <T> LazyListScope.section(
    key: String,
    labelRes: StringResource,
    rows: List<T>,
    row: @Composable (T) -> Unit
) {
    if (rows.isEmpty()) return
    item(key = key) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            GroupLabel(stringResource(labelRes))
            SectionGroup {
                rows.take(MAX_ROWS).forEachIndexed { index, item ->
                    if (index > 0) Divider()
                    row(item)
                }
            }
            if (rows.size > MAX_ROWS) {
                Text(
                    text = stringResource(Res.string.earlier_family_more, rows.size - MAX_ROWS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecordRow(title: String, supporting: String?, iconRes: ImageVector) {
    SectionRow(title = title, icon = iconRes, supporting = supporting)
}

/** "With Zuzana", or "Earlier family" for one whose co-parent this phone never named. */
@Composable
private fun familyTitle(family: EarlierFamily): String =
    family.coParentName?.let { stringResource(Res.string.earlier_families_with, it) }
        ?: stringResource(Res.string.earlier_families_unnamed)

@Composable
private fun BackTopBar(title: String, onNavigateUp: () -> Unit) {
    PushedTopBar(title = title, backLabel = stringResource(Res.string.common_back), onBack = onNavigateUp)
}
