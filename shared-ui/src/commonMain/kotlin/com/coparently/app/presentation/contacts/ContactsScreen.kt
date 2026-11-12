package com.coparently.app.presentation.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.coparently.app.domain.contacts.ContactDirectory
import com.coparently.app.domain.contacts.ContactGroup
import com.coparently.app.domain.contacts.DirectoryContact
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.GroupLabel
import com.coparently.app.presentation.common.ListSkeleton
import com.coparently.app.presentation.common.Loadable
import com.coparently.app.presentation.common.PillChip
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.common.valueOrNull
import com.coparently.app.presentation.platform.LocalOpenUrl
import com.coparently.app.presentation.platform.OpenUrl
import com.coparently.app.presentation.theme.ScreenContainerColor
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.screenTopAppBarColors
import com.coparently.app.resources.Res
import com.coparently.app.resources.contacts_back
import com.coparently.app.resources.contacts_call
import com.coparently.app.resources.contacts_empty
import com.coparently.app.resources.contacts_empty_action
import com.coparently.app.resources.contacts_empty_hint
import com.coparently.app.resources.contacts_no_dialer
import com.coparently.app.resources.contacts_title
import com.coparently.app.resources.contacts_vet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** How the numbers on one row are joined. Punctuation, not text — nothing to translate. */
private const val NUMBER_SEPARATOR = " · "

/**
 * Important phone numbers, one tap from the dialler: grandparents, doctors, a friend's parents.
 *
 * The list is the children's own `emergencyContacts` — one list, widened in meaning. A doctor is
 * an emergency contact by any reasonable reading, and two parallel lists would make a parent
 * guess which one grandma went into. `relationship` is free text and already carries
 * "grandmother" or "paediatrician".
 *
 * **Dialling goes through `Intent.ACTION_DIAL` ([OpenUrl.dial]), never `ACTION_CALL`.** `DIAL` opens the dialler
 * pre-filled and needs no permission; `CALL` places the call immediately and requires
 * `CALL_PHONE`. Asking a separated parent for permission to place calls, to save one tap, is not
 * a trade this app should make.
 *
 * @param onNavigateUp Goes back
 * @param onAddContact Opens the children's details, which is where a contact is added — this
 *   screen never writes, see [ContactsViewModel]. Offered from the empty state, which used to be
 *   two grey sentences and no way forward on the one screen a parent opens in a hurry.
 * @param viewModel Screen state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onNavigateUp: () -> Unit,
    onAddContact: () -> Unit,
    viewModel: ContactsViewModel = koinViewModel()
) {
    val groupsState by viewModel.groups.collectAsState()
    val groups = groupsState.valueOrNull.orEmpty()
    val openUrl = LocalOpenUrl.current
    // A phone that opens no links has no dialler: no call button and no tappable row there
    // (design item 8). On Android the platform always opens them, so nothing changes.
    val canDial = remember(openUrl) { openUrl.available }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Resolved in composable scope: the failure message is shown from a coroutine, which is not
    // one.
    val noDialerMessage = stringResource(Res.string.contacts_no_dialer)

    Scaffold(
        containerColor = ScreenContainerColor,
        topBar = { ContactsTopBar(onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (groupsState is Loadable.Loading) {
            // The emergency surface. A parent opening this in a hurry must not be told there
            // are no contacts a frame before being shown them.
            ListSkeleton(modifier = Modifier.padding(padding), rows = 4)
        } else if (groups.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Contacts,
                title = stringResource(Res.string.contacts_empty),
                description = stringResource(Res.string.contacts_empty_hint),
                actionLabel = stringResource(Res.string.contacts_empty_action),
                onAction = onAddContact,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L)
        ) {
            items(items = groups, key = { it.childId }) { group ->
                ChildContacts(
                    group = group,
                    onDial = if (canDial) {
                        { number ->
                            if (!openUrl.dial(number)) {
                                scope.launch { snackbarHostState.showSnackbar(noDialerMessage) }
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/** The screen's title and its up arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsTopBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        colors = screenTopAppBarColors(),
        title = { Text(stringResource(Res.string.contacts_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.contacts_back)
                )
            }
        }
    )
}

/**
 * One child's contacts, as a labelled group of rows.
 *
 * @param group The child and their contacts
 * @param onDial Opens the dialler on a number, or null where the phone has none
 */
@Composable
private fun ChildContacts(
    group: ContactGroup,
    onDial: ((String) -> Unit)?
) {
    Column {
        GroupLabel(group.childName)
        SectionGroup {
            group.contacts.forEachIndexed { index, contact ->
                ContactRow(contact = contact, onDial = onDial)
                if (index != group.contacts.lastIndex) Divider()
            }
        }
    }
}

/**
 * One person: their name, how they relate to the child, their numbers, and a call button.
 *
 * The whole row taps to dial, not only the chip — this is the screen someone opens with a hurt
 * child in the other arm, and a 48dp target beats a 24dp one. A contact with no number has
 * neither: the row is inert and carries no call action, because a button that opens an empty
 * dialler is worse than no button.
 *
 * The row shows *every* number recorded rather than only the one it dials, so a second number is
 * at least readable. It stays one action, per [SectionRow]'s one-trailing-control rule.
 *
 * @param contact The person
 * @param onDial Opens the dialler on a number, or null where the phone has none: the row is
 *   then inert, as for a contact with no number
 */
@Composable
private fun ContactRow(
    contact: DirectoryContact,
    onDial: ((String) -> Unit)?
) {
    val dial = onDial
    val dialable = if (dial != null) contact.dialable else null
    // A vet row carries a sentinel rather than a typed relationship, so the word is resolved
    // here, where there is a `Context` and five translations, instead of in the domain.
    val vetLabel = stringResource(Res.string.contacts_vet)
    val relationship = if (contact.relationship == ContactDirectory.VET_RELATIONSHIP) {
        vetLabel
    } else {
        contact.relationship
    }
    val title = contact.name.ifBlank { relationship }
    val supporting = (listOf(relationship) + contact.numbers)
        .filter { it.isNotBlank() }
        .joinToString(NUMBER_SEPARATOR)
    val callLabel = stringResource(Res.string.contacts_call)

    SectionRow(
        title = title,
        icon = Icons.Default.Person,
        supporting = supporting.ifBlank { null },
        onClick = dialable?.let { number -> { dial?.invoke(number) } },
        trailing = dialable?.let { number ->
            {
                PillChip(
                    label = callLabel,
                    icon = Icons.Default.Call,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { dial?.invoke(number) }
                )
            }
        }
    )
}
