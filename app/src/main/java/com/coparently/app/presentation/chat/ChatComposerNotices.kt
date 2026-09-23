package com.coparently.app.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coparently.app.R
import com.coparently.app.domain.chat.ToneNudge
import com.coparently.app.utils.LightDarkPreviews
import com.coparently.app.utils.PreviewWrapper

/** Size of the leading glyph on both composer notices. */
private val NOTICE_ICON_SIZE = 18.dp

/**
 * A held message (MON-19): "Sending in N s…", what it says, and Undo.
 *
 * It sits above the composer rather than in the thread because the message is not in the thread
 * yet — it is not anywhere yet, which is what makes Undo real.
 *
 * @param pending The held message
 * @param onUndo Cancels it and returns the text to the composer
 */
@Composable
fun PendingSendNotice(pending: PendingSend, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.HourglassTop,
            contentDescription = null,
            modifier = Modifier.size(NOTICE_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chat_pending_send, pending.secondsLeft),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pending.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onUndo) {
            Text(stringResource(R.string.chat_pending_send_undo))
        }
    }
}

/**
 * The gentle second look (MON-19): one line per thing [ToneCheck][com.coparently.app.domain.chat.ToneCheck]
 * noticed. It never blocks the send button and never says more than what it counted — capitals,
 * "!!!", listed words — because that is all it is.
 *
 * @param nudge What was noticed; the caller renders this only when it is not empty
 */
@Composable
fun ToneNudgeHint(nudge: ToneNudge, modifier: Modifier = Modifier) {
    val shouting = stringResource(R.string.chat_nudge_shouting)
    val punctuation = stringResource(R.string.chat_nudge_punctuation)
    val listed = stringResource(R.string.chat_nudge_listed_words, nudge.words.joinToString(", "))
    val lines = listOfNotNull(
        shouting.takeIf { nudge.shouting },
        punctuation.takeIf { nudge.raisedPunctuation },
        listed.takeIf { nudge.words.isNotEmpty() }
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // Polite: read once when it appears, never interrupting what is being typed.
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(NOTICE_ICON_SIZE),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Column {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun ComposerNoticesPreview() {
    PreviewWrapper {
        Column {
            ToneNudgeHint(ToneNudge(shouting = true, raisedPunctuation = true, words = listOf("never")))
            PendingSendNotice(PendingSend("c", "See you at five.", secondsLeft = 3), onUndo = {})
        }
    }
}
