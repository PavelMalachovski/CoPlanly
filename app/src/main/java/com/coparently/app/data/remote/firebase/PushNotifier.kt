package com.coparently.app.data.remote.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.coparently.app.R
import com.coparently.app.data.platform.logger
import com.coparently.app.domain.activity.ActivityAnnouncement
import com.coparently.app.domain.chat.ChatUri
import com.coparently.app.domain.pairing.PairingUri
import com.coparently.app.pushwording.PushText
import com.coparently.app.pushwording.PushWording
import com.coparently.app.utils.pushWording

/**
 * Turns a push's data into the notification a person sees — or into nothing.
 *
 * [CoPlanlyMessagingService] owns the FCM entry points and hands every message here, with the
 * signed-in uid and the sync to run. The decisions live in this class rather than in the service
 * so an instrumented test can drive them with a real [Context] and read what
 * [NotificationManager] actually shows: a `FirebaseMessagingService` cannot be constructed with
 * a base context outside the framework, and the service reads the signed-in account from the
 * default `FirebaseApp`, which a test build does not have.
 *
 * Every string is resolved through [context], so the notification is worded in *its*
 * configuration's language. The service and the workers pass a context in the language the app
 * speaks (`inAppLanguage()`, the owner's rule); a test passes one in the language it checks.
 *
 * @param context The context whose resources word the notification and which posts it.
 */
class PushNotifier(private val context: Context) {

    /**
     * Handles one push's data: drops one addressed to somebody else, runs [onAccepted], then
     * posts the notification when this build can word its type.
     *
     * A push addressed to somebody else is dropped whole — no sync, no notification. The token
     * identifies this *device*, and the device may since have signed in as a different person:
     * until `FcmService.unregisterToken` ran on sign-out, and on any sign-out that could not reach
     * the network, the previous account's pushes still arrived here. `sendNotification` stamps the
     * addressee into the data for exactly this check; a push with none is an older queue entry
     * and is treated as addressed to whoever is signed in, as it always was.
     *
     * [onAccepted] runs *before* deciding whether the push is renderable. A push announces a
     * change the device could not yet see: events, child records and pets are downloaded only by
     * the fifteen-minute worker, so tapping the notification opened an app that still knew
     * nothing about the thing it had just announced — and accepting a proposed change failed
     * outright, because the event was not in Room. Running the sync even for a type this build
     * has no wording for is deliberate: an unrecognised push is still evidence that something
     * changed on the server.
     *
     * @param data The message's data payload.
     * @param signedInUid The account signed in on this device, or null.
     * @param onAccepted Runs once the push is known to be for [signedInUid] (the service syncs).
     * @return The id of the notification posted, or null when nothing was posted.
     */
    fun receive(data: Map<String, String>, signedInUid: String?, onAccepted: () -> Unit = {}): Int? {
        val addressee = data[PushPayload.TARGET_USER_ID]?.takeIf { it.isNotBlank() }
        if (signedInUid == null || (addressee != null && addressee != signedInUid)) {
            logger.i(TAG, "Dropping a push addressed to an account that is not signed in here")
            return null
        }

        onAccepted()

        val type = data[PushPayload.TYPE]
        val text = compose(type, data) ?: return null
        // Only meaningful for chat_message and coparent_account_deleted (notifyOfChatMessage and
        // deleteAccount in functions/index.js are the only producers that set it); null for every
        // other type, and tapIntent only reads it for those two.
        val target = NotificationTarget(
            text = text,
            type = type,
            conversationId = data[PushPayload.CONVERSATION_ID],
            familyId = data[PushPayload.FAMILY_ID]?.takeIf { it.isNotBlank() },
            card = if (type == TYPE_CHAT_MESSAGE) chatCardOf(data) else null
        )
        return show(target)
    }

    /**
     * The notification's text: `:push-wording`'s [PushWording.compose] (iPhone phase 4 part C), in
     * [context]'s language and on its clock. A type it cannot word is null, and nothing is posted
     * (SEC-3: dropped, never shown with a fallback); the rules of the wording are the module's.
     */
    internal fun compose(type: String?, data: Map<String, String>): PushText? =
        context.pushWording().compose(type, data)

