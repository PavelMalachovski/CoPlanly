package com.coparently.app.data.support

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coparently.app.R
import com.coparently.app.data.money.MinorUnits
import com.coparently.app.data.notification.notificationsAllowed
import com.coparently.app.data.payment.PaymentStore
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.PushChannel
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushNotifier
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.changerequests.ReminderPlan
import com.coparently.app.domain.payments.PaymentId
import com.coparently.app.domain.payments.PaymentKind
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.domain.support.SupportReminderRule
import com.coparently.app.domain.support.SupportTerms
import com.coparently.app.presentation.expenses.currencyFormat
import com.coparently.app.utils.inAppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.yearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * Reminds the payer of this month's child support (25 October 2026): every few hours, for each
 * family where this phone's reminder is on, it reads the agreement and this month's payment from
 * the server and posts one notice a month on the money channel when [SupportReminderRule] says so.
 *
 * Nothing in the quiet hours ([ReminderPlan.isQuietAt]) — the next run comes after them — and
 * nothing when either read fails: a reminder that is not sure keeps quiet. With no reminder left
 * on, the job cancels itself.
 */
@Suppress("LongParameterList") // WorkManager's two, and one per thing a reminder reads
class SupportReminderWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val auth: FirebaseAuthService,
    private val reminders: SupportReminders,
    private val agreements: SupportAgreementStore,
    private val payments: PaymentStore,
    private val pairingRepository: PairingRepository,
    private val scheduler: SupportReminderScheduler
) : CoroutineWorker(context, workerParams) {

    @Suppress("ReturnCount") // nothing to do signed out, with no reminder, or in the quiet hours
    override suspend fun doWork(): Result {
        val uid = auth.getCurrentUser()?.uid ?: return Result.success()
        val families = reminders.families(uid)
        if (families.isEmpty()) {
            scheduler.cancel()
            return Result.success()
        }
        val zone = ZoneId.systemDefault()
        if (ReminderPlan.isQuietAt(System.currentTimeMillis(), zone.toKotlinTimeZone())) return Result.success()
        val today = java.time.LocalDate.now(zone).toKotlinLocalDate()
        families.forEach { family ->
            try {
                remindIfDue(uid, family, today)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.w(TAG, "A child support reminder could not be checked", e)
            }
        }
        return Result.success()
    }

    @Suppress("ReturnCount") // one early answer per reason a reminder is not due
    private suspend fun remindIfDue(uid: String, familyId: String, today: LocalDate) {
        val days = reminders.daysBefore(uid, familyId) ?: return
        val read = agreements.readFromServer(familyId) as? SupportRead.Known ?: return
        val terms = read.agreement.agreement?.takeIf { it.payerUid == uid } ?: return
        val month = today.yearMonth
        val payment = payments.readFromServer(PaymentId.of(familyId, PaymentKind.SUPPORT, month, terms.currency))
            .getOrElse { return }
        val lastReminded = reminders.lastReminded(uid, familyId)
        if (!SupportReminderRule.shouldRemind(terms, today, days, payment != null, lastReminded)) return
        val payee = terms.payeeIn(read.agreement.participants) ?: return
        // The month is spent only on a reminder that was shown (notification audit, 9 November
        // 2026): with notifications off it used to be recorded anyway, and the month's reminder was
        // lost even once they were allowed again.
        if (post(familyId, terms, month, nameOf(payee))) reminders.reminded(uid, familyId, month)
    }

    private suspend fun nameOf(uid: String): String {
        val name = try {
            withTimeoutOrNull(NAME_WAIT_MILLIS) { pairingRepository.observeCoParentProfile(uid).first().name }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }
        return name?.takeIf { it.isNotBlank() } ?: applicationContext.inAppLanguage()
            .getString(R.string.push_actor_fallback)
    }

    /**
     * Posts the reminder, worded, dated and priced in the language the app speaks now
     * ([inAppLanguage]), and says whether it is shown ([notificationsAllowed]).
     */
    private fun post(familyId: String, terms: SupportTerms, month: YearMonth, payeeName: String): Boolean {
        val context = applicationContext
        val words = context.inAppLanguage()
        PushNotifier(words).createChannels()
        if (!notificationsAllowed(context, PushChannel.MONEY.id)) return false
        // The date and the amount in the notice's own locale, the one its words are in.
        val locale = words.resources.configuration.locales[0]
        val due = terms.dueDate(month).toJavaLocalDate().format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        )
        val amount = currencyFormat(terms.currency, locale).format(MinorUnits.toDecimal(terms.amountMinor))
        val title = words.getString(R.string.support_reminder_title, due)
        val body = words.getString(R.string.support_reminder_body, amount, payeeName)
        val key = listOf("support", familyId).hashCode()
        val tap = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(PushDestination.EXTRA, PushDestination.SUPPORT.key)
            putExtra(PushPayload.FAMILY_ID, familyId)
        }
        val notification = NotificationCompat.Builder(context, PushChannel.MONEY.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .apply {
                tap?.let {
                    setContentIntent(
                        PendingIntent.getActivity(
                            context,
                            key,
                            it,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
            .build()
        context.getSystemService(NotificationManager::class.java).notify(key, notification)
        return true
    }

    private companion object {
        const val TAG = "SupportReminderWorker"
        const val NAME_WAIT_MILLIS = 5_000L
    }
}

/**
 * [SupportReminderScheduler] over WorkManager: unique periodic work [WORK_NAME], every
 * [INTERVAL_HOURS] hours with a network, kept rather than replaced.
 */
class WorkManagerSupportReminderScheduler(
    private val context: Context
) : SupportReminderScheduler {

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<SupportReminderWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        /** The unique work's name. Part of what WorkManager persists; never renamed. */
        const val WORK_NAME = "support_reminder"
        private const val INTERVAL_HOURS = 6L
    }
}
