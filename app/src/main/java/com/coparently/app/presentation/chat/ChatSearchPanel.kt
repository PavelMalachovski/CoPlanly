package com.coparently.app.presentation.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.coparently.app.R
import com.coparently.app.domain.chat.ChatSearchHit
import com.coparently.app.domain.chat.SearchSnippet
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.ParentNames
import com.coparently.app.presentation.theme.Spacing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Date and time on a search result: the result may be from any day, unlike a bubble's. */
private val resultTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** Most lines a result's snippet takes. */
private const val SNIPPET_MAX_LINES = 2

/**
 * The search field that replaces the thread header's title while search is open (MON-15).
 *
 * @param query The query as typed
 * @param onQueryChange Called on every edit
 * @param onClear Empties the field, shown only when there is something to empty
 */
@Composable
fun ChatSearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val focus = remember { FocusRequester() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
        placeholder = { Text(stringResource(R.string.chat_search_placeholder)) },
        singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_search_clear))
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
    // Opening search is asking to type; the keyboard comes up with it.
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * What search found, in place of the thread while search is open (MON-15).
 *
 * @param state What to show
 * @param names Resolves a sender uid to that parent's name
 * @param onSelect Called with the result the reader tapped
 * @param modifier Modifier for the panel
 */
@Composable
fun ChatSearchResults(
    state: ChatSearchState,
    names: ParentNames,
    onSelect: (ChatSearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        ChatSearchState.Closed -> Unit
        // The prompt doubles as the privacy statement: what is searched, and that it stays here.
        ChatSearchState.Prompt -> EmptyState(
            icon = Icons.Default.Search,
            title = stringResource(R.string.chat_search_prompt_title),
            description = stringResource(R.string.chat_search_prompt_description),
            modifier = modifier.fillMaxSize()
        )
        ChatSearchState.Searching -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ChatSearchState.Failed -> EmptyState(
            icon = Icons.Default.ErrorOutline,
            title = stringResource(R.string.chat_search_failed_title),
            description = stringResource(R.string.chat_search_failed_description),
            modifier = modifier.fillMaxSize()
        )
        is ChatSearchState.Results -> if (state.result.hits.isEmpty()) {
            EmptyState(
                icon = Icons.Default.SearchOff,
                title = stringResource(R.string.chat_search_no_results_title),
                description = stringResource(R.string.chat_search_no_results_description, state.query.trim()),
                modifier = modifier.fillMaxSize()
            )
        } else {
            ResultList(state, names, onSelect, modifier)
        }
    }
}

@Composable
private fun ResultList(
    state: ChatSearchState.Results,
    names: ParentNames,
    onSelect: (ChatSearchHit) -> Unit,
    modifier: Modifier
) {
    val showLabel = stringResource(R.string.chat_search_show_in_thread)
    LazyColumn(modifier = modifier.fillMaxSize()) {
        // A message id is unique within a thread, and search returns each message once.
        items(state.result.hits, key = { it.message.id }) { hit ->
            ListItem(
                overlineContent = {
                    Text(
                        text = stringResource(
                            R.string.chat_search_result_meta,
                            senderName(hit, names),
                            formatSentAt(hit.message.sentAtMillis, resultTimeFormatter)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                headlineContent = {
                    Text(
                        text = highlighted(hit.snippet),
                        maxLines = SNIPPET_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.clickable(onClickLabel = showLabel, role = Role.Button) { onSelect(hit) }
            )
            HorizontalDivider()
        }
        if (state.result.truncated) {
            item(key = "truncated") {
                Text(
                    text = stringResource(R.string.chat_search_truncated, state.result.hits.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.L)
                )
            }
        }
    }
}

/**
 * Who sent [hit]'s message, by name. The stored `senderName` covers a uid [names] cannot place —
 * a message from before a re-pairing, or a co-parent of a family other than the one
 * `ParentsSource` follows.
 */
private fun senderName(hit: ChatSearchHit, names: ParentNames): String {
    val label = names.labelForUid(hit.message.senderId)
    return if (label == names.unknownFallback) hit.message.senderName.ifBlank { label } else label
}

/** [snippet]'s text with the match picked out in the theme's primary container. */
@Composable
private fun highlighted(snippet: SearchSnippet): AnnotatedString {
    val style = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold
    )
    return remember(snippet, style) {
        buildAnnotatedString {
            append(snippet.text)
            addStyle(style, snippet.matchStart, snippet.matchEnd)
        }
    }
}
