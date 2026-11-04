package com.coparently.app.presentation.pairing.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coparently.app.domain.model.PairingInvite
import com.coparently.app.presentation.common.InviteCodeText
import com.coparently.app.presentation.common.dashedRoundedBorder
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.resources.Res
import com.coparently.app.resources.pairing_code_expired_generate
import com.coparently.app.resources.pairing_copy_code
import com.coparently.app.resources.pairing_expires_in_hours
import com.coparently.app.resources.pairing_expires_in_minutes
import com.coparently.app.resources.pairing_new_code
import com.coparently.app.resources.pairing_qr_code_content_description
import com.coparently.app.resources.pairing_share_link
import com.coparently.app.resources.pairing_tap_to_copy_and_expiry
import com.coparently.app.resources.pairing_your_code_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Side of the inline QR code. */
private val QR_SIZE = 180.dp

/**
 * The hero card of the "share my code" mode: the invite code, how long it lasts, a scannable
 * QR, and the two ways to hand it over.
 *
 * Reworked by the August 2026 design review, which found the code was a bare `TextButton` whose
 * tap copied it — with nothing on screen saying so — and that the QR sat behind a dialog even
 * though showing it is most of what this screen is for. The code now sits in a dashed container
 * with a copy glyph and an explicit "tap to copy" line, and the QR renders inline.
 *
 * Always fills the width of its container, so unlike the other pairing components it takes no
 * `modifier` — adding one would push this composable past detekt's parameter-count limit for
 * no actual caller need.
 *
 * @param invite The outstanding invite
 * @param qrBitmap Rendered QR for the invite link, or null while it is still being generated
 * @param qrAvailable Whether this phone draws QR codes at all; where it does not (the iPhone for
 *   now) no frame is drawn, since an empty one would wait for a code that never comes
 * @param onCopy Copies the code to the clipboard, or null where the phone has no clipboard for it
 * @param onShare Opens the system share sheet with the invite message, or null where there is none
 *   (the code and the link are then read off the screen)
 * @param onRegenerate Issues a fresh code
 */
@Composable
// One card, one parameter per thing it shows or does; code, QR and actions are one visual unit.
@Suppress("LongParameterList", "LongMethod")
fun InviteCodeCard(
    invite: PairingInvite,
    qrBitmap: ImageBitmap?,
    qrAvailable: Boolean,
    onCopy: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onRegenerate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL, vertical = Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            Text(
                text = stringResource(Res.string.pairing_your_code_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Dashed container + copy glyph: the affordance the old bare code had none of. Without
            // a clipboard the code is only read, so nothing in it says it can be tapped.
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .dashedRoundedBorder(
                        color = MaterialTheme.colorScheme.outline,
                        cornerRadius = 16.dp
                    )
                    .then(if (onCopy != null) Modifier.clickable(onClick = onCopy) else Modifier)
                    .padding(horizontal = Spacing.XL, vertical = Spacing.M),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                InviteCodeText(code = invite.code, color = MaterialTheme.colorScheme.primary)
                if (onCopy != null) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(Res.string.pairing_copy_code),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSizes.Standard)
                    )
                }
            }

            val countdown = countdownText(invite.expiresAtMillis)
            Text(
                text = if (onCopy != null) {
                    stringResource(Res.string.pairing_tap_to_copy_and_expiry, countdown)
                } else {
                    countdown
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Inline, not behind a dialog — handing the code over is most of what this
            // screen is for, and a QR the other parent cannot see is not handing anything over.
            if (qrAvailable) {
                Box(
                    modifier = Modifier
                        .size(QR_SIZE)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.inverseOnSurface),
                    contentAlignment = Alignment.Center
                ) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription =
                            stringResource(Res.string.pairing_qr_code_content_description),
                            modifier = Modifier.padding(Spacing.S)
                        )
                    }
                }
            }

            if (onShare != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(IconSizes.Small)
                        )
                        Text(
                            text = stringResource(Res.string.pairing_share_link),
                            modifier = Modifier.padding(start = Spacing.S)
                        )
                    }
                }
            }

            TextButton(onClick = onRegenerate) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text(
                    text = stringResource(Res.string.pairing_new_code),
                    modifier = Modifier.padding(start = Spacing.S)
                )
            }
        }
    }
}

/**
 * Live "valid for …" text, refreshed periodically until the code expires —
 * the refresh loop then stops on its own instead of waking up forever to
 * recompute an already-frozen "expired" string.
 */
@Composable
private fun countdownText(expiresAtMillis: Long): String {
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(expiresAtMillis) {
        while (now < expiresAtMillis) {
            delay(REFRESH_INTERVAL_SECONDS.seconds)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    val remaining = (expiresAtMillis - now).coerceAtLeast(0)
    if (remaining == 0L) return stringResource(Res.string.pairing_code_expired_generate)
    // Whole hours and the minutes left over, as `TimeUnit`'s truncating conversions counted them.
    val left = remaining.milliseconds
    val hours = left.inWholeHours
    val minutes = left.inWholeMinutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        stringResource(Res.string.pairing_expires_in_hours, hours, minutes)
    } else {
        stringResource(Res.string.pairing_expires_in_minutes, minutes)
    }
}

/** How often [countdownText] refreshes while the card is on screen. */
private const val REFRESH_INTERVAL_SECONDS = 30L

private const val MINUTES_PER_HOUR = 60L