    /**
     * Where tapping [target] leads: pairing events into the pairing screen, a chat message into
     * its conversation (or the Chat tab's list when the conversation id is null — a manual test
     * push or an older payload), and everything else into the app's launcher activity, naming
     * the screen it is about as [PushDestination.EXTRA] (D-13) — the calendar for an event, the
     * inbox for a change request, Home for an ask waiting on this parent. A type with no
     * destination opens the app as it always did.
     *
     * **The family rides along as an intent extra** ([PushPayload.FAMILY_ID], M-8), and
     * `MainActivity` switches to it before it hands the link to navigation — a push from the
     * family this device is not showing must not open on the other family's screens.
     */
    internal fun tapIntent(target: NotificationTarget): Intent? {
        val intent = when (target.type) {
            TYPE_PAIRING_ACCEPTED, TYPE_PAIRING_REMOVED ->
                Intent(Intent.ACTION_VIEW, Uri.parse(PAIRING_DEEP_LINK)).setPackage(context.packageName)
            // A departed co-parent's notice opens the thread it is about: the banner there says
            // the same thing and carries the export action.
            TYPE_CHAT_MESSAGE, TYPE_COPARENT_ACCOUNT_DELETED ->
                Intent(Intent.ACTION_VIEW, Uri.parse(ChatUri.build(target.conversationId)))
                    .setPackage(context.packageName)
            else -> context.packageManager.getLaunchIntentForPackage(context.packageName)?.also { launch ->
                PushRouting.destinationOf(target.type)?.let { launch.putExtra(PushDestination.EXTRA, it.key) }
            }
        }
        target.familyId?.let { intent?.putExtra(PushPayload.FAMILY_ID, it) }
        return intent
    }

