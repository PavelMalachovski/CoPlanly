package com.coparently.app.presentation.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.material3.ColorProviders
import com.coparently.app.data.repository.CustodyModelRepository
import com.coparently.app.domain.repository.EventRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.presentation.MainActivity
import com.coparently.app.presentation.common.STACK_CONTROLS_FONT_SCALE
import com.coparently.app.presentation.theme.DarkColorScheme
import com.coparently.app.presentation.theme.LightColorScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The "Today" home-screen widget (October 2026 design audit, week 6): whose day it is, the day's
 * contact windows, the next handover and today's events — Home's today card and handover hero,
 * readable without opening the app.
 *
 * It reads this device's Room rows and nothing else. The day is computed by [TodayWidgetModel]
 * with Home's own functions, worded by [TodayWidgetText] with Home's own strings, and drawn by
 * [TodayWidgetContent]; the co-parent's name comes from [TodayWidgetNames], the snapshot the app
 * keeps while it is open, because that name lives in Firestore alone.
 *
 * It is redrawn by [TodayWidgetRefresher] — when events or the custody schedule change in Room,
 * when the parents' names change, just after midnight — and hourly by the system as a backstop
 * (`updatePeriodMillis` in `res/xml/today_widget_info.xml`). A tap opens the app.
 *
 * Home screen only (`widgetCategory="home_screen"`): the family's day is not for a lock screen.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(TODAY_WIDGET_COMPACT, TODAY_WIDGET_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loadedFor = getAppWidgetState<Preferences>(context, id)[REFRESH_STAMP]
        val initial = contentState(context)
        TodayWidgetMidnight.ensureScheduled(context)
        provideContent {
            // A redraw asked for while this session is still open recomposes rather than calling
            // provideGlance again, so a new stamp is what makes the lines load afresh.
            val stamp = currentState(REFRESH_STAMP)
            var state by remember { mutableStateOf(initial) }
            LaunchedEffect(stamp) {
                if (stamp != loadedFor) state = contentState(context)
            }
            TodayWidgetRoot(state, onClick = actionStartActivity<MainActivity>())
        }
    }

    companion object {

        /** Written by [refreshAll]; a changed value makes an open session reload its lines. */
        private val REFRESH_STAMP = longPreferencesKey("today_widget_refresh")

        /**
         * Today, read from this device's Room and worded in [context]'s language — what the widget
         * draws, and what the UI tour renders to review it.
         */
        internal suspend fun contentState(context: Context): TodayWidgetContentState {
            val source = entryPoint(context)
            val today = LocalDate.now()
            val userId = source.userRepository().getCurrentUserId()?.takeIf { it.isNotBlank() }
            val model = if (userId == null) {
                TodayWidgetModel.signedOut(today)
            } else {
                val (custody, swaps) = source.custodyModelRepository().storedCustody()
                TodayWidgetModel.of(
                    today = today,
                    userId = userId,
                    events = source.eventRepository()
                        .getEventsByDateRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                        .first(),
                    model = custody,
                    overrides = swaps
                )
            }
            val parents = userId?.let { source.widgetNames().recall(it) }
            return TodayWidgetText.contentState(context, model, parents)
        }

        /** Redraws every placed Today widget. Does nothing when none is placed. */
        suspend fun refreshAll(context: Context) {
            val widget = TodayWidget()
            GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[REFRESH_STAMP] = System.currentTimeMillis() }
                widget.update(context, id)
            }
        }

        private fun entryPoint(context: Context): TodayWidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, TodayWidgetEntryPoint::class.java)
    }
}

/** What the widget reads, from the app's singleton graph: it is not a Hilt-injected class. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    /** Today's events, from Room. */
    fun eventRepository(): EventRepository

    /** The stored custody pattern and swaps, from Room. */
    fun custodyModelRepository(): CustodyModelRepository

    /** Who is signed in. */
    fun userRepository(): UserRepository

    /** The parents' names as the app last loaded them. */
    fun widgetNames(): TodayWidgetNames
}

/** Declares [TodayWidget] to the system; see the `<receiver>` in the manifest. */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    /** The last widget is gone: nothing needs redrawing at midnight any more. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        TodayWidgetMidnight.cancel(context)
    }
}

/** The app's own light and dark schemes, not the wallpaper's: parent colours sit on them. */
private val WIDGET_COLORS = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)

/**
 * The widget in the app's colours, at the layout its size calls for: [TodayWidgetContentState.tall]
 * from [TODAY_WIDGET_TALL]'s height, [TodayWidgetContentState.compact] below it — cut to three
 * lines ([compactAtLargeText]) from [STACK_CONTROLS_FONT_SCALE], where a fourth was clipped.
 *
 * @param onClick Where a tap goes; null for a picture of the widget rather than the widget.
 */
/**
 * The font scale the widget is drawn at. Glance has no `LocalConfiguration`: every update composes
 * the widget afresh from its context, so there is no recomposition for a stale read to miss.
 */
private fun fontScaleOf(context: Context): Float = context.resources.configuration.fontScale

@Composable
internal fun TodayWidgetRoot(state: TodayWidgetContentState, onClick: Action?) {
    GlanceTheme(colors = WIDGET_COLORS) {
        val tall = LocalSize.current.height >= TODAY_WIDGET_TALL.height
        // A widget cannot measure its text; the font scale is the one thing it knows about it.
        val largeText = fontScaleOf(LocalContext.current) >= STACK_CONTROLS_FONT_SCALE
        val lines = when {
            tall -> state.tall
            largeText -> state.compact.compactAtLargeText()
            else -> state.compact
        }
        TodayWidgetContent(lines = lines, onClick = onClick, roomy = tall)
    }
}
