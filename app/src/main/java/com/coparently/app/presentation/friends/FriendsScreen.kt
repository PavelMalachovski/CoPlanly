package com.coparently.app.presentation.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coparently.app.R
import com.coparently.app.domain.friends.CalendarFriendGrant
import com.coparently.app.domain.friends.FriendAccessDuration
import com.coparently.app.presentation.common.AccountAvatar
import com.coparently.app.presentation.common.EmptyState
import com.coparently.app.presentation.common.SectionGroup
import com.coparently.app.presentation.common.SectionRow
import com.coparently.app.presentation.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Who outside the family can see the calendar, and the one control that changes it (item 16).
 *
 * A parent reads, top to bottom, who a family friend is and what they see beside a co-parent
 * ([FriendComparisonTable], phase 2), the family's friends, and the invite row: the question a
 * parent opens this screen with is usually "who can see this", not "let somebody else in". Every
 * grant states when it ends — an access with no visible expiry is the failure this whole feature
 * is built to avoid.
 *
 * A row opens that friend's card rather than removing them: their phone number and blood group
 * are the reason the profile exists, and a list that only ever offered "remove access" would put
 * a destructive action where a person's name is and hide everything worth reading.
 *
 * A friend reads the other side: one row per family that admitted them, opening its calendar,
 * their own profile, and the row for another family's code. A code just redeemed opens the
 * calendar it names at once.
 *
 * @param onNavigateUp Returns to Settings.
 * @param onOpenFriend Opens one friend's card, by uid.
 * @param onOpenMyProfile Opens the friend's own profile — only reachable when signed in as one.
 * @param onOpenFriendCalendar Opens a family's calendar for its friend, by family id.
 * @param viewModel Screen state.
 */
@Composable
fun FriendsScreen(
    onNavigateUp: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenMyProfile: () -> Unit,
    onOpenFriendCalendar: (String) -> Unit,
    viewModel: FriendViewModel = hiltViewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val invite by viewModel.invite.collectAsState()
    val myGrants by viewModel.myGrants.collectAsState()
    val redeem by viewModel.redeem.collectAsState()
    val myName by viewModel.myName.collectAsState()

    // The button said "Open the family calendar", so a code that lands opens it — once.
    LaunchedEffect(redeem.openFamilyId) {
        redeem.openFamilyId?.let { familyId ->
            onOpenFriendCalendar(familyId)
            viewModel.calendarOpened()
        }
    }

    Scaffold(topBar = { FriendsTopBar(onNavigateUp) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            // The friend's own view of this screen: their families' calendars, the way into
            // their profile and the row for another family's code. A friend has no family friends
            // to list and no invitations to mint, so they see neither — the screen answers
            // whichever side of the relationship is signed in rather than showing controls that
            // would be refused.
            if (myGrants.isNotEmpty()) {
                MyFamilies(grants = myGrants, onOpenCalendar = onOpenFriendCalendar, onOpenMyProfile = onOpenMyProfile)
                // A friend of one family may be sent another's code, and its message sends her
                // here to type it (L-5: a friend may hold a grant in each family).
                FriendRedeemRow(
                    state = redeem,
                    onExpand = viewModel::expandRedeem,
                    onCodeChange = viewModel::updateCode,
                    onRedeem = viewModel::redeemCode
                )
            } else {
                ParentView(
                    friends = friends,
                    redeem = redeem,
                    actions = ParentViewActions(
                        onOpenFriend = onOpenFriend,
                        onInvite = viewModel::openInvite,
                        onExpandRedeem = viewModel::expandRedeem,
                        onCodeChange = viewModel::updateCode,
                        onRedeem = viewModel::redeemCode
                    )
                )
            }
        }
    }

    if (invite.isOpen) {
        FriendInviteSheet(
            state = invite,
            myName = myName,
            onChooseDuration = viewModel::chooseDuration,
            onCreate = viewModel::createInvite,
            onDismiss = viewModel::dismissInvite
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendsTopBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.friend_section_title)) },
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

/** What a parent's view of the screen can do. */
private data class ParentViewActions(
    val onOpenFriend: (String) -> Unit,
    val onInvite: () -> Unit,
    val onExpandRedeem: () -> Unit,
    val onCodeChange: (String) -> Unit,
    val onRedeem: () -> Unit
)

/**
 * A parent's view: who a family friend is, the comparison, the family's friends (or the empty
 * state), the invite row and — while there are no friends — the friend's way to type a code.
 */
