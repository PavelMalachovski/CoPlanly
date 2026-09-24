package com.coparently.app.e2e

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.presentation.widget.TodayWidget
import com.coparently.app.presentation.widget.TodayWidgetRoot
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Draws the Today widget for the UI tour, from the app's own Room rows and names, the way a launcher
 * would: the widget's `RemoteViews` composed at a size, applied to real views and drawn to a bitmap.
 *
 * Not a screenshot of a home screen — placing a widget needs the launcher's own permission dialog —
 * but the same `RemoteViews` a launcher would inflate, so the lines, the parent colours and the
 * reader's font scale are what a parent sees. The picture follows the tour variant's language and,
 * for a dark variant, the *system's* night mode: a widget is drawn by the launcher, so it follows
 * the device's theme, never the app's own theme setting.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
object UiTourWidget {

    /** A three-by-two widget on a phone: the compact layout. */
    val COMPACT = DpSize(250.dp, 120.dp)

    /** The same widget stretched to four rows: the tall layout, which lists the events. */
    val TALL = DpSize(250.dp, 230.dp)

    /** A mid-grey wallpaper behind the widget, so its own background and corners show. */
    private const val WALLPAPER = 0xFF8A8F98.toInt()
    private val MARGIN = 12.dp

    /** The widget as [variant] would show it, at [size]. */
    fun render(variant: UiTourVariant, size: DpSize): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = widgetContext(instrumentation.targetContext, variant)
        val views = runBlocking {
            val state = TodayWidget.contentState(context)
            GlanceRemoteViews().compose(context, size) { TodayWidgetRoot(state, onClick = null) }.remoteViews
        }
        var bitmap: Bitmap? = null
        instrumentation.runOnMainSync { bitmap = draw(views, context, size) }
        return checkNotNull(bitmap) { "The widget drew nothing" }
    }

    /** [variant]'s language, and the night mode a dark variant's launcher would have. */
    private fun widgetContext(context: Context, variant: UiTourVariant): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(variant.language))
        val night = if (variant.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return context.createConfigurationContext(configuration)
    }

    private fun draw(views: RemoteViews, context: Context, size: DpSize): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt()
        val height = (size.height.value * density).roundToInt()
        val margin = (MARGIN.value * density).roundToInt()
        val view = views.apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width + 2 * margin, height + 2 * margin, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(WALLPAPER)
        canvas.translate(margin.toFloat(), margin.toFloat())
        view.draw(canvas)
        return bitmap
    }
}
