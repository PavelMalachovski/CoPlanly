package com.coparently.app.data.remote.firebase

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coparently.app.R
import com.coparently.app.domain.chat.ChatUri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A push as a person sees it: real data payloads through [PushNotifier] — the class
 * `CoPlanlyMessagingService.onMessageReceived` hands every message to — and the notification
 * [NotificationManager] then actually holds, read back from `activeNotifications`.
 *
 * The two-parent `e2e` job proves a push as far as its `notification_queue` document. This is the
 * other end: what the receiving phone does with the data FCM delivers. What it still cannot see
 * is FCM delivering it, and the shade as a vendor skin draws it (`docs/DEVICE-CHECKLIST.md` §3.7).
 *
 * **Which language.** The service words a push through its own `Context`, whose configuration is
 * the process's. So this builds the same thing — a configuration context in the language under
 * test — rather than switching AppCompat's per-app locale, which on API 32 and below reaches
 * activities only and never a service. On API 33+ the system applies a per-app choice to the
 * whole process, so the process configuration *is* that choice there.
 *
 * Deliberately not a Hilt test: nothing here is injected, so it also runs on the 16 KB-page
 * API 35 leg, where the Hilt tests' MockK agent cannot load. Posting is paced
 * ([POST_INTERVAL_MS]) because `cancel` is asynchronous, so a re-post of a reused id (pairing,
 * chat) can land as an *update* — and `NotificationManagerService` silently sheds a package's
 * updates above about five a second, which would look exactly like a push this app dropped.
 */
