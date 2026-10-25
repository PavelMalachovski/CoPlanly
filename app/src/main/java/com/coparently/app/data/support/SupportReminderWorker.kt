package com.coparently.app.data.support

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coparently.app.R
import com.coparently.app.data.payment.PaymentStore
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminds the payer of this month's child support (25 October 2026): every few hours, for each
 * family where this phone's reminder is on, it reads the agreement and this month's payment from
 * the server and posts one notice a month on the money channel when [SupportReminderRule] says so.
 *
 * Nothing in the quiet hours ([ReminderPlan.isQuietAt]) — the next run comes after them — and
 * nothing when either read fails: a reminder that is not sure keeps quiet. With no reminder left
 * on, the job cancels itself.
 */
@HiltWorker
@Suppress("LongParameterList") // WorkManager's two, and one per thing a reminder reads
class SupportReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
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
        if (ReminderPlan.isQuietAt(System.currentTimeMillis(), zone)) return Result.success()
        val today = LocalDate.now(zone)
        families.forEach { family ->
            try {
                remindIfDue(uid, family, today)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "A child support reminder could not be checked", e)
            }
        }
        return Result.success()
    }

    @Suppress("ReturnCount") // one early answer per reason a reminder is not due
    private suspend fun remindIfDue(uid: String, familyId: String, today: LocalDate) {
        val days = reminders.daysBefore(uid, familyId) ?: return
        val read = agreements.readFromServer(familyId) as? SupportRead.Known ?: return
        val terms = read.agreement.agreement?.takeIf { it.payerUid == uid } ?: return
        val month = YearMonth.from(today)
        val payment = payments.readFromServer(PaymentId.of(familyId, PaymentKind.SUPPORT, month, terms.currency))
            .getOrElse { return }
        val lastReminded = reminders.lastReminded(uid, familyId)
        if (!SupportReminderRule.shouldRemind(terms, today, days, payment != null, lastReminded)) return
        val payee = terms.payeeIn(read.agreement.participants) ?: return
        post(familyId, terms, month, nameOf(payee))
        reminders.reminded(uid, familyId, month)
    }

    private suspend fun nameOf(uid: String): String {
        val name = try {
            withTimeoutOrNull(NAME_WAIT_MILLIS) { pairingRepository.observeCoParentProfile(uid).first().name }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }
        return name?.takeIf { it.isNotBlank() } ?: applicationContext.getString(R.string.push_actor_fallback)
    }

    private fun post(familyId: String, terms: SupportTerms, month: YearMonth, payeeName: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        PushNotifier(context).createChannels()
        val due = terms.dueDate(month).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val amount = currencyFormat(terms.currency).format(terms.amount)
        val title = context.getString(R.string.support_reminder_title, due)
        val body = context.getString(R.string.support_reminder_body, amount, payeeName)
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
    }

    private companion object {
        const val TAG = "SupportReminderWorker"
        const val NAME_WAIT_MILLIS = 5_000L
    }
}

/** Starts and stops the child support reminder job. An interface so the screen can be tested. */
interface SupportReminderScheduler {
    /** Makes sure the job runs; a no-op when it already does. */
    fun schedule()

    /** Stops the job. */
    fun cancel()
}

/**
 * [SupportReminderScheduler] over WorkManager: unique periodic work [WORK_NAME], every
 * [INTERVAL_HOURS] hours with a network, kept rather than replaced.
 */
@Singleton
class WorkManagerSupportReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
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