    /**
     * Shows [target] and returns its notification id.
     *
     * Pairing notifications reuse [PAIRING_NOTIFICATION_ID] instead of a timestamp-derived id: a
     * `pairing_accepted` followed by a `pairing_removed` (or vice versa) describes the *current*
     * pairing state, not two separate things worth reviewing together, so the second should
     * replace the first in the tray rather than stack next to it. A chat message reuses one id per
     * conversation ([chatNotificationId]) for the same reason — the latest preview is what matters,
     * and the thread itself holds the full history — and a chat card one of its own. Every other
     * notification type keeps a timestamp id so unrelated notifications keep accumulating.
     *
     * **The body is set twice, on purpose** (phase 3a). `setContentText` is the collapsed line and
     * what `EXTRA_TEXT` holds; a [NotificationCompat.BigTextStyle] carries the whole body for when
     * the notification is expanded. A plain notification cuts a long body to one line, which for a
     * request's first push is exactly where its deadline sits ("Answer by <time>.") and for a last
     * chance where its time sits ("until <time>"). Every push gets the style, not only those.
     */
    private fun show(target: NotificationTarget): Int {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode(target.type, target.familyId),
            tapIntent(target),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, PushRouting.channelOf(target.type).id)
            .setContentTitle(target.text.title)
            .setContentText(target.text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(target.text.body))
            // android.R.drawable.ic_dialog_info is a framework placeholder and
            // renders as a grey blob in the status bar.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationId = when (target.type) {
            TYPE_PAIRING_ACCEPTED, TYPE_PAIRING_REMOVED -> PAIRING_NOTIFICATION_ID
            TYPE_CHAT_MESSAGE -> chatNotificationId(target.conversationId ?: target.familyId, target.card)
            else -> System.currentTimeMillis().toInt()
        }
        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    /**
     * Creates the four notification channels on Android O and above, one per [PushChannel], and
     * deletes the single channel every push used to share (D-13).
     *
     * The names and descriptions are what system Settings shows, so they are resources (CQ-14).
     * Re-creating an existing channel updates both, which is how a language change reaches it,
     * and leaves the importance a person chose alone.
     */
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            PushChannel.entries.forEach { kind ->
                val channel = NotificationChannel(
                    kind.id,
                    context.getString(kind.nameRes),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(kind.descriptionRes)
                }
                manager.createNotificationChannel(channel)
            }
            manager.deleteNotificationChannel(PushChannel.LEGACY_ID)
        }
    }

    /**
     * A notification and where tapping it leads.
     *
     * @property conversationId The `data["conversationId"]` read off the message; only
     *   meaningful (and only ever non-null) for [TYPE_CHAT_MESSAGE] and
     *   [TYPE_COPARENT_ACCOUNT_DELETED].
     * @property familyId The family the push belongs to ([PushPayload.FAMILY_ID]), or null for
     *   a payload from an older sender or one that names no family.
     * @property card The chat card a [TYPE_CHAT_MESSAGE] push carries, which gives it a tray id of
     *   its own ([chatNotificationId]); null for a message, and for every other type.
     */
    internal data class NotificationTarget(
        val text: PushText,
        val type: String?,
        val conversationId: String?,
        val familyId: String?,
        val card: ActivityAnnouncement? = null
    )

    /**
     * The chat card a `chat_message` push carries ([PushPayload.ACTIVITY_KIND] and its siblings),
     * read as the thread reads a message's `activity`: null for a plain message, a push from before
     * the function sent the facts, and a kind or entity this build does not know.
     */
    private fun chatCardOf(data: Map<String, String>): ActivityAnnouncement? =
        ActivityAnnouncement.fromMap(
            mapOf(
                "kind" to data[PushPayload.ACTIVITY_KIND],
                "entityType" to data[PushPayload.ACTIVITY_ENTITY_TYPE],
                "entityId" to data[PushPayload.ACTIVITY_ENTITY_ID],
                "title" to data[PushPayload.ACTIVITY_TITLE],
                "whenIso" to data[PushPayload.ACTIVITY_WHEN_ISO],
                "amount" to data[PushPayload.ACTIVITY_AMOUNT],
                "currency" to data[PushPayload.ACTIVITY_CURRENCY]
            )
        )

    internal companion object {
        /** Log tag; `docs/DEVICE-CHECKLIST.md` §3.7 tells a tester to filter on it. */
        private const val TAG = "CoPlanlyMessaging"

        // The server-only types with a tap or a tray id of their own, aliased from `PushPayload`
        // rather than re-declared. They were literals here and in the sending code, in the rules
        // and in `functions/index.js`; one of those four drifting is a notification that silently
        // stops being recognised, which looks exactly like a push that was never sent.

        /** Queued by `acceptPairingInvitation` (`functions/index.js`) for the inviter. */
        private const val TYPE_PAIRING_ACCEPTED = PushPayload.PAIRING_ACCEPTED

        /** Queued by `unpairCoParent` (`functions/index.js`) for the ex-partner. */
        private const val TYPE_PAIRING_REMOVED = PushPayload.PAIRING_REMOVED

        /** Queued by `onChatMessageCreated` (`functions/index.js`) for the message recipient. */
        private const val TYPE_CHAT_MESSAGE = PushPayload.CHAT_MESSAGE

        /** Queued by `deleteAccount` (`functions/index.js`) for the parent who remains. */
        private const val TYPE_COPARENT_ACCOUNT_DELETED = PushPayload.COPARENT_ACCOUNT_DELETED

        /**
         * Opens the pairing screen with no prefilled code — see
         * [MainActivity][com.coparently.app.presentation.MainActivity]'s
         * `readLaunchLink`, which treats a code-less pairing link as "land on
         * the pairing screen with nothing pre-filled" rather than a no-op.
         */
        internal val PAIRING_DEEP_LINK = "${PairingUri.SCHEME}://${PairingUri.HOST}"

        /**
         * Stable id shared by both pairing notification types so a newer one
         * replaces an older one in the tray instead of stacking (see [show]).
         */
        const val PAIRING_NOTIFICATION_ID = 918_273

        /**
         * The tray id of a chat push (notification audit, 9 November 2026): one per conversation
         * for its messages, so the latest message replaces the one before it, and one per card
         * (its kind and what it is about), so a card never replaces a message. Every chat push
         * used to share one fixed id: an expense's card a second after "pick up Emma at five"
         * took its place in the tray, and the second family's message took the first family's.
         *
         * @param conversation The conversation (or, failing that, the family) the push is from.
         * @param card The card the push carries, or null for a message.
         */
        fun chatNotificationId(conversation: String?, card: ActivityAnnouncement?): Int =
            if (card == null) {
                listOf("chat", conversation.orEmpty()).hashCode()
            } else {
                listOf("chat-card", conversation.orEmpty(), card.kind.name, card.entityId).hashCode()
            }

        /**
         * The PendingIntent request code for a push of [type] from [familyId].
         *
         * Distinct per notification type so a pairing-accepted notification's tap target can
         * never overwrite a differently-typed one's PendingIntent (PendingIntent identity is
         * request code + intent action/data/component, *not* extras), and per family: two
         * same-typed pushes from two families would otherwise share one PendingIntent,
         * `FLAG_UPDATE_CURRENT` would overwrite the first one's family with the second's, and
         * tapping the older notification would switch to the wrong family. A push with no family
         * keeps the type-only code it always had.
         */
        fun requestCode(type: String?, familyId: String?): Int =
            familyId?.let { listOf(type, it).hashCode() } ?: type?.hashCode() ?: 0
    }
}
