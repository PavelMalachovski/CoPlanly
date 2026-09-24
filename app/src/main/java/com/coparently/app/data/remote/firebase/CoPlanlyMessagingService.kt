package com.coparently.app.data.remote.firebase

import android.util.Log
import com.coparently.app.data.crashlytics.CrashlyticsManager
import com.coparently.app.data.sync.SyncWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service for handling push notifications.
 * Extends FirebaseMessagingService to receive and process FCM messages.
 *
 * `functions/index.js`'s `sendNotification` sends a **data-only** message —
 * there is no top-level `notification` block. A message that has one is
 * auto-displayed by the OS from the system tray whenever the app is
 * backgrounded or killed, and [onMessageReceived] is never called for it in
 * that case, so this class's deep links, icon and per-type notification id
 * would only ever run while the app happened to be in the foreground. A
 * data-only message with `android.priority: "high"` is instead delivered to
 * [onMessageReceived] uniformly across foreground, background and killed
 * app states, so this class is always the one deciding how — and whether —
 * to show it. The one state neither message shape reaches is a
 * user-force-stopped app: the OS blocks FCM delivery there regardless, and a
 * `notification`-block message would have still surfaced from the tray in
 * that case where a data-only one will not.
 *
 * What a push *says* and where its tap leads is [PushNotifier]'s: this class supplies the
 * signed-in account and the sync, and hands everything else over.
 */
@AndroidEntryPoint
class CoPlanlyMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var fcmService: FcmService

    @Inject
    lateinit var crashlyticsManager: CrashlyticsManager

    override fun onCreate() {
        super.onCreate()
        PushNotifier(this).createChannels()
    }

    /**
     * Handles a push message, delivered here in every app state (see the
     * class doc) because the backend sends data-only messages.
     *
     * **This device writes the sentence** (SEC-3). The payload carries a
     * [PushPayload.TYPE] and the few names that type needs; the frame around them comes from
     * this app's own string resources, in the reader's language. It used to carry `title` and
     * `body` written by the *sending* phone and render them verbatim, which made a push able to
     * claim to be anything and made every notification English regardless of who read it.
     *
     * A type this build cannot compose is **dropped**, not rendered from whatever text happened
     * to arrive. That is the half that makes the change worth anything: a fallback to relayed
     * text would leave the forgery open under any unrecognised type. `firestore.rules` refuses a
     * client-written payload carrying `title`/`body` at all, so nothing legitimate is lost.
     *
     * `remoteMessage.notification` is no longer consulted either. It only ever appeared on a
     * message that did not come from this backend — a test push from the Firebase console — and
     * that is precisely the message whose text should not be trusted.
     *
     * The decisions — whom a push is for, what it says, where a tap leads — are
     * [PushNotifier.receive]'s, so an instrumented test can drive them with real resources; this
     * supplies the signed-in account and the sync a push triggers once it is known to be for
     * that account.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        PushNotifier(this).receive(
            data = remoteMessage.data,
            signedInUid = FirebaseAuth.getInstance().currentUser?.uid,
            onAccepted = { SyncWorker.syncNow(applicationContext) }
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save the new token to Firestore
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                fcmService.updateUserToken(token)
            } catch (e: Exception) {
                // A token that never reaches Firestore means the co-parent's pushes go nowhere,
                // and nothing on either device says so.
                Log.e(TAG, "Storing the refreshed FCM token failed", e)
                crashlyticsManager.recordException(e)
            }
        }
    }

    private companion object {
        const val TAG = "CoPlanlyMessaging"
    }
}
