package com.coparently.app.presentation.friends

import androidx.compose.runtime.Composable
import com.coparently.app.domain.friends.FriendAccessDuration
import com.coparently.app.domain.guests.GuestAccessDuration
import com.coparently.app.resources.Res
import com.coparently.app.resources.friend_compare_duration_choice
import com.coparently.app.resources.friend_invite_duration_while_linked
import com.coparently.app.resources.guest_invite_duration_month
import com.coparently.app.resources.guest_invite_duration_quarter
import com.coparently.app.resources.guest_invite_duration_week
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The localized name of a grant length.
 *
 * Reuses the guest sheet's strings: they name a duration ("30 days"), not what it grants, so a
 * second set worded identically would be five more files to keep in step for no gain.
 */
internal fun GuestAccessDuration.labelRes(): StringResource = when (this) {
    GuestAccessDuration.WEEK -> Res.string.guest_invite_duration_week
    GuestAccessDuration.MONTH -> Res.string.guest_invite_duration_month
    GuestAccessDuration.QUARTER -> Res.string.guest_invite_duration_quarter
}

/** The localized name of a family friend's access length: the guest strings, and "While we are linked". */
internal fun FriendAccessDuration.labelRes(): StringResource = when (this) {
    FriendAccessDuration.WEEK -> Res.string.guest_invite_duration_week
    FriendAccessDuration.MONTH -> Res.string.guest_invite_duration_month
    FriendAccessDuration.QUARTER -> Res.string.guest_invite_duration_quarter
    FriendAccessDuration.WHILE_LINKED -> Res.string.friend_invite_duration_while_linked
}

/**
 * Every length a family friend can be given, as one phrase — "1 week, 30 days, 90 days or While
 * we are linked" — for the comparison table's "How long" cell (phase 2, spec §4.2).
 *
 * Built from [FriendAccessDuration.entries], the list the invite's chips draw, rather than written
 * out: a length added to the chips then appears in the table without anybody remembering it.
 */
@Composable
internal fun durationChoicesText(): String {
    val labels = FriendAccessDuration.entries.map { stringResource(it.labelRes()) }
    val choice = if (labels.size >= 2) {
        stringResource(Res.string.friend_compare_duration_choice, labels[labels.size - 2], labels.last())
    } else {
        ""
    }
    return joinChoices(labels) { _, _ -> choice }
}

/**
 * [labels] as one phrase: the last two joined by [lastTwo] ("30 days or 90 days"), any earlier
 * ones before them with ", ".
 */
internal fun joinChoices(labels: List<String>, lastTwo: (String, String) -> String): String =
    when (labels.size) {
        0 -> ""
        1 -> labels.single()
        else -> (labels.dropLast(2) + lastTwo(labels[labels.size - 2], labels.last())).joinToString(", ")
    }
