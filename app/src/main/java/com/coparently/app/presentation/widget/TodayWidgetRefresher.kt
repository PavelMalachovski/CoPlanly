package com.coparently.app.presentation.widget

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.room.InvalidationTracker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coparently.app.data.local.CoPlanlyDatabase
import com.coparently.app.presentation.common.ParentsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when the Today widget redraws. Every redraw goes through [TodayWidget.refreshAll],
 * which does nothing when no widget is placed, so none of this costs a device without one more
 * than a Room observer.
 *
 * Three triggers, each for a fact the widget shows:
 * - **Room** ([start]): an event or the custody schedule changed — a parent's own edit, the
 *   co-parent's arriving by sync, an accepted swap. Observed through Room's invalidation tracker
 *   rather than by re-running the day's query on every write, and debounced, because a sync
 *   writes rows in bursts.
 * - **The parents' names** ([followParents]): while the app is on screen, the names and colours
 *   [ParentsSource] loads are remembered for the widget by [TodayWidgetNames], and a change
 *   redraws it. Only while on screen, because that source attaches Firestore listeners and must
 *   be let go in the background, as every screen already does.
 * - **Midnight** ([TodayWidgetMidnight]): "today" moves on with nothing written anywhere.
 */
@Singleton
class TodayWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CoPlanlyDatabase,
    private val names: TodayWidgetNames,
    private val parentsSource: ParentsSource
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    /** Starts redrawing on Room changes; from `CoPlanlyApplication.onCreate`, once per process. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            roomChanges()
                .debounce(REDRAW_DEBOUNCE_MS)
                .collect { redraw() }
        }
    }

    /**
     * Remembers the parents' names for the widget while [owner] is started, redrawing it when they
     * change; from `MainActivity`, whose lifecycle is the app being on screen.
     */
    fun followParents(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentsSource.observe().collect { parents ->
                    val changed = withContext(Dispatchers.IO) { names.remember(parents) }
                    if (changed) scope.launch { redraw() }
                }
            }
        }
    }

    private suspend fun redraw() {
        try {
            TodayWidget.refreshAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // The widget's state file could not be written. A failed redraw leaves the last
            // drawing up until the next trigger; it must never take the process down with it.
            Log.w(TAG, "Could not redraw the Today widget", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not redraw the Today widget", e)
        }
    }

    /** One emission per write to a table the widget reads. */
    private fun roomChanges(): Flow<Unit> = callbackFlow {
        val observer = object : InvalidationTracker.Observer(WATCHED_TABLES) {
            override fun onInvalidated(tables: Set<String>) {
                trySend(Unit)
            }
        }
        database.invalidationTracker.addObserver(observer)
        awaitClose { database.invalidationTracker.removeObserver(observer) }
    }.catch { e ->
        // The database could not be opened (SEC-2's fallback path logs its own reason); the widget
        // still redraws at midnight and hourly.
        Log.w(TAG, "The Today widget stopped following Room", e)
    }

    private companion object {
        const val TAG = "TodayWidgetRefresher"

        /** Long enough to fold a sync's burst of writes into one redraw. */
        const val REDRAW_DEBOUNCE_MS = 2_000L

        /** The tables the widget's day is computed from: events, and the pattern with its swaps. */
        val WATCHED_TABLES = arrayOf("events", "custody_models")
    }
}

/**
 * Redraws the Today widget once a day, a minute after local midnight, when "today" has moved on
 * and nothing in Room says so.
 *
 * Periodic rather than a one-shot re-armed by each draw: a draw that re-armed the work it was
 * called from would cancel itself. A periodic run drifts under Doze, and a change of time zone
 * does not realign it; the widget's hourly `updatePeriodMillis` bounds both.
 */
object TodayWidgetMidnight {

    private const val WORK_NAME = "today_widget_midnight"

    /** Keeps the daily redraw scheduled; cheap to repeat, which every draw does. */
    fun ensureScheduled(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<TodayWidgetMidnightWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilAfterMidnight(now).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stops the daily redraw; when the last widget is removed. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * From [now] to the next one minute past local midnight — today's, when [now] is in that
     * first minute. Through `atStartOfDay(zone)`, so a day that starts at 01:00 after a clock
     * change is measured from when it really starts.
     */
    internal fun delayUntilAfterMidnight(now: ZonedDateTime): Duration {
        fun oneMinutePast(day: LocalDate): ZonedDateTime = day.atStartOfDay(now.zone).plusMinutes(1)
        val todays = oneMinutePast(now.toLocalDate())
        val next = if (now.isBefore(todays)) todays else oneMinutePast(now.toLocalDate().plusDays(1))
        return Duration.between(now, next)
    }
}

/** Runs [TodayWidget.refreshAll] for [TodayWidgetMidnight]. */
class TodayWidgetMidnightWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TodayWidget.refreshAll(applicationContext)
        return Result.success()
    }
}
