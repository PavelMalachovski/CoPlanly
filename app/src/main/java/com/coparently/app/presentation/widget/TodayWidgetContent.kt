package com.coparently.app.presentation.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.coparently.app.presentation.theme.ParentColorChoice
import com.coparently.app.presentation.theme.Spacing
import com.coparently.app.presentation.theme.bodyMediumEmphasized
import com.coparently.app.presentation.theme.labelMediumEmphasized
import androidx.compose.ui.text.TextStyle as TypeRole
import androidx.compose.ui.text.font.FontWeight as TypeWeight
import androidx.glance.color.ColorProvider as dayNight
import com.coparently.app.presentation.theme.Typography as AppTypography

/** The smallest widget: whose day it is, a contact window, the handover, and a count. */
internal val TODAY_WIDGET_COMPACT = DpSize(110.dp, 110.dp)

/** Tall enough to list today's events under the same lines. */
internal val TODAY_WIDGET_TALL = DpSize(110.dp, 190.dp)

private val MARK_WIDTH = 3.dp
private val MARK_HEIGHT = 16.dp

/**
 * The Today widget's layout, for one size.
 *
 * Reads no `Context` and no repository: [TodayWidgetText] has already worded everything, so this
 * only decides how it looks, and a test can hand it lines directly. The anatomy follows Home's
 * today card — the date small and muted, whose day it is promoted and in that parent's colour,
 * the contact windows under it, then the day's events with the owner's mark — because the widget
 * is that card on the home screen.
 *
 * Colours are Material roles from [GlanceTheme], which the widget fills from the app's own
 * schemes, and the parent's chosen tones: [ParentColorChoice.dark] and [ParentColorChoice.light]
 * as text (never the full hue, which fails AA as a foreground), the full hue only for the marks.
 *
 * @param lines What to draw.
 * @param onClick Where a tap goes; the whole widget is one target. Null draws it inert.
 */
@Composable
internal fun TodayWidgetContent(lines: TodayWidgetLines, onClick: Action? = null) {
    val colors = GlanceTheme.colors
    val surface = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(colors.surface)
        .launcherCorner()
        .padding(Spacing.M)
    Column(modifier = onClick?.let { surface.clickable(it) } ?: surface) {
        Text(
            text = lines.title,
            style = role(AppTypography.labelMediumEmphasized, colors.onSurfaceVariant),
            maxLines = 1
        )
        lines.custody?.let { line ->
            Text(
                text = line.text,
                style = role(AppTypography.titleMedium, textColor(line.parent, colors.onSurface)),
                maxLines = 1
            )
        }
        lines.windows.forEach { line ->
            Text(
                text = line.text,
                style = role(AppTypography.bodyMediumEmphasized, textColor(line.parent, colors.onSurface)),
                maxLines = 1
            )
        }
        lines.handover?.let { line ->
            Text(
                text = line.text,
                style = role(AppTypography.bodyMedium, textColor(line.parent, colors.onSurface)),
                maxLines = 1
            )
        }
        if (lines.events.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(Spacing.S))
            lines.events.forEach { EventRow(it) }
        }
        lines.footer?.let { footer ->
            Text(
                text = footer,
                style = role(AppTypography.bodySmall, colors.onSurfaceVariant),
                maxLines = 2
            )
        }
    }
}

/** One event: the owner's mark, the time, the title. */
@Composable
private fun EventRow(line: WidgetEventLine) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = Spacing.XXS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(MARK_WIDTH)
                .height(MARK_HEIGHT)
                .background(markColor(line.parent, colors.outline))
        ) {}
        Spacer(modifier = GlanceModifier.width(Spacing.S))
        Text(
            text = line.time,
            style = role(AppTypography.bodySmall, colors.onSurfaceVariant),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.width(Spacing.S))
        Text(
            text = line.title,
            modifier = GlanceModifier.defaultWeight(),
            style = role(AppTypography.bodyMediumEmphasized, colors.onSurface),
            maxLines = 1
        )
    }
}

/** A parent's text-grade tone for each theme, or the neutral text colour. */
private fun textColor(parent: ParentColorChoice?, neutral: ColorProvider): ColorProvider =
    parent?.let { dayNight(day = it.dark, night = it.light) } ?: neutral

/** A parent's full hue for each theme, for a mark, or the neutral outline. */
private fun markColor(parent: ParentColorChoice?, neutral: ColorProvider): ColorProvider =
    parent?.let { dayNight(day = it.fill, night = it.darkFill) } ?: neutral

/**
 * The launcher's own widget radius on Android 12 and later, so this card's corner matches every
 * other widget on the same home screen rather than a radius of ours. Glance draws no corner below
 * Android 12 whatever it is given, so there is nothing to choose there.
 */
private fun GlanceModifier.launcherCorner(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        this
    }

/**
 * One of the app's type roles as a Glance style, so the widget reads on the scale of the today
 * card it mirrors rather than on sizes of its own. A widget draws the system font in three
 * weights, so the role's size carries over exactly and its weight to the nearest lighter of
 * Normal, Medium and Bold: an emphasised role's SemiBold becomes Medium, not Bold.
 */
private fun role(style: TypeRole, color: ColorProvider): TextStyle {
    val weight = style.fontWeight ?: TypeWeight.Normal
    return TextStyle(
        color = color,
        fontSize = style.fontSize,
        fontWeight = when {
            weight >= TypeWeight.Bold -> FontWeight.Bold
            weight >= TypeWeight.Medium -> FontWeight.Medium
            else -> FontWeight.Normal
        }
    )
}