@Composable
private fun ParentView(friends: List<CalendarFriendGrant>, redeem: FriendRedeemState, actions: ParentViewActions) {
    Text(
        text = stringResource(R.string.friend_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FriendComparisonTable()

    if (friends.isEmpty()) {
        // No action of its own: the invite row below is the way forward, and a second button
        // saying the same thing would be two answers to one question.
        EmptyState(
            icon = Icons.Default.Diversity3,
            title = stringResource(R.string.friend_empty),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        SectionGroup {
            friends.forEachIndexed { index, grant ->
                FriendRow(grant = grant, onOpen = { actions.onOpenFriend(grant.friendUid) })
                if (index != friends.lastIndex) Divider()
            }
        }
    }

    SectionGroup {
        SectionRow(
            icon = Icons.Default.PersonAdd,
            title = stringResource(R.string.friend_invite_action),
            onClick = actions.onInvite
        )
    }

    // Somebody handed a code needs somewhere to type it, and this screen is where Settings sends
    // them. Only while the account holds no grant and admits no friend: once a grant arrives the
    // friend's own view replaces this one, and carries the same row for another family's code.
    if (friends.isEmpty()) {
        FriendRedeemRow(
            state = redeem,
            onExpand = actions.onExpandRedeem,
            onCodeChange = actions.onCodeChange,
            onRedeem = actions.onRedeem
        )
    }
}

/**
 * A friend's view: one row per family that admitted them, each opening that family's calendar,
 * then their own profile — whose line says, as it always has, when the first access ends.
 */
@Composable
private fun MyFamilies(
    grants: List<CalendarFriendGrant>,
    onOpenCalendar: (String) -> Unit,
    onOpenMyProfile: () -> Unit
) {
    SectionGroup {
        grants.forEachIndexed { index, grant ->
            FamilyCalendarRow(grant = grant, onOpen = { onOpenCalendar(grant.familyId) })
            if (index != grants.lastIndex) Divider()
        }
    }
    // Soonest-ending first, so the date shown is the first access to end. With one family the row
    // reads exactly as it always did; a friend of several families is told how many, since a
    // single date would otherwise speak for all of them (L-5).
    val first = grants.first().expiresAtMillis
    val until = rememberDate(first)
    val whileLinked = FriendAccessDuration.isWhileLinked(first)
    SectionGroup {
        SectionRow(
            icon = Icons.Default.Diversity3,
            title = stringResource(R.string.friend_profile_title),
            supporting = when {
                grants.size == 1 -> accessText(first)
                whileLinked -> stringResource(R.string.friend_access_several_while_linked, grants.size)
                else -> stringResource(R.string.friend_access_several, grants.size, until)
            },
            onClick = onOpenMyProfile,
            trailing = {}
        )
    }
}

/** One family's calendar, named by its two parents when the grant carries their names. */
@Composable
private fun FamilyCalendarRow(grant: CalendarFriendGrant, onOpen: () -> Unit) {
    val until = rememberDate(grant.expiresAtMillis)
    val names = grant.familyParents.mapNotNull { grant.parentNames[it] }
    SectionRow(
        icon = Icons.Default.CalendarMonth,
        title = stringResource(R.string.friend_calendar_title),
        supporting = when {
            names.size != 2 -> accessText(grant.expiresAtMillis)
            FriendAccessDuration.isWhileLinked(grant.expiresAtMillis) ->
                stringResource(R.string.friend_calendar_row_supporting_while_linked, names[0], names[1])
            else -> stringResource(R.string.friend_calendar_row_supporting, names[0], names[1], until)
        },
        onClick = onOpen,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/** "Access until 19 Nov 2026", or "Access while the parents are linked" for a grant given so. */
@Composable
private fun accessText(expiresAtMillis: Long): String =
    if (FriendAccessDuration.isWhileLinked(expiresAtMillis)) {
        stringResource(R.string.friend_access_while_linked)
    } else {
        stringResource(R.string.friend_access_until, rememberDate(expiresAtMillis))
    }

/** An expiry instant as the reader's medium date. */
@Composable
private fun rememberDate(epochMillis: Long): String = remember(epochMillis) {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    )
}

/** One friend: their face, their name, when their access ends, and the way into their card. */
@Composable
private fun FriendRow(grant: CalendarFriendGrant, onOpen: () -> Unit) {
    SectionRow(
        title = grant.name,
        supporting = accessText(grant.expiresAtMillis),
        // Their Google account's own picture, copied into the grant at accept time so this list
        // needs no second read; `AccountAvatar` falls back to the initial when there is none.
        leading = { AccountAvatar(name = grant.name, photoUrl = grant.photoUrl, size = 32.dp) },
        // The one control on the row, per the design language's "at most one trailing control".
        onClick = onOpen,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/**
 * "I have a code from the parents" (phase 2, spec §4.2): a row for the friend rather than the
 * parent, which opens into one field and one button in place.
 *
 * Reaches the friend callable only. A co-parent or guest code typed here is refused and *said*
 * so — the two other kinds exist, somebody may well have been sent both, and the remedy differs.
 */
@Composable
private fun FriendRedeemRow(
    state: FriendRedeemState,
    onExpand: () -> Unit,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit
) {
    SectionGroup {
        SectionRow(
            icon = Icons.Default.Key,
            title = stringResource(R.string.friend_redeem_row_title),
            supporting = stringResource(R.string.friend_redeem_row_supporting),
            onClick = if (state.isExpanded) null else onExpand
        )
        if (state.isExpanded) {
            Column(
                modifier = Modifier.padding(start = Spacing.L, end = Spacing.L, bottom = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    // Asks for a code; the invite sheet's "Give them this code" is the other
                    // direction and read here as an instruction to share (release audit R-10).
                    label = { Text(stringResource(R.string.friend_redeem_code_label)) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                state.errorRes?.let { res ->
                    Text(
                        text = stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = onRedeem,
                    enabled = !state.isBusy && state.code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.friend_redeem_open_calendar))
                }
            }
        }
    }
}
