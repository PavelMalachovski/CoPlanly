package com.coparently.app.data.notification

import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.data.support.SupportRead
import com.coparently.app.data.support.SupportReminderScheduler
import com.coparently.app.data.support.SupportReminders
import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.money.DecimalText
import com.coparently.app.domain.payment.PaymentQr
import com.coparently.app.domain.payments.Payment
import com.coparently.app.domain.payments.PaymentId
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.support.SupportTerms
import com.coparently.app.domain.time.IsoText
import com.coparently.app.pushwording.ReminderWording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * [SupportReminderScheduler] on the iPhone (iPhone phase 4 part C, design §7.5): the payer's child
 * support reminder as a local notification, planned by [SupportReminderPlanner] and kept by
 * [IosLocalReminders]. In place of the stand-in `NoSupportReminderJob`, so the child support screen
 * now offers the reminder here ([available]).
 *
 * Each call asks the book to plan again: turning the reminder on or off, an agreement accepted or
 * a month recorded as paid on this phone ([refresh]), and the sync and the app's start, which also
 * read what the other phone recorded.
 */
class IosSupportReminders(private val book: IosLocalReminders) : SupportReminderScheduler {

    /** The iPhone reminds, so the screen offers the reminder. */
    override val available: Boolean = true

    override fun schedule() = book.requestRearm()

    override fun cancel() = book.requestRearm()

    override fun refresh() = book.requestRearm()
}

/**
 * Plans the payer's child support reminders on the iPhone (design §7.5), Android's
 * `SupportReminderWorker` decided ahead of time, since a local notification runs no code when it
 * is shown. For each family where this phone's reminder is on (`SupportReminders`), at most one:
 * - **the month**: this month unless it was already reminded about, is paid (confirmed or not), or
 *   is before the agreement's first month — then the next month, under the same three rules
 *   (`SupportReminderRule`'s);
 * - **the instant**: [REMIND_HOUR] o'clock on the day the parent's chosen number of days (0, 1, 3
 *   or 7) before the due day, in the phone's zone; when that has passed (this month, not yet reminded), as soon as the
 *   quiet hours allow ([ReminderPlan.isQuietAt]: never 21:00–08:00) — never inside them;
 * - **only for the payer**, with an agreement in force, worded by [ReminderWording.support] when it
 *   is armed (the due day, the amount and the payee's first name, or "Your co-parent").
 *
 * A month is counted as reminded once its armed instant has passed (`SupportReminders.armed`), the
 * moment the system showed it; once a month, as on Android. A family whose server read fails keeps
 * the reminder it has armed (`SupportPlan.keptPrefixes`): a reminder that is not sure changes
 * nothing. A tap opens Family → Child support on the family, as Android's does.
 *
 * @param reminders This phone's choices, and what it armed.
 * @param uid The signed-in account; nothing is planned while nobody is signed in.
 * @param agreement A family's child support as the server holds it now; null when it could not be read.
 * @param payment A payment as the server holds it now; a failure when it could not be read.
 * @param payeeName The first name of the parent paid, from their uid; null or blank for "Your co-parent".
 * @param wording The reminders' wording, in the language the app speaks now.
 * @param zone The phone's zone.
 */
