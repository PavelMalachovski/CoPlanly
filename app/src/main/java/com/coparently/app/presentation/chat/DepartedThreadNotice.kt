package com.coparently.app.presentation.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coparently.app.R
import com.coparently.app.domain.chat.DepartedThread
import com.coparently.app.presentation.common.BannerTone
import com.coparently.app.presentation.common.InlineBanner
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.utils.DAY_IN_SENTENCE
import com.coparently.app.utils.localizedDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The banner over a thread kept after the co-parent deleted their account: who left, the day the
 * thread is deleted, and the way to export it before then (GDPR review, September 2026).
 *
 * ATTENTION, because it asks for something with a deadline. The action opens the same export
 * screen as Settings → Family → Export the record, naming this thread, so it is the thread the
 * record covers even while another family is on screen.
 *
 * @param thread The kept thread.
 * @param onOpenExport Opens the export screen for the thread's conversation id, or null where this
 *   screen cannot navigate there — then the banner says what will happen and offers nothing it
 *   cannot do (design item 8).
 * @param modifier Modifier for the banner.
 */
@Composable
internal fun DepartedThreadBanner(
    thread: DepartedThread,
    onOpenExport: ((conversationId: String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val unnamed = stringResource(R.string.chat_departed_unnamed)
    val name = thread.departedName.ifBlank { unnamed }
    val day = departureDay(thread.retainedUntilMillis).format(localizedDate(DAY_IN_SENTENCE))
    val exportAction: (@Composable () -> Unit)? = if (onOpenExport == null) {
        null
    } else {
        {
            Button(onClick = { onOpenExport(thread.conversationId) }) {
                Text(stringResource(R.string.chat_departed_export))
            }
        }
    }
    InlineBanner(
        text = stringResource(R.string.chat_departed_banner, name, day),
        modifier = modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
        icon = Icons.Default.Schedule,
        tone = BannerTone.ATTENTION,
        actions = exportAction
    )
}

/**
 * Where the composer was: one line saying the thread takes no new messages. The pairing is gone,
 * so `firestore.rules` would refuse one anyway, and a composer whose sends always fail would be
 * the promise design item 8 forbids.
 */
@Composable
internal fun DepartedThreadComposerNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.chat_departed_no_sending),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.L, vertical = Spacing.M)
    )
}

/** The reader's calendar day of [millis]. `Instant.atZone`, not `LocalDate.ofInstant` (API 34). */
private fun departureDay(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