@RunWith(AndroidJUnit4::class)
class PushNotificationTest {

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager = appContext.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(appContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        PushNotifier(appContext).createChannel()
        assertTrue("notifications are enabled for the app under test", manager.areNotificationsEnabled())
        clearAll()
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    @Test
    fun everyClientType_hasWords() {
        val unworded = PushPayload.CLIENT_TYPES - PushNotifier.PUSH_TEXT.keys
        assertTrue("client push types with no wording, dropped on arrival: $unworded", unworded.isEmpty())
    }

    @Test
    fun everyWordedType_postsTheAppsOwnWords_inEnglishAndInGerman() {
        val english = localized("en")
        val german = localized("de")
        for ((type, spec) in PushNotifier.PUSH_TEXT) {
            val inEnglish = postAndRead(english, payload(type))
            val inGerman = postAndRead(german, payload(type))

            assertEquals("$type title (en)", english.getString(spec.title), inEnglish.title)
            assertEquals("$type text (en)", expectedBody(english, spec), inEnglish.text)
            assertEquals("$type title (de)", german.getString(spec.title), inGerman.title)
            assertEquals("$type text (de)", expectedBody(german, spec), inGerman.text)
            assertNotEquals("$type is worded in German, not in English", inEnglish.title, inGerman.title)
            assertNotEquals("$type text is worded in German, not in English", inEnglish.text, inGerman.text)
            assertCarriesItsNames(type, spec, inEnglish.text)
            assertCarriesItsNames(type, spec, inGerman.text)
        }
    }

    @Test
    fun everyWordedType_composesInEveryShippedLanguage() {
        for (tag in SHIPPED_LANGUAGES) {
            val notifier = PushNotifier(localized(tag))
            for ((type, spec) in PushNotifier.PUSH_TEXT) {
                val text = notifier.compose(type, payload(type))
                assertNotNull("$type composes in $tag", text)
                assertTrue("$type has a title in $tag", text!!.title.isNotBlank())
                assertCarriesItsNames("$type ($tag)", spec, text.body)
            }
        }
    }

    @Test
    fun aMissingActor_isNamedAsTheCoParent_inTheReadersLanguage() {
        val german = localized("de")
        val shown = postAndRead(german, payload(PushPayload.EVENT_CREATED) - PushPayload.ACTOR)

        assertTrue(
            "\"${shown.text}\" names the co-parent in German",
            shown.text.contains(german.getString(R.string.push_actor_fallback))
        )
    }

    @Test
    fun aChatMessage_relaysSenderAndPreview_underItsOwnId() {
        val id = PushNotifier(appContext).receive(chatPayload(), SIGNED_IN)

        assertEquals(PushNotifier.CHAT_NOTIFICATION_ID, id)
        val shown = read(awaitPosted(id!!))
        assertEquals(ACTOR_NAME, shown.title)
        assertEquals(PREVIEW, shown.text)
    }

    @Test
    fun anUnknownType_postsNothing_evenWithATitleAndBody() {
        var synced = false
        val forged = mapOf(
            PushPayload.TYPE to "account_locked",
            "title" to "Your account is locked",
            "body" to "Sign in again at example.invalid",
            PushPayload.TARGET_USER_ID to SIGNED_IN
        )

        assertNull(PushNotifier(appContext).receive(forged, SIGNED_IN) { synced = true })
        assertNull(PushNotifier(appContext).receive(forged - PushPayload.TYPE, SIGNED_IN))
        assertNothingPosted()
        // Dropped, but still evidence that something changed on the server: the sync runs.
        assertTrue("an unrecognised push still asks for a sync", synced)
    }

    @Test
    fun aGroupSwapWithoutACount_postsNothing() {
        val data = payload(PushPayload.DAY_SWAP_GROUP_OFFERED) + (PushPayload.DAY_COUNT to "several")

        assertNull(PushNotifier(appContext).receive(data, SIGNED_IN))
        assertNothingPosted()
    }

    @Test
    fun aPushForAnotherAccount_postsNothing_andSyncsNothing() {
        var synced = false
        val notifier = PushNotifier(appContext)
        val forSomebodyElse = payload(PushPayload.EVENT_CREATED) + (PushPayload.TARGET_USER_ID to "previous-owner-uid")

        assertNull(notifier.receive(forSomebodyElse, SIGNED_IN) { synced = true })
        assertNull(notifier.receive(payload(PushPayload.EVENT_CREATED), signedInUid = null) { synced = true })
        assertNothingPosted()
        assertFalse("a push for another account triggers no sync", synced)
    }

    @Test
    fun aPushNamingNoAddressee_isForWhoeverIsSignedIn() {
        // An older queue entry carries no targetUserId; it was always shown, and still is.
        val legacy = payload(PushPayload.EVENT_CREATED) - PushPayload.TARGET_USER_ID
        val id = PushNotifier(appContext).receive(legacy, SIGNED_IN)

        assertNotNull(id)
        awaitPosted(id!!)
    }

    @Test
    fun theTap_carriesTheFamily_andOpensThePairingScreen() {
        val data = payload(PushPayload.PAIRING_ACCEPTED)
        val posted = awaitPosted(PushNotifier(appContext).receive(data, SIGNED_IN)!!)
        val tap = tapIntentFor(data)

        assertEquals(PushNotifier.PAIRING_NOTIFICATION_ID, posted.id)
        assertEquals(Intent.ACTION_VIEW, tap.action)
        assertEquals("coplanly://pair", tap.dataString)
        assertEquals(appContext.packageName, tap.`package`)
        assertEquals(FAMILY, tap.getStringExtra(PushPayload.FAMILY_ID))
        assertIsTheTapOf(posted, data, tap)
    }

    @Test
    fun theTap_carriesTheFamily_andOpensTheThread() {
        val data = chatPayload()
        val posted = awaitPosted(PushNotifier(appContext).receive(data, SIGNED_IN)!!)
        val tap = tapIntentFor(data)

        assertEquals(Intent.ACTION_VIEW, tap.action)
        assertEquals(ChatUri.build(CONVERSATION), tap.dataString)
        assertEquals(appContext.packageName, tap.`package`)
        assertEquals(FAMILY, tap.getStringExtra(PushPayload.FAMILY_ID))
        assertIsTheTapOf(posted, data, tap)
    }

    @Test
    fun theTap_carriesTheFamily_andOpensTheApp() {
        val data = payload(PushPayload.EVENT_CREATED)
        val posted = awaitPosted(PushNotifier(appContext).receive(data, SIGNED_IN)!!)
        val tap = tapIntentFor(data)

        val launcher = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        assertEquals(launcher?.component, tap.component)
        assertEquals(FAMILY, tap.getStringExtra(PushPayload.FAMILY_ID))
        assertIsTheTapOf(posted, data, tap)
    }

    @Test
    fun theTap_namesNoFamily_whenThePushNamesNone() {
        val data = payload(PushPayload.EVENT_CREATED) + (PushPayload.FAMILY_ID to " ")
        val posted = awaitPosted(PushNotifier(appContext).receive(data, SIGNED_IN)!!)
        val tap = tapIntentFor(data)

        assertFalse(tap.hasExtra(PushPayload.FAMILY_ID))
        assertIsTheTapOf(posted, data, tap)
    }

    @Test
    fun twoFamilies_sameType_keepTwoTapTargets() {
        // A PendingIntent is identified by request code and intent, never by extras: were the
        // family not in the request code, the second push would overwrite the first one's family
        // and tapping the older notification would open the wrong family.
        val fromFirst = payload(PushPayload.EVENT_CREATED)
        val fromSecond = fromFirst + (PushPayload.FAMILY_ID to OTHER_FAMILY)
        val first = awaitPosted(PushNotifier(appContext).receive(fromFirst, SIGNED_IN)!!)
        SystemClock.sleep(POST_INTERVAL_MS)
        val second = awaitPosted(PushNotifier(appContext).receive(fromSecond, SIGNED_IN)!!)

        assertNotEquals(first.notification.contentIntent, second.notification.contentIntent)
        assertIsTheTapOf(first, fromFirst, tapIntentFor(fromFirst))
        assertIsTheTapOf(second, fromSecond, tapIntentFor(fromSecond))
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Title and text as the posted notification holds them. */
    private data class Shown(val title: String, val text: String)

    /** A full payload for [type]: every name a type may need, addressed to [SIGNED_IN]. */
    private fun payload(type: String): Map<String, String> = mapOf(
        PushPayload.TYPE to type,
        PushPayload.ACTOR to ACTOR_NAME,
        PushPayload.SUBJECT to SUBJECT,
        PushPayload.DATE to DATE,
        PushPayload.DAY_COUNT to DAY_COUNT.toString(),
        PushPayload.TARGET_USER_ID to SIGNED_IN,
        PushPayload.FAMILY_ID to FAMILY
    )

    private fun chatPayload(): Map<String, String> = payload(PushPayload.CHAT_MESSAGE) + mapOf(
        PushPayload.PREVIEW to PREVIEW,
        PushPayload.CONVERSATION_ID to CONVERSATION
    )

    /** What the text of a push worded by [spec] reads as in [context]'s language. */
    private fun expectedBody(context: Context, spec: PushNotifier.PushTextSpec): String = when (spec.args) {
        PushNotifier.BodyArgs.ACTOR_AND_SUBJECT -> context.getString(spec.body, ACTOR_NAME, SUBJECT)
        PushNotifier.BodyArgs.ACTOR -> context.getString(spec.body, ACTOR_NAME)
        PushNotifier.BodyArgs.DATE -> context.getString(spec.body, DATE)
        PushNotifier.BodyArgs.DAY_COUNT -> context.resources.getQuantityString(spec.body, DAY_COUNT, DAY_COUNT)
        PushNotifier.BodyArgs.NONE -> context.getString(spec.body)
    }

    /** The names the payload carried appear in [body] — a translation that drops one fails here. */
    private fun assertCarriesItsNames(label: String, spec: PushNotifier.PushTextSpec, body: String) {
        assertTrue("$label has a text", body.isNotBlank())
        val names = when (spec.args) {
            PushNotifier.BodyArgs.ACTOR_AND_SUBJECT -> listOf(ACTOR_NAME, SUBJECT)
            PushNotifier.BodyArgs.ACTOR -> listOf(ACTOR_NAME)
            PushNotifier.BodyArgs.DATE -> listOf(DATE)
            PushNotifier.BodyArgs.DAY_COUNT -> listOf(DAY_COUNT.toString())
            PushNotifier.BodyArgs.NONE -> emptyList()
        }
        for (name in names) assertTrue("$label: \"$body\" shows \"$name\"", body.contains(name))
    }

    /** Posts [data] through a notifier worded in [context]'s language and reads it back. */
    private fun postAndRead(context: Context, data: Map<String, String>): Shown {
        val id = PushNotifier(context).receive(data, SIGNED_IN)
        assertNotNull("${data[PushPayload.TYPE]} was posted", id)
        val shown = read(awaitPosted(id!!))
        manager.cancel(id)
        SystemClock.sleep(POST_INTERVAL_MS)
        return shown
    }

    private fun read(posted: StatusBarNotification): Shown {
        val extras = posted.notification.extras
        return Shown(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        )
    }

    /** `notify` is handled asynchronously by the system; waits until [id] is in the shade. */
    private fun awaitPosted(id: Int): StatusBarNotification {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            manager.activeNotifications.firstOrNull { it.id == id }?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("notification $id was never posted")
    }

    private fun assertNothingPosted() {
        SystemClock.sleep(SETTLE_MS)
        val shown = manager.activeNotifications.map { read(it) }
        assertTrue("nothing is posted, but the shade holds $shown", shown.isEmpty())
    }

    /** The intent the tap of a push carrying [data] would start, as the notifier builds it. */
    private fun tapIntentFor(data: Map<String, String>): Intent {
        val target = PushNotifier.NotificationTarget(
            text = PushNotifier.PushText("", ""),
            type = data[PushPayload.TYPE],
            conversationId = data[PushPayload.CONVERSATION_ID],
            familyId = data[PushPayload.FAMILY_ID]?.takeIf { it.isNotBlank() }
        )
        return requireNotNull(PushNotifier(appContext).tapIntent(target)) { "no tap intent for $data" }
    }

    /**
     * The posted notification's content intent *is* [tap] under the push's request code.
     *
     * A PendingIntent's intent cannot be read back through the public API, but the system can be
     * asked for the existing one matching a request code and an intent (`FLAG_NO_CREATE`): it
     * returns that very record, which equals the one on the notification only if both match.
     */
    private fun assertIsTheTapOf(posted: StatusBarNotification, data: Map<String, String>, tap: Intent) {
        val familyId = data[PushPayload.FAMILY_ID]?.takeIf { it.isNotBlank() }
        val existing = PendingIntent.getActivity(
            appContext,
            PushNotifier.requestCode(data[PushPayload.TYPE], familyId),
            tap,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        assertNotNull("a PendingIntent exists for $tap", existing)
        assertEquals(existing, posted.notification.contentIntent)
    }

    private fun localized(tag: String): Context {
        val configuration = Configuration(appContext.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        return appContext.createConfigurationContext(configuration)
    }

    /** Cancels everything and waits until the shade is empty, so a later "nothing" means nothing. */
    private fun clearAll() {
        manager.cancelAll()
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (manager.activeNotifications.isNotEmpty() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(POLL_MS)
        }
    }

    private companion object {
        const val SIGNED_IN = "push-test-uid"
        const val FAMILY = "push-test-uid__co-parent-uid"
        const val OTHER_FAMILY = "push-test-uid__second-co-parent-uid"
        const val ACTOR_NAME = "Jana"
        const val SUBJECT = "Dentist"
        const val DATE = "2026-05-14"
        const val DAY_COUNT = 3
        const val PREVIEW = "See you at five"
        const val CONVERSATION = "conversation-1"
        val SHIPPED_LANGUAGES = listOf("en", "cs", "de", "ru", "uk")

        /** Under the system's ~5 updates a second per package, with room to spare. */
        const val POST_INTERVAL_MS = 250L
        const val POLL_MS = 50L
        const val SETTLE_MS = 500L
        const val TIMEOUT_MS = 5_000L
    }
}
