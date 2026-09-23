package com.coparently.app.presentation.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.feed.CalendarFeedLink
import com.coparently.app.domain.feed.CreatedCalendarFeed
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.asString
import com.coparently.app.presentation.common.copySensitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Read-only calendar links for a parent whose phone cannot run the app (MON-17).
 *
 * The explanation comes first and says exactly what the link is and is not — design item 8: a
 * subscribed calendar is not the app, it shows no chat and changes nothing, and a parent who
 * expects either would be misled by a screen that let them assume it. Then the one action, the
 * link just made (the only time its URL can be seen), and the list with a revoke per link.
 *
 * @param onNavigateUp Returns to Settings.
 * @param viewModel Screen state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarFeedScreen(
    onNavigateUp: () -> Unit,
    viewModel: CalendarFeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val snackbarHostState = remember { SnackbarHostState() }
    var revoking by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message.asString(context))
            viewModel.messageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_feed_title)) },
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
    ) { padding ->
        FeedBody(
            state = state,
            onCreate = { viewModel.create(language) },
            onDone = viewModel::dismissCreated,
            onRevoke = { feedId -> revoking = feedId },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    revoking?.let { feedId ->
        RevokeDialog(
            onConfirm = {
                viewModel.revoke(feedId)
                revoking = null
            },
            onDismiss = { revoking = null }
        )
    }
}

/**
 * The screen below its top bar: the explanation, the one action, the link just made, the list.
 *
 * @param state What to show
 * @param onCreate Makes a new link
 * @param onDone Forgets the link just made
 * @param onRevoke Asks to revoke the link with this id
 * @param modifier Carries the Scaffold padding
 */
@Composable
private fun FeedBody(
    state: CalendarFeedUiState,
    onCreate: () -> Unit,
    onDone: () -> Unit,
    onRevoke: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isLoading && state.familyId == null) {
        EmptyState(
            icon = Icons.Default.Link,
            title = stringResource(R.string.calendar_feed_no_family),
            modifier = modifier
        )
        return
    }
    val context = LocalContext.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FeedExplanation()
        val canCreate = !state.isBusy && state.familyId != null
        SectionGroup {
            SectionRow(
                icon = Icons.Default.AddLink,
                title = stringResource(R.string.calendar_feed_create),
                onClick = if (canCreate) onCreate else null
            )
        }
        state.created?.let { created ->
            CreatedLink(
                onShare = { context.startActivity(shareIntent(context, created)) },
                onCopy = {
                    copySensitive(context, context.getString(R.string.calendar_feed_title), created.url)
                },
                onDone = onDone
            )
        }
        FeedLinks(
            links = state.links,
            isLoading = state.isLoading,
            busy = state.isBusy,
            onRevoke = onRevoke
        )
    }
}

/** What the link is, what it carries, what it never carries, and who can read it. */
@Composable
private fun FeedExplanation() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.calendar_feed_intro,
            R.string.calendar_feed_included,
            R.string.calendar_feed_excluded,
            R.string.calendar_feed_warning
        ).forEach { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The link just made: the one moment its address exists outside the calendar it goes to. */
@Composable
private fun CreatedLink(
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit
) {
    Column {
        SectionGroup {
            SectionRow(
                icon = Icons.Default.Link,
                title = stringResource(R.string.calendar_feed_created_title),
                supporting = stringResource(R.string.calendar_feed_created_supporting)
            )
            Divider()
            SectionRow(
                icon = Icons.Default.Share,
                title = stringResource(R.string.calendar_feed_share),
                onClick = onShare
            )
            Divider()
            SectionRow(
                icon = Icons.Default.ContentCopy,
                title = stringResource(R.string.calendar_feed_copy),
                onClick = onCopy
            )
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.calendar_feed_done))
        }
    }
}

/** The parent's live links to this family, each with its one control: revoke. */
@Composable
private fun FeedLinks(
    links: List<CalendarFeedLink>,
    isLoading: Boolean,
    busy: Boolean,
    onRevoke: (String) -> Unit
) {
    Column {
        GroupLabel(stringResource(R.string.calendar_feed_links_label))
        if (links.isEmpty()) {
            if (!isLoading) {
                EmptyState(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.calendar_feed_empty),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@Column
        }
        SectionGroup {
            links.forEachIndexed { index, link ->
                FeedLinkRow(link = link, enabled = !busy, onRevoke = { onRevoke(link.feedId) })
                if (index != links.lastIndex) Divider()
            }
        }
    }
}

/** One link: when it was made, whether a calendar has fetched it, and the revoke control. */
@Composable
private fun FeedLinkRow(link: CalendarFeedLink, enabled: Boolean, onRevoke: () -> Unit) {
    val made = remember(link.createdAtMillis) { formatDay(link.createdAtMillis) }
    val used = remember(link.lastUsedAtMillis) { formatDay(link.lastUsedAtMillis) }
    SectionRow(
        icon = Icons.Default.Link,
        title = stringResource(R.string.calendar_feed_link_title, made),
        // Creating a link stamps it as used at that moment; only a later fetch is a calendar.
        supporting = if (link.lastUsedAtMillis > link.createdAtMillis) {
            stringResource(R.string.calendar_feed_link_used, used)
        } else {
            stringResource(R.string.calendar_feed_link_unused)
        },
        trailing = {
            IconButton(onClick = onRevoke, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = stringResource(R.string.calendar_feed_revoke),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/** Revoking asks once, and says what it does and does not do. */
@Composable
private fun RevokeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_feed_revoke_confirm_title)) },
        text = { Text(stringResource(R.string.calendar_feed_revoke_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.calendar_feed_revoke_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_dialog_cancel))
            }
        }
    )
}

/** An epoch-millis instant as a medium date in the reader's locale. */
private fun formatDay(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())

/** The share sheet for a new link: the `webcal://` form to tap on an iPhone, and the https one. */
private fun shareIntent(context: Context, created: CreatedCalendarFeed): Intent {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            context.getString(R.string.calendar_feed_share_message, created.webcalUrl, created.url)
        )
    }
    return Intent.createChooser(sendIntent, context.getString(R.string.calendar_feed_share_chooser))
}
