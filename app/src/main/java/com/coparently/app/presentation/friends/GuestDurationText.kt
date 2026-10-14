package com.coparently.app.presentation.friends

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.guests.GuestAccessDuration

/**
 * The localized name of a grant length.
 *
 * Reuses the guest sheet's strings: they name a duration ("30 days"), not what it grants, so a
 * second set worded identically would be five more files to keep in step for no gain.
 */
@StringRes
internal fun GuestAccessDuration.labelRes(): Int = when (this) {
    GuestAccessDuration.WEEK -> R.string.guest_invite_duration_week
    GuestAccessDuration.MONTH -> R.string.guest_invite_duration_month
    GuestAccessDuration.QUARTER -> R.string.guest_invite_duration_quarter
}

/**
 * Every length a family friend can be given, as one phrase — "1 week, 30 days or 90 days" — for
 * the comparison table's "How long" cell (phase 2, spec §4.2).
 *
 * Built from [GuestAccessDuration.entries], the list the invite's chips draw, rather than written
 * out: a length added to the chips then appears in the table without anybody remembering it.
 */
@Composable
internal fun durationChoicesText(): String {
    val labels = GuestAccessDuration.entries.map { stringResource(it.labelRes()) }
    val choice = if (labels.size >= 2) {
        stringResource(R.string.friend_compare_duration_choice, labels[labels.size - 2], labels.last())
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