@Suppress("LongParameterList") // what a reminder reads, as the worker's
class SupportReminderPlanner(
    private val reminders: SupportReminders,
    private val uid: () -> String?,
    private val agreement: suspend (String) -> SupportRead?,
    private val payment: suspend (String) -> Result<Payment?>,
    private val payeeName: suspend (String) -> String?,
    private val wording: () -> ReminderWording,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    /** The reminders as of [nowMillis], and the families to keep as they are. */
    suspend fun plan(nowMillis: Long): SupportPlan {
        val account = uid()?.takeIf { it.isNotBlank() } ?: return SupportPlan(emptyList(), emptyList())
        val families = reminders.families(account)
        // A reminder turned off: what it armed is removed, so forget it.
        reminders.armedFamilies(account).filter { it !in families }.forEach { reminders.disarm(account, it) }
        val zone = zone()
        val armed = mutableListOf<LocalReminder>()
        val kept = mutableListOf<String>()
        families.forEach { family ->
            countShown(account, family, nowMillis)
            when (val decision = decide(account, family, nowMillis, zone)) {
                is Decision.Arm -> armed += decision.reminder
                Decision.Keep -> kept += prefixOf(family)
                Decision.None -> reminders.disarm(account, family)
            }
        }
        return SupportPlan(armed, kept) { reminder -> recordArmed(account, reminder) }
    }

    /** Records [reminder] as armed, once the notification center has it: its family and month from its id. */
    private fun recordArmed(account: String, reminder: LocalReminder) {
        val family = reminder.data[PushPayload.FAMILY_ID] ?: return
        val month = runCatching { IsoText.parseYearMonth(reminder.id.removePrefix(prefixOf(family))) }.getOrNull()
            ?: return
        reminders.setArmed(account, family, month, reminder.atMillis)
    }

    /** A month whose armed notification's instant has passed was shown: counted, once a month. */
    private fun countShown(account: String, family: String, nowMillis: Long) {
        val shown = reminders.armed(account, family)?.takeIf { it.atMillis <= nowMillis } ?: return
        reminders.reminded(account, family, shown.month)
        reminders.disarm(account, family)
    }

    @Suppress("ReturnCount") // one early answer per reason there is no reminder
    private suspend fun decide(account: String, family: String, nowMillis: Long, zone: TimeZone): Decision {
        val days = reminders.daysBefore(account, family) ?: return Decision.None
        val stored = agreement(family) ?: return Decision.Keep
        val known = (stored as? SupportRead.Known)?.agreement ?: return Decision.None
        val terms = known.agreement?.takeIf { it.payerUid == account } ?: return Decision.None
        val thisMonth = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date.yearMonth
        val lastReminded = reminders.lastReminded(account, family)
        // This month, else the next one; each read from the server, the next one only if needed.
        val months = listOf(thisMonth, thisMonth.plusMonth()).filter { it >= terms.fromMonth && it != lastReminded }

        // A month is taken when unpaid and its reminder still fits inside it; a failed read stops here.
        fun decided(month: YearMonth, paid: Result<Payment?>): Boolean =
            paid.isFailure || (paid.getOrNull() == null && instantOf(terms, month, days, nowMillis, zone) != null)
        val reads = mutableListOf<Pair<YearMonth, Result<Payment?>>>()
        for (candidate in months) {
            val paid = payment(PaymentId.of(family, PaymentKind.SUPPORT, candidate, terms.currency))
            reads += candidate to paid
            if (decided(candidate, paid)) break
        }
        val (month, open) = reads.lastOrNull()?.takeIf { (candidate, paid) -> decided(candidate, paid) }
            ?: return Decision.None
        if (open.isFailure) return Decision.Keep
        val payee = terms.payeeIn(known.participants)
        return Decision.Arm(reminderFor(account, family, terms, month, days, nowMillis, zone, payee))
    }

    /** [family]'s reminder for [month] under [terms], [days] before the due day, worded now. */
    @Suppress("LongParameterList") // the reminder's facts, as decided
    private suspend fun reminderFor(
        account: String,
        family: String,
        terms: SupportTerms,
        month: YearMonth,
        days: Int,
        nowMillis: Long,
        zone: TimeZone,
        payee: String?,
    ): LocalReminder {
        val atMillis = checkNotNull(instantOf(terms, month, days, nowMillis, zone)) { "No time left for $month" }
        val text = wording().support(
            dueMillis = terms.dueDate(month).atStartOfDayIn(zone).toEpochMilliseconds(),
            amount = DecimalText.plain(terms.amountMinor, PaymentQr.MONEY_SCALE),
            currency = terms.currency,
            payeeName = payee?.let { nameOf(it) },
        )
        return LocalReminder(idOf(family, month), atMillis, text.title, text.body, tapData(account, family))
    }

    /**
     * When the reminder for [month] is shown: [REMIND_HOUR] o'clock on the day [days] before its due
     * day, or — once that has passed — a minute from now, moved out of the quiet hours to the next
     * [REMIND_HOUR]. Null when that moved time falls after [month]: on a month's last evening it
     * would come on the 1st, about a due day a month gone, so it is dropped and the next month
     * is planned instead.
     */
    private fun instantOf(terms: SupportTerms, month: YearMonth, days: Int, nowMillis: Long, zone: TimeZone): Long? {
        val remindDay = terms.dueDate(month).minus(days, DateTimeUnit.DAY)
        val planned = LocalDateTime(remindDay, REMIND_TIME).toInstant(zone).toEpochMilliseconds()
        if (planned > nowMillis) return planned
        val soon = nowMillis + LATE_DELAY_MILLIS
        val late = if (ReminderPlan.isQuietAt(soon, zone)) {
            val local = Instant.fromEpochMilliseconds(soon).toLocalDateTime(zone)
            val day = if (local.time < REMIND_TIME) local.date else local.date.plus(1, DateTimeUnit.DAY)
            LocalDateTime(day, REMIND_TIME).toInstant(zone).toEpochMilliseconds()
        } else {
            soon
        }
        return late.takeIf { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date.yearMonth <= month }
    }

    /** The payee's first name, or null — "Your co-parent" — when it cannot be read in time. */
    private suspend fun nameOf(uid: String): String? = try {
        withTimeoutOrNull(NAME_WAIT_MILLIS) { payeeName(uid) }?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception
    ) {
        logger.w(TAG, "The payee's name could not be read", e)
        null
    }

    /** What a tap opens: Family → Child support, on the family. */
    private fun tapData(account: String, family: String): Map<String, String> = mapOf(
        PushPayload.TARGET_USER_ID to account,
        PushDestination.EXTRA to PushDestination.SUPPORT.key,
        PushPayload.FAMILY_ID to family,
    )

    private sealed interface Decision {
        data class Arm(val reminder: LocalReminder) : Decision
        data object Keep : Decision
        data object None : Decision
    }

    /** The ids' shape and the time of day. */
    companion object {
        /** The hour a reminder is shown on its day: after the quiet hours, as Android's worker runs. */
        const val REMIND_HOUR: Int = 9

        private val REMIND_TIME = LocalTime(REMIND_HOUR, 0)

        /** How soon a reminder whose time has passed is shown. */
        private const val LATE_DELAY_MILLIS = 60_000L

        /** As Android's worker waits for the payee's name. */
        private const val NAME_WAIT_MILLIS = 5_000L

        private const val TAG = "SupportReminderPlanner"

        /** The reminder's id for [familyId]'s [month]: `support-<familyId>-<yyyy-MM>`. */
        fun idOf(familyId: String, month: YearMonth): String =
            prefixOf(familyId) + IsoText.yearMonth(month)

        /** Every id of [familyId]'s reminders starts so. */
        fun prefixOf(familyId: String): String = "${IosLocalReminders.SUPPORT_PREFIX}$familyId-"
    }
}
