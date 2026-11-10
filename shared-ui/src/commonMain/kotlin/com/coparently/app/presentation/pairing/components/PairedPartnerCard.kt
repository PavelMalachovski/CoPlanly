package com.coparently.app.presentation.pairing.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.coparently.app.domain.model.PartnerSummary
import com.coparently.app.presentation.common.AccountAvatar
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.pairing_paired_since
import com.coparently.app.resources.pairing_unknown_sender
import com.coparently.app.utils.readerMediumDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Summary of the linked co-parent.
 *
 * [PartnerSummary.name] and [PartnerSummary.email] can both be blank — the
 * repository falls back to an empty [PartnerSummary] when the partner's
 * profile document fails to load, rather than dropping the pairing itself.
 * This card must not render that as if it were real data: a blank name shows
 * a translated placeholder (and feeds the avatar initial instead of an empty
 * circle). The address is not shown at all.
 *
 * [PartnerSummary.photoUrl] is null until the co-parent's own phone runs a build
 * that stores one, so the initial-letter fallback in [AccountAvatar] is the
 * normal case here for as long as the other device is behind.
 */
@Composable
fun PairedPartnerCard(
    partner: PartnerSummary,
    modifier: Modifier = Modifier
) {
    val displayName = partner.name.ifBlank { stringResource(Res.string.pairing_unknown_sender) }

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.L),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccountAvatar(name = displayName, photoUrl = partner.photoUrl)
            // The name and the date, and no address (new-user review, October 2026): the
            // co-parent's e-mail is theirs, and on a screen two former partners both look at it
            // said more than the card needs to.
            Column(modifier = Modifier.padding(start = Spacing.L)) {
                Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                partner.pairedSinceMillis?.let { millis ->
                    Text(
                        text = stringResource(Res.string.pairing_paired_since, formatDate(millis)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Formats an epoch-millis instant as a localized medium date, on the phone's own day
 * (`readerMediumDate`, `FormatStyle.MEDIUM` on Android, as before).
 */
private fun formatDate(millis: Long): String =
    readerMediumDate(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date)
