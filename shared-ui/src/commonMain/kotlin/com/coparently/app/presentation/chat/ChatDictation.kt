package com.coparently.app.presentation.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.coparently.app.domain.dictation.DictationFailure
import com.coparently.app.presentation.platform.LocalOpenUrl
import com.coparently.app.presentation.theme.IconSizes
import com.coparently.app.presentation.theme.Motion
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.rememberReducedMotion
import com.coparently.app.resources.Res
import com.coparently.app.resources.voice_error_busy
import com.coparently.app.resources.voice_error_language
import com.coparently.app.resources.voice_error_nothing_heard
import com.coparently.app.resources.voice_error_other
import com.coparently.app.resources.voice_error_unavailable
import com.coparently.app.resources.voice_open_settings
import com.coparently.app.resources.voice_permission_rationale
import com.coparently.app.resources.voice_start
import com.coparently.app.resources.voice_stop
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The composer's microphone: voice dictation, on the phone only (see `OnDeviceSpeechDictation`).
 *
 * It sits *inside* the pill field as its trailing icon rather than as a fourth control in the
 * row: the attach button, the field and the send button stay where they were, the send button
 * keeps its fixed place (it never swaps with the microphone, which would move the one control a
 * parent aims for as the field fills), and dictation can be started with text already typed —
 * it appends. A standard 48 dp icon button, so the target meets the minimum.
 *
 * While listening the glyph turns into a stop square on the primary container, and its content
 * description says "Stop voice typing"; the glyph breathes gently unless the person has switched
 * animations off ([rememberReducedMotion]), when it stands still. Tapping it stops.
 *
 * Stateless: the caller draws it only when [DictationUiState.available] is true.
 *
 * @param listening A session is running
 * @param onClick Starts (behind the microphone permission) or stops dictation
 */
@Composable
fun DictationMicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = if (listening) {
        IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = onClick, modifier = modifier, colors = colors) {
        if (listening) {
            Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = stringResource(Res.string.voice_stop),
                modifier = Modifier
                    .size(IconSizes.Standard)
                    .alpha(listeningPulse())
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(Res.string.voice_start),
                modifier = Modifier.size(IconSizes.Standard)
            )
        }
    }
}

/** A slow breathing alpha while listening; 1 (still) when animations are off. */
@Composable
private fun listeningPulse(): Float {
    if (rememberReducedMotion()) return 1f
    val transition = rememberInfiniteTransition(label = "dictationPulse")
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.LONG_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dictationPulseAlpha"
    )
    return alpha.value
}

/**
 * One short line under the composer saying why dictation stopped without text. For a refused
 * microphone it says what the microphone is for and offers the phone's app settings, where a
 * permanently refused permission can still be allowed — where the phone can open them
 * (`OpenUrl.available`; the iPhone has no dictation to fail yet).
 *
 * @param failure Why the last session ended
 */
@Composable
fun DictationFailureNotice(failure: DictationFailure, modifier: Modifier = Modifier) {
    val openUrl = LocalOpenUrl.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.L, vertical = Spacing.XS)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.MicOff,
            contentDescription = null,
            modifier = Modifier.size(IconSizes.Small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(failure.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (failure == DictationFailure.PERMISSION && openUrl.available) {
            TextButton(onClick = { openUrl.openAppSettings() }) { Text(stringResource(Res.string.voice_open_settings)) }
        }
    }
}

/** The sentence for each failure (`voice_strings.xml`). */
internal fun DictationFailure.messageRes(): StringResource = when (this) {
    DictationFailure.NOTHING_HEARD -> Res.string.voice_error_nothing_heard
    DictationFailure.LANGUAGE_UNAVAILABLE -> Res.string.voice_error_language
    DictationFailure.BUSY -> Res.string.voice_error_busy
    DictationFailure.PERMISSION -> Res.string.voice_permission_rationale
    DictationFailure.UNAVAILABLE -> Res.string.voice_error_unavailable
    DictationFailure.OTHER -> Res.string.voice_error_other
}

/** How far the listening glyph fades at the bottom of its breath. */
private const val PULSE_MIN_ALPHA = 0.45f
