package com.coparently.app.data.requests

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.coparently.app.R
import com.coparently.app.data.remote.firebase.PushChannel
import com.coparently.app.data.remote.firebase.PushDestination
import com.coparently.app.data.remote.firebase.PushNotifier
import com.coparently.app.data.remote.firebase.PushPayload
import com.coparently.app.domain.changerequests.RequestDeadline
import com.coparently.app.domain.repository.PairingRepository
import com.coparently.app.utils.answerByText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The local notifications a request's clock posts on this phone (phase 3a, spec §4.3–§4.4, §5.3):
 * a reminder to the answering parent, and to the proposer that the deadline passed and that the
 * request ended with no answer.
 *
 * Local, not pushes: the reminder needs the answering parent's own clock for its quiet hours, and
 * the proposer's phone knows both of its times from the clock it wrote. Each is worded here, in
 * this phone's language, and names the other parent by name — never by role — or "Your co-parent"
 * when no name can be read in time.
 *
 * They post on the schedule channel, so a parent who muted money still hears about a handover, and
 * a tap opens the Requests inbox in the clock's family, like the request's own push (CLAUDE.md
 * item 15). Nothing is posted while notifications are not allowed.
 */
@Singleton
class RequestNotices @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pairingRepository: PairingRepository
) {

    /** Reminds the answering parent that the proposer waits for an answer until the deadline. */
    suspend fun remind(clock: RequestDeadline) {
        val name = nameOf(clock.proposerUid)
        val until = answerByText(
            clock.answerByMillis,
            locale = context.resources.configuration.locales[0],
            is24Hour = DateFormat.is24HourFormat(context)
        )
        post(
            clock,
            STEP_REMINDER,
            context.getString(R.string.request_notice_reminder_title),
            context.getString(R.string.request_notice_reminder_body, name, until)
        )
    }

    /** Tells the proposer that the answering parent did not answer by the deadline. */
    suspend fun deadlinePassed(clock: RequestDeadline) {
        val name = nameOf(clock.responderUid)
        post(
            clock,
            STEP_DEADLINE,
            context.getString(R.string.request_notice_deadline_title, name),
            context.getString(R.string.request_notice_deadline_body)
        )
    }

    /** Tells the proposer that the request is closed with no answer. */
    suspend fun noAnswer(clock: RequestDeadline) {
        val name = nameOf(clock.responderUid)
        post(
            clock,
            STEP_NO_ANSWER,
            context.getString(R.string.request_notice_no_answer_title, name),
            context.getString(R.string.request_notice_no_answer_body)
        )
    }

    /**
     * The co-parent's first name as their own profile states it, waited for at most
     * [NAME_WAIT_MILLIS]; "Your co-parent" when it is blank, unreadable or late.
     */
    private suspend fun nameOf(uid: String): String {
        val name = try {
            withTimeoutOrNull(NAME_WAIT_MILLIS) { pairingRepository.observeCoParentProfile(uid).first().name }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Any failure to read the profile words the notice with the fallback.
            null
        }
        return name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.push_actor_fallback)
    }

    /**
     * Posts one notice. Its id and its tap's request code are derived from the clock and the step,
     * so the same notice posted twice replaces itself and two clocks never replace each other.
     */
    private fun post(clock: RequestDeadline, step: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        PushNotifier(context).createChannels()
        val key = listOf(clock.id, step).hashCode()
        val tap = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(PushDestination.EXTRA, PushDestination.CHANGE_REQUESTS.key)
            putExtra(PushPayload.FAMILY_ID, clock.familyId)
        }
        val notification = NotificationCompat.Builder(context, PushChannel.SCHEDULE.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        /** How long a notice waits for the co-parent's name before it says "Your co-parent". */
        const val NAME_WAIT_MILLIS = 3_000L

        const val STEP_REMINDER = "reminder"
        const val STEP_DEADLINE = "deadline"
        const val STEP_NO_ANSWER = "no_answer"
    }
}
