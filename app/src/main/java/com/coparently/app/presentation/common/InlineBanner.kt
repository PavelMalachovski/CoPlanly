package com.coparently.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A banner inside a screen's content (docs/AUDIT-2026-10-design.md D-15): the app telling the
 * reader something about what they are looking at, or asking them something.
 *
 * One anatomy for what the expense split's two banners, the vault's two notices, the journal's
 * privacy line and the events list's waiting strip each drew their own way — a tonal container
 * of the tone's colour, an optional icon, an optional title, the text, and the actions in a
 * [FlowRow], so three buttons wrap onto a second line at large text instead of squeezing their
 * labels. Nothing here truncates.
 *
 * The calendar's banners over the grid are a separate, deliberately compact anatomy
 * (`CalendarBanners.kt`); they are one line tall by design.
 *
 * @param text What the banner says
 * @param modifier Modifier for the banner
 * @param title An optional first line, for a banner that asks something
 * @param icon An optional leading icon
 * @param tone [BannerTone.INFO] or [BannerTone.ATTENTION]
 * @param actions Buttons, if the banner asks for an answer — `TextButton`s, and at most one
 *   filled `Button` for the answer it expects
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList") // one banner anatomy: each parameter is a part it may show
fun InlineBanner(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    tone: BannerTone = BannerTone.INFO,
    actions: (@Composable () -> Unit)? = null
) {
    val colours = bannerColours(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colours.container
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colours.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = colours.title)
                }
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colours.body)
                if (actions != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                }
            }
        }
    }
}

/** The four colours a banner of one tone is drawn in. */
private data class BannerColours(val container: Color, val accent: Color, val title: Color, val body: Color)

@Composable
private fun bannerColours(tone: BannerTone): BannerColours {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        BannerTone.INFO -> BannerColours(
            container = scheme.surfaceContainer,
            accent = scheme.primary,
            title = scheme.onSurface,
            body = scheme.onSurfaceVariant
        )
        BannerTone.ATTENTION -> BannerColours(
            container = scheme.primaryContainer,
            accent = scheme.primary,
            title = scheme.onPrimaryContainer,
            body = scheme.onPrimaryContainer
        )
    }
}
