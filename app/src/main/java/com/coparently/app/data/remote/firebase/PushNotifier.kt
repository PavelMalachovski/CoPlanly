package com.coparently.app.data.remote.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.coparently.app.R
import com.coparently.app.domain.chat.ChatUri
import com.coparently.app.domain.pairing.PairingUri

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
 * configuration's language. The service passes itself, whose configuration is the process's.
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
            Log.i(TAG, "Dropping a push addressed to an account that is not signed in here")
            return null
        }

        onAccepted()

        val type = data[PushPayload.TYPE]
        val text = compose(type, data) ?: return null
        // Only meaningful for chat_message (see notifyOfChatMessage in functions/index.js, which
        // is the only producer that sets it); null for every other type, and tapIntent only
        // reads it for that one branch.
        val target = NotificationTarget(
            text = text,
            type = type,
            conversationId = data[PushPayload.CONVERSATION_ID],
            familyId = data[PushPayload.FAMILY_ID]?.takeIf { it.isNotBlank() }
        )
        return show(target)
    }

    /**
     * The notification's text, written here from [type] and the payload's names.
     *
     * **This device writes the sentence** (SEC-3). The payload carries a [PushPayload.TYPE] and
     * the few names that type needs; the frame around them comes from this app's own string
     * resources, in the reader's language. A type this build cannot compose is **dropped** (null),
     * not rendered from whatever text happened to arrive: a fallback to relayed text would leave
     * the forgery open under any unrecognised type — an older notification still in the queue, a
     * newer client's type, or a forgery alike.
     *
     * A missing or blank actor name falls back to a translated "your co-parent" rather than to
     * an empty gap in the sentence. `displayName` is genuinely absent for an email/password
     * account that never set one, so this is the normal case for some pairs, not a defect.
     */
    internal fun compose(type: String?, data: Map<String, String>): PushText? {
        // The chat preview is the one notification whose text is not a frame: the title is who
        // sent it and the body is what they wrote. Both were composed server-side by
        // `notifyOfChatMessage`, which is the only party that has seen the message, and
        // `firestore.rules` refuses `chat_message` from a client — so this relays rather than
        // composes, without reopening what the rest of this function closes.
        if (type == TYPE_CHAT_MESSAGE) {
            val sender = data[PushPayload.ACTOR]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.app_name)
            return PushText(sender, data[PushPayload.PREVIEW].orEmpty())
        }

        // `type?.let` rather than `PUSH_TEXT[type]`: the map is keyed on a non-null String, so
        // indexing it with the nullable value straight off the payload does not compile.
        val spec = type?.let { PUSH_TEXT[it] } ?: return null
        return bodyOf(spec, data)?.let { body -> PushText(context.getString(spec.title), body) }
    }

    /** The text of a push worded by [spec], filled from [data]; null when [data] cannot fill it. */
    private fun bodyOf(spec: PushTextSpec, data: Map<String, String>): String? {
        val actor = data[PushPayload.ACTOR]?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.push_actor_fallback)
        return when (spec.args) {
            BodyArgs.ACTOR_AND_SUBJECT -> context.getString(spec.body, actor, data[PushPayload.SUBJECT].orEmpty())
            BodyArgs.ACTOR -> context.getString(spec.body, actor)
            BodyArgs.DATE -> context.getString(spec.body, data[PushPayload.DATE].orEmpty())
            // An unparseable count composes nothing rather than announcing "0 days" — the same
            // rule as an unrecognised type. Only this build's own writer produces it.
            BodyArgs.DAY_COUNT -> data[PushPayload.DAY_COUNT]?.toIntOrNull()?.let { count ->
                context.resources.getQuantityString(spec.body, count, count)
            }
            BodyArgs.NONE -> context.getString(spec.body)
        }
    }

    /**
     * Where tapping [target] leads: pairing events into the pairing screen, a chat message into
     * its conversation (or the Chat tab's list when the conversation id is null — a manual test
     * push or an older payload), and everything else into the app's launcher activity.
     *
     * **The family rides along as an intent extra** ([PushPayload.FAMILY_ID], M-8), and
     * `MainActivity` switches to it before it hands the link to navigation — a push from the
     * family this device is not showing must not open on the other family's screens.
     */
    internal fun tapIntent(target: NotificationTarget): Intent? {
        val intent = when (target.type) {
            TYPE_PAIRING_ACCEPTED, TYPE_PAIRING_REMOVED ->
                Intent(Intent.ACTION_VIEW, Uri.parse(PAIRING_DEEP_LINK)).setPackage(context.packageName)
            TYPE_CHAT_MESSAGE ->
                Intent(Intent.ACTION_VIEW, Uri.parse(ChatUri.build(target.conversationId)))
                    .setPackage(context.packageName)
            else -> context.packageManager.getLaunchIntentForPackage(context.packageName)
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
     * replace the first in the tray rather than stack next to it. A chat message reuses
     * [CHAT_NOTIFICATION_ID] for the same reason — the latest preview is what matters, and the
     * thread itself holds the full history — and, being a distinct constant, never collides with
     * (or is replaced by) a pairing notification. Every other notification type keeps a
     * timestamp id so unrelated notifications keep accumulating.
     */
    private fun show(target: NotificationTarget): Int {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode(target.type, target.familyId),
            tapIntent(target),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(target.text.title)
            .setContentText(target.text.body)
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
            TYPE_CHAT_MESSAGE -> CHAT_NOTIFICATION_ID
            else -> System.currentTimeMillis().toInt()
        }
        notificationManager.notify(notificationId, notification)
        return notificationId
    }

    /**
     * Creates the notification channel on Android O and above.
     *
     * The name and description are what system Settings shows for this channel, so they are
     * resources (CQ-14). Re-creating an existing channel updates both, which is how a language
     * change reaches it.
     */
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.push_channel_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** A composed notification, ready to render. */
    internal data class PushText(val title: String, val body: String)

    /**
     * A notification and where tapping it leads.
     *
     * @property conversationId The `data["conversationId"]` read off the message; only
     *   meaningful (and only ever non-null) for [TYPE_CHAT_MESSAGE].
     * @property familyId The family the push belongs to ([PushPayload.FAMILY_ID]), or null for
     *   a payload from an older sender or one that names no family.
     */
    internal data class NotificationTarget(
        val text: PushText,
        val type: String?,
        val conversationId: String?,
        val familyId: String?
    )

    /** Which of the payload's names a body string takes, in order. */
    internal enum class BodyArgs { ACTOR_AND_SUBJECT, ACTOR, DATE, DAY_COUNT, NONE }

    /**
     * A type's wording: the frame, and what fills it.
     *
     * [body] is a plurals resource, not a string, when [args] is [BodyArgs.DAY_COUNT] — Czech,
     * Russian and Ukrainian each need three forms for "N days", so a count cannot go through
     * `getString`. Kotlin cannot express that in the type, hence this note.
     */
    internal data class PushTextSpec(
        @StringRes val title: Int,
        val body: Int,
        val args: BodyArgs
    )

    internal companion object {
        /** Log tag; `docs/DEVICE-CHECKLIST.md` §3.7 tells a tester to filter on it. */
        private const val TAG = "CoPlanlyMessaging"

        /** The one channel every push is posted to. */
        const val CHANNEL_ID = "coparently_notifications"

        // The three server-only types, aliased from `PushPayload` rather than re-declared.
        // They were literals here and in the sending code, in the rules and in
        // `functions/index.js`; one of those four drifting is a notification that silently
        // stops being recognised, which looks exactly like a push that was never sent.

        /** Queued by `acceptPairingInvitation` (`functions/index.js`) for the inviter. */
        private const val TYPE_PAIRING_ACCEPTED = PushPayload.PAIRING_ACCEPTED

        /** Queued by `unpairCoParent` (`functions/index.js`) for the ex-partner. */
        private const val TYPE_PAIRING_REMOVED = PushPayload.PAIRING_REMOVED

        /** Queued by `onChatMessageCreated` (`functions/index.js`) for the message recipient. */
        private const val TYPE_CHAT_MESSAGE = PushPayload.CHAT_MESSAGE

        /**
         * Opens the pairing screen with no prefilled code — see
         * [MainActivity][com.coparently.app.presentation.MainActivity]'s
         * `readPairingCode`, which treats a code-less pairing link as "land on
         * the pairing screen with nothing pre-filled" rather than a no-op.
         */
        internal val PAIRING_DEEP_LINK = "${PairingUri.SCHEME}://${PairingUri.HOST}"

        /**
         * Stable id shared by both pairing notification types so a newer one
         * replaces an older one in the tray instead of stacking (see [show]).
         */
        const val PAIRING_NOTIFICATION_ID = 918_273

        /**
         * Stable id for a chat-message notification, distinct from
         * [PAIRING_NOTIFICATION_ID] so neither type ever replaces the other in
         * the tray (see [show]).
         */
        const val CHAT_NOTIFICATION_ID = 918_274

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

        /**
         * Every notification this build knows how to word.
         *
         * A table rather than a `when`, so the set of types the app will render is one list a
         * reader can check against `PushPayload` and against `firestore.rules` — the three have
         * to agree, and two of them being code paths spread over a file would make that hard to
         * see. A type absent from here is dropped on arrival (see [compose]), which is also why
         * adding a type to `PushPayload` without adding it here is a push that silently never
         * appears. `tools/check-invariants.js` reads this table.
         *
         * `chat_message` is deliberately not in it: its text is not a frame, and [compose]
         * handles it before consulting this.
         */
        val PUSH_TEXT: Map<String, PushTextSpec> = mapOf(
            PushPayload.EVENT_CREATED to PushTextSpec(
                R.string.push_event_created_title,
                R.string.push_event_created_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.EVENT_UPDATED to PushTextSpec(
                R.string.push_event_updated_title,
                R.string.push_event_updated_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.EVENT_DELETED to PushTextSpec(
                R.string.push_event_deleted_title,
                R.string.push_event_deleted_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.CHILD_INFO_UPDATED to PushTextSpec(
                R.string.push_child_info_updated_title,
                R.string.push_child_info_updated_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.CHANGE_REQUEST_CREATED to PushTextSpec(
                R.string.push_change_request_created_title,
                R.string.push_change_request_created_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.CHANGE_REQUEST_ACCEPTED to PushTextSpec(
                R.string.push_change_request_accepted_title,
                R.string.push_change_request_accepted_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.CHANGE_REQUEST_DECLINED to PushTextSpec(
                R.string.push_change_request_declined_title,
                R.string.push_change_request_declined_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.CHANGE_REQUEST_CANCELLED to PushTextSpec(
                R.string.push_change_request_cancelled_title,
                R.string.push_change_request_cancelled_body,
                BodyArgs.ACTOR_AND_SUBJECT
            ),
            PushPayload.CUSTODY_PROPOSAL_PROPOSED to PushTextSpec(
                R.string.push_custody_proposal_proposed_title,
                R.string.push_custody_proposal_proposed_body,
                BodyArgs.NONE
            ),
            PushPayload.CUSTODY_PROPOSAL_ACCEPTED to PushTextSpec(
                R.string.push_custody_proposal_accepted_title,
                R.string.push_custody_proposal_accepted_body,
                BodyArgs.NONE
            ),
            PushPayload.CUSTODY_PROPOSAL_DECLINED to PushTextSpec(
                R.string.push_custody_proposal_declined_title,
                R.string.push_custody_proposal_declined_body,
                BodyArgs.NONE
            ),
            PushPayload.DAY_SWAP_OFFERED to PushTextSpec(
                R.string.push_day_swap_offered_title,
                R.string.push_day_swap_offered_body,
                BodyArgs.DATE
            ),
            PushPayload.DAY_SWAP_ACCEPTED to PushTextSpec(
                R.string.push_day_swap_accepted_title,
                R.string.push_day_swap_accepted_body,
                BodyArgs.DATE
            ),
            PushPayload.DAY_SWAP_DECLINED to PushTextSpec(
                R.string.push_day_swap_declined_title,
                R.string.push_day_swap_declined_body,
                BodyArgs.DATE
            ),
            PushPayload.DAY_SWAP_GROUP_OFFERED to PushTextSpec(
                R.string.push_day_swap_offered_title,
                R.plurals.push_day_swap_group_offered_body,
                BodyArgs.DAY_COUNT
            ),
            PushPayload.DAY_SWAP_GROUP_ACCEPTED to PushTextSpec(
                R.string.push_day_swap_accepted_title,
                R.plurals.push_day_swap_group_accepted_body,
                BodyArgs.DAY_COUNT
            ),
            PushPayload.DAY_SWAP_GROUP_DECLINED to PushTextSpec(
                R.string.push_day_swap_declined_title,
                R.plurals.push_day_swap_group_declined_body,
                BodyArgs.DAY_COUNT
            ),
            PushPayload.SPLIT_RATIO_PROPOSED to PushTextSpec(
                R.string.push_split_ratio_proposed_title,
                R.string.push_split_ratio_proposed_body,
                BodyArgs.NONE
            ),
            PushPayload.SPLIT_RATIO_ACCEPTED to PushTextSpec(
                R.string.push_split_ratio_accepted_title,
                R.string.push_split_ratio_accepted_body,
                BodyArgs.NONE
            ),
            PushPayload.SPLIT_RATIO_DECLINED to PushTextSpec(
                R.string.push_split_ratio_declined_title,
                R.string.push_split_ratio_declined_body,
                BodyArgs.NONE
            ),
            PushPayload.SPLIT_RATIO_AGREED to PushTextSpec(
                R.string.push_split_ratio_agreed_title,
                R.string.push_split_ratio_agreed_body,
                BodyArgs.NONE
            ),
            PushPayload.RECORDS_SHARED to PushTextSpec(
                R.string.push_records_shared_title,
                R.string.push_records_shared_body,
                BodyArgs.ACTOR
            ),
            PushPayload.PAIRING_ACCEPTED to PushTextSpec(
                R.string.push_pairing_accepted_title,
                R.string.push_pairing_accepted_body,
                BodyArgs.ACTOR
            ),
            PushPayload.PAIRING_REMOVED to PushTextSpec(
                R.string.push_pairing_removed_title,
                R.string.push_pairing_removed_body,
                BodyArgs.ACTOR
            ),
            PushPayload.PROFESSIONAL_ACCESS_REQUESTED to PushTextSpec(
                R.string.push_professional_access_requested_title,
                R.string.push_professional_access_requested_body,
                BodyArgs.ACTOR
            )
        )
    }
}
