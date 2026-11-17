package com.coparently.app.data.notification

import com.coparently.app.data.platform.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * One local notification the iPhone means to have pending: its [id] (`event-<eventId>` or
 * `support-<familyId>-<yyyy-MM>`), the instant it is shown ([atMillis], epoch millis), its two lines,
 * and the [data] a tap hands the app.
 */
data class LocalReminder(
    val id: String,
    val atMillis: Long,
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

/**
 * What the child support reminders decided: the [reminders] to arm, and the id prefixes
 * ([keptPrefixes], `support-<familyId>-`) of the families whose server read failed, whose pending
 * reminder is kept as it was armed — a reminder that is not sure changes nothing.
 */
data class SupportPlan(val reminders: List<LocalReminder>, val keptPrefixes: List<String>)

/**
 * The iPhone's local reminders, event and child support together (iPhone phase 4 part C, design
 * §7.4–7.5): the one place that decides which are pending, because iOS keeps at most 64 pending
 * notifications for an app, and this one keeps at most [limit] (60, four slots free).
 *
 * A local notification runs no code when it is shown, so what Android checks when its alarm fires
 * or its worker runs is decided here, when the phone arms, and undone by removing. Each [rearm]
 * plans everything again from what the phone holds now — the events in Room, this phone's support
 * choices and the server's agreement and payments — and leaves pending exactly the nearest
 * reminders: a child support reminder first (one per family, at most one month ahead), then the
 * events by time. Whatever it no longer plans is removed, so an event deleted on the other phone
 * loses its reminder at the next sync. [cancelAll] removes every one, at sign-out, an account
 * switch and deletion (CLAUDE.md item 9), and [onCancelAll] forgets what the support planner
 * remembered having armed.
 *
 * The schedulers ([IosEventReminders], [IosSupportReminders]) only ask, from any thread and as
 * often as they like ([requestRearm], [requestCancelAll]); the asks are run one batch at a time in
 * [scope], in order, a run of re-arms counted as one. A failed plan never throws out of here: an
 * event plan that failed keeps the event reminders pending as they are, a support plan that failed
 * keeps the support ones.
 *
 * @param events The event reminders due after the instant given (epoch millis).
 * @param support The child support reminders as of the instant given.
 * @param onCancelAll What else [cancelAll] clears: the support planner's record of what it armed.
 * @param now The clock, in epoch millis.
 */
@Suppress("LongParameterList") // the center, the two plans, the clear, the clock, the scope and the cap
class IosLocalReminders(
    private val center: LocalNotificationCenter,
    private val events: suspend (Long) -> List<LocalReminder>,
    private val support: suspend (Long) -> SupportPlan,
    private val onCancelAll: () -> Unit,
    scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val limit: Int = LIMIT,
) {

    private val mutex = Mutex()

    /** The asks, in order: true for [cancelAll], false for [rearm]. */
    private val asks = Channel<Boolean>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (first in asks) {
                val batch = mutableListOf(first)
                while (true) batch += asks.tryReceive().getOrNull() ?: break
                // A cancel anywhere in the batch runs; a re-arm after the last cancel runs once.
                if (true in batch) cancelAll()
                if (!batch.last()) rearm()
            }
        }
    }

    /** Asks for a [rearm]; returns at once. */
    fun requestRearm() {
        asks.trySend(false)
    }

    /** Asks for a [cancelAll]; returns at once, and every ask made before it runs first. */
    fun requestCancelAll() {
        asks.trySend(true)
    }

    /** Plans every reminder again and leaves pending exactly the nearest [limit]. */
    suspend fun rearm(): Unit = mutex.withLock {
        try {
            val at = now()
            val supportPlan = attempt("child support") { support(at) }
            val eventPlan = attempt("event") { events(at) }
            arm(at, center.pending().filter(::isOurs), supportPlan, eventPlan)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.w(TAG, "The reminders could not be armed", e)
        }
    }

    /** Removes every pending event and child support reminder, and forgets what support armed. */
    suspend fun cancelAll(): Unit = mutex.withLock {
        try {
            onCancelAll()
            center.remove(center.pending().filter(::isOurs))
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception
        ) {
            logger.w(TAG, "The reminders could not be cancelled", e)
        }
    }

    private fun arm(at: Long, pending: List<String>, supportPlan: SupportPlan?, eventPlan: List<LocalReminder>?) {
        val keptSupport = pending.filter { id ->
            id.startsWith(SUPPORT_PREFIX) &&
                (supportPlan == null || supportPlan.keptPrefixes.any { id.startsWith(it) })
        }
        val keptEvents = if (eventPlan == null) pending.filter { it.startsWith(EVENT_PREFIX) } else emptyList()
        val slots = (limit - keptSupport.size - keptEvents.size).coerceAtLeast(0)
        val wantedSupport = supportPlan?.reminders.orEmpty().filter { it.atMillis > at }
            .sortedBy { it.atMillis }
            .take(slots)
        val wantedEvents = eventPlan.orEmpty().filter { it.atMillis > at }
            .sortedBy { it.atMillis }
            .take(slots - wantedSupport.size)
        val wanted = wantedSupport + wantedEvents
        val keep = (keptSupport + keptEvents + wanted.map { it.id }).toSet()
        center.remove(pending.filter { it !in keep })
        wanted.forEach { center.add(it.id, it.atMillis, it.title, it.body, it.data) }
    }

    /** [block]'s plan, or null — logged — when it failed. */
    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        logger.w(TAG, "The $what reminders could not be planned; the armed ones are kept", e)
        null
    }

    private fun isOurs(id: String): Boolean = id.startsWith(EVENT_PREFIX) || id.startsWith(SUPPORT_PREFIX)

    /** The ids' shapes and the cap. */
    companion object {
        /** Every event reminder's id starts so: `event-<eventId>`. */
        const val EVENT_PREFIX: String = "event-"

        /** Every child support reminder's id starts so: `support-<familyId>-<yyyy-MM>`. */
        const val SUPPORT_PREFIX: String = "support-"

        /** The most reminders pending at once: iOS keeps 64 for an app, and four stay free. */
        const val LIMIT: Int = 60

        private const val TAG = "IosLocalReminders"
    }
}
